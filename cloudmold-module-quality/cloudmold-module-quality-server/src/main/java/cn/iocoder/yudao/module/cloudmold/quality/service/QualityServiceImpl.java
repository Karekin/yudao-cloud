package cn.iocoder.yudao.module.cloudmold.quality.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.quality.api.*;
import cn.iocoder.yudao.module.cloudmold.quality.dal.dataobject.QualityRecords.*;
import cn.iocoder.yudao.module.cloudmold.quality.dal.mysql.QualityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class QualityServiceImpl implements QualityCommandApi {
    private static final int OPERATION_SUCCEEDED = 10;
    private static final String SOURCE_SYSTEM = "cloudmold-quality";
    private static final Pattern SAFE_REF = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/-]{0,255}");
    private static final Pattern SAFE_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern EVIDENCE_REF =
            Pattern.compile("(sha256:[0-9a-f]{64}|restricted:[A-Za-z0-9][A-Za-z0-9._:/-]{0,199})");
    private static final Set<String> CERTIFICATION_LEVELS = Set.of("JUNIOR", "SENIOR", "EXPERT");
    private static final Set<String> SUBJECT_TYPES =
            Set.of("INBOUND_ITEM", "RETURN_ITEM", "LISTING_SAMPLE", "RISK_SAMPLE");
    private static final Set<String> PRIORITIES = Set.of("LOW", "NORMAL", "HIGH", "URGENT");

    private final QualityMapper mapper;
    private final OutboxAppender outboxAppender;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QualityResult execute(QualityCommand command) {
        validateEnvelope(command);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = LocalDateTime.ofInstant(command.getOccurredAt(), ZoneOffset.UTC);
        String requestHash = DigestUtil.sha256Hex(JsonUtils.toJsonString(command));
        String attemptToken = UUID.randomUUID().toString();
        mapper.insertOrResolveOperation(tenantId, command.getIdempotencyKey(),
                command.getOperation().name(), requestHash, attemptToken, now);
        Long operationId = mapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve quality operation");
        Operation operation = mapper.selectOperationForUpdate(operationId, tenantId);
        require(operation != null, "quality operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(requestHash, operation.getRequestHash()),
                    "idempotency key conflicts with different payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing quality operation is incomplete");
            QualityResult replay = JsonUtils.parseObject(operation.getResultJson(), QualityResult.class);
            replay.setDuplicate(true);
            return replay;
        }

        Outcome outcome = switch (command.getOperation()) {
            case CREATE_STANDARD -> createStandard(tenantId, command, now);
            case PUBLISH_STANDARD -> publishStandard(tenantId, command, now);
            case CERTIFY_AUTHENTICATOR -> certifyAuthenticator(tenantId, command, now);
            case REVOKE_AUTHENTICATOR -> revokeAuthenticator(tenantId, command, now);
            case CREATE_INSPECTION_TASK -> createInspectionTask(tenantId, operationId, command, now);
            case ASSIGN_INSPECTION_TASK -> transitionTask(
                    tenantId, operationId, command, now, "CREATED", "ASSIGNED");
            case START_INSPECTION_TASK -> transitionTask(
                    tenantId, operationId, command, now, "ASSIGNED", "IN_PROGRESS");
            case DECIDE_INSPECTION_TASK -> transitionTask(
                    tenantId, operationId, command, now, "IN_PROGRESS", "DECIDED");
            case REQUEST_RECHECK, ASSIGN_RECHECK_REVIEWER -> assignRecheckReviewer(
                    tenantId, operationId, command, now);
            case SUBMIT_RECHECK_DECISION -> submitRecheckDecision(
                    tenantId, operationId, command, now);
            case ADJUDICATE_INSPECTION_TASK -> adjudicateInspectionTask(
                    tenantId, operationId, command, now);
            case COMPLETE_INSPECTION_TASK -> completeInspectionTask(
                    tenantId, operationId, command, now);
            case OPEN_CAPA -> openCapa(tenantId, command, now);
            case RESOLVE_CAPA -> resolveCapa(tenantId, command, now);
            case OPEN_RECALL_ACTION -> openRecallAction(tenantId, command, now);
            case ACKNOWLEDGE_RECALL_ACTION -> acknowledgeRecallAction(tenantId, command, now);
            case RESOLVE_RECALL_ACTION -> resolveRecallAction(tenantId, command, now);
        };
        appendEvent(tenantId, command, outcome);
        QualityResult result = QualityResult.builder()
                .operationId(operationId).duplicate(false)
                .aggregateType(outcome.aggregateType()).aggregateId(outcome.aggregateId())
                .aggregateVersion(outcome.version()).status(outcome.status()).build();
        require(mapper.markOperationSucceeded(operationId, tenantId, outcome.aggregateType(),
                        outcome.aggregateId(), JsonUtils.toJsonString(result), now) == 1,
                "quality operation completion conflict");
        return result;
    }

    private Outcome createStandard(Long tenantId, QualityCommand command, LocalDateTime now) {
        QualityCommand.StandardDefinition input = nonNull(command.getStandard(), "standard is required");
        String id = valueOrUuid(input.getStandardId());
        requireRef(input.getStandardCode(), "standardCode", 64);
        requireCode(input.getCategoryCode(), "categoryCode");
        if (input.getBrandCode() != null) requireCode(input.getBrandCode(), "brandCode");
        if (input.getApplicableSkuId() != null) {
            requireRef(input.getApplicableSkuId(), "applicableSkuId", 128);
        }
        requireSha256(input.getContentSha256(), "contentSha256");
        Standard row = new Standard().setStandardId(id).setTenantId(tenantId)
                .setStandardCode(input.getStandardCode()).setCategoryCode(upper(input.getCategoryCode()))
                .setBrandCode(upper(input.getBrandCode())).setApplicableSkuId(input.getApplicableSkuId())
                .setDraftContentSha256(input.getContentSha256()).setStatus("DRAFT")
                .setCurrentVersion(0L).setAggregateVersion(1L)
                .setCreatedAt(now).setUpdatedAt(now);
        require(mapper.insertStandard(row) == 1, "failed to persist quality standard");
        return outcome("quality.standard.created", "quality_standard", id, 1L, "DRAFT",
                payload("standard_id", id, "standard_code", row.getStandardCode(),
                        "category_code", row.getCategoryCode(), "brand_code", row.getBrandCode(),
                        "applicable_sku_id", row.getApplicableSkuId(),
                        "content_sha256", row.getDraftContentSha256()));
    }

    private Outcome publishStandard(Long tenantId, QualityCommand command, LocalDateTime now) {
        QualityCommand.StandardDefinition input = nonNull(command.getStandard(), "standard is required");
        requireRef(input.getStandardId(), "standardId", 128);
        requireRef(input.getApproverPrincipalId(), "approverPrincipalId", 128);
        Standard row = nonNull(mapper.selectStandardForUpdate(tenantId, input.getStandardId()),
                "quality standard not found");
        requireExpectedVersion(input.getExpectedVersion(), row.getAggregateVersion());
        require("DRAFT".equals(row.getStatus()), "only a draft quality standard can be published");
        long nextVersion = row.getCurrentVersion() + 1;
        StandardVersion version = new StandardVersion().setStandardVersionId(UUID.randomUUID().toString())
                .setTenantId(tenantId).setStandardId(row.getStandardId())
                .setStandardVersion(nextVersion).setContentSha256(row.getDraftContentSha256())
                .setApproverPrincipalId(input.getApproverPrincipalId()).setEffectiveAt(now).setCreatedAt(now);
        require(mapper.insertStandardVersion(version) == 1, "failed to persist quality standard version");
        require(mapper.publishStandard(tenantId, row.getStandardId(), row.getAggregateVersion(), now) == 1,
                "quality standard publish conflict");
        return outcome("quality.standard.published", "quality_standard", row.getStandardId(),
                row.getAggregateVersion() + 1, "PUBLISHED",
                payload("standard_id", row.getStandardId(), "standard_code", row.getStandardCode(),
                        "standard_version", nextVersion,
                        "standard_version_id", version.getStandardVersionId(),
                        "content_sha256", version.getContentSha256(),
                        "approver_principal_id", version.getApproverPrincipalId()));
    }

    private Outcome certifyAuthenticator(Long tenantId, QualityCommand command, LocalDateTime now) {
        QualityCommand.CertificationDefinition input =
                nonNull(command.getCertification(), "certification is required");
        String id = valueOrUuid(input.getCertificationId());
        requireRef(input.getAuthenticatorPrincipalId(), "authenticatorPrincipalId", 128);
        requireRef(input.getStandardId(), "standardId", 128);
        String level = upper(input.getCertificationLevel());
        require(CERTIFICATION_LEVELS.contains(level), "unsupported certificationLevel");
        require(input.getEffectiveFrom() != null && input.getEffectiveTo() != null
                        && !input.getEffectiveTo().isBefore(input.getEffectiveFrom()),
                "certification effective window is invalid");
        requireSha256(input.getEvidenceSha256(), "evidenceSha256");
        Standard standard = nonNull(mapper.selectStandardForUpdate(tenantId, input.getStandardId()),
                "quality standard not found");
        require("PUBLISHED".equals(standard.getStatus()), "certification requires a published standard");
        require(mapper.countOverlappingActiveCertification(tenantId,
                        input.getAuthenticatorPrincipalId(), input.getStandardId(),
                        input.getEffectiveFrom(), input.getEffectiveTo()) == 0,
                "an active certification already overlaps this window");
        Certification row = new Certification().setCertificationId(id).setTenantId(tenantId)
                .setAuthenticatorPrincipalId(input.getAuthenticatorPrincipalId())
                .setStandardId(input.getStandardId()).setCertificationLevel(level)
                .setEffectiveFrom(input.getEffectiveFrom()).setEffectiveTo(input.getEffectiveTo())
                .setEvidenceSha256(input.getEvidenceSha256()).setStatus("ACTIVE").setVersion(1L)
                .setCreatedAt(now).setUpdatedAt(now);
        require(mapper.insertCertification(row) == 1, "failed to persist authenticator certification");
        return outcome("quality.authenticator.certified", "authenticator_certification", id,
                1L, "ACTIVE",
                payload("certification_id", id,
                        "authenticator_principal_id", row.getAuthenticatorPrincipalId(),
                        "standard_id", row.getStandardId(), "certification_level", level,
                        "effective_from", row.getEffectiveFrom().toString(),
                        "effective_to", row.getEffectiveTo().toString(),
                        "evidence_sha256", row.getEvidenceSha256()));
    }

    private Outcome revokeAuthenticator(Long tenantId, QualityCommand command, LocalDateTime now) {
        QualityCommand.CertificationDefinition input =
                nonNull(command.getCertification(), "certification is required");
        requireRef(input.getCertificationId(), "certificationId", 128);
        requireCode(input.getReasonCode(), "reasonCode");
        Certification row = nonNull(
                mapper.selectCertificationForUpdate(tenantId, input.getCertificationId()),
                "authenticator certification not found");
        requireExpectedVersion(input.getExpectedVersion(), row.getVersion());
        require("ACTIVE".equals(row.getStatus()), "only an active certification can be revoked");
        require(mapper.revokeCertification(tenantId, row.getCertificationId(), row.getVersion(),
                        upper(input.getReasonCode()), now) == 1,
                "authenticator certification revoke conflict");
        return outcome("quality.authenticator.certification.revoked",
                "authenticator_certification", row.getCertificationId(), row.getVersion() + 1,
                "REVOKED", payload("certification_id", row.getCertificationId(),
                        "authenticator_principal_id", row.getAuthenticatorPrincipalId(),
                        "standard_id", row.getStandardId(),
                        "reason_code", upper(input.getReasonCode())));
    }

    private Outcome createInspectionTask(Long tenantId, Long operationId,
                                         QualityCommand command, LocalDateTime now) {
        QualityCommand.InspectionTaskDefinition input =
                nonNull(command.getInspectionTask(), "inspectionTask is required");
        String id = valueOrUuid(input.getTaskId());
        requireRef(input.getStandardId(), "standardId", 128);
        String subjectType = upper(input.getSubjectType());
        require(SUBJECT_TYPES.contains(subjectType), "unsupported subjectType");
        requireRef(input.getSubjectRef(), "subjectRef", 128);
        requireRef(input.getCanonicalSkuId(), "canonicalSkuId", 128);
        if (input.getLotId() != null) requireRef(input.getLotId(), "lotId", 128);
        requireRef(input.getWarehouseId(), "warehouseId", 128);
        String priority = upper(input.getPriority());
        require(PRIORITIES.contains(priority), "unsupported priority");
        Standard standard = nonNull(mapper.selectStandardForUpdate(tenantId, input.getStandardId()),
                "quality standard not found");
        require("PUBLISHED".equals(standard.getStatus()) && standard.getCurrentVersion() > 0,
                "inspection task requires a published standard");
        StandardVersion version = nonNull(mapper.selectStandardVersion(
                        tenantId, standard.getStandardId(), standard.getCurrentVersion()),
                "published quality standard version is missing");
        InspectionTask row = new InspectionTask().setTaskId(id).setTenantId(tenantId)
                .setStandardId(standard.getStandardId()).setStandardVersion(version.getStandardVersion())
                .setStandardVersionId(version.getStandardVersionId()).setSubjectType(subjectType)
                .setSubjectRef(input.getSubjectRef()).setCanonicalSkuId(input.getCanonicalSkuId())
                .setLotId(input.getLotId()).setWarehouseId(input.getWarehouseId()).setPriority(priority)
                .setStatus("CREATED").setVersion(1L).setCreatedAt(now).setUpdatedAt(now);
        require(mapper.insertInspectionTask(row) == 1, "failed to persist inspection task");
        insertHistory(tenantId, operationId, row, null, "CREATED", null, null, now);
        return taskOutcome("quality.inspection_task.created", row, null, "CREATED");
    }

    private Outcome transitionTask(Long tenantId, Long operationId, QualityCommand command,
                                   LocalDateTime now, String expectedStatus, String nextStatus) {
        QualityCommand.InspectionTaskDefinition input =
                nonNull(command.getInspectionTask(), "inspectionTask is required");
        requireRef(input.getTaskId(), "taskId", 128);
        InspectionTask row = nonNull(mapper.selectInspectionTaskForUpdate(tenantId, input.getTaskId()),
                "inspection task not found");
        requireExpectedVersion(input.getExpectedVersion(), row.getVersion());
        require(expectedStatus.equals(row.getStatus()),
                "inspection task cannot transition from " + row.getStatus() + " to " + nextStatus);

        String principal = row.getAuthenticatorPrincipalId();
        String decision = row.getDecision();
        String defectCode = row.getDefectCode();
        String evidenceRef = row.getEvidenceRef();
        String recheckReason = row.getRecheckReasonCode();
        LocalDateTime assignedAt = null;
        LocalDateTime startedAt = null;
        LocalDateTime decidedAt = null;
        LocalDateTime completedAt = null;
        String actor;
        String reason = null;
        if ("ASSIGNED".equals(nextStatus)) {
            requireRef(input.getAuthenticatorPrincipalId(), "authenticatorPrincipalId", 128);
            require(mapper.selectActiveCertification(tenantId, input.getAuthenticatorPrincipalId(),
                            row.getStandardId(), now.toLocalDate()) != null,
                    "authenticator has no active certification for this standard");
            principal = input.getAuthenticatorPrincipalId();
            assignedAt = now;
            actor = principal;
        } else if ("IN_PROGRESS".equals(nextStatus)) {
            actor = nonNull(principal, "assigned authenticator is missing");
            startedAt = now;
        } else if ("DECIDED".equals(nextStatus)) {
            decision = upper(input.getDecision());
            require(Set.of("PASS", "FAIL").contains(decision), "decision must be PASS or FAIL");
            requireEvidenceRef(input.getEvidenceRef(), "evidenceRef");
            evidenceRef = input.getEvidenceRef();
            if ("FAIL".equals(decision)) {
                requireCode(input.getDefectCode(), "defectCode");
                defectCode = upper(input.getDefectCode());
            } else {
                require(input.getDefectCode() == null, "PASS decision must not carry defectCode");
                defectCode = null;
            }
            decidedAt = now;
            actor = nonNull(principal, "assigned authenticator is missing");
        } else if ("RECHECK_REQUIRED".equals(nextStatus)) {
            requireCode(input.getRecheckReasonCode(), "recheckReasonCode");
            recheckReason = upper(input.getRecheckReasonCode());
            reason = recheckReason;
            actor = principal;
        } else {
            throw new IllegalArgumentException("unsupported inspection transition");
        }
        require(mapper.transitionInspectionTask(tenantId, row.getTaskId(), row.getVersion(),
                        expectedStatus, nextStatus, principal, decision, defectCode, evidenceRef,
                        recheckReason, assignedAt, startedAt, decidedAt, completedAt, now) == 1,
                "inspection task transition conflict");
        long nextVersion = row.getVersion() + 1;
        row.setStatus(nextStatus).setAuthenticatorPrincipalId(principal).setDecision(decision)
                .setDefectCode(defectCode).setEvidenceRef(evidenceRef)
                .setRecheckReasonCode(recheckReason).setVersion(nextVersion).setUpdatedAt(now);
        insertHistory(tenantId, operationId, row, expectedStatus, nextStatus, actor, reason, now);
        return taskOutcome("quality.inspection_task.status_changed", row, expectedStatus, nextStatus);
    }

    private Outcome assignRecheckReviewer(Long tenantId, Long operationId,
                                          QualityCommand command, LocalDateTime now) {
        QualityCommand.InspectionTaskDefinition input =
                nonNull(command.getInspectionTask(), "inspectionTask is required");
        requireRef(input.getTaskId(), "taskId", 128);
        requireRef(input.getSecondaryAuthenticatorPrincipalId(),
                "secondaryAuthenticatorPrincipalId", 128);
        requireCode(input.getRecheckReasonCode(), "recheckReasonCode");
        InspectionTask row = nonNull(mapper.selectInspectionTaskForUpdate(tenantId, input.getTaskId()),
                "inspection task not found");
        requireExpectedVersion(input.getExpectedVersion(), row.getVersion());
        require("DECIDED".equals(row.getStatus()),
                "only a decided task can enter independent recheck");
        require(!input.getSecondaryAuthenticatorPrincipalId()
                        .equals(row.getAuthenticatorPrincipalId()),
                "secondary reviewer must be independent from the primary reviewer");
        require(mapper.selectActiveCertification(tenantId,
                        input.getSecondaryAuthenticatorPrincipalId(),
                        row.getStandardId(), now.toLocalDate()) != null,
                "secondary reviewer has no active certification for this standard");
        String reason = upper(input.getRecheckReasonCode());
        require(mapper.assignRecheckReviewer(tenantId, row.getTaskId(), row.getVersion(),
                        input.getSecondaryAuthenticatorPrincipalId(), reason, now) == 1,
                "inspection recheck assignment conflict");
        String before = row.getStatus();
        row.setStatus("RECHECK_REQUIRED")
                .setSecondaryAuthenticatorPrincipalId(input.getSecondaryAuthenticatorPrincipalId())
                .setRecheckReasonCode(reason).setVersion(row.getVersion() + 1).setUpdatedAt(now);
        insertHistory(tenantId, operationId, row, before, "RECHECK_REQUIRED",
                input.getSecondaryAuthenticatorPrincipalId(), reason, now);
        return taskOutcome("quality.inspection_task.recheck_assigned",
                row, before, "RECHECK_REQUIRED");
    }

    private Outcome submitRecheckDecision(Long tenantId, Long operationId,
                                          QualityCommand command, LocalDateTime now) {
        QualityCommand.InspectionTaskDefinition input =
                nonNull(command.getInspectionTask(), "inspectionTask is required");
        requireRef(input.getTaskId(), "taskId", 128);
        InspectionTask row = nonNull(mapper.selectInspectionTaskForUpdate(tenantId, input.getTaskId()),
                "inspection task not found");
        requireExpectedVersion(input.getExpectedVersion(), row.getVersion());
        require("RECHECK_REQUIRED".equals(row.getStatus()),
                "inspection task is not awaiting an independent recheck");
        require(row.getSecondaryAuthenticatorPrincipalId() != null,
                "secondary reviewer has not been assigned");
        String decision = validateInspectionDecision(
                input.getDecision(), input.getDefectCode(), input.getEvidenceRef());
        String defectCode = "FAIL".equals(decision) ? upper(input.getDefectCode()) : null;
        boolean agrees = decision.equals(row.getDecision());
        String after = agrees ? "DECIDED" : "CONFLICTED";
        String groundTruthDecision = agrees ? decision : null;
        String groundTruthDefectCode = agrees ? defectCode : null;
        String groundTruthEvidenceRef = agrees ? input.getEvidenceRef() : null;
        require(mapper.submitRecheckDecision(tenantId, row.getTaskId(), row.getVersion(),
                        after, decision, defectCode, input.getEvidenceRef(),
                        groundTruthDecision, groundTruthDefectCode, groundTruthEvidenceRef, now) == 1,
                "inspection recheck submission conflict");
        String before = row.getStatus();
        row.setStatus(after).setSecondaryDecision(decision).setSecondaryDefectCode(defectCode)
                .setSecondaryEvidenceRef(input.getEvidenceRef())
                .setGroundTruthDecision(groundTruthDecision)
                .setGroundTruthDefectCode(groundTruthDefectCode)
                .setGroundTruthEvidenceRef(groundTruthEvidenceRef)
                .setRecheckedAt(now).setVersion(row.getVersion() + 1).setUpdatedAt(now);
        insertHistory(tenantId, operationId, row, before, after,
                row.getSecondaryAuthenticatorPrincipalId(),
                agrees ? "RECHECK_AGREED" : "RECHECK_CONFLICT", now);
        return taskOutcome(agrees
                        ? "quality.inspection_task.recheck_agreed"
                        : "quality.inspection_task.recheck_conflicted",
                row, before, after);
    }

    private Outcome adjudicateInspectionTask(Long tenantId, Long operationId,
                                              QualityCommand command, LocalDateTime now) {
        QualityCommand.InspectionTaskDefinition input =
                nonNull(command.getInspectionTask(), "inspectionTask is required");
        requireRef(input.getTaskId(), "taskId", 128);
        requireRef(input.getAdjudicatorPrincipalId(), "adjudicatorPrincipalId", 128);
        InspectionTask row = nonNull(mapper.selectInspectionTaskForUpdate(tenantId, input.getTaskId()),
                "inspection task not found");
        requireExpectedVersion(input.getExpectedVersion(), row.getVersion());
        require("CONFLICTED".equals(row.getStatus()),
                "only a conflicted inspection task can be adjudicated");
        require(!input.getAdjudicatorPrincipalId().equals(row.getAuthenticatorPrincipalId())
                        && !input.getAdjudicatorPrincipalId()
                        .equals(row.getSecondaryAuthenticatorPrincipalId()),
                "adjudicator must be independent from both reviewers");
        require(mapper.selectActiveCertification(tenantId, input.getAdjudicatorPrincipalId(),
                        row.getStandardId(), now.toLocalDate()) != null,
                "adjudicator has no active certification for this standard");
        String decision = validateInspectionDecision(input.getGroundTruthDecision(),
                input.getGroundTruthDefectCode(), input.getGroundTruthEvidenceRef());
        String defectCode = "FAIL".equals(decision) ? upper(input.getGroundTruthDefectCode()) : null;
        require(mapper.adjudicateInspectionTask(tenantId, row.getTaskId(), row.getVersion(),
                        input.getAdjudicatorPrincipalId(), decision, defectCode,
                        input.getGroundTruthEvidenceRef(), now) == 1,
                "inspection adjudication conflict");
        String before = row.getStatus();
        row.setStatus("DECIDED").setAdjudicatorPrincipalId(input.getAdjudicatorPrincipalId())
                .setGroundTruthDecision(decision).setGroundTruthDefectCode(defectCode)
                .setGroundTruthEvidenceRef(input.getGroundTruthEvidenceRef())
                .setAdjudicatedAt(now).setVersion(row.getVersion() + 1).setUpdatedAt(now);
        insertHistory(tenantId, operationId, row, before, "DECIDED",
                input.getAdjudicatorPrincipalId(), "THIRD_PARTY_ADJUDICATION", now);
        return taskOutcome("quality.inspection_task.adjudicated", row, before, "DECIDED");
    }

    private Outcome completeInspectionTask(Long tenantId, Long operationId,
                                           QualityCommand command, LocalDateTime now) {
        QualityCommand.InspectionTaskDefinition input =
                nonNull(command.getInspectionTask(), "inspectionTask is required");
        requireRef(input.getTaskId(), "taskId", 128);
        InspectionTask row = nonNull(mapper.selectInspectionTaskForUpdate(tenantId, input.getTaskId()),
                "inspection task not found");
        requireExpectedVersion(input.getExpectedVersion(), row.getVersion());
        require("DECIDED".equals(row.getStatus()),
                "only a final decided task can be completed");
        String before = row.getStatus();
        require(mapper.transitionInspectionTask(tenantId, row.getTaskId(), row.getVersion(),
                        before, "COMPLETED", row.getAuthenticatorPrincipalId(), row.getDecision(),
                        row.getDefectCode(), row.getEvidenceRef(), row.getRecheckReasonCode(),
                        null, null, null, now, now) == 1,
                "inspection task completion conflict");
        row.setStatus("COMPLETED").setVersion(row.getVersion() + 1)
                .setCompletedAt(now).setUpdatedAt(now);
        insertHistory(tenantId, operationId, row, before, "COMPLETED",
                finalDecisionActor(row), row.getRecheckReasonCode(), now);
        return taskOutcome("quality.inspection_task.status_changed", row, before, "COMPLETED");
    }

    private Outcome openCapa(Long tenantId, QualityCommand command, LocalDateTime now) {
        QualityCommand.CapaDefinition input = nonNull(command.getCapa(), "capa is required");
        String id = valueOrUuid(input.getCapaId());
        requireRef(input.getInspectionTaskId(), "inspectionTaskId", 128);
        requireCode(input.getRootCauseCode(), "rootCauseCode");
        requireRef(input.getOwnerPrincipalId(), "ownerPrincipalId", 128);
        require(input.getDueDate() != null && !input.getDueDate().isBefore(now.toLocalDate()),
                "dueDate must not be in the past");
        InspectionTask task = nonNull(
                mapper.selectInspectionTaskForUpdate(tenantId, input.getInspectionTaskId()),
                "inspection task not found");
        require("FAIL".equals(finalDecision(task))
                        && Set.of("DECIDED", "COMPLETED").contains(task.getStatus()),
                "CAPA requires a failed decided inspection task");
        Capa row = new Capa().setCapaId(id).setTenantId(tenantId)
                .setInspectionTaskId(input.getInspectionTaskId())
                .setRootCauseCode(upper(input.getRootCauseCode()))
                .setOwnerPrincipalId(input.getOwnerPrincipalId()).setDueDate(input.getDueDate())
                .setStatus("OPEN").setVersion(1L).setOpenedAt(now)
                .setCreatedAt(now).setUpdatedAt(now);
        require(mapper.insertCapa(row) == 1, "failed to persist CAPA");
        return outcome("quality.capa.opened", "quality_capa", id, 1L, "OPEN",
                payload("capa_id", id, "inspection_task_id", row.getInspectionTaskId(),
                        "root_cause_code", row.getRootCauseCode(),
                        "owner_principal_id", row.getOwnerPrincipalId(),
                        "due_date", row.getDueDate().toString()));
    }

    private Outcome resolveCapa(Long tenantId, QualityCommand command, LocalDateTime now) {
        QualityCommand.CapaDefinition input = nonNull(command.getCapa(), "capa is required");
        requireRef(input.getCapaId(), "capaId", 128);
        requireEvidenceRef(input.getEffectivenessEvidenceRef(), "effectivenessEvidenceRef");
        Capa row = nonNull(mapper.selectCapaForUpdate(tenantId, input.getCapaId()), "CAPA not found");
        requireExpectedVersion(input.getExpectedVersion(), row.getVersion());
        require("OPEN".equals(row.getStatus()), "only an open CAPA can be verified");
        require(mapper.resolveCapa(tenantId, row.getCapaId(), row.getVersion(),
                        input.getEffectivenessEvidenceRef(), now) == 1,
                "CAPA resolution conflict");
        return outcome("quality.capa.verified", "quality_capa", row.getCapaId(),
                row.getVersion() + 1, "VERIFIED",
                payload("capa_id", row.getCapaId(), "inspection_task_id", row.getInspectionTaskId(),
                        "owner_principal_id", row.getOwnerPrincipalId(),
                        "effectiveness_evidence_ref", input.getEffectivenessEvidenceRef()));
    }

    private Outcome openRecallAction(Long tenantId, QualityCommand command, LocalDateTime now) {
        QualityCommand.RecallActionDefinition input =
                nonNull(command.getRecallAction(), "recallAction is required");
        String id = valueOrUuid(input.getRecallActionId());
        requireRef(input.getInspectionTaskId(), "inspectionTaskId", 128);
        requireCode(input.getReasonCode(), "reasonCode");
        requireRef(input.getOwnerPrincipalId(), "ownerPrincipalId", 128);
        InspectionTask task = nonNull(mapper.selectInspectionTaskForUpdate(
                tenantId, input.getInspectionTaskId()), "inspection task not found");
        require(Set.of("DECIDED", "COMPLETED").contains(task.getStatus())
                        && "FAIL".equals(finalDecision(task)),
                "recall action requires a final failed inspection");
        require(task.getLotId() != null, "recall action requires a governed lot reference");
        RecallAction row = new RecallAction().setRecallActionId(id).setTenantId(tenantId)
                .setInspectionTaskId(task.getTaskId()).setCanonicalSkuId(task.getCanonicalSkuId())
                .setLotId(task.getLotId()).setWarehouseId(task.getWarehouseId())
                .setReasonCode(upper(input.getReasonCode())).setStatus("OPEN")
                .setOwnerPrincipalId(input.getOwnerPrincipalId()).setVersion(1L)
                .setOpenedAt(now).setCreatedAt(now).setUpdatedAt(now);
        require(mapper.insertRecallAction(row) == 1, "failed to persist quality recall action");
        return recallOutcome("quality.recall_action.opened", row, null, "OPEN");
    }

    private Outcome acknowledgeRecallAction(Long tenantId, QualityCommand command, LocalDateTime now) {
        QualityCommand.RecallActionDefinition input =
                nonNull(command.getRecallAction(), "recallAction is required");
        requireRef(input.getRecallActionId(), "recallActionId", 128);
        requireRef(input.getOwnerPrincipalId(), "ownerPrincipalId", 128);
        RecallAction row = nonNull(mapper.selectRecallActionForUpdate(
                tenantId, input.getRecallActionId()), "quality recall action not found");
        requireExpectedVersion(input.getExpectedVersion(), row.getVersion());
        require("OPEN".equals(row.getStatus()), "only an open recall action can be acknowledged");
        require(mapper.acknowledgeRecallAction(tenantId, row.getRecallActionId(), row.getVersion(),
                        input.getOwnerPrincipalId(), now) == 1,
                "quality recall acknowledgement conflict");
        row.setStatus("ACKNOWLEDGED").setOwnerPrincipalId(input.getOwnerPrincipalId())
                .setAcknowledgedAt(now).setVersion(row.getVersion() + 1).setUpdatedAt(now);
        return recallOutcome("quality.recall_action.acknowledged", row, "OPEN", "ACKNOWLEDGED");
    }

    private Outcome resolveRecallAction(Long tenantId, QualityCommand command, LocalDateTime now) {
        QualityCommand.RecallActionDefinition input =
                nonNull(command.getRecallAction(), "recallAction is required");
        requireRef(input.getRecallActionId(), "recallActionId", 128);
        requireRef(input.getOwnerPrincipalId(), "ownerPrincipalId", 128);
        requireCode(input.getResolutionCode(), "resolutionCode");
        RecallAction row = nonNull(mapper.selectRecallActionForUpdate(
                tenantId, input.getRecallActionId()), "quality recall action not found");
        requireExpectedVersion(input.getExpectedVersion(), row.getVersion());
        require(Set.of("OPEN", "ACKNOWLEDGED").contains(row.getStatus()),
                "only an active recall action can be resolved");
        String before = row.getStatus();
        require(mapper.resolveRecallAction(tenantId, row.getRecallActionId(), row.getVersion(),
                        input.getOwnerPrincipalId(), upper(input.getResolutionCode()), now) == 1,
                "quality recall resolution conflict");
        row.setStatus("RESOLVED").setOwnerPrincipalId(input.getOwnerPrincipalId())
                .setResolutionCode(upper(input.getResolutionCode())).setResolvedAt(now)
                .setVersion(row.getVersion() + 1).setUpdatedAt(now);
        return recallOutcome("quality.recall_action.resolved", row, before, "RESOLVED");
    }

    private Outcome recallOutcome(String eventType, RecallAction row,
                                  String previousStatus, String currentStatus) {
        return outcome(eventType, "quality_recall_action", row.getRecallActionId(),
                row.getVersion(), currentStatus,
                payload("recall_action_id", row.getRecallActionId(),
                        "inspection_task_id", row.getInspectionTaskId(),
                        "canonical_sku_id", row.getCanonicalSkuId(), "lot_id", row.getLotId(),
                        "warehouse_id", row.getWarehouseId(), "reason_code", row.getReasonCode(),
                        "owner_principal_id", row.getOwnerPrincipalId(),
                        "resolution_code", row.getResolutionCode(),
                        "previous_status", previousStatus, "current_status", currentStatus));
    }

    private void insertHistory(Long tenantId, Long operationId, InspectionTask row,
                               String previousStatus, String currentStatus, String actor,
                               String reason, LocalDateTime now) {
        TaskHistory history = new TaskHistory().setTenantId(tenantId).setTaskId(row.getTaskId())
                .setTaskVersion(row.getVersion()).setPreviousStatus(previousStatus)
                .setCurrentStatus(currentStatus).setActorPrincipalId(actor).setReasonCode(reason)
                .setOperationId(operationId).setOccurredAt(now).setCreatedAt(now);
        require(mapper.insertTaskHistory(history) == 1, "failed to persist inspection task history");
    }

    private Outcome taskOutcome(String eventType, InspectionTask row,
                                String previousStatus, String currentStatus) {
        return outcome(eventType, "inspection_task", row.getTaskId(), row.getVersion(),
                currentStatus, payload("task_id", row.getTaskId(), "standard_id", row.getStandardId(),
                        "standard_version", row.getStandardVersion(),
                        "standard_version_id", row.getStandardVersionId(),
                        "subject_type", row.getSubjectType(), "subject_ref", row.getSubjectRef(),
                        "canonical_sku_id", row.getCanonicalSkuId(), "lot_id", row.getLotId(),
                        "warehouse_id", row.getWarehouseId(), "priority", row.getPriority(),
                        "authenticator_principal_id", row.getAuthenticatorPrincipalId(),
                        "decision", row.getDecision(), "defect_code", row.getDefectCode(),
                        "evidence_ref", row.getEvidenceRef(), "recheck_reason_code",
                        row.getRecheckReasonCode(),
                        "secondary_authenticator_principal_id",
                        row.getSecondaryAuthenticatorPrincipalId(),
                        "secondary_decision", row.getSecondaryDecision(),
                        "secondary_defect_code", row.getSecondaryDefectCode(),
                        "secondary_evidence_ref", row.getSecondaryEvidenceRef(),
                        "adjudicator_principal_id", row.getAdjudicatorPrincipalId(),
                        "ground_truth_decision", row.getGroundTruthDecision(),
                        "ground_truth_defect_code", row.getGroundTruthDefectCode(),
                        "ground_truth_evidence_ref", row.getGroundTruthEvidenceRef(),
                        "previous_status", previousStatus,
                        "current_status", currentStatus));
    }

    private static String validateInspectionDecision(String decisionInput,
                                                     String defectCodeInput,
                                                     String evidenceRefInput) {
        String decision = upper(decisionInput);
        require(Set.of("PASS", "FAIL").contains(decision), "decision must be PASS or FAIL");
        requireEvidenceRef(evidenceRefInput, "evidenceRef");
        if ("FAIL".equals(decision)) {
            requireCode(defectCodeInput, "defectCode");
        } else {
            require(defectCodeInput == null, "PASS decision must not carry defectCode");
        }
        return decision;
    }

    private static String finalDecision(InspectionTask row) {
        return row.getGroundTruthDecision() == null ? row.getDecision() : row.getGroundTruthDecision();
    }

    private static String finalDecisionActor(InspectionTask row) {
        if (row.getAdjudicatorPrincipalId() != null) return row.getAdjudicatorPrincipalId();
        if (row.getSecondaryAuthenticatorPrincipalId() != null) {
            return row.getSecondaryAuthenticatorPrincipalId();
        }
        return row.getAuthenticatorPrincipalId();
    }

    private void appendEvent(Long tenantId, QualityCommand command, Outcome outcome) {
        outboxAppender.append(AppendDomainEventCommand.builder()
                .eventType(outcome.eventType()).schemaVersion(1).sourceSystem(SOURCE_SYSTEM)
                .tenantId(tenantId).aggregateType(outcome.aggregateType())
                .aggregateId(outcome.aggregateId()).aggregateVersion(outcome.version())
                .eventSequence(outcome.version().shortValue()).occurredAt(command.getOccurredAt())
                .traceId(command.getRunId()).correlationId(command.getCorrelationId())
                .causationId(command.getCausationId())
                .idempotencyKey(outcome.aggregateType() + ":" + outcome.aggregateId()
                        + ":event:" + outcome.version())
                .payload(outcome.payload())
                .headers(Map.of("pii_safe", true, "quality_authority", true))
                .destination("lakehouse").build());
    }

    private static void validateEnvelope(QualityCommand command) {
        require(command != null, "quality command is required");
        require(command.getOperation() != null, "operation is required");
        requireRef(command.getIdempotencyKey(), "idempotencyKey", 128);
        if (command.getRunId() != null) requireRef(command.getRunId(), "runId", 128);
        requireUuid(command.getCorrelationId(), "correlationId");
        if (command.getCausationId() != null) requireUuid(command.getCausationId(), "causationId");
        require(command.getOccurredAt() != null, "occurredAt is required");
    }

    private static Outcome outcome(String eventType, String aggregateType, String aggregateId,
                                   Long version, String status, Map<String, Object> payload) {
        return new Outcome(eventType, aggregateType, aggregateId, version, status, payload);
    }

    private static String valueOrUuid(String value) {
        if (value == null) return UUID.randomUUID().toString();
        requireRef(value, "aggregate id", 128);
        return value;
    }

    private static void requireExpectedVersion(Long expected, Long actual) {
        require(expected != null && expected.equals(actual), "aggregate version conflict");
    }

    private static void requireRef(String value, String field, int maxLength) {
        require(value != null && value.length() <= maxLength && SAFE_REF.matcher(value).matches(),
                field + " must be a safe opaque reference");
    }

    private static void requireCode(String value, String field) {
        String normalized = upper(value);
        require(normalized != null && SAFE_CODE.matcher(normalized).matches(),
                field + " must be an uppercase code");
    }

    private static void requireSha256(String value, String field) {
        require(value != null && SHA256.matcher(value).matches(),
                field + " must be a lowercase SHA-256");
    }

    private static void requireEvidenceRef(String value, String field) {
        require(value != null && EVIDENCE_REF.matcher(value).matches(),
                field + " must be sha256:<digest> or restricted:<opaque-reference>");
    }

    private static void requireUuid(String value, String field) {
        try {
            UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(field + " must be a UUID", exception);
        }
    }

    private static String upper(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static <T> T nonNull(T value, String message) {
        require(value != null, message);
        return value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private static Map<String, Object> payload(Object... values) {
        Map<String, Object> payload = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            payload.put((String) values[index], values[index + 1]);
        }
        return payload;
    }

    private record Outcome(String eventType, String aggregateType, String aggregateId,
                           Long version, String status, Map<String, Object> payload) {
    }
}
