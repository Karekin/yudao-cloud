package cn.iocoder.yudao.module.cloudmold.agentcontrol.integration.bpm;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.*;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.mysql.AgentControlStoreMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "cloudmold.agent-control.approval-workflow",
        name = "enabled", havingValue = "true")
public class AgentApprovalWorkflowService implements AgentApprovalWorkflowRegistrar {

    private static final int ERROR_CODE_LIMIT = 128;
    private static final Pattern PROCESS_DEFINITION_KEY =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");

    private final AgentControlStoreMapper mapper;
    private final AgentApprovalWorkflowAdapter adapter;
    private final AgentApprovalWorkflowProperties properties;
    private final Clock clock;

    public AgentApprovalWorkflowService(AgentControlStoreMapper mapper, AgentApprovalWorkflowAdapter adapter,
                                        AgentApprovalWorkflowProperties properties) {
        this(mapper, adapter, properties, Clock.systemUTC());
    }

    AgentApprovalWorkflowService(AgentControlStoreMapper mapper, AgentApprovalWorkflowAdapter adapter,
                                 AgentApprovalWorkflowProperties properties, Clock clock) {
        this.mapper = mapper;
        this.adapter = adapter;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void register(Long tenantId, Approval approval, WorkOrder workOrder, LocalDateTime now) {
        require(tenantId != null && tenantId > 0, "tenant is required");
        require(approval != null && workOrder != null, "approval and work order are required");
        require(Objects.equals(tenantId, approval.getTenantId())
                        && Objects.equals(tenantId, workOrder.getTenantId())
                        && Objects.equals(approval.getWorkOrderId(), workOrder.getWorkOrderId()),
                "approval workflow registration crosses a tenant or work-order boundary");
        require(PROCESS_DEFINITION_KEY.matcher(
                        StrUtil.nullToEmpty(properties.getProcessDefinitionKey())).matches(),
                "BPM process definition key is invalid");
        String businessKey = "cloudmold-agent-approval:" + tenantId + ":" + approval.getApprovalId();
        ApprovalWorkflowBinding binding = new ApprovalWorkflowBinding()
                .setApprovalId(approval.getApprovalId()).setTenantId(tenantId)
                .setWorkOrderId(workOrder.getWorkOrderId()).setActionCode(workOrder.getActionCode())
                .setRoleCode(workOrder.getRoleCode()).setRiskLevel(workOrder.getRiskLevel())
                .setRequesterUserId(approval.getRequesterUserId())
                .setScopeHash(approval.getScopeHash())
                .setProcessDefinitionKey(properties.getProcessDefinitionKey()).setBusinessKey(businessKey)
                .setStatus("START_REQUESTED").setStartAttemptCount(0).setVersion(1L)
                .setRequestedAt(now).setUpdatedAt(now);
        if (mapper.insertApprovalWorkflowBinding(binding) == 1) {
            return;
        }
        ApprovalWorkflowBinding existing = mapper.selectApprovalWorkflowBinding(tenantId, approval.getApprovalId());
        require(existing != null
                        && Objects.equals(existing.getWorkOrderId(), binding.getWorkOrderId())
                        && Objects.equals(existing.getScopeHash(), binding.getScopeHash())
                        && Objects.equals(existing.getProcessDefinitionKey(), binding.getProcessDefinitionKey())
                        && Objects.equals(existing.getBusinessKey(), binding.getBusinessKey()),
                "approval workflow binding conflicts with the frozen approval scope");
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean start(ApprovalWorkflowStartCandidate candidate) {
        String attemptToken = UUID.randomUUID().toString();
        LocalDateTime now = now();
        if (mapper.claimApprovalWorkflowStart(candidate.getTenantId(), candidate.getApprovalId(),
                candidate.getVersion(), candidate.getApproverUserId(), attemptToken, now) != 1) {
            return false;
        }
        try {
            String processInstanceId = adapter.start(candidate);
            int updated = mapper.markApprovalWorkflowRunning(candidate.getTenantId(), candidate.getApprovalId(),
                    attemptToken, processInstanceId, now());
            if (updated == 0) {
                ApprovalWorkflowBinding current = mapper.selectApprovalWorkflowBinding(
                        candidate.getTenantId(), candidate.getApprovalId());
                require(current != null && current.getStatus().startsWith("BPM_")
                                && Objects.equals(current.getProcessInstanceId(), processInstanceId),
                        "approval workflow start completion conflict");
            }
            return true;
        } catch (RuntimeException exception) {
            String errorCode = StrUtil.maxLength(exception.getClass().getSimpleName(), ERROR_CODE_LIMIT);
            int updated = mapper.markApprovalWorkflowStartUncertain(candidate.getTenantId(),
                    candidate.getApprovalId(), attemptToken, errorCode, now());
            if (updated == 0) {
                ApprovalWorkflowBinding current = mapper.selectApprovalWorkflowBinding(
                        candidate.getTenantId(), candidate.getApprovalId());
                require(current != null && current.getStatus().startsWith("BPM_"),
                        "approval workflow uncertain-start completion conflict");
            }
            log.warn("BPM approval start outcome is uncertain; automatic retry is disabled tenant={} approval={}",
                    candidate.getTenantId(), candidate.getApprovalId(), exception);
            return true;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void observe(BpmProcessInstanceStatusEvent event) {
        String terminalStatus = terminalStatus(event.getStatus());
        if (terminalStatus == null) {
            return;
        }
        require(StrUtil.isNotBlank(event.getId()), "BPM process instance id is required");
        require(StrUtil.isNotBlank(event.getBusinessKey()), "BPM business key is required");
        ApprovalWorkflowBinding binding = mapper.selectApprovalWorkflowBindingForEvent(
                event.getProcessDefinitionKey(), event.getId(), event.getBusinessKey());
        if (binding == null) {
            log.warn("Ignoring unbound BPM approval event processInstance={} businessKey={}",
                    event.getId(), event.getBusinessKey());
            return;
        }
        require(Objects.equals(binding.getBusinessKey(), event.getBusinessKey()),
                "BPM event business key does not match the approval binding");
        require(binding.getProcessInstanceId() == null || Objects.equals(binding.getProcessInstanceId(), event.getId()),
                "BPM event process instance does not match the approval binding");
        String reasonSha256 = DigestUtil.sha256Hex(StrUtil.nullToEmpty(event.getReason()));
        String eventId = DigestUtil.sha256Hex(String.join("|", event.getId(),
                String.valueOf(event.getStatus()), reasonSha256, event.getBusinessKey()));
        LocalDateTime now = now();
        ApprovalWorkflowEvent receipt = new ApprovalWorkflowEvent().setEventId(eventId)
                .setTenantId(binding.getTenantId()).setApprovalId(binding.getApprovalId())
                .setProcessInstanceId(event.getId()).setBpmStatus(event.getStatus())
                .setObservedStatus(terminalStatus).setReasonSha256(reasonSha256)
                .setTerminalOperatorUserId(event.getTerminalOperatorUserId())
                .setTerminalTaskId(event.getTerminalTaskId())
                .setTerminalTaskDefinitionKey(event.getTerminalTaskDefinitionKey()).setObservedAt(now);
        if (mapper.insertApprovalWorkflowEvent(receipt) == 0) {
            return;
        }
        mapper.markApprovalWorkflowTerminal(binding.getTenantId(), binding.getApprovalId(), event.getId(),
                event.getStatus(), terminalStatus, reasonSha256, event.getTerminalOperatorUserId(),
                event.getTerminalTaskId(), event.getTerminalTaskDefinitionKey(), now);
    }

    private static String terminalStatus(Integer bpmStatus) {
        if (BpmProcessInstanceStatusEnum.APPROVE.getStatus().equals(bpmStatus)) {
            return "BPM_APPROVED_PENDING_ATTESTATION";
        }
        if (BpmProcessInstanceStatusEnum.REJECT.getStatus().equals(bpmStatus)) {
            return "BPM_REJECTED";
        }
        if (BpmProcessInstanceStatusEnum.CANCEL.getStatus().equals(bpmStatus)) {
            return "BPM_CANCELLED";
        }
        return null;
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

}
