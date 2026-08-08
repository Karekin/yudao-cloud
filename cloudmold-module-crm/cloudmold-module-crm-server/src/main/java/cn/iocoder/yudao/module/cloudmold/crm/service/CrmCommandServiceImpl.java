package cn.iocoder.yudao.module.cloudmold.crm.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.crm.api.*;
import cn.iocoder.yudao.module.cloudmold.crm.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.crm.dal.mysql.CrmStoreMapper;
import cn.iocoder.yudao.module.cloudmold.crm.service.actor.CrmActorPrincipalPort;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CrmCommandServiceImpl implements CrmAutomationCommandApi {

    static final int OPERATION_SUCCEEDED = 10;
    static final String SOURCE_SYSTEM = "cloudmold-crm";
    private static final Pattern CODE = Pattern.compile("[A-Z0-9][A-Z0-9_-]{1,63}");
    private static final Set<String> CUSTOMER_LIFECYCLE = Set.of("ACTIVE", "DORMANT", "ARCHIVED");
    private static final Set<String> CUSTOMER_POOL = Set.of("OWNED", "IN_POOL");
    private static final Set<String> LEAD_STATUS = Set.of("NEW", "QUALIFYING", "CONTACTED", "CONVERTED", "DISQUALIFIED");
    private static final Set<String> CONTACT_STATUS = Set.of("ACTIVE", "INACTIVE");
    private static final Set<String> OPPORTUNITY_STAGE = Set.of("DISCOVERY", "QUALIFICATION", "PROPOSAL", "NEGOTIATION", "CLOSED_WON", "CLOSED_LOST");
    private static final Set<String> SUBJECT_TYPES = Set.of("CUSTOMER", "LEAD", "CONTACT", "OPPORTUNITY");
    private static final Set<String> FOLLOW_UP_METHODS = Set.of("CALL", "WECHAT", "EMAIL", "MEETING", "VISIT", "NOTE");

    private final CrmStoreMapper mapper;
    private final OutboxAppender outboxAppender;
    private final CrmActorPrincipalPort actorPrincipalPort;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CrmCommandResult execute(CrmCommand command) {
        return execute(command, actorPrincipalPort.resolveSystemAdmin(getLoginUserId()));
    }

    @Transactional(rollbackFor = Exception.class)
    public CrmCommandResult execute(CrmCommand command, String actorPrincipalId) {
        validateCommon(command, actorPrincipalId);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String requestHash = DigestUtil.sha256Hex(tenantId + "\n" + actorPrincipalId + "\n" + JsonUtils.toJsonString(command));
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Instant occurredAt = command.getOccurredAt() == null ? now.toInstant(ZoneOffset.UTC) : command.getOccurredAt();
        String attemptToken = UUID.randomUUID().toString();
        mapper.insertOrResolveOperation(tenantId, command.getIdempotencyKey(), command.getOperation().name(), requestHash, attemptToken, now);
        Long operationId = mapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve crm operation");
        CrmOperationDO operation = mapper.selectOperationForUpdate(operationId, tenantId);
        require(operation != null, "crm operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(operation.getRequestHash(), requestHash), "idempotency key conflicts with different crm payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing crm operation is not complete");
            CrmCommandResult replay = JsonUtils.parseObject(operation.getResultJson(), CrmCommandResult.class);
            replay.setDuplicate(true);
            return replay;
        }

        CrmCommandResult result = switch (command.getOperation()) {
            case CREATE_CUSTOMER -> createCustomer(tenantId, operationId, command, actorPrincipalId, occurredAt, now);
            case UPDATE_CUSTOMER -> updateCustomer(tenantId, operationId, command, actorPrincipalId, occurredAt, now);
            case CLAIM_CUSTOMER -> claimCustomer(tenantId, operationId, command, actorPrincipalId, occurredAt, now);
            case RETURN_CUSTOMER_TO_POOL -> returnCustomerToPool(tenantId, operationId, command, actorPrincipalId, occurredAt, now);
            case CREATE_LEAD -> createLead(tenantId, operationId, command, actorPrincipalId, occurredAt, now);
            case UPDATE_LEAD -> updateLead(tenantId, operationId, command, actorPrincipalId, occurredAt, now);
            case ASSIGN_LEAD -> assignLead(tenantId, operationId, command, actorPrincipalId, occurredAt, now);
            case CREATE_CONTACT -> createContact(tenantId, operationId, command, actorPrincipalId, occurredAt, now);
            case UPDATE_CONTACT -> updateContact(tenantId, operationId, command, actorPrincipalId, occurredAt, now);
            case CREATE_OPPORTUNITY -> createOpportunity(tenantId, operationId, command, actorPrincipalId, occurredAt, now);
            case UPDATE_OPPORTUNITY -> updateOpportunity(tenantId, operationId, command, actorPrincipalId, occurredAt, now);
            case RECORD_FOLLOW_UP -> recordFollowUp(tenantId, operationId, command, actorPrincipalId, occurredAt, now);
        };
        require(mapper.markOperationSucceeded(operationId, tenantId, result.getAggregateId(), JsonUtils.toJsonString(result), now) == 1,
                "crm operation completion conflict");
        return result;
    }

    private CrmCommandResult createCustomer(Long tenantId, Long operationId, CrmCommand command, String actorPrincipalId,
                                            Instant occurredAt, LocalDateTime now) {
        CrmCommand.CustomerDefinition input = requireNonNull(command.getCustomer(), "customer");
        requireCode(input.getCustomerCode(), "customerCode");
        requireText(input.getCustomerName(), "customerName", 128);
        String lifecycleStatus = upper(input.getLifecycleStatus());
        require(CUSTOMER_LIFECYCLE.contains(lifecycleStatus), "lifecycleStatus is invalid");
        String poolStatus = normalizePool(input.getPoolStatus(), input.getOwnerPrincipalId());
        String ownerPrincipalId = normalizeOwner(input.getOwnerPrincipalId(), poolStatus, actorPrincipalId);
        String customerId = hasText(input.getCustomerId()) ? input.getCustomerId().trim() : UUID.randomUUID().toString();
        CrmCustomerDO row = new CrmCustomerDO().setCustomerId(customerId).setTenantId(tenantId).setCustomerCode(input.getCustomerCode().trim())
                .setCustomerName(input.getCustomerName().trim()).setLevelCode(upperNullable(input.getLevelCode()))
                .setLifecycleStatus(lifecycleStatus).setPoolStatus(poolStatus).setOwnerPrincipalId(ownerPrincipalId)
                .setSourceCode(upperNullable(input.getSourceCode())).setIndustryCode(upperNullable(input.getIndustryCode()))
                .setRegionCode(upperNullable(input.getRegionCode())).setNextFollowUpAt(input.getNextFollowUpAt())
                .setVersion(1L).setCreatedAt(now).setUpdatedAt(now);
        require(mapper.insertCustomer(row) == 1, "failed to create customer");
        insertStatusHistory(tenantId, "CUSTOMER", customerId, row.getVersion(), null, lifecycleStatus, command.getReasonCode(), actorPrincipalId, occurredAt, now);
        appendCustomerStatusEvent(row, null, command.getReasonCode(), occurredAt,
                command.getCorrelationId(), command.getCausationId());
        return result(operationId, "CUSTOMER", customerId, row.getVersion(), lifecycleStatus, ownerPrincipalId, poolStatus, row.getNextFollowUpAt());
    }

    private CrmCommandResult updateCustomer(Long tenantId, Long operationId, CrmCommand command, String actorPrincipalId,
                                            Instant occurredAt, LocalDateTime now) {
        CrmCommand.CustomerDefinition input = requireNonNull(command.getCustomer(), "customer");
        requireId(input.getCustomerId(), "customerId");
        require(input.getExpectedVersion() != null && input.getExpectedVersion() > 0, "expectedVersion must be positive");
        CrmCustomerDO before = mapper.selectCustomerForUpdate(tenantId, input.getCustomerId().trim());
        require(before != null, "customer does not exist");
        require(Objects.equals(before.getVersion(), input.getExpectedVersion()), "customer version conflict");
        require(!hasText(input.getOwnerPrincipalId()) || Objects.equals(before.getOwnerPrincipalId(), input.getOwnerPrincipalId().trim()),
                "customer owner changes require CLAIM_CUSTOMER or RETURN_CUSTOMER_TO_POOL");
        require(!hasText(input.getPoolStatus()) || Objects.equals(before.getPoolStatus(), input.getPoolStatus().trim().toUpperCase()),
                "customer pool changes require CLAIM_CUSTOMER or RETURN_CUSTOMER_TO_POOL");
        String lifecycleStatus = upper(input.getLifecycleStatus());
        require(CUSTOMER_LIFECYCLE.contains(lifecycleStatus), "lifecycleStatus is invalid");
        requireCode(input.getCustomerCode(), "customerCode");
        requireText(input.getCustomerName(), "customerName", 128);
        require(mapper.updateCustomer(tenantId, before.getCustomerId(), input.getCustomerCode().trim(), input.getCustomerName().trim(),
                upperNullable(input.getLevelCode()), lifecycleStatus, upperNullable(input.getSourceCode()),
                upperNullable(input.getIndustryCode()), upperNullable(input.getRegionCode()), input.getNextFollowUpAt(),
                input.getExpectedVersion(), now) == 1, "customer update conflict");
        CrmCustomerDO after = before.setCustomerCode(input.getCustomerCode().trim()).setCustomerName(input.getCustomerName().trim())
                .setLevelCode(upperNullable(input.getLevelCode())).setLifecycleStatus(lifecycleStatus)
                .setSourceCode(upperNullable(input.getSourceCode())).setIndustryCode(upperNullable(input.getIndustryCode()))
                .setRegionCode(upperNullable(input.getRegionCode())).setNextFollowUpAt(input.getNextFollowUpAt())
                .setVersion(before.getVersion() + 1).setUpdatedAt(now);
        insertStatusHistory(tenantId, "CUSTOMER", after.getCustomerId(), after.getVersion(), before.getLifecycleStatus(),
                after.getLifecycleStatus(), command.getReasonCode(), actorPrincipalId, occurredAt, now);
        appendCustomerStatusEvent(after, before.getLifecycleStatus(), command.getReasonCode(), occurredAt,
                command.getCorrelationId(), command.getCausationId());
        return result(operationId, "CUSTOMER", after.getCustomerId(), after.getVersion(), after.getLifecycleStatus(),
                after.getOwnerPrincipalId(), after.getPoolStatus(), after.getNextFollowUpAt());
    }

    private CrmCommandResult claimCustomer(Long tenantId, Long operationId, CrmCommand command, String actorPrincipalId,
                                           Instant occurredAt, LocalDateTime now) {
        CrmCommand.CustomerDefinition input = requireNonNull(command.getCustomer(), "customer");
        requireId(input.getCustomerId(), "customerId");
        require(input.getExpectedVersion() != null && input.getExpectedVersion() > 0, "expectedVersion must be positive");
        String ownerPrincipalId = defaultOwner(input.getOwnerPrincipalId(), actorPrincipalId);
        CrmCustomerDO before = mapper.selectCustomerForUpdate(tenantId, input.getCustomerId().trim());
        require(before != null, "customer does not exist");
        require(Objects.equals(before.getVersion(), input.getExpectedVersion()), "customer version conflict");
        require("IN_POOL".equals(before.getPoolStatus()) && before.getOwnerPrincipalId() == null, "customer is not currently in pool");
        require(mapper.transitionCustomerPool(tenantId, before.getCustomerId(), "OWNED", ownerPrincipalId,
                input.getExpectedVersion(), now) == 1, "customer claim conflict");
        CrmCustomerDO after = before.setPoolStatus("OWNED").setOwnerPrincipalId(ownerPrincipalId)
                .setVersion(before.getVersion() + 1).setUpdatedAt(now);
        insertOwnerHistory(tenantId, operationId, before, after, command.getReasonCode(), actorPrincipalId, occurredAt, now);
        insertStatusHistory(tenantId, "CUSTOMER", after.getCustomerId(), after.getVersion(), before.getPoolStatus(), after.getPoolStatus(),
                command.getReasonCode(), actorPrincipalId, occurredAt, now);
        appendCustomerOwnerChangedEvent(after, before.getOwnerPrincipalId(), before.getPoolStatus(),
                command.getReasonCode(), occurredAt, command.getCorrelationId(), command.getCausationId());
        appendCustomerStatusEvent(after, before.getPoolStatus(), command.getReasonCode(), occurredAt,
                command.getCorrelationId(), command.getCausationId());
        return result(operationId, "CUSTOMER", after.getCustomerId(), after.getVersion(), after.getPoolStatus(), after.getOwnerPrincipalId(),
                after.getPoolStatus(), after.getNextFollowUpAt());
    }

    private CrmCommandResult returnCustomerToPool(Long tenantId, Long operationId, CrmCommand command, String actorPrincipalId,
                                                  Instant occurredAt, LocalDateTime now) {
        CrmCommand.CustomerDefinition input = requireNonNull(command.getCustomer(), "customer");
        requireId(input.getCustomerId(), "customerId");
        require(input.getExpectedVersion() != null && input.getExpectedVersion() > 0, "expectedVersion must be positive");
        CrmCustomerDO before = mapper.selectCustomerForUpdate(tenantId, input.getCustomerId().trim());
        require(before != null, "customer does not exist");
        require(Objects.equals(before.getVersion(), input.getExpectedVersion()), "customer version conflict");
        require("OWNED".equals(before.getPoolStatus()) && hasText(before.getOwnerPrincipalId()), "customer is not currently owned");
        require(mapper.transitionCustomerPool(tenantId, before.getCustomerId(), "IN_POOL", null, input.getExpectedVersion(), now) == 1,
                "customer return-to-pool conflict");
        CrmCustomerDO after = before.setPoolStatus("IN_POOL").setOwnerPrincipalId(null).setVersion(before.getVersion() + 1).setUpdatedAt(now);
        insertOwnerHistory(tenantId, operationId, before, after, command.getReasonCode(), actorPrincipalId, occurredAt, now);
        insertStatusHistory(tenantId, "CUSTOMER", after.getCustomerId(), after.getVersion(), before.getPoolStatus(), after.getPoolStatus(),
                command.getReasonCode(), actorPrincipalId, occurredAt, now);
        appendCustomerOwnerChangedEvent(after, before.getOwnerPrincipalId(), before.getPoolStatus(),
                command.getReasonCode(), occurredAt, command.getCorrelationId(), command.getCausationId());
        appendCustomerStatusEvent(after, before.getPoolStatus(), command.getReasonCode(), occurredAt,
                command.getCorrelationId(), command.getCausationId());
        return result(operationId, "CUSTOMER", after.getCustomerId(), after.getVersion(), after.getPoolStatus(), null,
                after.getPoolStatus(), after.getNextFollowUpAt());
    }

    private CrmCommandResult createLead(Long tenantId, Long operationId, CrmCommand command, String actorPrincipalId,
                                        Instant occurredAt, LocalDateTime now) {
        CrmCommand.LeadDefinition input = requireNonNull(command.getLead(), "lead");
        requireCode(input.getLeadCode(), "leadCode");
        requireText(input.getLeadName(), "leadName", 128);
        String status = upper(input.getStatus());
        require(LEAD_STATUS.contains(status), "lead status is invalid");
        String ownerPrincipalId = defaultOwner(input.getOwnerPrincipalId(), actorPrincipalId);
        if (ownerPrincipalId != null) actorPrincipalPort.requireActive(ownerPrincipalId);
        validateMaskedContact(input.getMaskedContact(), "maskedContact");
        String leadId = hasText(input.getLeadId()) ? input.getLeadId().trim() : UUID.randomUUID().toString();
        CrmLeadDO row = new CrmLeadDO().setLeadId(leadId).setTenantId(tenantId).setLeadCode(input.getLeadCode().trim())
                .setLeadName(input.getLeadName().trim()).setSourceCode(upperNullable(input.getSourceCode())).setStatus(status)
                .setOwnerPrincipalId(ownerPrincipalId).setContactChannelRef(normalize(input.getContactChannelRef()))
                .setMaskedContact(normalize(input.getMaskedContact())).setNextFollowUpAt(input.getNextFollowUpAt())
                .setVersion(1L).setCreatedAt(now).setUpdatedAt(now);
        require(mapper.insertLead(row) == 1, "failed to create lead");
        insertStatusHistory(tenantId, "LEAD", leadId, row.getVersion(), null, status, command.getReasonCode(), actorPrincipalId, occurredAt, now);
        appendLeadStatusEvent(row, null, command.getReasonCode(), occurredAt,
                command.getCorrelationId(), command.getCausationId());
        return result(operationId, "LEAD", leadId, row.getVersion(), status, ownerPrincipalId, null, row.getNextFollowUpAt());
    }

    private CrmCommandResult updateLead(Long tenantId, Long operationId, CrmCommand command, String actorPrincipalId,
                                        Instant occurredAt, LocalDateTime now) {
        CrmCommand.LeadDefinition input = requireNonNull(command.getLead(), "lead");
        requireId(input.getLeadId(), "leadId");
        require(input.getExpectedVersion() != null && input.getExpectedVersion() > 0, "expectedVersion must be positive");
        CrmLeadDO before = mapper.selectLeadForUpdate(tenantId, input.getLeadId().trim());
        require(before != null, "lead does not exist");
        require(Objects.equals(before.getVersion(), input.getExpectedVersion()), "lead version conflict");
        require(!hasText(input.getOwnerPrincipalId()) || Objects.equals(before.getOwnerPrincipalId(), input.getOwnerPrincipalId().trim()),
                "lead owner changes require ASSIGN_LEAD");
        String status = upper(input.getStatus());
        require(LEAD_STATUS.contains(status), "lead status is invalid");
        requireCode(input.getLeadCode(), "leadCode");
        requireText(input.getLeadName(), "leadName", 128);
        validateMaskedContact(input.getMaskedContact(), "maskedContact");
        require(mapper.updateLead(tenantId, before.getLeadId(), input.getLeadCode().trim(), input.getLeadName().trim(),
                upperNullable(input.getSourceCode()), status, normalize(input.getContactChannelRef()), normalize(input.getMaskedContact()),
                input.getNextFollowUpAt(), input.getExpectedVersion(), now) == 1, "lead update conflict");
        CrmLeadDO after = before.setLeadCode(input.getLeadCode().trim()).setLeadName(input.getLeadName().trim())
                .setSourceCode(upperNullable(input.getSourceCode())).setStatus(status).setContactChannelRef(normalize(input.getContactChannelRef()))
                .setMaskedContact(normalize(input.getMaskedContact())).setNextFollowUpAt(input.getNextFollowUpAt())
                .setVersion(before.getVersion() + 1).setUpdatedAt(now);
        insertStatusHistory(tenantId, "LEAD", after.getLeadId(), after.getVersion(), before.getStatus(), after.getStatus(),
                command.getReasonCode(), actorPrincipalId, occurredAt, now);
        appendLeadStatusEvent(after, before.getStatus(), command.getReasonCode(), occurredAt,
                command.getCorrelationId(), command.getCausationId());
        return result(operationId, "LEAD", after.getLeadId(), after.getVersion(), after.getStatus(), after.getOwnerPrincipalId(), null,
                after.getNextFollowUpAt());
    }

    private CrmCommandResult assignLead(Long tenantId, Long operationId, CrmCommand command, String actorPrincipalId,
                                        Instant occurredAt, LocalDateTime now) {
        CrmCommand.LeadDefinition input = requireNonNull(command.getLead(), "lead");
        requireId(input.getLeadId(), "leadId");
        require(input.getExpectedVersion() != null && input.getExpectedVersion() > 0, "expectedVersion must be positive");
        requireId(input.getOwnerPrincipalId(), "ownerPrincipalId");
        actorPrincipalPort.requireActive(input.getOwnerPrincipalId().trim());
        CrmLeadDO before = mapper.selectLeadForUpdate(tenantId, input.getLeadId().trim());
        require(before != null, "lead does not exist");
        require(Objects.equals(before.getVersion(), input.getExpectedVersion()), "lead version conflict");
        String status = upperNullable(input.getStatus()) == null ? "CONTACTED" : upper(input.getStatus());
        require(LEAD_STATUS.contains(status), "lead status is invalid");
        require(mapper.assignLead(tenantId, before.getLeadId(), input.getOwnerPrincipalId().trim(), status, input.getExpectedVersion(), now) == 1,
                "lead assignment conflict");
        CrmLeadDO after = before.setOwnerPrincipalId(input.getOwnerPrincipalId().trim()).setStatus(status)
                .setVersion(before.getVersion() + 1).setUpdatedAt(now);
        insertStatusHistory(tenantId, "LEAD", after.getLeadId(), after.getVersion(), before.getStatus(), after.getStatus(),
                command.getReasonCode(), actorPrincipalId, occurredAt, now);
        appendLeadStatusEvent(after, before.getStatus(), command.getReasonCode(), occurredAt,
                command.getCorrelationId(), command.getCausationId());
        return result(operationId, "LEAD", after.getLeadId(), after.getVersion(), after.getStatus(), after.getOwnerPrincipalId(), null,
                after.getNextFollowUpAt());
    }

    private CrmCommandResult createContact(Long tenantId, Long operationId, CrmCommand command, String actorPrincipalId,
                                           Instant occurredAt, LocalDateTime now) {
        CrmCommand.ContactDefinition input = requireNonNull(command.getContact(), "contact");
        requireId(input.getCustomerId(), "customerId");
        requireText(input.getContactName(), "contactName", 128);
        String status = upper(input.getStatus());
        require(CONTACT_STATUS.contains(status), "contact status is invalid");
        validateMaskedContact(input.getMaskedContact(), "maskedContact");
        String contactId = hasText(input.getContactId()) ? input.getContactId().trim() : UUID.randomUUID().toString();
        CrmContactDO row = new CrmContactDO().setContactId(contactId).setTenantId(tenantId).setCustomerId(input.getCustomerId().trim())
                .setContactName(input.getContactName().trim()).setRoleTitle(normalize(input.getRoleTitle()))
                .setContactChannelRef(normalize(input.getContactChannelRef())).setMaskedContact(normalize(input.getMaskedContact()))
                .setIsPrimary(Boolean.TRUE.equals(input.getIsPrimary())).setStatus(status).setVersion(1L)
                .setCreatedAt(now).setUpdatedAt(now);
        require(mapper.insertContact(row) == 1, "failed to create contact");
        insertStatusHistory(tenantId, "CONTACT", contactId, row.getVersion(), null, status, command.getReasonCode(), actorPrincipalId, occurredAt, now);
        appendContactStatusEvent(row, null, occurredAt, command.getCorrelationId(), command.getCausationId());
        return result(operationId, "CONTACT", contactId, row.getVersion(), status, null, null, null);
    }

    private CrmCommandResult updateContact(Long tenantId, Long operationId, CrmCommand command, String actorPrincipalId,
                                           Instant occurredAt, LocalDateTime now) {
        CrmCommand.ContactDefinition input = requireNonNull(command.getContact(), "contact");
        requireId(input.getContactId(), "contactId");
        require(input.getExpectedVersion() != null && input.getExpectedVersion() > 0, "expectedVersion must be positive");
        CrmContactDO before = mapper.selectContactForUpdate(tenantId, input.getContactId().trim());
        require(before != null, "contact does not exist");
        require(Objects.equals(before.getVersion(), input.getExpectedVersion()), "contact version conflict");
        requireText(input.getContactName(), "contactName", 128);
        String status = upper(input.getStatus());
        require(CONTACT_STATUS.contains(status), "contact status is invalid");
        validateMaskedContact(input.getMaskedContact(), "maskedContact");
        require(mapper.updateContact(tenantId, before.getContactId(), input.getContactName().trim(), normalize(input.getRoleTitle()),
                normalize(input.getContactChannelRef()), normalize(input.getMaskedContact()), Boolean.TRUE.equals(input.getIsPrimary()),
                status, input.getExpectedVersion(), now) == 1, "contact update conflict");
        CrmContactDO after = before.setContactName(input.getContactName().trim()).setRoleTitle(normalize(input.getRoleTitle()))
                .setContactChannelRef(normalize(input.getContactChannelRef())).setMaskedContact(normalize(input.getMaskedContact()))
                .setIsPrimary(Boolean.TRUE.equals(input.getIsPrimary())).setStatus(status).setVersion(before.getVersion() + 1).setUpdatedAt(now);
        insertStatusHistory(tenantId, "CONTACT", after.getContactId(), after.getVersion(), before.getStatus(), after.getStatus(),
                command.getReasonCode(), actorPrincipalId, occurredAt, now);
        appendContactStatusEvent(after, before.getStatus(), occurredAt,
                command.getCorrelationId(), command.getCausationId());
        return result(operationId, "CONTACT", after.getContactId(), after.getVersion(), after.getStatus(), null, null, null);
    }

    private CrmCommandResult createOpportunity(Long tenantId, Long operationId, CrmCommand command, String actorPrincipalId,
                                               Instant occurredAt, LocalDateTime now) {
        CrmCommand.OpportunityDefinition input = requireNonNull(command.getOpportunity(), "opportunity");
        requireId(input.getCustomerId(), "customerId");
        requireCode(input.getOpportunityCode(), "opportunityCode");
        requireText(input.getOpportunityName(), "opportunityName", 128);
        String stage = upper(input.getStage());
        require(OPPORTUNITY_STAGE.contains(stage), "opportunity stage is invalid");
        require(input.getExpectedAmountMinor() != null && input.getExpectedAmountMinor() >= 0, "expectedAmountMinor must be nonnegative");
        require("CNY".equals(upper(input.getCurrencyCode())), "currencyCode must be CNY in first slice");
        String ownerPrincipalId = defaultOwner(input.getOwnerPrincipalId(), actorPrincipalId);
        if (ownerPrincipalId != null) actorPrincipalPort.requireActive(ownerPrincipalId);
        String opportunityId = hasText(input.getOpportunityId()) ? input.getOpportunityId().trim() : UUID.randomUUID().toString();
        CrmOpportunityDO row = new CrmOpportunityDO().setOpportunityId(opportunityId).setTenantId(tenantId)
                .setOpportunityCode(input.getOpportunityCode().trim()).setCustomerId(input.getCustomerId().trim())
                .setOpportunityName(input.getOpportunityName().trim()).setStage(stage).setExpectedAmountMinor(input.getExpectedAmountMinor())
                .setCurrencyCode("CNY").setExpectedCloseDate(input.getExpectedCloseDate()).setOwnerPrincipalId(ownerPrincipalId)
                .setVersion(1L).setCreatedAt(now).setUpdatedAt(now);
        require(mapper.insertOpportunity(row) == 1, "failed to create opportunity");
        insertStatusHistory(tenantId, "OPPORTUNITY", opportunityId, row.getVersion(), null, stage, command.getReasonCode(), actorPrincipalId, occurredAt, now);
        appendOpportunityStageEvent(row, null, occurredAt,
                command.getCorrelationId(), command.getCausationId());
        return result(operationId, "OPPORTUNITY", opportunityId, row.getVersion(), stage, ownerPrincipalId, null, null);
    }

    private CrmCommandResult updateOpportunity(Long tenantId, Long operationId, CrmCommand command, String actorPrincipalId,
                                               Instant occurredAt, LocalDateTime now) {
        CrmCommand.OpportunityDefinition input = requireNonNull(command.getOpportunity(), "opportunity");
        requireId(input.getOpportunityId(), "opportunityId");
        require(input.getExpectedVersion() != null && input.getExpectedVersion() > 0, "expectedVersion must be positive");
        CrmOpportunityDO before = mapper.selectOpportunityForUpdate(tenantId, input.getOpportunityId().trim());
        require(before != null, "opportunity does not exist");
        require(Objects.equals(before.getVersion(), input.getExpectedVersion()), "opportunity version conflict");
        requireCode(input.getOpportunityCode(), "opportunityCode");
        requireText(input.getOpportunityName(), "opportunityName", 128);
        String stage = upper(input.getStage());
        require(OPPORTUNITY_STAGE.contains(stage), "opportunity stage is invalid");
        require(input.getExpectedAmountMinor() != null && input.getExpectedAmountMinor() >= 0, "expectedAmountMinor must be nonnegative");
        require("CNY".equals(upper(input.getCurrencyCode())), "currencyCode must be CNY in first slice");
        String ownerPrincipalId = nullableId(input.getOwnerPrincipalId());
        ownerPrincipalId = ownerPrincipalId == null ? before.getOwnerPrincipalId() : ownerPrincipalId;
        actorPrincipalPort.requireActive(ownerPrincipalId);
        require(mapper.updateOpportunity(tenantId, before.getOpportunityId(), input.getOpportunityCode().trim(),
                input.getOpportunityName().trim(), stage, input.getExpectedAmountMinor(), "CNY", input.getExpectedCloseDate(),
                ownerPrincipalId, input.getExpectedVersion(), now) == 1, "opportunity update conflict");
        CrmOpportunityDO after = before.setOpportunityCode(input.getOpportunityCode().trim()).setOpportunityName(input.getOpportunityName().trim())
                .setStage(stage).setExpectedAmountMinor(input.getExpectedAmountMinor()).setCurrencyCode("CNY")
                .setExpectedCloseDate(input.getExpectedCloseDate()).setOwnerPrincipalId(ownerPrincipalId)
                .setVersion(before.getVersion() + 1).setUpdatedAt(now);
        insertStatusHistory(tenantId, "OPPORTUNITY", after.getOpportunityId(), after.getVersion(), before.getStage(), after.getStage(),
                command.getReasonCode(), actorPrincipalId, occurredAt, now);
        appendOpportunityStageEvent(after, before.getStage(), occurredAt,
                command.getCorrelationId(), command.getCausationId());
        return result(operationId, "OPPORTUNITY", after.getOpportunityId(), after.getVersion(), after.getStage(), ownerPrincipalId, null, null);
    }

    private CrmCommandResult recordFollowUp(Long tenantId, Long operationId, CrmCommand command, String actorPrincipalId,
                                            Instant occurredAt, LocalDateTime now) {
        CrmCommand.FollowUpDefinition input = requireNonNull(command.getFollowUp(), "followUp");
        String subjectType = upper(input.getSubjectType());
        require(SUBJECT_TYPES.contains(subjectType), "subjectType is invalid");
        requireId(input.getSubjectId(), "subjectId");
        String methodCode = upper(input.getMethodCode());
        require(FOLLOW_UP_METHODS.contains(methodCode), "methodCode is invalid");
        requireText(input.getSummary(), "summary", 1024);
        String followUpId = hasText(input.getFollowUpId()) ? input.getFollowUpId().trim() : UUID.randomUUID().toString();
        CrmFollowUpDO row = new CrmFollowUpDO().setFollowUpId(followUpId).setTenantId(tenantId).setSubjectType(subjectType)
                .setSubjectId(input.getSubjectId().trim()).setMethodCode(methodCode).setSummary(input.getSummary().trim())
                .setNextFollowUpAt(input.getNextFollowUpAt()).setActorPrincipalId(actorPrincipalId)
                .setOccurredAt(LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC)).setCreatedAt(now);
        require(mapper.insertFollowUp(row) == 1, "failed to record follow-up");
        if (input.getNextFollowUpAt() != null) {
            if ("CUSTOMER".equals(subjectType)) {
                mapper.bumpCustomerNextFollowUp(tenantId, input.getSubjectId().trim(), input.getNextFollowUpAt(), now);
            } else if ("LEAD".equals(subjectType)) {
                mapper.bumpLeadNextFollowUp(tenantId, input.getSubjectId().trim(), input.getNextFollowUpAt(), now);
            }
        }
        appendFollowUpRecordedEvent(row, occurredAt, command.getCorrelationId(), command.getCausationId());
        return result(operationId, "FOLLOW_UP", followUpId, 1L, methodCode, actorPrincipalId, null, row.getNextFollowUpAt());
    }

    private void appendCustomerStatusEvent(CrmCustomerDO row, String fromStatus, String reasonCode, Instant occurredAt,
                                           String correlationId, String causationId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("customer_id", row.getCustomerId());
        payload.put("customer_code", row.getCustomerCode());
        payload.put("lifecycle_status_from", fromStatus);
        payload.put("lifecycle_status_to", row.getLifecycleStatus());
        payload.put("pool_status", row.getPoolStatus());
        payload.put("owner_principal_id", row.getOwnerPrincipalId());
        payload.put("source_code", row.getSourceCode());
        payload.put("industry_code", row.getIndustryCode());
        payload.put("region_code", row.getRegionCode());
        payload.put("reason_code", reasonCode);
        append("crm.customer.status_changed", "crm_customer", row.getCustomerId(), row.getVersion(),
                row.getTenantId(), occurredAt, correlationId, causationId, payload);
    }

    private void appendCustomerOwnerChangedEvent(CrmCustomerDO row, String previousOwnerPrincipalId, String previousPoolStatus,
                                                 String reasonCode, Instant occurredAt,
                                                 String correlationId, String causationId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("customer_id", row.getCustomerId());
        payload.put("customer_code", row.getCustomerCode());
        payload.put("owner_principal_id_from", previousOwnerPrincipalId);
        payload.put("owner_principal_id_to", row.getOwnerPrincipalId());
        payload.put("pool_status_from", previousPoolStatus);
        payload.put("pool_status_to", row.getPoolStatus());
        payload.put("reason_code", reasonCode);
        append("crm.customer.owner_changed", "crm_customer", row.getCustomerId(), row.getVersion(),
                row.getTenantId(), occurredAt, correlationId, causationId, payload);
    }

    private void appendLeadStatusEvent(CrmLeadDO row, String fromStatus, String reasonCode, Instant occurredAt,
                                       String correlationId, String causationId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("lead_id", row.getLeadId());
        payload.put("lead_code", row.getLeadCode());
        payload.put("status_from", fromStatus);
        payload.put("status_to", row.getStatus());
        payload.put("owner_principal_id", row.getOwnerPrincipalId());
        payload.put("contact_channel_ref", row.getContactChannelRef());
        payload.put("masked_contact", row.getMaskedContact());
        payload.put("source_code", row.getSourceCode());
        payload.put("reason_code", reasonCode);
        append("crm.lead.status_changed", "crm_lead", row.getLeadId(), row.getVersion(),
                row.getTenantId(), occurredAt, correlationId, causationId, payload);
    }

    private void appendContactStatusEvent(CrmContactDO row, String fromStatus, Instant occurredAt,
                                          String correlationId, String causationId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contact_id", row.getContactId());
        payload.put("customer_id", row.getCustomerId());
        payload.put("status_from", fromStatus);
        payload.put("status_to", row.getStatus());
        payload.put("contact_channel_ref", row.getContactChannelRef());
        payload.put("masked_contact", row.getMaskedContact());
        payload.put("is_primary", row.getIsPrimary());
        append("crm.contact.status_changed", "crm_contact", row.getContactId(), row.getVersion(),
                row.getTenantId(), occurredAt, correlationId, causationId, payload);
    }

    private void appendOpportunityStageEvent(CrmOpportunityDO row, String fromStage, Instant occurredAt,
                                             String correlationId, String causationId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("opportunity_id", row.getOpportunityId());
        payload.put("opportunity_code", row.getOpportunityCode());
        payload.put("customer_id", row.getCustomerId());
        payload.put("stage_from", fromStage);
        payload.put("stage_to", row.getStage());
        payload.put("expected_amount_minor", row.getExpectedAmountMinor());
        payload.put("currency_code", row.getCurrencyCode());
        payload.put("owner_principal_id", row.getOwnerPrincipalId());
        append("crm.opportunity.stage_changed", "crm_opportunity", row.getOpportunityId(), row.getVersion(),
                row.getTenantId(), occurredAt, correlationId, causationId, payload);
    }

    private void appendFollowUpRecordedEvent(CrmFollowUpDO row, Instant occurredAt,
                                             String correlationId, String causationId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("follow_up_id", row.getFollowUpId());
        payload.put("subject_type", row.getSubjectType());
        payload.put("subject_id", row.getSubjectId());
        payload.put("method_code", row.getMethodCode());
        payload.put("summary", row.getSummary());
        payload.put("next_follow_up_at", row.getNextFollowUpAt() == null ? null : row.getNextFollowUpAt().toInstant(ZoneOffset.UTC).toString());
        payload.put("actor_principal_id", row.getActorPrincipalId());
        append("crm.follow_up.recorded", "crm_follow_up", row.getFollowUpId(), 1L,
                row.getTenantId(), occurredAt, correlationId, causationId, payload);
    }

    private void append(String eventType, String aggregateType, String aggregateId, Long aggregateVersion,
                        Long tenantId, Instant occurredAt, String correlationId, String causationId,
                        Map<String, Object> payload) {
        outboxAppender.append(AppendDomainEventCommand.builder()
                .eventType(eventType)
                .schemaVersion(1)
                .sourceSystem(SOURCE_SYSTEM)
                .tenantId(tenantId)
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .aggregateVersion(aggregateVersion)
                .eventSequence((short) 1)
                .occurredAt(occurredAt)
                .traceId(aggregateId)
                .correlationId(hasText(correlationId) ? correlationId.trim() : aggregateId)
                .causationId(hasText(causationId) ? causationId.trim() : null)
                .idempotencyKey(aggregateType + ":" + aggregateId + ":event:" + aggregateVersion + ":" + eventType)
                .payload(payload)
                .headers(Map.of("pii_safe", true))
                .destination("lakehouse")
                .build());
    }

    private void insertStatusHistory(Long tenantId, String aggregateType, String aggregateId, Long aggregateVersion,
                                     String fromStatus, String toStatus, String reasonCode, String actorPrincipalId,
                                     Instant occurredAt, LocalDateTime now) {
        mapper.insertStatusHistory(new CrmStatusHistoryDO().setTenantId(tenantId).setAggregateType(aggregateType)
                .setAggregateId(aggregateId).setAggregateVersion(aggregateVersion).setFromStatus(fromStatus)
                .setToStatus(toStatus).setReasonCode(normalize(reasonCode)).setActorPrincipalId(actorPrincipalId)
                .setOccurredAt(LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC)).setCreatedAt(now));
    }

    private void insertOwnerHistory(Long tenantId, Long operationId, CrmCustomerDO before, CrmCustomerDO after,
                                    String reasonCode, String actorPrincipalId, Instant occurredAt, LocalDateTime now) {
        mapper.insertCustomerOwnerHistory(new CrmCustomerOwnerHistoryDO().setTenantId(tenantId).setCustomerId(after.getCustomerId())
                .setFromOwnerPrincipalId(before.getOwnerPrincipalId()).setToOwnerPrincipalId(after.getOwnerPrincipalId())
                .setFromPoolStatus(before.getPoolStatus()).setToPoolStatus(after.getPoolStatus()).setOperationId(operationId)
                .setReasonCode(normalize(reasonCode)).setActorPrincipalId(actorPrincipalId)
                .setOccurredAt(LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC)).setCreatedAt(now));
    }

    private static CrmCommandResult result(Long operationId, String aggregateType, String aggregateId, Long aggregateVersion,
                                           String currentStatus, String ownerPrincipalId, String poolStatus, LocalDateTime nextFollowUpAt) {
        return CrmCommandResult.builder().operationId(operationId).duplicate(false).aggregateType(aggregateType)
                .aggregateId(aggregateId).aggregateVersion(aggregateVersion).currentStatus(currentStatus)
                .ownerPrincipalId(ownerPrincipalId).poolStatus(poolStatus).nextFollowUpAt(nextFollowUpAt).build();
    }

    private void validateCommon(CrmCommand command, String actorPrincipalId) {
        require(command != null, "crm command is required");
        require(command.getOperation() != null, "crm operation is required");
        requireText(command.getIdempotencyKey(), "idempotencyKey", 64);
        requireText(command.getRunId(), "runId", 128);
        requireText(actorPrincipalId, "actorPrincipalId", 128);
        actorPrincipalPort.requireActive(actorPrincipalId);
    }

    private static <T> T requireNonNull(T value, String field) {
        require(value != null, field + " is required");
        return value;
    }

    private static void validateMaskedContact(String value, String field) {
        requireText(value, field, 128);
        require(value.contains("*"), field + " must be masked");
    }

    private static String normalizePool(String poolStatus, String ownerPrincipalId) {
        String normalized = upperNullable(poolStatus);
        if (normalized == null) {
            return hasText(ownerPrincipalId) ? "OWNED" : "IN_POOL";
        }
        require(CUSTOMER_POOL.contains(normalized), "poolStatus is invalid");
        return normalized;
    }

    private String normalizeOwner(String ownerPrincipalId, String poolStatus, String actorPrincipalId) {
        String normalized = nullableId(ownerPrincipalId);
        if ("OWNED".equals(poolStatus)) {
            normalized = normalized == null ? actorPrincipalId : normalized;
            actorPrincipalPort.requireActive(normalized);
            return normalized;
        }
        require(normalized == null, "ownerPrincipalId must be null when poolStatus is IN_POOL");
        return null;
    }

    private String defaultOwner(String ownerPrincipalId, String actorPrincipalId) {
        String normalized = nullableId(ownerPrincipalId);
        normalized = normalized == null ? actorPrincipalId : normalized;
        actorPrincipalPort.requireActive(normalized);
        return normalized;
    }

    private static String nullableId(String value) {
        if (!hasText(value)) return null;
        requireId(value, "id");
        return value.trim();
    }

    private static void requireId(String value, String field) {
        requireText(value, field, 64);
    }

    private static void requireCode(String value, String field) {
        requireText(value, field, 64);
        require(CODE.matcher(value.trim().toUpperCase(Locale.ROOT)).matches(), field + " is invalid");
    }

    private static String upper(String value) {
        return requireText(value, "value", 64).toUpperCase(Locale.ROOT);
    }

    private static String upperNullable(String value) {
        return hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private static String normalize(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private static String requireText(String value, String field, int maxLen) {
        require(hasText(value), field + " is required");
        String normalized = value.trim();
        require(normalized.length() <= maxLen, field + " is too long");
        return normalized;
    }

    private static boolean hasText(String value) {
        return StringUtils.hasText(value);
    }

    private static void require(boolean expression, String message) {
        if (!expression) {
            throw new IllegalArgumentException(message);
        }
    }
}
