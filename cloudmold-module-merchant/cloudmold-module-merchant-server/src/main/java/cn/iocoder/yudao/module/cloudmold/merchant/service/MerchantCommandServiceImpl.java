package cn.iocoder.yudao.module.cloudmold.merchant.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.identity.api.PrincipalValidationApi;
import cn.iocoder.yudao.module.cloudmold.listing.api.unpublish.*;
import cn.iocoder.yudao.module.cloudmold.merchant.api.*;
import cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.merchant.dal.mysql.MerchantStoreMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MerchantCommandServiceImpl implements MerchantCommandApi, MerchantReferenceValidationApi,
        MerchantOperatorAuthorizationApi, MerchantOwnerValidationApi, SourceMappingQueryApi {

    static final int OPERATION_SUCCEEDED = 10;
    static final String ONBOARDING_EVENT = "merchant.onboarding.status_changed";
    static final String ENTITY_EVENT = "merchant.entity.status_changed";
    static final String ASSIGNMENT_EVENT = "merchant.operator_assignment.changed";
    static final String SOURCE_MAPPING_EVENT = "merchant.source_mapping.changed";
    private static final Set<String> CHANNELS = Set.of("INTERNAL_COMPANY", "YSHOPPING_INTERNAL", "YSHOPPING");
    private static final Set<String> SOURCE_TARGET_TYPES = Set.of("LEGAL_ENTITY", "MERCHANT", "SHOP");

    private final MerchantStoreMapper mapper;
    private final PrincipalValidationApi principalValidationApi;
    private final OutboxAppender outboxAppender;
    private final ListingUnpublishSagaCommandApi listingUnpublishSagaCommandApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MerchantCommandResult execute(MerchantCommand command) {
        validateCommon(command);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String requestHash = DigestUtil.sha256Hex(tenantId + "|" + JsonUtils.toJsonString(command));
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String attemptToken = UUID.randomUUID().toString();
        mapper.insertOrResolveOperation(tenantId, command.getIdempotencyKey(), command.getOperation().name(),
                requestHash, attemptToken, now);
        Long operationId = mapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve merchant operation");
        MerchantOperationDO operation = mapper.selectOperationForUpdate(operationId, tenantId);
        require(operation != null, "merchant operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(operation.getRequestHash(), requestHash),
                    "idempotency key conflicts with different merchant payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing merchant operation is not complete");
            MerchantCommandResult replay = JsonUtils.parseObject(operation.getResultJson(), MerchantCommandResult.class);
            replay.setDuplicate(true);
            return replay;
        }

        MerchantCommandResult result = switch (command.getOperation()) {
            case CREATE_ONBOARDING_DRAFT -> createDraft(tenantId, operationId, command, now);
            case SUBMIT_ONBOARDING, START_ONBOARDING_REVIEW, APPROVE_ONBOARDING, REJECT_ONBOARDING,
                    WITHDRAW_ONBOARDING -> transitionOnboarding(tenantId, operationId, command, now);
            case ACTIVATE_MERCHANT -> activateMerchant(tenantId, operationId, command, now);
            case ACTIVATE_SHOP -> activateShop(tenantId, operationId, command, now);
            case SUSPEND_MERCHANT -> transitionMerchantStatus(tenantId, operationId, command, now,
                    "ACTIVE", "SUSPENDED", true);
            case RESUME_MERCHANT -> transitionMerchantStatus(tenantId, operationId, command, now,
                    "SUSPENDED", "ACTIVE", false);
            case PAUSE_SHOP -> transitionShopStatus(tenantId, operationId, command, now,
                    "ACTIVE", "PAUSED", true, false);
            case RESUME_SHOP -> transitionShopStatus(tenantId, operationId, command, now,
                    "PAUSED", "ACTIVE", false, true);
            case LINK_SOURCE -> linkSource(tenantId, operationId, command, now);
            case REVOKE_SOURCE -> revokeSource(tenantId, operationId, command, now);
        };
        String aggregateId = firstNonNull(result.getApplicationId(), result.getMerchantId(), result.getShopId(),
                result.getSourceMappingId());
        require(mapper.markOperationSucceeded(operationId, tenantId, aggregateId, JsonUtils.toJsonString(result), now) == 1,
                "merchant operation completion conflict");
        return result;
    }

    @Override
    public MerchantReferenceView requireActiveReference(MerchantReferenceValidationCommand command) {
        require(command != null, "merchant reference validation command is required");
        requireId(command.getMerchantId(), "merchantId");
        requireId(command.getShopId(), "shopId");
        MerchantReferenceView result = mapper.selectActiveReference(TenantContextHolder.getRequiredTenantId(),
                command.getMerchantId(), command.getShopId());
        require(result != null, "merchant/shop reference is not active or does not belong together");
        return result;
    }

    @Override
    public MerchantOwnerView requireActiveMerchant(String merchantId) {
        requireId(merchantId, "merchantId");
        MerchantOwnerView result = mapper.selectActiveMerchant(TenantContextHolder.getRequiredTenantId(), merchantId);
        require(result != null, "merchant owner is not active");
        return result;
    }

    @Override
    public SourceMappingView resolveActive(SourceReference reference) {
        SourceReference normalized = normalizeSourceReference(reference);
        Instant effectiveAt = normalized.getEffectiveAt() == null ? Instant.now() : normalized.getEffectiveAt();
        List<MerchantSourceMappingDO> matches = mapper.selectActiveSourceMappings(
                TenantContextHolder.getRequiredTenantId(), normalized.getSourceSystem(), normalized.getSourceType(),
                normalized.getSourceId(), toUtc(effectiveAt));
        require(matches != null && matches.size() == 1,
                "source reference must resolve to exactly one active merchant mapping");
        return sourceMappingView(matches.get(0));
    }

    @Override
    public MerchantOperatorAuthorizationView requireAuthorizedOperator(MerchantOperatorAuthorizationCommand command) {
        require(command != null, "merchant operator authorization command is required");
        requireId(command.getMerchantId(), "merchantId");
        requireId(command.getShopId(), "shopId");
        requireId(command.getPrincipalId(), "principalId");
        requireText(command.getRoleCode(), "roleCode", 32);
        requireActiveReference(new MerchantReferenceValidationCommand().setMerchantId(command.getMerchantId())
                .setShopId(command.getShopId()));
        MerchantOperatorAuthorizationView result = mapper.selectActiveAssignment(
                TenantContextHolder.getRequiredTenantId(), command.getMerchantId(), command.getShopId(),
                command.getPrincipalId(), command.getRoleCode());
        if (result == null && !"OWNER".equals(command.getRoleCode())) {
            result = mapper.selectActiveAssignment(TenantContextHolder.getRequiredTenantId(), command.getMerchantId(),
                    command.getShopId(), command.getPrincipalId(), "OWNER");
        }
        require(result != null, "principal is not authorized for the merchant shop role");
        return result;
    }

    private MerchantCommandResult createDraft(Long tenantId, Long operationId, MerchantCommand command,
                                               LocalDateTime now) {
        requireText(command.getLegalName(), "legalName", 256);
        requireText(command.getRegistrationHashToken(), "registrationHashToken", 256);
        require(isSafeRestrictedReference(command.getRegistrationHashToken()),
                "registrationHashToken must be a digest or restricted-store token");
        if (command.getBusinessLicenseToken() != null) {
            requireText(command.getBusinessLicenseToken(), "businessLicenseToken", 256);
            require(isSafeRestrictedReference(command.getBusinessLicenseToken()),
                    "businessLicenseToken must be a digest or restricted-store token");
        }
        requireId(command.getOwnerPrincipalId(), "ownerPrincipalId");
        requireText(command.getChannelCode(), "channelCode", 32);
        require(CHANNELS.contains(command.getChannelCode()), "unsupported merchant channelCode");
        requireText(command.getExternalShopId(), "externalShopId", 128);

        String legalEntityId = UUID.randomUUID().toString();
        String applicationId = UUID.randomUUID().toString();
        MerchantLegalEntityDO entity = new MerchantLegalEntityDO().setLegalEntityId(legalEntityId)
                .setTenantId(tenantId).setLegalName(command.getLegalName())
                .setRegistrationHashToken(command.getRegistrationHashToken())
                .setBusinessLicenseToken(command.getBusinessLicenseToken()).setStatus("PENDING_VERIFICATION")
                .setVersion(1L).setCreatedAt(now).setUpdatedAt(now);
        MerchantOnboardingApplicationDO application = new MerchantOnboardingApplicationDO()
                .setApplicationId(applicationId).setTenantId(tenantId).setRunId(command.getRunId())
                .setLegalEntityId(legalEntityId).setOwnerPrincipalId(command.getOwnerPrincipalId())
                .setChannelCode(command.getChannelCode()).setExternalShopId(command.getExternalShopId())
                .setStatus("DRAFT").setVersion(1L).setCreatedAt(now).setUpdatedAt(now);
        require(mapper.insertLegalEntity(entity) == 1, "failed to create legal entity");
        require(mapper.insertApplication(application) == 1, "failed to create onboarding application");
        appendHistory(tenantId, operationId, "LEGAL_ENTITY", entity.getLegalEntityId(), 1L, null,
                entity.getStatus(), null, command, now);
        appendHistory(tenantId, operationId, "ONBOARDING", applicationId, 1L, null, "DRAFT", null, command, now);
        appendEntityEvent(tenantId, "LEGAL_ENTITY", entity.getLegalEntityId(), 1L, null, entity.getStatus(),
                command, Map.of("legal_entity_id", entity.getLegalEntityId()));
        appendOnboardingEvent(tenantId, application, null, command);
        return onboardingResult(operationId, application, entity, null, null, null, false);
    }

    private MerchantCommandResult transitionOnboarding(Long tenantId, Long operationId, MerchantCommand command,
                                                       LocalDateTime now) {
        requireId(command.getApplicationId(), "applicationId");
        requireExpectedVersion(command);
        MerchantOnboardingApplicationDO application = mapper.selectApplicationForUpdate(tenantId,
                command.getApplicationId());
        require(application != null, "onboarding application does not exist");
        require(Objects.equals(application.getVersion(), command.getExpectedVersion()),
                "onboarding application version conflict");
        String before = application.getStatus();
        String after = nextOnboardingStatus(command, before);
        if (command.getOperation() == MerchantOperation.REJECT_ONBOARDING) {
            requireText(command.getReason(), "reason", 512);
        }

        MerchantLegalEntityDO entity = null;
        MerchantAccountDO merchant = null;
        MerchantShopDO shop = null;
        MerchantOperatorAssignmentDO assignment = null;
        if (command.getOperation() == MerchantOperation.APPROVE_ONBOARDING) {
            principalValidationApi.requireActivePrincipal(application.getOwnerPrincipalId());
            entity = mapper.selectLegalEntityForUpdate(tenantId, application.getLegalEntityId());
            require(entity != null, "onboarding legal entity does not exist");
            require("PENDING_VERIFICATION".equals(entity.getStatus()) && Objects.equals(1L, entity.getVersion()),
                    "onboarding legal entity is not pending verification");
            require(mapper.transitionLegalEntity(tenantId, entity.getLegalEntityId(), entity.getVersion(),
                    "PENDING_VERIFICATION", "VERIFIED", now) == 1, "legal entity transition conflict");
            entity.setStatus("VERIFIED").setVersion(entity.getVersion() + 1).setUpdatedAt(now);
        }

        require(mapper.transitionApplication(tenantId, application.getApplicationId(), application.getVersion(),
                before, after, command.getReason(), now) == 1, "onboarding application transition conflict");
        application.setStatus(after).setVersion(application.getVersion() + 1).setDecisionReason(command.getReason())
                .setUpdatedAt(now);
        appendHistory(tenantId, operationId, "ONBOARDING", application.getApplicationId(), application.getVersion(),
                before, after, command.getReason(), command, now);
        if (command.getOperation() != MerchantOperation.APPROVE_ONBOARDING) {
            appendOnboardingEvent(tenantId, application, before, command);
        }

        if (command.getOperation() == MerchantOperation.APPROVE_ONBOARDING) {
            merchant = newMerchant(tenantId, entity.getLegalEntityId(), now);
            shop = newShop(tenantId, merchant.getMerchantId(), application, now);
            assignment = newOwnerAssignment(tenantId, merchant.getMerchantId(), shop.getShopId(),
                    application.getOwnerPrincipalId(), command, now);
            require(mapper.insertMerchant(merchant) == 1, "failed to create approved merchant");
            require(mapper.insertShop(shop) == 1, "failed to create approved merchant shop");
            require(mapper.insertAssignment(assignment) == 1, "failed to create merchant owner assignment");
            require(mapper.attachApprovedEntities(tenantId, application.getApplicationId(), application.getVersion(),
                    merchant.getMerchantId(), shop.getShopId(), assignment.getAssignmentId(), now) == 1,
                    "approved onboarding entity linkage conflict");
            application.setMerchantId(merchant.getMerchantId()).setShopId(shop.getShopId())
                    .setOwnerAssignmentId(assignment.getAssignmentId());
            appendOnboardingEvent(tenantId, application, before, command);
            appendHistory(tenantId, operationId, "LEGAL_ENTITY", entity.getLegalEntityId(), entity.getVersion(),
                    "PENDING_VERIFICATION", entity.getStatus(), null, command, now);
            appendHistory(tenantId, operationId, "MERCHANT", merchant.getMerchantId(), 1L, null,
                    merchant.getStatus(), null, command, now);
            appendHistory(tenantId, operationId, "SHOP", shop.getShopId(), 1L, null, shop.getStatus(), null,
                    command, now);
            appendHistory(tenantId, operationId, "OPERATOR_ASSIGNMENT", assignment.getAssignmentId(), 1L, null,
                    assignment.getStatus(), null, command, now);
            appendEntityEvent(tenantId, "LEGAL_ENTITY", entity.getLegalEntityId(), entity.getVersion(),
                    "PENDING_VERIFICATION", entity.getStatus(), command,
                    Map.of("legal_entity_id", entity.getLegalEntityId()));
            appendEntityEvent(tenantId, "MERCHANT", merchant.getMerchantId(), 1L, null, merchant.getStatus(), command,
                    Map.of("merchant_id", merchant.getMerchantId(), "legal_entity_id", entity.getLegalEntityId()));
            appendEntityEvent(tenantId, "SHOP", shop.getShopId(), 1L, null, shop.getStatus(), command,
                    Map.of("shop_id", shop.getShopId(), "merchant_id", merchant.getMerchantId(),
                            "channel_code", shop.getChannelCode(), "external_shop_id", shop.getExternalShopId()));
            appendAssignmentEvent(tenantId, assignment, null, command);
        }
        return onboardingResult(operationId, application, entity, merchant, shop, assignment, false);
    }

    private MerchantCommandResult activateMerchant(Long tenantId, Long operationId, MerchantCommand command,
                                                    LocalDateTime now) {
        requireId(command.getMerchantId(), "merchantId");
        requireExpectedVersion(command);
        MerchantAccountDO merchant = mapper.selectMerchantForUpdate(tenantId, command.getMerchantId());
        require(merchant != null, "merchant does not exist");
        require(Objects.equals(merchant.getVersion(), command.getExpectedVersion()), "merchant version conflict");
        require("PENDING_ACTIVATION".equals(merchant.getStatus()),
                "ACTIVATE_MERCHANT requires PENDING_ACTIVATION merchant");
        require(mapper.transitionMerchant(tenantId, merchant.getMerchantId(), merchant.getVersion(),
                "PENDING_ACTIVATION", "ACTIVE", now) == 1, "merchant transition conflict");
        merchant.setStatus("ACTIVE").setVersion(merchant.getVersion() + 1).setUpdatedAt(now);
        appendHistory(tenantId, operationId, "MERCHANT", merchant.getMerchantId(), merchant.getVersion(),
                "PENDING_ACTIVATION", "ACTIVE", null, command, now);
        appendEntityEvent(tenantId, "MERCHANT", merchant.getMerchantId(), merchant.getVersion(),
                "PENDING_ACTIVATION", "ACTIVE", command,
                Map.of("merchant_id", merchant.getMerchantId(), "legal_entity_id", merchant.getLegalEntityId()));
        return new MerchantCommandResult().setOperationId(operationId).setMerchantId(merchant.getMerchantId())
                .setMerchantStatus(merchant.getStatus()).setMerchantVersion(merchant.getVersion());
    }

    private MerchantCommandResult activateShop(Long tenantId, Long operationId, MerchantCommand command,
                                                LocalDateTime now) {
        requireId(command.getShopId(), "shopId");
        requireExpectedVersion(command);
        MerchantShopDO shop = mapper.selectShopForUpdate(tenantId, command.getShopId());
        require(shop != null, "merchant shop does not exist");
        require(Objects.equals(shop.getVersion(), command.getExpectedVersion()), "merchant shop version conflict");
        require("DRAFT".equals(shop.getStatus()), "ACTIVATE_SHOP requires DRAFT shop");
        MerchantAccountDO merchant = mapper.selectMerchantForUpdate(tenantId, shop.getMerchantId());
        require(merchant != null && "ACTIVE".equals(merchant.getStatus()),
                "ACTIVATE_SHOP requires ACTIVE merchant");
        require(mapper.transitionShop(tenantId, shop.getShopId(), shop.getVersion(), "DRAFT", "ACTIVE", now) == 1,
                "merchant shop transition conflict");
        shop.setStatus("ACTIVE").setVersion(shop.getVersion() + 1).setUpdatedAt(now);
        appendHistory(tenantId, operationId, "SHOP", shop.getShopId(), shop.getVersion(), "DRAFT", "ACTIVE",
                null, command, now);
        appendEntityEvent(tenantId, "SHOP", shop.getShopId(), shop.getVersion(), "DRAFT", "ACTIVE", command,
                Map.of("shop_id", shop.getShopId(), "merchant_id", shop.getMerchantId(),
                        "channel_code", shop.getChannelCode(), "external_shop_id", shop.getExternalShopId()));
        return new MerchantCommandResult().setOperationId(operationId).setMerchantId(shop.getMerchantId())
                .setMerchantStatus(merchant.getStatus()).setMerchantVersion(merchant.getVersion())
                .setShopId(shop.getShopId()).setShopStatus(shop.getStatus()).setShopVersion(shop.getVersion());
    }

    private MerchantCommandResult transitionMerchantStatus(Long tenantId, Long operationId, MerchantCommand command,
                                                            LocalDateTime now, String before, String after,
                                                            boolean reasonRequired) {
        requireId(command.getMerchantId(), "merchantId");
        requireExpectedVersion(command);
        if (reasonRequired) requireText(command.getReason(), "reason", 512);
        MerchantAccountDO merchant = mapper.selectMerchantForUpdate(tenantId, command.getMerchantId());
        require(merchant != null, "merchant does not exist");
        require(Objects.equals(merchant.getVersion(), command.getExpectedVersion()), "merchant version conflict");
        require(before.equals(merchant.getStatus()), command.getOperation() + " requires " + before + " merchant");
        require(mapper.transitionMerchant(tenantId, merchant.getMerchantId(), merchant.getVersion(), before, after,
                now) == 1, "merchant transition conflict");
        merchant.setStatus(after).setVersion(merchant.getVersion() + 1).setUpdatedAt(now);
        appendHistory(tenantId, operationId, "MERCHANT", merchant.getMerchantId(), merchant.getVersion(), before,
                after, command.getReason(), command, now);
        String sourceEventId = appendEntityEvent(tenantId, "MERCHANT", merchant.getMerchantId(), merchant.getVersion(), before, after,
                command, Map.of("merchant_id", merchant.getMerchantId(),
                        "legal_entity_id", merchant.getLegalEntityId()));
        ListingUnpublishSagaView saga = "SUSPENDED".equals(after)
                ? startListingUnpublishSaga(command, sourceEventId, "MERCHANT", merchant.getVersion(),
                        merchant.getMerchantId(), null) : null;
        return new MerchantCommandResult().setOperationId(operationId).setMerchantId(merchant.getMerchantId())
                .setMerchantStatus(merchant.getStatus()).setMerchantVersion(merchant.getVersion())
                .setListingUnpublishSagaId(saga == null ? null : saga.getSagaId())
                .setAffectedListingCount(saga == null ? null : saga.getExpectedListingCount());
    }

    private MerchantCommandResult transitionShopStatus(Long tenantId, Long operationId, MerchantCommand command,
                                                        LocalDateTime now, String before, String after,
                                                        boolean reasonRequired, boolean activeMerchantRequired) {
        requireId(command.getShopId(), "shopId");
        requireExpectedVersion(command);
        if (reasonRequired) requireText(command.getReason(), "reason", 512);
        MerchantShopDO shop = mapper.selectShopForUpdate(tenantId, command.getShopId());
        require(shop != null, "merchant shop does not exist");
        require(Objects.equals(shop.getVersion(), command.getExpectedVersion()), "merchant shop version conflict");
        require(before.equals(shop.getStatus()), command.getOperation() + " requires " + before + " shop");
        MerchantAccountDO merchant = mapper.selectMerchantForUpdate(tenantId, shop.getMerchantId());
        require(merchant != null, "merchant shop owner does not exist");
        if (activeMerchantRequired) {
            require("ACTIVE".equals(merchant.getStatus()), command.getOperation() + " requires ACTIVE merchant");
        }
        require(mapper.transitionShop(tenantId, shop.getShopId(), shop.getVersion(), before, after, now) == 1,
                "merchant shop transition conflict");
        shop.setStatus(after).setVersion(shop.getVersion() + 1).setUpdatedAt(now);
        appendHistory(tenantId, operationId, "SHOP", shop.getShopId(), shop.getVersion(), before, after,
                command.getReason(), command, now);
        String sourceEventId = appendEntityEvent(tenantId, "SHOP", shop.getShopId(), shop.getVersion(), before, after, command,
                Map.of("shop_id", shop.getShopId(), "merchant_id", shop.getMerchantId(),
                        "channel_code", shop.getChannelCode(), "external_shop_id", shop.getExternalShopId()));
        ListingUnpublishSagaView saga = "PAUSED".equals(after)
                ? startListingUnpublishSaga(command, sourceEventId, "SHOP", shop.getVersion(),
                        shop.getMerchantId(), shop.getShopId()) : null;
        return new MerchantCommandResult().setOperationId(operationId).setMerchantId(shop.getMerchantId())
                .setMerchantStatus(merchant.getStatus()).setMerchantVersion(merchant.getVersion())
                .setShopId(shop.getShopId()).setShopStatus(shop.getStatus()).setShopVersion(shop.getVersion())
                .setListingUnpublishSagaId(saga == null ? null : saga.getSagaId())
                .setAffectedListingCount(saga == null ? null : saga.getExpectedListingCount());
    }

    private ListingUnpublishSagaView startListingUnpublishSaga(MerchantCommand command, String sourceEventId,
                                                                 String sourceEntityType, Long sourceVersion,
                                                                 String merchantId, String shopId) {
        return listingUnpublishSagaCommandApi.execute(ListingUnpublishSagaCommand.builder()
                .operation(ListingUnpublishSagaOperation.START)
                .idempotencyKey("listing-enforcement:" + sourceEventId).runId(command.getRunId())
                .sourceEventId(sourceEventId).sourceEntityType(sourceEntityType)
                .sourceAggregateVersion(sourceVersion).merchantId(merchantId).shopId(shopId)
                .reason(command.getReason()).correlationId(resolvedCorrelationId(command))
                .causationId(command.getCausationId()).occurredAt(command.getOccurredAt()).build());
    }

    private MerchantCommandResult linkSource(Long tenantId, Long operationId, MerchantCommand command,
                                              LocalDateTime now) {
        SourceReference source = normalizeSourceReference(command.getSourceReference());
        String targetType = normalizeUpper(command.getTargetType(), "targetType", 32);
        require(SOURCE_TARGET_TYPES.contains(targetType),
                "targetType must be LEGAL_ENTITY, MERCHANT, or SHOP");
        requireId(command.getTargetId(), "targetId");
        String targetId = command.getTargetId().trim();
        require(!targetId.equalsIgnoreCase(source.getSourceId()), "canonical target id must differ from source id");
        require(command.getValidFrom() != null, "validFrom is required");
        if (command.getValidTo() != null) {
            require(command.getValidTo().isAfter(command.getValidFrom()), "validTo must be after validFrom");
        }
        requireText(command.getVerificationRef(), "verificationRef", 256);
        require(isSafeVerificationReference(command.getVerificationRef()),
                "verificationRef must be an opaque evidence reference");
        requireText(command.getMigrationRunId(), "migrationRunId", 128);
        validateSourceMappingTarget(tenantId, targetType, targetId);
        List<MerchantSourceMappingDO> existing = mapper.selectActiveSourceMappingsForUpdate(tenantId,
                source.getSourceSystem(), source.getSourceType(), source.getSourceId());
        require(existing == null || existing.isEmpty(), "source reference already has an active mapping");

        MerchantSourceMappingDO mapping = new MerchantSourceMappingDO().setMappingId(UUID.randomUUID().toString())
                .setTenantId(tenantId).setSourceSystem(source.getSourceSystem()).setSourceType(source.getSourceType())
                .setSourceId(source.getSourceId()).setTargetType(targetType).setTargetId(targetId)
                .setValidFrom(toUtc(command.getValidFrom()))
                .setValidTo(command.getValidTo() == null ? null : toUtc(command.getValidTo()))
                .setVerificationRef(command.getVerificationRef()).setMigrationRunId(command.getMigrationRunId())
                .setStatus("ACTIVE").setVersion(1L).setCreatedAt(now).setUpdatedAt(now);
        switch (targetType) {
            case "LEGAL_ENTITY" -> mapping.setLegalEntityId(targetId);
            case "MERCHANT" -> mapping.setMerchantId(targetId);
            case "SHOP" -> mapping.setShopId(targetId);
            default -> throw new IllegalArgumentException("unsupported source mapping target type");
        }
        require(mapper.insertSourceMapping(mapping) == 1, "failed to link merchant source mapping");
        appendSourceMappingEvent(tenantId, mapping, null, command);
        return sourceMappingResult(operationId, mapping);
    }

    private MerchantCommandResult revokeSource(Long tenantId, Long operationId, MerchantCommand command,
                                                LocalDateTime now) {
        requireId(command.getSourceMappingId(), "sourceMappingId");
        requireExpectedVersion(command);
        MerchantSourceMappingDO mapping = mapper.selectSourceMappingForUpdate(tenantId, command.getSourceMappingId());
        require(mapping != null, "merchant source mapping does not exist");
        require(Objects.equals(mapping.getVersion(), command.getExpectedVersion()),
                "merchant source mapping version conflict");
        require("ACTIVE".equals(mapping.getStatus()), "REVOKE_SOURCE requires ACTIVE source mapping");
        LocalDateTime requestedEnd = command.getValidTo() == null ? toUtc(command.getOccurredAt())
                : toUtc(command.getValidTo());
        require(requestedEnd.isAfter(mapping.getValidFrom()), "source mapping revoke time must be after validFrom");
        LocalDateTime effectiveEnd = mapping.getValidTo() == null || requestedEnd.isBefore(mapping.getValidTo())
                ? requestedEnd : mapping.getValidTo();
        require(mapper.revokeSourceMapping(tenantId, mapping.getMappingId(), mapping.getVersion(), effectiveEnd, now)
                == 1, "merchant source mapping revoke conflict");
        mapping.setStatus("REVOKED").setVersion(mapping.getVersion() + 1).setValidTo(effectiveEnd).setUpdatedAt(now);
        appendSourceMappingEvent(tenantId, mapping, "ACTIVE", command);
        return sourceMappingResult(operationId, mapping);
    }

    private void validateSourceMappingTarget(Long tenantId, String targetType, String targetId) {
        boolean exists = switch (targetType) {
            case "LEGAL_ENTITY" -> mapper.selectLegalEntityForUpdate(tenantId, targetId) != null;
            case "MERCHANT" -> mapper.selectMerchantForUpdate(tenantId, targetId) != null;
            case "SHOP" -> mapper.selectShopForUpdate(tenantId, targetId) != null;
            default -> false;
        };
        require(exists, targetType + " source mapping target does not exist in current tenant");
    }

    private static MerchantCommandResult sourceMappingResult(Long operationId, MerchantSourceMappingDO mapping) {
        return new MerchantCommandResult().setOperationId(operationId).setSourceMappingId(mapping.getMappingId())
                .setSourceSystem(mapping.getSourceSystem()).setSourceType(mapping.getSourceType())
                .setSourceId(mapping.getSourceId()).setTargetType(mapping.getTargetType())
                .setTargetId(mapping.getTargetId()).setValidFrom(toInstant(mapping.getValidFrom()))
                .setValidTo(toInstant(mapping.getValidTo())).setVerificationRef(mapping.getVerificationRef())
                .setMigrationRunId(mapping.getMigrationRunId()).setSourceMappingStatus(mapping.getStatus())
                .setSourceMappingVersion(mapping.getVersion());
    }

    private static SourceMappingView sourceMappingView(MerchantSourceMappingDO mapping) {
        return new SourceMappingView().setMappingId(mapping.getMappingId()).setSourceSystem(mapping.getSourceSystem())
                .setSourceType(mapping.getSourceType()).setSourceId(mapping.getSourceId())
                .setTargetType(mapping.getTargetType()).setTargetId(mapping.getTargetId())
                .setValidFrom(toInstant(mapping.getValidFrom())).setValidTo(toInstant(mapping.getValidTo()))
                .setVerificationRef(mapping.getVerificationRef()).setMigrationRunId(mapping.getMigrationRunId())
                .setStatus(mapping.getStatus()).setVersion(mapping.getVersion());
    }

    private static String nextOnboardingStatus(MerchantCommand command, String before) {
        return switch (command.getOperation()) {
            case SUBMIT_ONBOARDING -> requireTransition(before, "DRAFT", "SUBMITTED", "SUBMIT_ONBOARDING");
            case START_ONBOARDING_REVIEW -> requireTransition(before, "SUBMITTED", "UNDER_REVIEW",
                    "START_ONBOARDING_REVIEW");
            case APPROVE_ONBOARDING -> requireTransition(before, "UNDER_REVIEW", "APPROVED",
                    "APPROVE_ONBOARDING");
            case REJECT_ONBOARDING -> requireTransition(before, "UNDER_REVIEW", "REJECTED", "REJECT_ONBOARDING");
            case WITHDRAW_ONBOARDING -> {
                require(Set.of("DRAFT", "SUBMITTED", "UNDER_REVIEW").contains(before),
                        "WITHDRAW_ONBOARDING requires DRAFT, SUBMITTED, or UNDER_REVIEW");
                yield "WITHDRAWN";
            }
            default -> throw new IllegalArgumentException(command.getOperation() + " is not an onboarding transition");
        };
    }

    private static String requireTransition(String actual, String expected, String target, String operation) {
        require(expected.equals(actual), operation + " requires " + expected);
        return target;
    }

    private static MerchantAccountDO newMerchant(Long tenantId, String legalEntityId, LocalDateTime now) {
        String id = UUID.randomUUID().toString();
        return new MerchantAccountDO().setMerchantId(id).setTenantId(tenantId)
                .setMerchantCode("CMM" + id.replace("-", "").substring(0, 20).toUpperCase(Locale.ROOT))
                .setLegalEntityId(legalEntityId).setStatus("PENDING_ACTIVATION").setVersion(1L)
                .setCreatedAt(now).setUpdatedAt(now);
    }

    private static MerchantShopDO newShop(Long tenantId, String merchantId,
                                           MerchantOnboardingApplicationDO application, LocalDateTime now) {
        return new MerchantShopDO().setShopId(UUID.randomUUID().toString()).setTenantId(tenantId)
                .setMerchantId(merchantId).setChannelCode(application.getChannelCode())
                .setExternalShopId(application.getExternalShopId()).setStatus("DRAFT").setVersion(1L)
                .setCreatedAt(now).setUpdatedAt(now);
    }

    private static MerchantOperatorAssignmentDO newOwnerAssignment(Long tenantId, String merchantId, String shopId,
                                                                    String principalId, MerchantCommand command,
                                                                    LocalDateTime now) {
        return new MerchantOperatorAssignmentDO().setAssignmentId(UUID.randomUUID().toString()).setTenantId(tenantId)
                .setMerchantId(merchantId).setShopId(shopId).setPrincipalId(principalId).setRoleCode("OWNER")
                .setStatus("ACTIVE").setVersion(1L).setValidFrom(toUtc(command.getOccurredAt()))
                .setCreatedAt(now).setUpdatedAt(now);
    }

    private MerchantCommandResult onboardingResult(Long operationId, MerchantOnboardingApplicationDO application,
                                                   MerchantLegalEntityDO entity, MerchantAccountDO merchant,
                                                   MerchantShopDO shop, MerchantOperatorAssignmentDO assignment,
                                                   boolean duplicate) {
        return new MerchantCommandResult().setOperationId(operationId).setApplicationId(application.getApplicationId())
                .setOnboardingStatus(application.getStatus()).setApplicationVersion(application.getVersion())
                .setLegalEntityId(application.getLegalEntityId())
                .setLegalEntityStatus(entity == null ? null : entity.getStatus())
                .setLegalEntityVersion(entity == null ? null : entity.getVersion())
                .setMerchantId(merchant == null ? application.getMerchantId() : merchant.getMerchantId())
                .setMerchantStatus(merchant == null ? null : merchant.getStatus())
                .setMerchantVersion(merchant == null ? null : merchant.getVersion())
                .setShopId(shop == null ? application.getShopId() : shop.getShopId())
                .setShopStatus(shop == null ? null : shop.getStatus())
                .setShopVersion(shop == null ? null : shop.getVersion())
                .setOwnerAssignmentId(assignment == null ? application.getOwnerAssignmentId() : assignment.getAssignmentId())
                .setOwnerAssignmentStatus(assignment == null ? null : assignment.getStatus()).setDuplicate(duplicate);
    }

    private void appendHistory(Long tenantId, Long operationId, String aggregateType, String aggregateId,
                               Long aggregateVersion, String previous, String current, String reason,
                               MerchantCommand command, LocalDateTime now) {
        require(mapper.insertHistory(new MerchantStatusHistoryDO().setTenantId(tenantId)
                .setAggregateType(aggregateType).setAggregateId(aggregateId).setAggregateVersion(aggregateVersion)
                .setPreviousStatus(previous).setCurrentStatus(current).setOperationId(operationId).setReason(reason)
                .setOccurredAt(toUtc(command.getOccurredAt())).setCreatedAt(now)) == 1,
                "failed to append merchant status history");
    }

    private void appendOnboardingEvent(Long tenantId, MerchantOnboardingApplicationDO application, String previous,
                                       MerchantCommand command) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("application_id", application.getApplicationId());
        payload.put("legal_entity_id", application.getLegalEntityId());
        payload.put("owner_principal_id", application.getOwnerPrincipalId());
        payload.put("channel_code", application.getChannelCode());
        payload.put("external_shop_id", application.getExternalShopId());
        payload.put("previous_status", previous);
        payload.put("current_status", application.getStatus());
        if (application.getMerchantId() != null) payload.put("merchant_id", application.getMerchantId());
        if (application.getShopId() != null) payload.put("shop_id", application.getShopId());
        appendEvent(tenantId, ONBOARDING_EVENT, "MERCHANT_ONBOARDING", application.getApplicationId(),
                application.getVersion(), command, payload);
    }

    private String appendEntityEvent(Long tenantId, String entityType, String aggregateId, Long version,
                                     String previous, String current, MerchantCommand command,
                                     Map<String, Object> identifiers) {
        Map<String, Object> payload = new LinkedHashMap<>(identifiers);
        payload.put("entity_type", entityType);
        payload.put("previous_status", previous);
        payload.put("current_status", current);
        if (command.getReason() != null) payload.put("reason", command.getReason());
        return appendEvent(tenantId, ENTITY_EVENT, "MERCHANT_" + entityType, aggregateId, version, command, payload);
    }

    private void appendAssignmentEvent(Long tenantId, MerchantOperatorAssignmentDO assignment, String previous,
                                       MerchantCommand command) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("assignment_id", assignment.getAssignmentId());
        payload.put("merchant_id", assignment.getMerchantId());
        payload.put("shop_id", assignment.getShopId());
        payload.put("principal_id", assignment.getPrincipalId());
        payload.put("role_code", assignment.getRoleCode());
        payload.put("previous_status", previous);
        payload.put("current_status", assignment.getStatus());
        payload.put("valid_from", assignment.getValidFrom().toInstant(ZoneOffset.UTC).toString());
        appendEvent(tenantId, ASSIGNMENT_EVENT, "MERCHANT_OPERATOR_ASSIGNMENT", assignment.getAssignmentId(),
                assignment.getVersion(), command, payload);
    }

    private void appendSourceMappingEvent(Long tenantId, MerchantSourceMappingDO mapping, String previous,
                                          MerchantCommand command) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("run_id", command.getRunId());
        payload.put("migration_run_id", mapping.getMigrationRunId());
        payload.put("mapping_id", mapping.getMappingId());
        payload.put("source_system", mapping.getSourceSystem());
        payload.put("source_type", mapping.getSourceType());
        payload.put("source_id", mapping.getSourceId());
        payload.put("target_type", mapping.getTargetType());
        payload.put("target_id", mapping.getTargetId());
        payload.put("valid_from", toInstant(mapping.getValidFrom()).toString());
        payload.put("valid_to", mapping.getValidTo() == null ? null : toInstant(mapping.getValidTo()).toString());
        payload.put("previous_status", previous);
        payload.put("current_status", mapping.getStatus());
        payload.put("verification_ref", mapping.getVerificationRef());
        appendEvent(tenantId, SOURCE_MAPPING_EVENT, "MERCHANT_SOURCE_MAPPING", mapping.getMappingId(),
                mapping.getVersion(), command, payload);
    }

    private String appendEvent(Long tenantId, String eventType, String aggregateType, String aggregateId, Long version,
                               MerchantCommand command, Map<String, Object> payload) {
        String idempotency = command.getIdempotencyKey() + ":" + aggregateType + ":" + version;
        String eventId = UUID.nameUUIDFromBytes((tenantId + "|" + idempotency).getBytes(StandardCharsets.UTF_8)).toString();
        String correlationId = resolvedCorrelationId(tenantId, command);
        Object previousStatus = payload.get("previous_status");
        Object currentStatus = payload.get("current_status");
        boolean salesEligibilityTransition = "SUSPENDED".equals(previousStatus) || "PAUSED".equals(previousStatus)
                || "SUSPENDED".equals(currentStatus) || "PAUSED".equals(currentStatus);
        int schemaVersion = ENTITY_EVENT.equals(eventType) && salesEligibilityTransition ? 2 : 1;
        outboxAppender.append(AppendDomainEventCommand.builder().eventId(eventId).eventType(eventType)
                .schemaVersion(schemaVersion).sourceSystem("cloudmold-merchant").tenantId(tenantId)
                .aggregateType(aggregateType).aggregateId(aggregateId).aggregateVersion(version).eventSequence((short) 1)
                .occurredAt(command.getOccurredAt()).traceId(command.getTraceId())
                .correlationId(correlationId)
                .causationId(command.getCausationId()).idempotencyKey(idempotency).payload(payload)
                .headers(Map.of("run_id", command.getRunId())).destination("lakehouse").build());
        return eventId;
    }

    private static String resolvedCorrelationId(MerchantCommand command) {
        return resolvedCorrelationId(TenantContextHolder.getRequiredTenantId(), command);
    }

    private static String resolvedCorrelationId(Long tenantId, MerchantCommand command) {
        return command.getCorrelationId() == null
                ? UUID.nameUUIDFromBytes((tenantId + "|correlation|" + command.getRunId())
                        .getBytes(StandardCharsets.UTF_8)).toString()
                : command.getCorrelationId();
    }

    private static boolean isSafeRestrictedReference(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.startsWith("sha256:") || lower.startsWith("sha512:") || lower.startsWith("vault:")
                || lower.startsWith("kms:") || lower.startsWith("token:");
    }

    private static boolean isSafeVerificationReference(String value) {
        return value.matches("(?i)(sha256|sha512|ticket|run|evidence|vault|kms|token):[a-z0-9._/-]+");
    }

    private static SourceReference normalizeSourceReference(SourceReference reference) {
        require(reference != null, "sourceReference is required");
        String sourceSystem = normalizeUpper(reference.getSourceSystem(), "sourceReference.sourceSystem", 32);
        String sourceType = normalizeUpper(reference.getSourceType(), "sourceReference.sourceType", 32);
        requireText(reference.getSourceId(), "sourceReference.sourceId", 128);
        String sourceId = reference.getSourceId().trim();
        require(!sourceId.isEmpty(), "sourceReference.sourceId is required");
        return new SourceReference().setSourceSystem(sourceSystem).setSourceType(sourceType).setSourceId(sourceId)
                .setEffectiveAt(reference.getEffectiveAt());
    }

    private static String normalizeUpper(String value, String field, int max) {
        requireText(value, field, max);
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        require(!normalized.isEmpty(), field + " is required");
        require(normalized.length() <= max, field + " exceeds " + max + " characters");
        return normalized;
    }

    private static void validateCommon(MerchantCommand command) {
        require(command != null, "merchant command is required");
        require(command.getOperation() != null, "merchant operation is required");
        requireText(command.getIdempotencyKey(), "idempotencyKey", 128);
        requireText(command.getRunId(), "runId", 128);
        requireText(command.getSourceSystem(), "sourceSystem", 64);
        requireText(command.getTraceId(), "traceId", 128);
        require(command.getOccurredAt() != null, "occurredAt is required");
        if (command.getCorrelationId() != null) requireUuid(command.getCorrelationId(), "correlationId");
        if (command.getCausationId() != null) requireUuid(command.getCausationId(), "causationId");
    }

    private static void requireExpectedVersion(MerchantCommand command) {
        require(command.getExpectedVersion() != null && command.getExpectedVersion() > 0,
                "expectedVersion must be positive");
    }

    private static void requireId(String value, String field) {
        requireText(value, field, 64);
    }

    private static void requireText(String value, String field, int max) {
        require(value != null && !value.isBlank(), field + " is required");
        require(value.length() <= max, field + " exceeds " + max + " characters");
    }

    private static void requireUuid(String value, String field) {
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(field + " must be a UUID", error);
        }
    }

    private static LocalDateTime toUtc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T value : values) if (value != null) return value;
        throw new IllegalArgumentException("merchant result has no aggregate id");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
