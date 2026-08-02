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
    static final String MANAGED_ADMISSION_EVENT = "merchant.managed_admission.status_changed";
    static final String EVIDENCE_PACKAGE_EVENT = "merchant.managed_evidence_package.status_changed";
    static final String AI_DIAGNOSTIC_EVENT = "merchant.ai_diagnostic.status_changed";
    static final String FACTORY_INSPECTION_EVENT = "merchant.factory_inspection.status_changed";
    static final String FINAL_REVIEW_EVENT = "merchant.managed_final_review.status_changed";
    static final String INVITATION_EVENT = "merchant.managed_invitation.status_changed";
    static final String BUYER_ASSIGNMENT_EVENT = "merchant.buyer_assignment.status_changed";
    static final String GRADE_DECISION_EVENT = "merchant.grade_decision.recorded";
    static final String PROBATION_ASSESSMENT_EVENT = "merchant.probation_assessment.recorded";
    static final String MONTHLY_SCORECARD_EVENT = "merchant.monthly_scorecard.recorded";
    static final String EXIT_DECISION_EVENT = "merchant.exit_decision.recorded";
    private static final Set<String> CHANNELS = Set.of("INTERNAL_COMPANY", "YSHOPPING_INTERNAL", "YSHOPPING");
    private static final Set<String> SOURCE_TARGET_TYPES = Set.of("LEGAL_ENTITY", "MERCHANT", "SHOP");
    private static final Set<String> ADMISSION_RECOMMENDATIONS = Set.of("FACTORY_INSPECTION_REQUIRED",
            "MANUAL_REVIEW_ONLY", "REJECT_ADMISSION");
    private static final Set<String> FINAL_REVIEW_DECISIONS = Set.of("APPROVED", "REJECTED");
    private static final Set<String> GRADE_DECISION_STATUSES = Set.of("APPROVED", "REJECTED");
    private static final Set<String> GRADE_TRANSITIONS = Set.of("UPGRADE", "MAINTAIN", "DOWNGRADE");
    private static final Set<String> PROBATION_STATUSES = Set.of("PASS", "FAIL", "NEEDS_REMEDIATION");
    private static final Set<String> SCORECARD_STATUSES = Set.of("RECORDED", "REVIEWED");
    private static final Set<String> SCORECARD_TRANSITIONS = Set.of("UPGRADE", "MAINTAIN", "DOWNGRADE");
    private static final Set<String> EXIT_REASON_TYPES = Set.of("RED_LINE", "MULTIPLE_REMEDIATION_FAILURE");
    private static final Set<String> BENEFIT_STATUSES = Set.of("APPROVED", "SUSPENDED", "REVOKED");
    private static final Set<String> PROBATION_GATE_CODES = Set.of("INVITATION_ATTRIBUTION", "GOODS_BOARD_QUALITY",
            "INSPECTION_READINESS");
    private static final Set<String> PROBATION_GATE_STATUSES = Set.of("PASS", "FAIL", "NEEDS_REMEDIATION");
    private static final Set<String> NINE_OBLIGATION_CODES = Set.of("NO_SOURCE_RISK", "PLAN_MATCHING_SUPPLY",
            "SHIPMENT_QUALITY", "SUPPLY_STABILITY", "SAMPLING_AND_PATTERN", "SLOT_FULFILLMENT",
            "SALES_VELOCITY", "PRICE_REVIEW_COORDINATION", "QUALITY_MONITORING");
    private static final Set<String> SCORECARD_ITEM_STATUSES = Set.of("MET", "NOT_MET", "RED_LINE",
            "UNDER_REMEDIATION");

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
            case OPEN_MANAGED_ADMISSION -> openManagedAdmission(tenantId, operationId, command, now);
            case RECORD_MANAGED_ATTRIBUTION -> recordManagedAttribution(tenantId, operationId, command, now);
            case SUBMIT_MANAGED_EVIDENCE_PACKAGE -> submitManagedEvidencePackage(tenantId, operationId, command, now);
            case PROPOSE_AI_DIAGNOSTIC -> proposeAiDiagnostic(tenantId, operationId, command, now);
            case ACCEPT_AI_DIAGNOSTIC -> reviewAiDiagnostic(tenantId, operationId, command, now, true);
            case REJECT_AI_DIAGNOSTIC -> reviewAiDiagnostic(tenantId, operationId, command, now, false);
            case CREATE_FACTORY_INSPECTION_TASK -> createFactoryInspectionTask(tenantId, operationId, command, now);
            case CLAIM_FACTORY_INSPECTION_TASK -> transitionFactoryInspectionTask(tenantId, operationId, command, now,
                    "PENDING_CLAIM", "PENDING_SCHEDULE", false);
            case SCHEDULE_FACTORY_INSPECTION_TASK -> transitionFactoryInspectionTask(tenantId, operationId, command, now,
                    "PENDING_SCHEDULE", "PENDING_INSPECTION", true);
            case SUBMIT_FACTORY_INSPECTION -> transitionFactoryInspectionTask(tenantId, operationId, command, now,
                    "PENDING_INSPECTION", "PENDING_QA_INSPECTION", false);
            case REQUEST_FACTORY_REMEDIATION -> transitionFactoryInspectionTask(tenantId, operationId, command, now,
                    "PENDING_QA_INSPECTION", "PENDING_REMEDIATION", false);
            case SUBMIT_FACTORY_FIRST_REVIEW -> transitionFactoryInspectionTaskToFirstReview(tenantId, operationId, command, now);
            case SUBMIT_FACTORY_FINAL_REVIEW -> transitionFactoryInspectionTask(tenantId, operationId, command, now,
                    "PENDING_FIRST_REVIEW", "PENDING_FINAL_REVIEW", false);
            case COMPLETE_FACTORY_INSPECTION_TASK -> completeFactoryInspectionTask(tenantId, operationId, command, now);
            case CANCEL_FACTORY_INSPECTION_TASK -> cancelFactoryInspectionTask(tenantId, operationId, command, now);
            case RECORD_MANAGED_FINAL_REVIEW -> recordManagedFinalReview(tenantId, operationId, command, now);
            case ISSUE_MANAGED_INVITATION -> issueManagedInvitation(tenantId, operationId, command, now);
            case MARK_MANAGED_INVITATION_USED -> markManagedInvitationUsed(tenantId, operationId, command, now);
            case ASSIGN_FACTORY_INSPECTION_TEAM -> assignFactoryInspectionTeam(tenantId, operationId, command, now);
            case RECORD_GRADE_BENEFIT_DECISION -> recordGradeBenefitDecision(tenantId, operationId, command, now);
            case RECORD_PROBATION_ASSESSMENT -> recordProbationAssessment(tenantId, operationId, command, now);
            case RECORD_MONTHLY_SCORECARD -> recordMonthlyScorecard(tenantId, operationId, command, now);
            case RECORD_EXIT_DECISION -> recordExitDecision(tenantId, operationId, command, now);
        };
        String aggregateId = firstNonNull(result.getApplicationId(), result.getMerchantId(), result.getShopId(),
                result.getSourceMappingId(), result.getAdmissionId(), result.getInvitationId(),
                result.getBuyerAssignmentId(), result.getGradeDecisionId(), result.getProbationAssessmentId(),
                result.getScorecardId(), result.getExitDecisionId());
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

    private MerchantCommandResult openManagedAdmission(Long tenantId, Long operationId, MerchantCommand command,
                                                       LocalDateTime now) {
        requireId(command.getApplicationId(), "applicationId");
        MerchantManagedAdmissionDO existing = mapper.selectManagedAdmissionByApplicationForUpdate(tenantId,
                command.getApplicationId());
        if (existing != null) {
            return managedAdmissionResult(operationId, existing, mapper.selectLatestManagedEvidencePackage(tenantId,
                    existing.getAdmissionId()), mapper.selectLatestAiDiagnostic(tenantId, existing.getAdmissionId()),
                    mapper.selectLatestFactoryInspectionTask(tenantId, existing.getAdmissionId()),
                    mapper.selectLatestManagedFinalReview(tenantId, existing.getAdmissionId()));
        }
        MerchantOnboardingApplicationDO application = mapper.selectApplicationForUpdate(tenantId, command.getApplicationId());
        require(application != null, "onboarding application does not exist");
        require("APPROVED".equals(application.getStatus()), "managed admission requires APPROVED onboarding");
        requireId(application.getMerchantId(), "approved onboarding merchantId");
        requireId(application.getShopId(), "approved onboarding shopId");
        MerchantManagedAdmissionDO admission = new MerchantManagedAdmissionDO()
                .setAdmissionId(UUID.randomUUID().toString())
                .setTenantId(tenantId)
                .setApplicationId(application.getApplicationId())
                .setMerchantId(application.getMerchantId())
                .setShopId(application.getShopId())
                .setStatus("ATTRIBUTION_PENDING")
                .setVersion(1L)
                .setCreatedAt(now)
                .setUpdatedAt(now);
        require(mapper.insertManagedAdmission(admission) == 1, "failed to open managed admission");
        appendHistory(tenantId, operationId, "MANAGED_ADMISSION", admission.getAdmissionId(), admission.getVersion(),
                null, admission.getStatus(), null, command, now);
        appendManagedAdmissionEvent(tenantId, admission, null, command);
        return managedAdmissionResult(operationId, admission, null, null, null, null);
    }

    private MerchantCommandResult recordManagedAttribution(Long tenantId, Long operationId, MerchantCommand command,
                                                           LocalDateTime now) {
        requireExpectedVersion(command);
        MerchantManagedAdmissionDO admission = requireManagedAdmissionForUpdate(tenantId, command);
        require(Objects.equals(admission.getVersion(), command.getExpectedVersion()),
                "managed admission version conflict");
        require(Set.of("ATTRIBUTION_PENDING", "EVIDENCE_PENDING").contains(admission.getStatus()),
                "managed attribution requires ATTRIBUTION_PENDING or EVIDENCE_PENDING admission");
        requireText(command.getAttributionChannelCode(), "attributionChannelCode", 32);
        SourceReference source = normalizeSourceReference(command.getSourceReference());
        requireText(command.getAttributionReference(), "attributionReference", 128);
        requireText(command.getAttributionEvidenceRef(), "attributionEvidenceRef", 256);
        require(isSafeVerificationReference(command.getAttributionEvidenceRef()),
                "attributionEvidenceRef must be an opaque evidence reference");
        String before = admission.getStatus();
        require(transitionManagedAdmission(tenantId, admission, before, "EVIDENCE_PENDING", command, now,
                command.getAttributionChannelCode().trim().toUpperCase(Locale.ROOT), source.getSourceSystem(),
                source.getSourceType(), source.getSourceId(), command.getAttributionReference().trim(),
                command.getAttributionEvidenceRef().trim(), admission.getDiagnosticId(),
                admission.getInspectionTaskId(), admission.getFinalReviewId()),
                "managed admission attribution transition conflict");
        admission.setStatus("EVIDENCE_PENDING")
                .setAttributionChannelCode(command.getAttributionChannelCode().trim().toUpperCase(Locale.ROOT))
                .setAttributionSourceSystem(source.getSourceSystem())
                .setAttributionSourceType(source.getSourceType())
                .setAttributionSourceId(source.getSourceId())
                .setAttributionReference(command.getAttributionReference().trim())
                .setAttributionEvidenceRef(command.getAttributionEvidenceRef().trim())
                .setVersion(admission.getVersion() + 1)
                .setUpdatedAt(now);
        appendHistory(tenantId, operationId, "MANAGED_ADMISSION", admission.getAdmissionId(), admission.getVersion(),
                before, admission.getStatus(), command.getAttributionReference(), command, now);
        appendManagedAdmissionEvent(tenantId, admission, before, command);
        return managedAdmissionResult(operationId, admission, null, null, null, null);
    }

    private MerchantCommandResult submitManagedEvidencePackage(Long tenantId, Long operationId, MerchantCommand command,
                                                               LocalDateTime now) {
        requireExpectedVersion(command);
        MerchantManagedAdmissionDO admission = requireManagedAdmissionForUpdate(tenantId, command);
        require(Objects.equals(admission.getVersion(), command.getExpectedVersion()),
                "managed admission version conflict");
        require("EVIDENCE_PENDING".equals(admission.getStatus()),
                "managed evidence package requires EVIDENCE_PENDING admission");
        requireText(command.getEvidencePackageRef(), "evidencePackageRef", 256);
        require(isSafeVerificationReference(command.getEvidencePackageRef()),
                "evidencePackageRef must be an opaque evidence reference");
        List<MerchantManagedEvidenceItem> items = normalizeEvidenceItems(command.getEvidenceItems());
        MerchantManagedEvidencePackageDO existing = mapper.selectLatestManagedEvidencePackageForUpdate(tenantId,
                admission.getAdmissionId());
        require(existing == null, "managed evidence package already exists");
        MerchantManagedEvidencePackageDO evidencePackage = new MerchantManagedEvidencePackageDO()
                .setEvidencePackageId(command.getEvidencePackageId() == null ? UUID.randomUUID().toString()
                        : command.getEvidencePackageId().trim())
                .setTenantId(tenantId)
                .setAdmissionId(admission.getAdmissionId())
                .setPackageRef(command.getEvidencePackageRef().trim())
                .setItemsJson(JsonUtils.toJsonString(items))
                .setStatus("SUBMITTED")
                .setVersion(1L)
                .setCreatedAt(now)
                .setUpdatedAt(now);
        require(mapper.insertManagedEvidencePackage(evidencePackage) == 1, "failed to create managed evidence package");
        String before = admission.getStatus();
        require(transitionManagedAdmission(tenantId, admission, before, "DIAGNOSTIC_PENDING", command, now,
                admission.getAttributionChannelCode(), admission.getAttributionSourceSystem(),
                admission.getAttributionSourceType(), admission.getAttributionSourceId(),
                admission.getAttributionReference(), admission.getAttributionEvidenceRef(), admission.getDiagnosticId(),
                admission.getInspectionTaskId(), admission.getFinalReviewId()),
                "managed admission evidence transition conflict");
        admission.setStatus("DIAGNOSTIC_PENDING").setVersion(admission.getVersion() + 1).setUpdatedAt(now);
        appendHistory(tenantId, operationId, "EVIDENCE_PACKAGE", evidencePackage.getEvidencePackageId(),
                evidencePackage.getVersion(), null, evidencePackage.getStatus(), evidencePackage.getPackageRef(),
                command, now);
        appendEvidencePackageEvent(tenantId, evidencePackage, null, items, command);
        appendHistory(tenantId, operationId, "MANAGED_ADMISSION", admission.getAdmissionId(), admission.getVersion(),
                before, admission.getStatus(), evidencePackage.getPackageRef(), command, now);
        appendManagedAdmissionEvent(tenantId, admission, before, command);
        return managedAdmissionResult(operationId, admission, evidencePackage, null, null, null);
    }

    private MerchantCommandResult proposeAiDiagnostic(Long tenantId, Long operationId, MerchantCommand command,
                                                      LocalDateTime now) {
        requireExpectedVersion(command);
        MerchantManagedAdmissionDO admission = requireManagedAdmissionForUpdate(tenantId, command);
        require(Objects.equals(admission.getVersion(), command.getExpectedVersion()),
                "managed admission version conflict");
        require("DIAGNOSTIC_PENDING".equals(admission.getStatus()),
                "AI diagnostic proposal requires DIAGNOSTIC_PENDING admission");
        MerchantManagedEvidencePackageDO evidencePackage = mapper.selectLatestManagedEvidencePackageForUpdate(tenantId,
                admission.getAdmissionId());
        require(evidencePackage != null && "SUBMITTED".equals(evidencePackage.getStatus()),
                "AI diagnostic proposal requires submitted evidence package");
        String recommendationCode = normalizeUpper(command.getRecommendationCode(), "recommendationCode", 64);
        require(ADMISSION_RECOMMENDATIONS.contains(recommendationCode), "unsupported recommendationCode");
        requireText(command.getRecommendationSummary(), "recommendationSummary", 512);
        requireText(command.getDiagnosticEvidenceRef(), "diagnosticEvidenceRef", 256);
        require(isSafeVerificationReference(command.getDiagnosticEvidenceRef()),
                "diagnosticEvidenceRef must be an opaque evidence reference");
        MerchantAiDiagnosticDO diagnostic = new MerchantAiDiagnosticDO()
                .setDiagnosticId(command.getDiagnosticId() == null ? UUID.randomUUID().toString()
                        : command.getDiagnosticId().trim())
                .setTenantId(tenantId)
                .setAdmissionId(admission.getAdmissionId())
                .setEvidencePackageId(evidencePackage.getEvidencePackageId())
                .setRecommendationCode(recommendationCode)
                .setRecommendationSummary(command.getRecommendationSummary().trim())
                .setEvidenceRef(command.getDiagnosticEvidenceRef().trim())
                .setStatus("PENDING_REVIEW")
                .setVersion(1L)
                .setCreatedAt(now)
                .setUpdatedAt(now);
        require(mapper.insertAiDiagnostic(diagnostic) == 1, "failed to create AI diagnostic");
        String before = admission.getStatus();
        require(transitionManagedAdmission(tenantId, admission, before, "DIAGNOSTIC_PENDING_REVIEW", command, now,
                admission.getAttributionChannelCode(), admission.getAttributionSourceSystem(),
                admission.getAttributionSourceType(), admission.getAttributionSourceId(),
                admission.getAttributionReference(), admission.getAttributionEvidenceRef(), diagnostic.getDiagnosticId(),
                admission.getInspectionTaskId(), admission.getFinalReviewId()),
                "managed admission diagnostic transition conflict");
        admission.setStatus("DIAGNOSTIC_PENDING_REVIEW")
                .setDiagnosticId(diagnostic.getDiagnosticId())
                .setVersion(admission.getVersion() + 1)
                .setUpdatedAt(now);
        appendHistory(tenantId, operationId, "AI_DIAGNOSTIC", diagnostic.getDiagnosticId(), diagnostic.getVersion(),
                null, diagnostic.getStatus(), diagnostic.getRecommendationCode(), command, now);
        appendAiDiagnosticEvent(tenantId, diagnostic, null, command);
        appendHistory(tenantId, operationId, "MANAGED_ADMISSION", admission.getAdmissionId(), admission.getVersion(),
                before, admission.getStatus(), diagnostic.getRecommendationCode(), command, now);
        appendManagedAdmissionEvent(tenantId, admission, before, command);
        return managedAdmissionResult(operationId, admission, evidencePackage, diagnostic, null, null);
    }

    private MerchantCommandResult reviewAiDiagnostic(Long tenantId, Long operationId, MerchantCommand command,
                                                     LocalDateTime now, boolean accepted) {
        MerchantManagedAdmissionDO admission = requireManagedAdmissionForUpdate(tenantId, command);
        require("DIAGNOSTIC_PENDING_REVIEW".equals(admission.getStatus()),
                (accepted ? "ACCEPT" : "REJECT") + "_AI_DIAGNOSTIC requires DIAGNOSTIC_PENDING_REVIEW admission");
        MerchantAiDiagnosticDO diagnostic = requireAiDiagnosticForAdmission(tenantId, admission, command);
        require("PENDING_REVIEW".equals(diagnostic.getStatus()), "AI diagnostic is not pending review");
        requireId(command.getActorPrincipalId(), "actorPrincipalId");
        String reviewNote = command.getReviewNote() == null ? null : command.getReviewNote().trim();
        require(mapper.transitionAiDiagnostic(tenantId, diagnostic.getDiagnosticId(), diagnostic.getVersion(),
                "PENDING_REVIEW", accepted ? "ACCEPTED" : "REJECTED", command.getActorPrincipalId().trim(), reviewNote,
                now) == 1, "AI diagnostic review transition conflict");
        String admissionAfter = accepted
                ? ("FACTORY_INSPECTION_REQUIRED".equals(diagnostic.getRecommendationCode()) ? "INSPECTION_PENDING"
                : "FINAL_REVIEW_PENDING")
                : "DIAGNOSTIC_REJECTED";
        String admissionBefore = admission.getStatus();
        require(transitionManagedAdmission(tenantId, admission, admissionBefore, admissionAfter, command, now,
                admission.getAttributionChannelCode(), admission.getAttributionSourceSystem(),
                admission.getAttributionSourceType(), admission.getAttributionSourceId(),
                admission.getAttributionReference(), admission.getAttributionEvidenceRef(), diagnostic.getDiagnosticId(),
                admission.getInspectionTaskId(), admission.getFinalReviewId()),
                "managed admission diagnostic review transition conflict");
        diagnostic.setStatus(accepted ? "ACCEPTED" : "REJECTED")
                .setReviewerPrincipalId(command.getActorPrincipalId().trim())
                .setReviewNote(reviewNote)
                .setVersion(diagnostic.getVersion() + 1)
                .setUpdatedAt(now);
        admission.setStatus(admissionAfter).setVersion(admission.getVersion() + 1).setUpdatedAt(now);
        appendHistory(tenantId, operationId, "AI_DIAGNOSTIC", diagnostic.getDiagnosticId(), diagnostic.getVersion(),
                "PENDING_REVIEW", diagnostic.getStatus(), reviewNote, command, now);
        appendAiDiagnosticEvent(tenantId, diagnostic, "PENDING_REVIEW", command);
        appendHistory(tenantId, operationId, "MANAGED_ADMISSION", admission.getAdmissionId(), admission.getVersion(),
                admissionBefore, admission.getStatus(), diagnostic.getRecommendationCode(), command, now);
        appendManagedAdmissionEvent(tenantId, admission, admissionBefore, command);
        return managedAdmissionResult(operationId, admission,
                mapper.selectLatestManagedEvidencePackage(tenantId, admission.getAdmissionId()), diagnostic, null, null);
    }

    private MerchantCommandResult createFactoryInspectionTask(Long tenantId, Long operationId, MerchantCommand command,
                                                              LocalDateTime now) {
        requireExpectedVersion(command);
        MerchantManagedAdmissionDO admission = requireManagedAdmissionForUpdate(tenantId, command);
        require(Objects.equals(admission.getVersion(), command.getExpectedVersion()),
                "managed admission version conflict");
        require("INSPECTION_PENDING".equals(admission.getStatus()),
                "factory inspection task requires INSPECTION_PENDING admission");
        MerchantAiDiagnosticDO diagnostic = requireAiDiagnosticForAdmission(tenantId, admission, command);
        require("ACCEPTED".equals(diagnostic.getStatus()), "factory inspection task requires accepted AI diagnostic");
        require("FACTORY_INSPECTION_REQUIRED".equals(diagnostic.getRecommendationCode()),
                "factory inspection task requires FACTORY_INSPECTION_REQUIRED recommendation");
        MerchantFactoryInspectionTaskDO existing = admission.getInspectionTaskId() == null ? null
                : mapper.selectFactoryInspectionTaskForUpdate(tenantId, admission.getInspectionTaskId());
        require(existing == null, "factory inspection task already exists");
        MerchantFactoryInspectionTaskDO task = new MerchantFactoryInspectionTaskDO()
                .setInspectionTaskId(command.getInspectionTaskId() == null ? UUID.randomUUID().toString()
                        : command.getInspectionTaskId().trim())
                .setTenantId(tenantId)
                .setAdmissionId(admission.getAdmissionId())
                .setDiagnosticId(diagnostic.getDiagnosticId())
                .setStatus("PENDING_CLAIM")
                .setEvidenceRef(null)
                .setNote(command.getReviewNote() == null ? null : command.getReviewNote().trim())
                .setVersion(1L)
                .setCreatedAt(now)
                .setUpdatedAt(now);
        require(mapper.insertFactoryInspectionTask(task) == 1, "failed to create factory inspection task");
        String before = admission.getStatus();
        require(transitionManagedAdmission(tenantId, admission, before, "INSPECTION_IN_PROGRESS", command, now,
                admission.getAttributionChannelCode(), admission.getAttributionSourceSystem(),
                admission.getAttributionSourceType(), admission.getAttributionSourceId(),
                admission.getAttributionReference(), admission.getAttributionEvidenceRef(), diagnostic.getDiagnosticId(),
                task.getInspectionTaskId(), admission.getFinalReviewId()),
                "managed admission inspection transition conflict");
        admission.setStatus("INSPECTION_IN_PROGRESS")
                .setInspectionTaskId(task.getInspectionTaskId())
                .setVersion(admission.getVersion() + 1)
                .setUpdatedAt(now);
        appendHistory(tenantId, operationId, "FACTORY_INSPECTION", task.getInspectionTaskId(), task.getVersion(), null,
                task.getStatus(), task.getNote(), command, now);
        appendFactoryInspectionEvent(tenantId, task, null, command);
        appendHistory(tenantId, operationId, "MANAGED_ADMISSION", admission.getAdmissionId(), admission.getVersion(),
                before, admission.getStatus(), task.getInspectionTaskId(), command, now);
        appendManagedAdmissionEvent(tenantId, admission, before, command);
        return managedAdmissionResult(operationId, admission,
                mapper.selectLatestManagedEvidencePackage(tenantId, admission.getAdmissionId()), diagnostic, task, null);
    }

    private MerchantCommandResult transitionFactoryInspectionTask(Long tenantId, Long operationId, MerchantCommand command,
                                                                  LocalDateTime now, String before, String after,
                                                                  boolean requireScheduledAt) {
        MerchantManagedAdmissionDO admission = requireManagedAdmissionForUpdate(tenantId, command);
        require("INSPECTION_IN_PROGRESS".equals(admission.getStatus()),
                command.getOperation() + " requires INSPECTION_IN_PROGRESS admission");
        MerchantFactoryInspectionTaskDO task = requireInspectionTaskForAdmission(tenantId, admission, command);
        require(before.equals(task.getStatus()), command.getOperation() + " requires " + before + " inspection task");
        if (requireScheduledAt) {
            require(command.getScheduledAt() != null, "scheduledAt is required");
        }
        String note = command.getReviewNote() == null ? null : command.getReviewNote().trim();
        String actorPrincipalId = command.getActorPrincipalId() == null ? null : command.getActorPrincipalId().trim();
        String evidenceRef = command.getInspectionOutcomeEvidenceRef() == null ? task.getEvidenceRef()
                : command.getInspectionOutcomeEvidenceRef().trim();
        if ("PENDING_SCHEDULE".equals(after) || "PENDING_INSPECTION".equals(after)
                || "PENDING_QA_INSPECTION".equals(after) || "PENDING_REMEDIATION".equals(after)
                || "PENDING_FIRST_REVIEW".equals(after) || "PENDING_FINAL_REVIEW".equals(after)) {
            requireId(actorPrincipalId, "actorPrincipalId");
        }
        if (Set.of("PENDING_QA_INSPECTION", "PENDING_REMEDIATION", "PENDING_FIRST_REVIEW", "PENDING_FINAL_REVIEW")
                .contains(after)) {
            requireText(evidenceRef, "inspectionOutcomeEvidenceRef", 256);
            require(isSafeVerificationReference(evidenceRef),
                    "inspectionOutcomeEvidenceRef must be an opaque evidence reference");
        }
        LocalDateTime scheduledAt = command.getScheduledAt() == null ? task.getScheduledAt() : toUtc(command.getScheduledAt());
        require(mapper.transitionFactoryInspectionTask(tenantId, task.getInspectionTaskId(), task.getVersion(), before,
                after, actorPrincipalId, scheduledAt, evidenceRef, note, now) == 1,
                "factory inspection task transition conflict");
        task.setStatus(after).setActorPrincipalId(actorPrincipalId).setScheduledAt(scheduledAt)
                .setEvidenceRef(evidenceRef).setNote(note)
                .setVersion(task.getVersion() + 1).setUpdatedAt(now);
        appendHistory(tenantId, operationId, "FACTORY_INSPECTION", task.getInspectionTaskId(), task.getVersion(),
                before, task.getStatus(), note, command, now);
        appendFactoryInspectionEvent(tenantId, task, before, command);
        return managedAdmissionResult(operationId, admission,
                mapper.selectLatestManagedEvidencePackage(tenantId, admission.getAdmissionId()),
                mapper.selectLatestAiDiagnostic(tenantId, admission.getAdmissionId()), task,
                mapper.selectLatestManagedFinalReview(tenantId, admission.getAdmissionId()));
    }

    private MerchantCommandResult transitionFactoryInspectionTaskToFirstReview(Long tenantId, Long operationId,
                                                                               MerchantCommand command,
                                                                               LocalDateTime now) {
        MerchantManagedAdmissionDO admission = requireManagedAdmissionForUpdate(tenantId, command);
        MerchantFactoryInspectionTaskDO task = requireInspectionTaskForAdmission(tenantId, admission, command);
        require(Set.of("PENDING_QA_INSPECTION", "PENDING_REMEDIATION").contains(task.getStatus()),
                "SUBMIT_FACTORY_FIRST_REVIEW requires PENDING_QA_INSPECTION or PENDING_REMEDIATION inspection task");
        return transitionFactoryInspectionTask(tenantId, operationId,
                command.setInspectionTaskId(task.getInspectionTaskId())
                        .setExpectedVersion(task.getVersion()), now, task.getStatus(), "PENDING_FIRST_REVIEW", false);
    }

    private MerchantCommandResult completeFactoryInspectionTask(Long tenantId, Long operationId, MerchantCommand command,
                                                                LocalDateTime now) {
        MerchantManagedAdmissionDO admission = requireManagedAdmissionForUpdate(tenantId, command);
        require("INSPECTION_IN_PROGRESS".equals(admission.getStatus()),
                "COMPLETE_FACTORY_INSPECTION_TASK requires INSPECTION_IN_PROGRESS admission");
        MerchantFactoryInspectionTaskDO task = requireInspectionTaskForAdmission(tenantId, admission, command);
        require("PENDING_FINAL_REVIEW".equals(task.getStatus()),
                "COMPLETE_FACTORY_INSPECTION_TASK requires PENDING_FINAL_REVIEW inspection task");
        String note = command.getReviewNote() == null ? null : command.getReviewNote().trim();
        String actorPrincipalId = command.getActorPrincipalId() == null ? null : command.getActorPrincipalId().trim();
        requireId(actorPrincipalId, "actorPrincipalId");
        String evidenceRef = command.getInspectionOutcomeEvidenceRef() == null ? task.getEvidenceRef()
                : command.getInspectionOutcomeEvidenceRef().trim();
        requireText(evidenceRef, "inspectionOutcomeEvidenceRef", 256);
        require(isSafeVerificationReference(evidenceRef),
                "inspectionOutcomeEvidenceRef must be an opaque evidence reference");
        require(mapper.transitionFactoryInspectionTask(tenantId, task.getInspectionTaskId(), task.getVersion(),
                "PENDING_FINAL_REVIEW", "COMPLETED", actorPrincipalId, task.getScheduledAt(), evidenceRef, note, now) == 1,
                "factory inspection task completion conflict");
        String admissionBefore = admission.getStatus();
        require(transitionManagedAdmission(tenantId, admission, admissionBefore, "FINAL_REVIEW_PENDING", command, now,
                admission.getAttributionChannelCode(), admission.getAttributionSourceSystem(),
                admission.getAttributionSourceType(), admission.getAttributionSourceId(),
                admission.getAttributionReference(), admission.getAttributionEvidenceRef(), admission.getDiagnosticId(),
                task.getInspectionTaskId(), admission.getFinalReviewId()),
                "managed admission completion transition conflict");
        task.setStatus("COMPLETED").setActorPrincipalId(actorPrincipalId).setEvidenceRef(evidenceRef).setNote(note)
                .setVersion(task.getVersion() + 1).setUpdatedAt(now);
        admission.setStatus("FINAL_REVIEW_PENDING").setVersion(admission.getVersion() + 1).setUpdatedAt(now);
        appendHistory(tenantId, operationId, "FACTORY_INSPECTION", task.getInspectionTaskId(), task.getVersion(),
                "PENDING_FINAL_REVIEW", task.getStatus(), note, command, now);
        appendFactoryInspectionEvent(tenantId, task, "PENDING_FINAL_REVIEW", command);
        appendHistory(tenantId, operationId, "MANAGED_ADMISSION", admission.getAdmissionId(), admission.getVersion(),
                admissionBefore, admission.getStatus(), task.getInspectionTaskId(), command, now);
        appendManagedAdmissionEvent(tenantId, admission, admissionBefore, command);
        return managedAdmissionResult(operationId, admission,
                mapper.selectLatestManagedEvidencePackage(tenantId, admission.getAdmissionId()),
                mapper.selectLatestAiDiagnostic(tenantId, admission.getAdmissionId()), task, null);
    }

    private MerchantCommandResult cancelFactoryInspectionTask(Long tenantId, Long operationId, MerchantCommand command,
                                                              LocalDateTime now) {
        MerchantManagedAdmissionDO admission = requireManagedAdmissionForUpdate(tenantId, command);
        require("INSPECTION_IN_PROGRESS".equals(admission.getStatus()),
                "CANCEL_FACTORY_INSPECTION_TASK requires INSPECTION_IN_PROGRESS admission");
        MerchantFactoryInspectionTaskDO task = requireInspectionTaskForAdmission(tenantId, admission, command);
        require(!Set.of("COMPLETED", "CANCELLED").contains(task.getStatus()),
                "CANCEL_FACTORY_INSPECTION_TASK requires nonterminal inspection task");
        String note = command.getReviewNote() == null ? command.getReason() : command.getReviewNote();
        requireText(note, "reviewNote", 512);
        String actorPrincipalId = command.getActorPrincipalId() == null ? null : command.getActorPrincipalId().trim();
        requireId(actorPrincipalId, "actorPrincipalId");
        String evidenceRef = command.getInspectionOutcomeEvidenceRef() == null ? task.getEvidenceRef()
                : command.getInspectionOutcomeEvidenceRef().trim();
        String taskBefore = task.getStatus();
        require(mapper.transitionFactoryInspectionTask(tenantId, task.getInspectionTaskId(), task.getVersion(),
                taskBefore, "CANCELLED", actorPrincipalId, task.getScheduledAt(), evidenceRef, note.trim(), now) == 1,
                "factory inspection task cancel conflict");
        String admissionBefore = admission.getStatus();
        require(transitionManagedAdmission(tenantId, admission, admissionBefore, "INSPECTION_CANCELLED", command, now,
                admission.getAttributionChannelCode(), admission.getAttributionSourceSystem(),
                admission.getAttributionSourceType(), admission.getAttributionSourceId(),
                admission.getAttributionReference(), admission.getAttributionEvidenceRef(), admission.getDiagnosticId(),
                task.getInspectionTaskId(), admission.getFinalReviewId()),
                "managed admission cancel transition conflict");
        task.setStatus("CANCELLED").setActorPrincipalId(actorPrincipalId).setEvidenceRef(evidenceRef).setNote(note.trim())
                .setVersion(task.getVersion() + 1).setUpdatedAt(now);
        admission.setStatus("INSPECTION_CANCELLED").setVersion(admission.getVersion() + 1).setUpdatedAt(now);
        appendHistory(tenantId, operationId, "FACTORY_INSPECTION", task.getInspectionTaskId(), task.getVersion(),
                taskBefore, "CANCELLED", note.trim(), command, now);
        appendFactoryInspectionEvent(tenantId, task, taskBefore, command);
        appendHistory(tenantId, operationId, "MANAGED_ADMISSION", admission.getAdmissionId(), admission.getVersion(),
                admissionBefore, admission.getStatus(), note.trim(), command, now);
        appendManagedAdmissionEvent(tenantId, admission, admissionBefore, command);
        return managedAdmissionResult(operationId, admission,
                mapper.selectLatestManagedEvidencePackage(tenantId, admission.getAdmissionId()),
                mapper.selectLatestAiDiagnostic(tenantId, admission.getAdmissionId()), task, null);
    }

    private MerchantCommandResult recordManagedFinalReview(Long tenantId, Long operationId, MerchantCommand command,
                                                           LocalDateTime now) {
        requireExpectedVersion(command);
        MerchantManagedAdmissionDO admission = requireManagedAdmissionForUpdate(tenantId, command);
        require(Objects.equals(admission.getVersion(), command.getExpectedVersion()),
                "managed admission version conflict");
        require("FINAL_REVIEW_PENDING".equals(admission.getStatus()),
                "managed final review requires FINAL_REVIEW_PENDING admission");
        MerchantManagedFinalReviewDO existing = admission.getFinalReviewId() == null ? null
                : mapper.selectManagedFinalReviewForUpdate(tenantId, admission.getFinalReviewId());
        require(existing == null, "managed final review already exists");
        String decision = normalizeUpper(command.getReviewDecision(), "reviewDecision", 32);
        require(FINAL_REVIEW_DECISIONS.contains(decision), "reviewDecision must be APPROVED or REJECTED");
        requireId(command.getActorPrincipalId(), "actorPrincipalId");
        requireText(command.getFinalReviewEvidenceRef(), "finalReviewEvidenceRef", 256);
        require(isSafeVerificationReference(command.getFinalReviewEvidenceRef()),
                "finalReviewEvidenceRef must be an opaque evidence reference");
        String note = command.getReviewNote() == null ? null : command.getReviewNote().trim();
        MerchantManagedFinalReviewDO review = new MerchantManagedFinalReviewDO()
                .setFinalReviewId(UUID.randomUUID().toString())
                .setTenantId(tenantId)
                .setAdmissionId(admission.getAdmissionId())
                .setInspectionTaskId(admission.getInspectionTaskId())
                .setDecision(decision)
                .setEvidenceRef(command.getFinalReviewEvidenceRef().trim())
                .setReviewerPrincipalId(command.getActorPrincipalId().trim())
                .setReviewNote(note)
                .setStatus(decision)
                .setVersion(1L)
                .setCreatedAt(now)
                .setUpdatedAt(now);
        require(mapper.insertManagedFinalReview(review) == 1, "failed to create managed final review");
        String admissionAfter = "APPROVED".equals(decision) ? "FINAL_APPROVED" : "FINAL_REJECTED";
        String admissionBefore = admission.getStatus();
        require(transitionManagedAdmission(tenantId, admission, admissionBefore, admissionAfter, command, now,
                admission.getAttributionChannelCode(), admission.getAttributionSourceSystem(),
                admission.getAttributionSourceType(), admission.getAttributionSourceId(),
                admission.getAttributionReference(), admission.getAttributionEvidenceRef(), admission.getDiagnosticId(),
                admission.getInspectionTaskId(), review.getFinalReviewId()),
                "managed admission final review transition conflict");
        admission.setStatus(admissionAfter).setFinalReviewId(review.getFinalReviewId())
                .setVersion(admission.getVersion() + 1).setUpdatedAt(now);
        appendHistory(tenantId, operationId, "FINAL_REVIEW", review.getFinalReviewId(), review.getVersion(), null,
                review.getStatus(), note, command, now);
        appendFinalReviewEvent(tenantId, review, command);
        appendHistory(tenantId, operationId, "MANAGED_ADMISSION", admission.getAdmissionId(), admission.getVersion(),
                admissionBefore, admission.getStatus(), decision, command, now);
        appendManagedAdmissionEvent(tenantId, admission, admissionBefore, command);
        return managedAdmissionResult(operationId, admission,
                mapper.selectLatestManagedEvidencePackage(tenantId, admission.getAdmissionId()),
                mapper.selectLatestAiDiagnostic(tenantId, admission.getAdmissionId()),
                mapper.selectLatestFactoryInspectionTask(tenantId, admission.getAdmissionId()), review);
    }

    private MerchantCommandResult issueManagedInvitation(Long tenantId, Long operationId, MerchantCommand command,
                                                         LocalDateTime now) {
        requireId(command.getRecruiterPrincipalId(), "recruiterPrincipalId");
        principalValidationApi.requireActivePrincipal(command.getRecruiterPrincipalId().trim());
        requireText(command.getInvitationEvidenceRef(), "invitationEvidenceRef", 256);
        require(isSafeVerificationReference(command.getInvitationEvidenceRef()),
                "invitationEvidenceRef must be an opaque evidence reference");
        SourceReference source = normalizeSourceReference(command.getSourceReference());
        String code = command.getInvitationCode() == null || command.getInvitationCode().isBlank()
                ? "INV-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT)
                : command.getInvitationCode().trim().toUpperCase(Locale.ROOT);
        require(code.length() <= 64, "invitationCode exceeds 64 characters");
        require(mapper.selectManagedInvitationByCodeForUpdate(tenantId, code) == null,
                "managed invitation code already exists");
        MerchantManagedInvitationDO invitation = new MerchantManagedInvitationDO()
                .setInvitationId(command.getInvitationId() == null ? UUID.randomUUID().toString()
                        : command.getInvitationId().trim())
                .setTenantId(tenantId)
                .setInvitationCode(code)
                .setRecruiterPrincipalId(command.getRecruiterPrincipalId().trim())
                .setAttributionSourceSystem(source.getSourceSystem())
                .setAttributionSourceType(source.getSourceType())
                .setAttributionSourceId(source.getSourceId())
                .setAttributionReference(command.getAttributionReference() == null ? code
                        : command.getAttributionReference().trim())
                .setEvidenceRef(command.getInvitationEvidenceRef().trim())
                .setStatus("ISSUED")
                .setVersion(1L)
                .setCreatedAt(now)
                .setUpdatedAt(now);
        require(mapper.insertManagedInvitation(invitation) == 1, "failed to issue managed invitation");
        appendHistory(tenantId, operationId, "MANAGED_INVITATION", invitation.getInvitationId(), invitation.getVersion(),
                null, invitation.getStatus(), invitation.getInvitationCode(), command, now);
        appendInvitationEvent(tenantId, invitation, null, command);
        return invitationResult(operationId, invitation, null);
    }

    private MerchantCommandResult markManagedInvitationUsed(Long tenantId, Long operationId, MerchantCommand command,
                                                            LocalDateTime now) {
        requireText(command.getInvitationCode(), "invitationCode", 64);
        requireExpectedVersion(command);
        MerchantManagedInvitationDO invitation = mapper.selectManagedInvitationByCodeForUpdate(tenantId,
                command.getInvitationCode().trim().toUpperCase(Locale.ROOT));
        require(invitation != null, "managed invitation does not exist");
        require(Objects.equals(invitation.getVersion(), command.getExpectedVersion()),
                "managed invitation version conflict");
        require("ISSUED".equals(invitation.getStatus()), "managed invitation must be ISSUED");
        MerchantManagedAdmissionDO admission = requireManagedAdmissionForUpdate(tenantId, command);
        require(Set.of("ATTRIBUTION_PENDING", "EVIDENCE_PENDING").contains(admission.getStatus()),
                "managed invitation can only be used before evidence submission");
        require(mapper.transitionManagedInvitation(tenantId, invitation.getInvitationId(), invitation.getVersion(),
                "ISSUED", "USED", admission.getAdmissionId(), now, now) == 1,
                "managed invitation use transition conflict");
        invitation.setUsedAdmissionId(admission.getAdmissionId()).setUsedAt(now).setStatus("USED")
                .setVersion(invitation.getVersion() + 1).setUpdatedAt(now);
        appendHistory(tenantId, operationId, "MANAGED_INVITATION", invitation.getInvitationId(), invitation.getVersion(),
                "ISSUED", "USED", admission.getAdmissionId(), command, now);
        appendInvitationEvent(tenantId, invitation, "ISSUED", command);
        String admissionBefore = admission.getStatus();
        if ("ATTRIBUTION_PENDING".equals(admissionBefore)) {
            require(transitionManagedAdmission(tenantId, admission, admissionBefore, "EVIDENCE_PENDING", command, now,
                    invitation.getInvitationCode(), invitation.getAttributionSourceSystem(),
                    invitation.getAttributionSourceType(), invitation.getAttributionSourceId(),
                    invitation.getAttributionReference(), invitation.getEvidenceRef(), admission.getDiagnosticId(),
                    admission.getInspectionTaskId(), admission.getFinalReviewId()),
                    "managed admission invitation attribution conflict");
            admission.setStatus("EVIDENCE_PENDING")
                    .setAttributionChannelCode(invitation.getInvitationCode())
                    .setAttributionSourceSystem(invitation.getAttributionSourceSystem())
                    .setAttributionSourceType(invitation.getAttributionSourceType())
                    .setAttributionSourceId(invitation.getAttributionSourceId())
                    .setAttributionReference(invitation.getAttributionReference())
                    .setAttributionEvidenceRef(invitation.getEvidenceRef())
                    .setVersion(admission.getVersion() + 1)
                    .setUpdatedAt(now);
            appendHistory(tenantId, operationId, "MANAGED_ADMISSION", admission.getAdmissionId(), admission.getVersion(),
                    admissionBefore, admission.getStatus(), invitation.getInvitationCode(), command, now);
            appendManagedAdmissionEvent(tenantId, admission, admissionBefore, command);
        }
        return invitationResult(operationId, invitation, admission);
    }

    private MerchantCommandResult assignFactoryInspectionTeam(Long tenantId, Long operationId, MerchantCommand command,
                                                              LocalDateTime now) {
        MerchantManagedAdmissionDO admission = requireManagedAdmissionForUpdate(tenantId, command);
        require(Set.of("INSPECTION_IN_PROGRESS", "FINAL_REVIEW_PENDING", "FINAL_APPROVED").contains(admission.getStatus()),
                "buyer assignment requires qualifying managed admission");
        MerchantFactoryInspectionTaskDO task = requireInspectionTaskForAdmission(tenantId, admission, command);
        require(!"CANCELLED".equals(task.getStatus()), "cannot assign buyer team to cancelled inspection task");
        requireId(command.getBuyerTlPrincipalId(), "buyerTlPrincipalId");
        requireId(command.getBuyerPrincipalId(), "buyerPrincipalId");
        requireText(command.getAssignmentEvidenceRef(), "assignmentEvidenceRef", 256);
        require(isSafeVerificationReference(command.getAssignmentEvidenceRef()),
                "assignmentEvidenceRef must be an opaque evidence reference");
        principalValidationApi.requireActivePrincipal(command.getBuyerTlPrincipalId().trim());
        principalValidationApi.requireActivePrincipal(command.getBuyerPrincipalId().trim());
        MerchantBuyerAssignmentDO existing = mapper.selectLatestBuyerAssignment(tenantId, admission.getAdmissionId());
        require(existing == null || !"ACTIVE".equals(existing.getStatus()), "active buyer assignment already exists");
        MerchantBuyerAssignmentDO assignment = new MerchantBuyerAssignmentDO()
                .setBuyerAssignmentId(command.getBuyerAssignmentId() == null ? UUID.randomUUID().toString()
                        : command.getBuyerAssignmentId().trim())
                .setTenantId(tenantId)
                .setAdmissionId(admission.getAdmissionId())
                .setInspectionTaskId(task.getInspectionTaskId())
                .setMerchantId(admission.getMerchantId())
                .setShopId(admission.getShopId())
                .setBuyerTlPrincipalId(command.getBuyerTlPrincipalId().trim())
                .setBuyerPrincipalId(command.getBuyerPrincipalId().trim())
                .setEvidenceRef(command.getAssignmentEvidenceRef().trim())
                .setStatus("ACTIVE")
                .setVersion(1L)
                .setCreatedAt(now)
                .setUpdatedAt(now);
        require(mapper.insertBuyerAssignment(assignment) == 1, "failed to record buyer assignment");
        appendHistory(tenantId, operationId, "BUYER_ASSIGNMENT", assignment.getBuyerAssignmentId(),
                assignment.getVersion(), null, assignment.getStatus(), assignment.getBuyerPrincipalId(), command, now);
        appendBuyerAssignmentEvent(tenantId, assignment, null, command);
        return buyerAssignmentResult(operationId, assignment, admission, task);
    }

    private MerchantCommandResult recordGradeBenefitDecision(Long tenantId, Long operationId, MerchantCommand command,
                                                             LocalDateTime now) {
        requireId(command.getMerchantId(), "merchantId");
        requireNoApprovedExitDecision(tenantId, command.getMerchantId().trim());
        requireText(command.getGradeCode(), "gradeCode", 32);
        String decisionStatus = normalizeUpper(command.getGradeDecisionStatus(), "gradeDecisionStatus", 32);
        require(GRADE_DECISION_STATUSES.contains(decisionStatus),
                "gradeDecisionStatus must be APPROVED or REJECTED");
        String transition = normalizeUpper(command.getGradeTransitionDecision(), "gradeTransitionDecision", 32);
        require(GRADE_TRANSITIONS.contains(transition), "unsupported gradeTransitionDecision");
        requireText(command.getThresholdsConfigRef(), "thresholdsConfigRef", 256);
        requireText(command.getBenefitDecisionEvidenceRef(), "benefitDecisionEvidenceRef", 256);
        require(isSafeVerificationReference(command.getBenefitDecisionEvidenceRef()),
                "benefitDecisionEvidenceRef must be an opaque evidence reference");
        List<MerchantBenefitEntitlementItem> entitlements = normalizeBenefitEntitlements(command.getBenefitEntitlements());
        MerchantGradeDecisionDO decision = new MerchantGradeDecisionDO()
                .setGradeDecisionId(command.getGradeDecisionId() == null ? UUID.randomUUID().toString()
                        : command.getGradeDecisionId().trim())
                .setTenantId(tenantId)
                .setMerchantId(command.getMerchantId().trim())
                .setShopId(blankToNull(command.getShopId()))
                .setProbationAssessmentId(blankToNull(command.getProbationAssessmentId()))
                .setScorecardId(blankToNull(command.getScorecardId()))
                .setGradeCode(command.getGradeCode().trim().toUpperCase(Locale.ROOT))
                .setTransitionDecision(transition)
                .setDecisionStatus(decisionStatus)
                .setThresholdsConfigRef(command.getThresholdsConfigRef().trim())
                .setEvidenceRef(command.getBenefitDecisionEvidenceRef().trim())
                .setEntitlementsJson(JsonUtils.toJsonString(entitlements))
                .setVersion(1L)
                .setCreatedAt(now)
                .setUpdatedAt(now);
        require(mapper.insertGradeDecision(decision) == 1, "failed to record grade decision");
        appendHistory(tenantId, operationId, "GRADE_DECISION", decision.getGradeDecisionId(), decision.getVersion(),
                null, decision.getDecisionStatus(), decision.getGradeCode(), command, now);
        appendGradeDecisionEvent(tenantId, decision, entitlements, command);
        return gradeDecisionResult(operationId, decision);
    }

    private MerchantCommandResult recordProbationAssessment(Long tenantId, Long operationId, MerchantCommand command,
                                                            LocalDateTime now) {
        String merchantId = resolveMerchantIdForAssessment(tenantId, command);
        String shopId = resolveShopIdForAssessment(tenantId, command);
        requireText(command.getProbationAssessmentStatus(), "probationAssessmentStatus", 32);
        String assessmentStatus = normalizeUpper(command.getProbationAssessmentStatus(), "probationAssessmentStatus", 32);
        require(PROBATION_STATUSES.contains(assessmentStatus),
                "unsupported probationAssessmentStatus");
        requireText(command.getThresholdsConfigRef(), "thresholdsConfigRef", 256);
        requireText(command.getAssessmentEvidenceRef(), "assessmentEvidenceRef", 256);
        require(isSafeVerificationReference(command.getAssessmentEvidenceRef()),
                "assessmentEvidenceRef must be an opaque evidence reference");
        List<MerchantProbationGateItem> gates = normalizeProbationGates(command.getProbationGates());
        MerchantProbationAssessmentDO assessment = new MerchantProbationAssessmentDO()
                .setProbationAssessmentId(command.getProbationAssessmentId() == null ? UUID.randomUUID().toString()
                        : command.getProbationAssessmentId().trim())
                .setTenantId(tenantId)
                .setAdmissionId(blankToNull(command.getAdmissionId()))
                .setMerchantId(merchantId)
                .setShopId(shopId)
                .setAssessmentStatus(assessmentStatus)
                .setThresholdsConfigRef(command.getThresholdsConfigRef().trim())
                .setGatesJson(JsonUtils.toJsonString(gates))
                .setGateCount(gates.size())
                .setEvidenceRef(command.getAssessmentEvidenceRef().trim())
                .setVersion(1L)
                .setCreatedAt(now)
                .setUpdatedAt(now);
        require(mapper.insertProbationAssessment(assessment) == 1, "failed to record probation assessment");
        appendHistory(tenantId, operationId, "PROBATION_ASSESSMENT", assessment.getProbationAssessmentId(),
                assessment.getVersion(), null, assessment.getAssessmentStatus(), assessment.getThresholdsConfigRef(),
                command, now);
        appendProbationAssessmentEvent(tenantId, assessment, gates, command);
        return probationAssessmentResult(operationId, assessment, gates);
    }

    private MerchantCommandResult recordMonthlyScorecard(Long tenantId, Long operationId, MerchantCommand command,
                                                         LocalDateTime now) {
        requireId(command.getMerchantId(), "merchantId");
        requireText(command.getScorecardMonth(), "scorecardMonth", 7);
        require(command.getScorecardMonth().matches("\\d{4}-\\d{2}"),
                "scorecardMonth must be YYYY-MM");
        require(mapper.selectMonthlyScorecardByMonth(tenantId, command.getMerchantId().trim(),
                command.getScorecardMonth().trim()) == null, "scorecard month already exists");
        String status = normalizeUpper(command.getScorecardStatus(), "scorecardStatus", 32);
        require(SCORECARD_STATUSES.contains(status), "unsupported scorecardStatus");
        String transition = normalizeUpper(command.getGradeTransitionDecision(), "gradeTransitionDecision", 32);
        require(SCORECARD_TRANSITIONS.contains(transition), "unsupported gradeTransitionDecision");
        requireText(command.getThresholdsConfigRef(), "thresholdsConfigRef", 256);
        requireText(command.getScorecardEvidenceRef(), "scorecardEvidenceRef", 256);
        require(isSafeVerificationReference(command.getScorecardEvidenceRef()),
                "scorecardEvidenceRef must be an opaque evidence reference");
        List<MerchantScorecardItem> items = normalizeScorecardItems(command.getScorecardItems());
        int redLineCount = (int) items.stream().filter(item -> "RED_LINE".equals(item.getAssessmentStatus())).count();
        int remediationFailureCount = (int) items.stream()
                .filter(item -> "UNDER_REMEDIATION".equals(item.getAssessmentStatus())).count();
        MerchantMonthlyScorecardDO scorecard = new MerchantMonthlyScorecardDO()
                .setScorecardId(command.getScorecardId() == null ? UUID.randomUUID().toString()
                        : command.getScorecardId().trim())
                .setTenantId(tenantId)
                .setMerchantId(command.getMerchantId().trim())
                .setShopId(blankToNull(command.getShopId()))
                .setScorecardMonth(command.getScorecardMonth().trim())
                .setScorecardStatus(status)
                .setTransitionRecommendation(transition)
                .setThresholdsConfigRef(command.getThresholdsConfigRef().trim())
                .setItemsJson(JsonUtils.toJsonString(items))
                .setItemCount(items.size())
                .setRedLineCount(redLineCount)
                .setRemediationFailureCount(remediationFailureCount)
                .setEvidenceRef(command.getScorecardEvidenceRef().trim())
                .setVersion(1L)
                .setCreatedAt(now)
                .setUpdatedAt(now);
        require(mapper.insertMonthlyScorecard(scorecard) == 1, "failed to record monthly scorecard");
        appendHistory(tenantId, operationId, "MONTHLY_SCORECARD", scorecard.getScorecardId(), scorecard.getVersion(),
                null, scorecard.getScorecardStatus(), scorecard.getScorecardMonth(), command, now);
        appendMonthlyScorecardEvent(tenantId, scorecard, items, command);
        return monthlyScorecardResult(operationId, scorecard, items);
    }

    private MerchantCommandResult recordExitDecision(Long tenantId, Long operationId, MerchantCommand command,
                                                     LocalDateTime now) {
        requireId(command.getMerchantId(), "merchantId");
        String reasonType = normalizeUpper(command.getExitReasonType(), "exitReasonType", 64);
        require(EXIT_REASON_TYPES.contains(reasonType), "unsupported exitReasonType");
        String decisionStatus = normalizeUpper(command.getReviewDecision(), "reviewDecision", 32);
        require(FINAL_REVIEW_DECISIONS.contains(decisionStatus), "reviewDecision must be APPROVED or REJECTED");
        requireText(command.getExitEvidenceRef(), "exitEvidenceRef", 256);
        require(isSafeVerificationReference(command.getExitEvidenceRef()),
                "exitEvidenceRef must be an opaque evidence reference");
        MerchantExitDecisionDO decision = new MerchantExitDecisionDO()
                .setExitDecisionId(command.getExitDecisionId() == null ? UUID.randomUUID().toString()
                        : command.getExitDecisionId().trim())
                .setTenantId(tenantId)
                .setMerchantId(command.getMerchantId().trim())
                .setShopId(blankToNull(command.getShopId()))
                .setAdmissionId(blankToNull(command.getAdmissionId()))
                .setScorecardId(blankToNull(command.getScorecardId()))
                .setReasonType(reasonType)
                .setDecisionStatus(decisionStatus)
                .setEvidenceRef(command.getExitEvidenceRef().trim())
                .setNote(command.getReviewNote() == null ? null : command.getReviewNote().trim())
                .setVersion(1L)
                .setCreatedAt(now)
                .setUpdatedAt(now);
        require(mapper.insertExitDecision(decision) == 1, "failed to record exit decision");
        appendHistory(tenantId, operationId, "EXIT_DECISION", decision.getExitDecisionId(), decision.getVersion(),
                null, decision.getDecisionStatus(), decision.getReasonType(), command, now);
        appendExitDecisionEvent(tenantId, decision, command);
        return exitDecisionResult(operationId, decision);
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

    private static MerchantCommandResult managedAdmissionResult(Long operationId, MerchantManagedAdmissionDO admission,
                                                                MerchantManagedEvidencePackageDO evidencePackage,
                                                                MerchantAiDiagnosticDO diagnostic,
                                                                MerchantFactoryInspectionTaskDO task,
                                                                MerchantManagedFinalReviewDO review) {
        return new MerchantCommandResult().setOperationId(operationId)
                .setAdmissionId(admission.getAdmissionId())
                .setAdmissionStatus(admission.getStatus())
                .setAdmissionVersion(admission.getVersion())
                .setApplicationId(admission.getApplicationId())
                .setMerchantId(admission.getMerchantId())
                .setShopId(admission.getShopId())
                .setEvidencePackageId(evidencePackage == null ? null : evidencePackage.getEvidencePackageId())
                .setEvidencePackageStatus(evidencePackage == null ? null : evidencePackage.getStatus())
                .setEvidencePackageVersion(evidencePackage == null ? null : evidencePackage.getVersion())
                .setEvidencePackageRef(evidencePackage == null ? null : evidencePackage.getPackageRef())
                .setEvidenceItems(evidencePackage == null ? List.of()
                        : JsonUtils.parseArray(evidencePackage.getItemsJson(), MerchantManagedEvidenceItem.class))
                .setDiagnosticId(diagnostic == null ? admission.getDiagnosticId() : diagnostic.getDiagnosticId())
                .setDiagnosticStatus(diagnostic == null ? null : diagnostic.getStatus())
                .setDiagnosticVersion(diagnostic == null ? null : diagnostic.getVersion())
                .setRecommendationCode(diagnostic == null ? null : diagnostic.getRecommendationCode())
                .setRecommendationSummary(diagnostic == null ? null : diagnostic.getRecommendationSummary())
                .setInspectionTaskId(task == null ? admission.getInspectionTaskId() : task.getInspectionTaskId())
                .setInspectionTaskStatus(task == null ? null : task.getStatus())
                .setInspectionTaskVersion(task == null ? null : task.getVersion())
                .setInspectionOutcomeEvidenceRef(task == null ? null : task.getEvidenceRef())
                .setFinalReviewId(review == null ? admission.getFinalReviewId() : review.getFinalReviewId())
                .setFinalReviewStatus(review == null ? null : review.getStatus())
                .setFinalReviewVersion(review == null ? null : review.getVersion())
                .setReviewDecision(review == null ? null : review.getDecision());
    }

    private static MerchantCommandResult invitationResult(Long operationId, MerchantManagedInvitationDO invitation,
                                                          MerchantManagedAdmissionDO admission) {
        MerchantCommandResult result = new MerchantCommandResult().setOperationId(operationId)
                .setInvitationId(invitation.getInvitationId())
                .setInvitationCode(invitation.getInvitationCode())
                .setInvitationStatus(invitation.getStatus())
                .setInvitationVersion(invitation.getVersion())
                .setApplicationId(admission == null ? null : admission.getApplicationId())
                .setAdmissionId(admission == null ? invitation.getUsedAdmissionId() : admission.getAdmissionId())
                .setAdmissionStatus(admission == null ? null : admission.getStatus())
                .setAdmissionVersion(admission == null ? null : admission.getVersion())
                .setMerchantId(admission == null ? null : admission.getMerchantId())
                .setShopId(admission == null ? null : admission.getShopId());
        if (admission != null) {
            result.setEvidencePackageRef(admission.getAttributionEvidenceRef());
        }
        return result;
    }

    private static MerchantCommandResult buyerAssignmentResult(Long operationId, MerchantBuyerAssignmentDO assignment,
                                                               MerchantManagedAdmissionDO admission,
                                                               MerchantFactoryInspectionTaskDO task) {
        return new MerchantCommandResult().setOperationId(operationId)
                .setAdmissionId(admission.getAdmissionId()).setAdmissionStatus(admission.getStatus())
                .setAdmissionVersion(admission.getVersion()).setMerchantId(admission.getMerchantId())
                .setShopId(admission.getShopId()).setInspectionTaskId(task.getInspectionTaskId())
                .setInspectionTaskStatus(task.getStatus()).setInspectionTaskVersion(task.getVersion())
                .setBuyerAssignmentId(assignment.getBuyerAssignmentId())
                .setBuyerAssignmentStatus(assignment.getStatus())
                .setBuyerAssignmentVersion(assignment.getVersion())
                .setBuyerTlPrincipalId(assignment.getBuyerTlPrincipalId())
                .setBuyerPrincipalId(assignment.getBuyerPrincipalId());
    }

    private static MerchantCommandResult gradeDecisionResult(Long operationId, MerchantGradeDecisionDO decision) {
        return new MerchantCommandResult().setOperationId(operationId).setMerchantId(decision.getMerchantId())
                .setShopId(decision.getShopId()).setGradeDecisionId(decision.getGradeDecisionId())
                .setGradeCode(decision.getGradeCode()).setGradeTransitionDecision(decision.getTransitionDecision())
                .setGradeDecisionStatus(decision.getDecisionStatus()).setGradeDecisionVersion(decision.getVersion())
                .setBenefitEntitlements(JsonUtils.parseArray(decision.getEntitlementsJson(),
                        MerchantBenefitEntitlementItem.class));
    }

    private static MerchantCommandResult probationAssessmentResult(Long operationId,
                                                                   MerchantProbationAssessmentDO assessment,
                                                                   List<MerchantProbationGateItem> gates) {
        return new MerchantCommandResult().setOperationId(operationId).setAdmissionId(assessment.getAdmissionId())
                .setMerchantId(assessment.getMerchantId()).setShopId(assessment.getShopId())
                .setProbationAssessmentId(assessment.getProbationAssessmentId())
                .setProbationAssessmentStatus(assessment.getAssessmentStatus())
                .setProbationAssessmentVersion(assessment.getVersion()).setProbationGates(gates);
    }

    private static MerchantCommandResult monthlyScorecardResult(Long operationId, MerchantMonthlyScorecardDO scorecard,
                                                                List<MerchantScorecardItem> items) {
        return new MerchantCommandResult().setOperationId(operationId).setMerchantId(scorecard.getMerchantId())
                .setShopId(scorecard.getShopId()).setScorecardId(scorecard.getScorecardId())
                .setScorecardMonth(scorecard.getScorecardMonth()).setScorecardStatus(scorecard.getScorecardStatus())
                .setScorecardVersion(scorecard.getVersion()).setGradeTransitionDecision(scorecard.getTransitionRecommendation())
                .setScorecardItems(items);
    }

    private static MerchantCommandResult exitDecisionResult(Long operationId, MerchantExitDecisionDO decision) {
        return new MerchantCommandResult().setOperationId(operationId).setMerchantId(decision.getMerchantId())
                .setShopId(decision.getShopId()).setAdmissionId(decision.getAdmissionId())
                .setScorecardId(decision.getScorecardId()).setExitDecisionId(decision.getExitDecisionId())
                .setExitReasonType(decision.getReasonType()).setExitDecisionStatus(decision.getDecisionStatus())
                .setExitDecisionVersion(decision.getVersion());
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

    private void appendManagedAdmissionEvent(Long tenantId, MerchantManagedAdmissionDO admission, String previous,
                                             MerchantCommand command) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("admission_id", admission.getAdmissionId());
        payload.put("application_id", admission.getApplicationId());
        payload.put("merchant_id", admission.getMerchantId());
        payload.put("shop_id", admission.getShopId());
        payload.put("previous_status", previous);
        payload.put("current_status", admission.getStatus());
        payload.put("attribution_channel_code", admission.getAttributionChannelCode());
        payload.put("attribution_source_system", admission.getAttributionSourceSystem());
        payload.put("attribution_source_type", admission.getAttributionSourceType());
        payload.put("attribution_source_id", admission.getAttributionSourceId());
        payload.put("diagnostic_id", admission.getDiagnosticId());
        payload.put("inspection_task_id", admission.getInspectionTaskId());
        payload.put("final_review_id", admission.getFinalReviewId());
        appendEvent(tenantId, MANAGED_ADMISSION_EVENT, "MERCHANT_MANAGED_ADMISSION", admission.getAdmissionId(),
                admission.getVersion(), command, payload);
    }

    private void appendEvidencePackageEvent(Long tenantId, MerchantManagedEvidencePackageDO evidencePackage,
                                            String previous, List<MerchantManagedEvidenceItem> items,
                                            MerchantCommand command) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("evidence_package_id", evidencePackage.getEvidencePackageId());
        payload.put("admission_id", evidencePackage.getAdmissionId());
        payload.put("package_ref", evidencePackage.getPackageRef());
        payload.put("item_count", items.size());
        payload.put("items", items);
        payload.put("previous_status", previous);
        payload.put("current_status", evidencePackage.getStatus());
        appendEvent(tenantId, EVIDENCE_PACKAGE_EVENT, "MERCHANT_MANAGED_EVIDENCE_PACKAGE",
                evidencePackage.getEvidencePackageId(), evidencePackage.getVersion(), command, payload);
    }

    private void appendAiDiagnosticEvent(Long tenantId, MerchantAiDiagnosticDO diagnostic, String previous,
                                         MerchantCommand command) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("diagnostic_id", diagnostic.getDiagnosticId());
        payload.put("admission_id", diagnostic.getAdmissionId());
        payload.put("evidence_package_id", diagnostic.getEvidencePackageId());
        payload.put("recommendation_code", diagnostic.getRecommendationCode());
        payload.put("recommendation_summary", diagnostic.getRecommendationSummary());
        payload.put("previous_status", previous);
        payload.put("current_status", diagnostic.getStatus());
        payload.put("reviewer_principal_id", diagnostic.getReviewerPrincipalId());
        appendEvent(tenantId, AI_DIAGNOSTIC_EVENT, "MERCHANT_AI_DIAGNOSTIC", diagnostic.getDiagnosticId(),
                diagnostic.getVersion(), command, payload);
    }

    private void appendFactoryInspectionEvent(Long tenantId, MerchantFactoryInspectionTaskDO task, String previous,
                                              MerchantCommand command) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("inspection_task_id", task.getInspectionTaskId());
        payload.put("admission_id", task.getAdmissionId());
        payload.put("diagnostic_id", task.getDiagnosticId());
        payload.put("previous_status", previous);
        payload.put("current_status", task.getStatus());
        payload.put("actor_principal_id", task.getActorPrincipalId());
        payload.put("scheduled_at", task.getScheduledAt() == null ? null : toInstant(task.getScheduledAt()).toString());
        payload.put("evidence_ref", task.getEvidenceRef());
        appendEvent(tenantId, FACTORY_INSPECTION_EVENT, "MERCHANT_FACTORY_INSPECTION", task.getInspectionTaskId(),
                task.getVersion(), command, payload);
    }

    private void appendFinalReviewEvent(Long tenantId, MerchantManagedFinalReviewDO review, MerchantCommand command) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("final_review_id", review.getFinalReviewId());
        payload.put("admission_id", review.getAdmissionId());
        payload.put("inspection_task_id", review.getInspectionTaskId());
        payload.put("decision", review.getDecision());
        payload.put("status", review.getStatus());
        payload.put("reviewer_principal_id", review.getReviewerPrincipalId());
        appendEvent(tenantId, FINAL_REVIEW_EVENT, "MERCHANT_MANAGED_FINAL_REVIEW", review.getFinalReviewId(),
                review.getVersion(), command, payload);
    }

    private void appendInvitationEvent(Long tenantId, MerchantManagedInvitationDO invitation, String previous,
                                       MerchantCommand command) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("invitation_id", invitation.getInvitationId());
        payload.put("invitation_code", invitation.getInvitationCode());
        payload.put("recruiter_principal_id", invitation.getRecruiterPrincipalId());
        payload.put("source_system", invitation.getAttributionSourceSystem());
        payload.put("source_type", invitation.getAttributionSourceType());
        payload.put("source_id", invitation.getAttributionSourceId());
        payload.put("used_admission_id", invitation.getUsedAdmissionId());
        payload.put("previous_status", previous);
        payload.put("current_status", invitation.getStatus());
        appendEvent(tenantId, INVITATION_EVENT, "MERCHANT_MANAGED_INVITATION", invitation.getInvitationId(),
                invitation.getVersion(), command, payload);
    }

    private void appendBuyerAssignmentEvent(Long tenantId, MerchantBuyerAssignmentDO assignment, String previous,
                                            MerchantCommand command) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("buyer_assignment_id", assignment.getBuyerAssignmentId());
        payload.put("admission_id", assignment.getAdmissionId());
        payload.put("inspection_task_id", assignment.getInspectionTaskId());
        payload.put("merchant_id", assignment.getMerchantId());
        payload.put("shop_id", assignment.getShopId());
        payload.put("buyer_tl_principal_id", assignment.getBuyerTlPrincipalId());
        payload.put("buyer_principal_id", assignment.getBuyerPrincipalId());
        payload.put("previous_status", previous);
        payload.put("current_status", assignment.getStatus());
        appendEvent(tenantId, BUYER_ASSIGNMENT_EVENT, "MERCHANT_BUYER_ASSIGNMENT", assignment.getBuyerAssignmentId(),
                assignment.getVersion(), command, payload);
    }

    private void appendGradeDecisionEvent(Long tenantId, MerchantGradeDecisionDO decision,
                                          List<MerchantBenefitEntitlementItem> entitlements,
                                          MerchantCommand command) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("grade_decision_id", decision.getGradeDecisionId());
        payload.put("merchant_id", decision.getMerchantId());
        payload.put("shop_id", decision.getShopId());
        payload.put("grade_code", decision.getGradeCode());
        payload.put("transition_decision", decision.getTransitionDecision());
        payload.put("decision_status", decision.getDecisionStatus());
        payload.put("entitlements", entitlements);
        appendEvent(tenantId, GRADE_DECISION_EVENT, "MERCHANT_GRADE_DECISION", decision.getGradeDecisionId(),
                decision.getVersion(), command, payload);
    }

    private void appendProbationAssessmentEvent(Long tenantId, MerchantProbationAssessmentDO assessment,
                                                List<MerchantProbationGateItem> gates, MerchantCommand command) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("probation_assessment_id", assessment.getProbationAssessmentId());
        payload.put("admission_id", assessment.getAdmissionId());
        payload.put("merchant_id", assessment.getMerchantId());
        payload.put("assessment_status", assessment.getAssessmentStatus());
        payload.put("gate_count", assessment.getGateCount());
        payload.put("gates", gates);
        appendEvent(tenantId, PROBATION_ASSESSMENT_EVENT, "MERCHANT_PROBATION_ASSESSMENT",
                assessment.getProbationAssessmentId(), assessment.getVersion(), command, payload);
    }

    private void appendMonthlyScorecardEvent(Long tenantId, MerchantMonthlyScorecardDO scorecard,
                                             List<MerchantScorecardItem> items, MerchantCommand command) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("scorecard_id", scorecard.getScorecardId());
        payload.put("merchant_id", scorecard.getMerchantId());
        payload.put("shop_id", scorecard.getShopId());
        payload.put("scorecard_month", scorecard.getScorecardMonth());
        payload.put("scorecard_status", scorecard.getScorecardStatus());
        payload.put("transition_recommendation", scorecard.getTransitionRecommendation());
        payload.put("item_count", scorecard.getItemCount());
        payload.put("red_line_count", scorecard.getRedLineCount());
        payload.put("remediation_failure_count", scorecard.getRemediationFailureCount());
        payload.put("items", items);
        appendEvent(tenantId, MONTHLY_SCORECARD_EVENT, "MERCHANT_MONTHLY_SCORECARD", scorecard.getScorecardId(),
                scorecard.getVersion(), command, payload);
    }

    private void appendExitDecisionEvent(Long tenantId, MerchantExitDecisionDO decision, MerchantCommand command) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("exit_decision_id", decision.getExitDecisionId());
        payload.put("merchant_id", decision.getMerchantId());
        payload.put("shop_id", decision.getShopId());
        payload.put("admission_id", decision.getAdmissionId());
        payload.put("scorecard_id", decision.getScorecardId());
        payload.put("reason_type", decision.getReasonType());
        payload.put("decision_status", decision.getDecisionStatus());
        appendEvent(tenantId, EXIT_DECISION_EVENT, "MERCHANT_EXIT_DECISION", decision.getExitDecisionId(),
                decision.getVersion(), command, payload);
    }

    private MerchantManagedAdmissionDO requireManagedAdmissionForUpdate(Long tenantId, MerchantCommand command) {
        MerchantManagedAdmissionDO admission;
        if (command.getAdmissionId() != null && !command.getAdmissionId().isBlank()) {
            admission = mapper.selectManagedAdmissionForUpdate(tenantId, command.getAdmissionId().trim());
        } else {
            requireId(command.getApplicationId(), "applicationId");
            admission = mapper.selectManagedAdmissionByApplicationForUpdate(tenantId, command.getApplicationId().trim());
        }
        require(admission != null, "managed admission does not exist");
        return admission;
    }

    private MerchantAiDiagnosticDO requireAiDiagnosticForAdmission(Long tenantId, MerchantManagedAdmissionDO admission,
                                                                   MerchantCommand command) {
        boolean explicitDiagnosticId = command.getDiagnosticId() != null && !command.getDiagnosticId().isBlank();
        String diagnosticId = explicitDiagnosticId ? command.getDiagnosticId().trim() : admission.getDiagnosticId();
        requireId(diagnosticId, "diagnosticId");
        MerchantAiDiagnosticDO diagnostic = mapper.selectAiDiagnosticForUpdate(tenantId, diagnosticId);
        require(diagnostic != null && admission.getAdmissionId().equals(diagnostic.getAdmissionId()),
                "AI diagnostic does not belong to managed admission");
        // Admission transitions use expectedVersion for the admission aggregate. A caller that explicitly
        // addresses a diagnostic is also required to present that diagnostic's version.
        if (explicitDiagnosticId && command.getExpectedVersion() != null) {
            require(Objects.equals(diagnostic.getVersion(), command.getExpectedVersion()),
                    "AI diagnostic version conflict");
        }
        return diagnostic;
    }

    private MerchantFactoryInspectionTaskDO requireInspectionTaskForAdmission(Long tenantId,
                                                                              MerchantManagedAdmissionDO admission,
                                                                              MerchantCommand command) {
        String inspectionTaskId = command.getInspectionTaskId() != null && !command.getInspectionTaskId().isBlank()
                ? command.getInspectionTaskId().trim() : admission.getInspectionTaskId();
        requireId(inspectionTaskId, "inspectionTaskId");
        MerchantFactoryInspectionTaskDO task = mapper.selectFactoryInspectionTaskForUpdate(tenantId, inspectionTaskId);
        require(task != null && admission.getAdmissionId().equals(task.getAdmissionId()),
                "factory inspection task does not belong to managed admission");
        if (command.getExpectedVersion() != null) {
            require(Objects.equals(task.getVersion(), command.getExpectedVersion()),
                    "factory inspection task version conflict");
        }
        return task;
    }

    private boolean transitionManagedAdmission(Long tenantId, MerchantManagedAdmissionDO admission, String before,
                                               String after, MerchantCommand command, LocalDateTime now,
                                               String attributionChannelCode, String attributionSourceSystem,
                                               String attributionSourceType, String attributionSourceId,
                                               String attributionReference, String attributionEvidenceRef,
                                               String diagnosticId, String inspectionTaskId, String finalReviewId) {
        return mapper.transitionManagedAdmission(tenantId, admission.getAdmissionId(), admission.getVersion(), before,
                after, attributionChannelCode, attributionSourceSystem, attributionSourceType, attributionSourceId,
                attributionReference, attributionEvidenceRef, diagnosticId, inspectionTaskId, finalReviewId, now) == 1;
    }

    private void requireNoApprovedExitDecision(Long tenantId, String merchantId) {
        require(mapper.selectLatestApprovedExitDecision(tenantId, merchantId) == null,
                "approved exit decision blocks grade or benefit changes");
    }

    private String resolveMerchantIdForAssessment(Long tenantId, MerchantCommand command) {
        if (command.getMerchantId() != null && !command.getMerchantId().isBlank()) {
            return command.getMerchantId().trim();
        }
        MerchantManagedAdmissionDO admission = requireManagedAdmissionForUpdate(tenantId, command);
        return admission.getMerchantId();
    }

    private String resolveShopIdForAssessment(Long tenantId, MerchantCommand command) {
        if (command.getShopId() != null && !command.getShopId().isBlank()) {
            return command.getShopId().trim();
        }
        MerchantManagedAdmissionDO admission = requireManagedAdmissionForUpdate(tenantId, command);
        return admission.getShopId();
    }

    private static List<MerchantBenefitEntitlementItem> normalizeBenefitEntitlements(
            List<MerchantBenefitEntitlementItem> items) {
        if (items == null) {
            return List.of();
        }
        List<MerchantBenefitEntitlementItem> normalized = new ArrayList<>();
        for (MerchantBenefitEntitlementItem item : items) {
            require(item != null, "benefitEntitlements must not contain null");
            requireText(item.getBenefitCode(), "benefitEntitlements.benefitCode", 64);
            requireText(item.getEntitlementStatus(), "benefitEntitlements.entitlementStatus", 32);
            String status = normalizeUpper(item.getEntitlementStatus(), "benefitEntitlements.entitlementStatus", 32);
            require(BENEFIT_STATUSES.contains(status), "unsupported benefit entitlement status");
            String evidenceRef = item.getEvidenceRef() == null ? null : item.getEvidenceRef().trim();
            if (evidenceRef != null) {
                require(isSafeVerificationReference(evidenceRef),
                        "benefitEntitlements.evidenceRef must be an opaque evidence reference");
            }
            normalized.add(new MerchantBenefitEntitlementItem()
                    .setBenefitCode(item.getBenefitCode().trim().toUpperCase(Locale.ROOT))
                    .setEntitlementStatus(status)
                    .setEvidenceRef(evidenceRef)
                    .setNote(item.getNote() == null ? null : item.getNote().trim()));
        }
        return normalized;
    }

    private static List<MerchantProbationGateItem> normalizeProbationGates(List<MerchantProbationGateItem> items) {
        require(items != null && items.size() == 3, "probationGates must contain exactly 3 gates");
        Map<String, MerchantProbationGateItem> unique = new LinkedHashMap<>();
        for (MerchantProbationGateItem item : items) {
            require(item != null, "probationGates must not contain null");
            String code = normalizeUpper(item.getGateCode(), "probationGates.gateCode", 64);
            require(PROBATION_GATE_CODES.contains(code), "unsupported probation gate code");
            String status = normalizeUpper(item.getGateStatus(), "probationGates.gateStatus", 32);
            require(PROBATION_GATE_STATUSES.contains(status), "unsupported probation gate status");
            requireText(item.getThresholdConfigRef(), "probationGates.thresholdConfigRef", 256);
            requireText(item.getEvidenceRef(), "probationGates.evidenceRef", 256);
            require(isSafeVerificationReference(item.getEvidenceRef()),
                    "probationGates.evidenceRef must be an opaque evidence reference");
            require(!unique.containsKey(code), "duplicate probation gate code");
            unique.put(code, new MerchantProbationGateItem()
                    .setGateCode(code)
                    .setGateStatus(status)
                    .setThresholdConfigRef(item.getThresholdConfigRef().trim())
                    .setEvidenceRef(item.getEvidenceRef().trim())
                    .setNote(item.getNote() == null ? null : item.getNote().trim()));
        }
        require(unique.size() == 3, "probationGates must contain exactly 3 unique gates");
        return new ArrayList<>(unique.values());
    }

    private static List<MerchantScorecardItem> normalizeScorecardItems(List<MerchantScorecardItem> items) {
        require(items != null && items.size() == 9, "scorecardItems must contain exactly 9 obligations");
        Map<String, MerchantScorecardItem> unique = new LinkedHashMap<>();
        for (MerchantScorecardItem item : items) {
            require(item != null, "scorecardItems must not contain null");
            String code = normalizeUpper(item.getObligationCode(), "scorecardItems.obligationCode", 64);
            require(NINE_OBLIGATION_CODES.contains(code), "unsupported scorecard obligation code");
            String status = normalizeUpper(item.getAssessmentStatus(), "scorecardItems.assessmentStatus", 32);
            require(SCORECARD_ITEM_STATUSES.contains(status), "unsupported scorecard assessment status");
            requireText(item.getThresholdConfigRef(), "scorecardItems.thresholdConfigRef", 256);
            requireText(item.getEvidenceRef(), "scorecardItems.evidenceRef", 256);
            require(isSafeVerificationReference(item.getEvidenceRef()),
                    "scorecardItems.evidenceRef must be an opaque evidence reference");
            require(!unique.containsKey(code), "duplicate scorecard obligation code");
            unique.put(code, new MerchantScorecardItem()
                    .setObligationCode(code)
                    .setAssessmentStatus(status)
                    .setThresholdConfigRef(item.getThresholdConfigRef().trim())
                    .setEvidenceRef(item.getEvidenceRef().trim())
                    .setNote(item.getNote() == null ? null : item.getNote().trim()));
        }
        require(unique.size() == 9, "scorecardItems must contain exactly 9 unique obligations");
        return new ArrayList<>(unique.values());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static List<MerchantManagedEvidenceItem> normalizeEvidenceItems(List<MerchantManagedEvidenceItem> items) {
        require(items != null && !items.isEmpty(), "evidenceItems must not be empty");
        List<MerchantManagedEvidenceItem> normalized = new ArrayList<>();
        for (MerchantManagedEvidenceItem item : items) {
            require(item != null, "evidenceItems must not contain null");
            requireText(item.getItemCode(), "evidenceItems.itemCode", 64);
            requireText(item.getItemLabel(), "evidenceItems.itemLabel", 128);
            requireText(item.getEvidenceRef(), "evidenceItems.evidenceRef", 256);
            require(isSafeVerificationReference(item.getEvidenceRef()),
                    "evidenceItems.evidenceRef must be an opaque evidence reference");
            MerchantManagedEvidenceItem normalizedItem = new MerchantManagedEvidenceItem()
                    .setItemCode(item.getItemCode().trim().toUpperCase(Locale.ROOT))
                    .setItemLabel(item.getItemLabel().trim())
                    .setEvidenceRef(item.getEvidenceRef().trim())
                    .setNote(item.getNote() == null ? null : item.getNote().trim());
            if (item.getSourceSystem() != null || item.getSourceType() != null || item.getSourceId() != null) {
                normalizedItem.setSourceSystem(normalizeUpper(item.getSourceSystem(), "evidenceItems.sourceSystem", 32))
                        .setSourceType(normalizeUpper(item.getSourceType(), "evidenceItems.sourceType", 32));
                requireText(item.getSourceId(), "evidenceItems.sourceId", 128);
                normalizedItem.setSourceId(item.getSourceId().trim());
            }
            normalized.add(normalizedItem);
        }
        return normalized;
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
