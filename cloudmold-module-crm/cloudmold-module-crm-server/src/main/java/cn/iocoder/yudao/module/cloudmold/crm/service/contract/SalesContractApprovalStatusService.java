package cn.iocoder.yudao.module.cloudmold.crm.service.contract;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.cloudmold.crm.dal.dataobject.contract.SalesContractRecords.Operation;
import cn.iocoder.yudao.module.cloudmold.crm.dal.dataobject.contract.SalesContractRecords.SalesContract;
import cn.iocoder.yudao.module.cloudmold.crm.dal.dataobject.contract.SalesContractRecords.StatusHistory;
import cn.iocoder.yudao.module.cloudmold.crm.dal.mysql.contract.SalesContractMapper;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SalesContractApprovalStatusService {

    private static final String STATUS_PENDING_APPROVAL = "PENDING_APPROVAL";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final String STATUS_CANCELLED = "CANCELLED";
    private static final String EVENT_TYPE = "crm.sales_contract.status_changed";

    private final SalesContractMapper mapper;
    private final SalesContractActorPrincipalPort actorPrincipalPort;
    private final OutboxAppender outboxAppender;

    @Transactional(rollbackFor = Exception.class)
    public void complete(BpmProcessInstanceStatusEvent event) {
        require(event != null, "BPM status event is required");
        require(BpmProcessInstanceStatusEnum.isProcessEndStatus(event.getStatus()),
                "only terminal BPM status may complete a sales contract approval");
        requireRef(event.getBusinessKey(), "businessKey");
        requireRef(event.getId(), "processInstanceId");
        require(event.getTerminalOperatorUserId() != null && event.getTerminalOperatorUserId() > 0,
                "terminal BPM operator is required for sales contract approval attribution");

        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String actorPrincipalId = actorPrincipalPort.resolveSystemAdmin(event.getTerminalOperatorUserId());
        actorPrincipalPort.requireActive(actorPrincipalId);
        String nextStatus = mapStatus(event.getStatus());
        LocalDateTime now = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        String idempotencyKey = "bpm:" + event.getId() + ":" + event.getStatus();
        String requestHash = DigestUtil.sha256Hex(event.getBusinessKey() + "\n" + event.getId() + "\n"
                + event.getStatus() + "\n" + event.getTerminalOperatorUserId());
        String attemptToken = UUID.randomUUID().toString();
        mapper.insertOrResolveOperation(tenantId, idempotencyKey, "COMPLETE_APPROVAL",
                requestHash, attemptToken, now);
        Long operationId = mapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve approval operation");
        Operation operation = mapper.selectOperationForUpdate(tenantId, operationId);
        require(operation != null, "sales contract approval operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(requestHash, operation.getRequestHash()),
                    "BPM approval event conflicts with an existing operation");
            require(operation.getStatus() == SalesContractService.OPERATION_SUCCEEDED,
                    "existing BPM approval operation is incomplete");
            return;
        }

        SalesContract contract = mapper.selectSalesContractForUpdate(tenantId, event.getBusinessKey());
        require(contract != null, "sales contract for BPM event does not exist");
        require(STATUS_PENDING_APPROVAL.equals(contract.getStatus()),
                "sales contract is not pending approval");
        require(event.getId().equals(contract.getApprovalProcessInstanceId()),
                "BPM process instance does not match sales contract");
        Long nextVersion = contract.getVersion() + 1;
        require(mapper.completeApproval(tenantId, contract.getSalesContractId(), event.getId(),
                        contract.getVersion(), nextVersion, nextStatus, actorPrincipalId, now) == 1,
                "sales contract approval completion conflict");
        require(mapper.insertStatusHistory(new StatusHistory()
                        .setTenantId(tenantId)
                        .setSalesContractId(contract.getSalesContractId())
                        .setOperationId(operationId)
                        .setAggregateVersion(nextVersion)
                        .setStatus(nextStatus)
                        .setActorPrincipalId(actorPrincipalId)
                        .setActorAdminUserId(event.getTerminalOperatorUserId())
                        .setReasonCode(reasonCode(event.getStatus()))
                        .setApprovalProcessInstanceId(event.getId())
                        .setOccurredAt(now)
                        .setCreatedAt(now)) == 1,
                "failed to persist sales contract approval history");

        contract.setStatus(nextStatus).setUpdatedByPrincipalId(actorPrincipalId)
                .setVersion(nextVersion).setUpdatedAt(now);
        appendEvent(tenantId, contract, event, now);
        require(mapper.markOperationSucceeded(tenantId, operationId, "sales_contract",
                        contract.getSalesContractId(), JsonUtils.toJsonString(Map.of(
                                "salesContractId", contract.getSalesContractId(),
                                "status", nextStatus,
                                "version", nextVersion)), now) == 1,
                "sales contract approval operation completion conflict");
    }

    private void appendEvent(Long tenantId, SalesContract contract,
                             BpmProcessInstanceStatusEvent event, LocalDateTime now) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sales_contract_id", contract.getSalesContractId());
        payload.put("customer_id", contract.getCustomerId());
        payload.put("previous_status", STATUS_PENDING_APPROVAL);
        payload.put("status", contract.getStatus());
        payload.put("currency_code", contract.getCurrencyCode());
        payload.put("total_amount_minor", contract.getTotalAmountMinor());
        payload.put("approval_process_instance_id", event.getId());
        payload.put("reason_code", reasonCode(event.getStatus()));
        outboxAppender.append(AppendDomainEventCommand.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(EVENT_TYPE)
                .schemaVersion(1)
                .sourceSystem("cloudmold-crm")
                .tenantId(tenantId)
                .aggregateType("sales_contract")
                .aggregateId(contract.getSalesContractId())
                .aggregateVersion(contract.getVersion())
                .eventSequence((short) 1)
                .occurredAt(now.toInstant(ZoneOffset.UTC))
                .traceId("bpm:" + event.getId())
                .correlationId(event.getId())
                .causationId(event.getTerminalTaskId() == null ? event.getId() : event.getTerminalTaskId())
                .idempotencyKey("bpm:" + event.getId() + ":" + event.getStatus() + ":" + EVENT_TYPE)
                .payload(payload)
                .headers(Map.of(
                        "status", contract.getStatus(),
                        "actor_admin_user_id", String.valueOf(event.getTerminalOperatorUserId()),
                        "approval_process_instance_id", event.getId()))
                .destination("lakehouse")
                .build());
    }

    private static String mapStatus(Integer status) {
        if (BpmProcessInstanceStatusEnum.APPROVE.getStatus().equals(status)) {
            return STATUS_ACTIVE;
        }
        if (BpmProcessInstanceStatusEnum.REJECT.getStatus().equals(status)) {
            return STATUS_REJECTED;
        }
        if (BpmProcessInstanceStatusEnum.CANCEL.getStatus().equals(status)) {
            return STATUS_CANCELLED;
        }
        throw new IllegalArgumentException("unsupported BPM terminal status");
    }

    private static String reasonCode(Integer status) {
        return "BPM_" + BpmProcessInstanceStatusEnum.valueOf(status).name();
    }

    private static String requireRef(String value, String field) {
        require(value != null && !value.isBlank() && value.length() <= 128,
                field + " must be a safe opaque reference");
        return value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
