package cn.iocoder.yudao.module.cloudmold.order.service.migration;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.order.api.migration.*;
import cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.migration.*;
import cn.iocoder.yudao.module.cloudmold.order.dal.mysql.migration.LegacyTradeBenefitGovernanceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LegacyTradeBenefitGovernanceServiceImpl implements LegacyTradeBenefitGovernanceApi {

    static final String GOVERNANCE_EVENT = "order.migration.legacy_trade_benefit_governance_assessed";
    static final String POLICY_VERSION = "legacy-trade-benefit-governance-v1";
    private static final int OPERATION_SUCCEEDED = 10;
    private static final String BLOCKED_STATUS =
            "BLOCKED_REQUIRES_HISTORICAL_BENEFIT_FUNDING_AND_QUARANTINE_DECISIONS";

    private final LegacyTradeBenefitGovernanceMapper mapper;
    private final OutboxAppender outboxAppender;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LegacyTradeBenefitGovernanceResult assess(LegacyTradeBenefitGovernanceCommand rawCommand) {
        LegacyTradeBenefitGovernanceCommand command = normalize(rawCommand);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        String requestHash = DigestUtil.sha256Hex(tenantId + "|" + JsonUtils.toJsonString(command));
        String attemptToken = UUID.randomUUID().toString();
        mapper.insertOrResolveOperation(tenantId, command.getIdempotencyKey(), command.getSourceEventId(),
                requestHash, attemptToken, now);
        Long operationId = mapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve benefit-governance operation");
        LegacyTradeBenefitGovernanceOperationDO operation = mapper.selectOperationForUpdate(tenantId, operationId);
        require(operation != null, "benefit-governance operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(requestHash, operation.getRequestHash()),
                    "idempotency key or source event conflicts with another benefit-governance assessment");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing benefit-governance assessment is not complete");
            LegacyTradeBenefitGovernanceResult replay = JsonUtils.parseObject(
                    operation.getResultJson(), LegacyTradeBenefitGovernanceResult.class);
            replay.setDuplicate(true);
            return replay;
        }

        LegacyTradeBenefitMigrationRunDO sourceRun = mapper.selectSourceRun(tenantId,
                command.getSourceMigrationRunId());
        require(sourceRun != null, "source legacy Trade assessment run does not exist");
        require("legacy-trade-benefit-v5".equals(sourceRun.getPolicyVersion())
                        && Boolean.TRUE.equals(sourceRun.getItemEvidenceComplete()),
                "benefit governance requires a complete immutable product-snapshot v5 source assessment");
        List<LegacyTradeBenefitGovernanceComponentSourceDO> componentSources =
                mapper.selectSourceComponents(tenantId, command.getSourceMigrationRunId());
        List<LegacyTradeBenefitGovernanceQuarantineSourceDO> quarantineSources =
                mapper.selectSourceQuarantines(tenantId, command.getSourceMigrationRunId());
        require(componentSources != null && !componentSources.isEmpty(),
                "benefit governance requires the complete non-empty component denominator");
        require(quarantineSources != null, "benefit governance quarantine denominator is missing");

        List<LegacyTradeBenefitGovernanceComponentDO> components = componentSources.stream()
                .map(source -> assessComponent(tenantId, command, source, now)).toList();
        List<LegacyTradeBenefitGovernanceQuarantineDO> quarantines = quarantineSources.stream()
                .map(source -> assessQuarantine(tenantId, command, source, now)).toList();
        require(components.size() == sourceRun.getBenefitComponentCount(),
                "benefit-governance component denominator differs from the immutable source run");
        require(quarantines.size() == sourceRun.getQuarantinedOrderCount(),
                "benefit-governance quarantine denominator differs from the immutable source run");

        int identityQualified = count(components, value -> "QUALIFIED".equals(value.getHistoricalIdentityStatus()));
        int fundingQualified = count(components, value -> "QUALIFIED".equals(value.getFundingResolutionStatus()));
        int admitted = count(components, value -> Boolean.TRUE.equals(value.getGovernanceAdmissionAllowed()));
        int decided = count(quarantines, value -> "DECIDED".equals(value.getDecisionStatus()));
        int referencePresent = count(components, value -> value.getSourceReference() != null);
        int currentObserved = count(components, value ->
                "CURRENT_REFERENCE_OBSERVED_NOT_HISTORICAL_VERSION".equals(value.getCurrentReferenceStatus()));
        String evidenceHash = DigestUtil.sha256Hex(java.util.stream.Stream.concat(
                        components.stream().map(LegacyTradeBenefitGovernanceComponentDO::getEvidenceHash),
                        quarantines.stream().map(LegacyTradeBenefitGovernanceQuarantineDO::getEvidenceHash))
                .sorted().reduce("", (left, right) -> left + "\n" + right));
        boolean ready = admitted == components.size() && decided == quarantines.size();
        LegacyTradeBenefitGovernanceRunDO run = new LegacyTradeBenefitGovernanceRunDO()
                .setGovernanceRunId(command.getGovernanceRunId()).setTenantId(tenantId)
                .setSourceMigrationRunId(command.getSourceMigrationRunId())
                .setPolicyVersion(command.getPolicyVersion()).setEvidenceRef(command.getEvidenceRef())
                .setGovernanceEvidenceHash(evidenceHash).setSourceComponentCount(components.size())
                .setSourceReferencePresentCount(referencePresent).setCurrentReferenceObservedCount(currentObserved)
                .setHistoricalIdentityQualifiedCount(identityQualified)
                .setIdentityBlockedCount(components.size() - identityQualified)
                .setFundingQualifiedCount(fundingQualified).setFundingBlockedCount(components.size() - fundingQualified)
                .setSourceQuarantineCount(quarantines.size()).setQuarantineDecidedCount(decided)
                .setQuarantineOpenCount(quarantines.size() - decided)
                .setGovernanceAdmittedComponentCount(admitted).setProductionMigrationEnabled(false)
                .setStatus(ready ? "READY_FOR_COMBINED_ADMISSION" : BLOCKED_STATUS)
                .setVersion(1L).setAssessedAt(now).setCreatedAt(now).setUpdatedAt(now);

        require(mapper.insertRun(run) == 1, "failed to persist benefit-governance run");
        for (LegacyTradeBenefitGovernanceComponentDO component : components) {
            require(mapper.insertComponent(component) == 1,
                    "failed to persist benefit-governance component evidence");
        }
        for (LegacyTradeBenefitGovernanceQuarantineDO quarantine : quarantines) {
            require(mapper.insertQuarantine(quarantine) == 1,
                    "failed to persist benefit-governance quarantine evidence");
        }
        appendAssessmentEvents(tenantId, command, run, components, quarantines);
        LegacyTradeBenefitGovernanceResult result = toResult(run).setOperationId(operationId);
        require(mapper.markOperationSucceeded(tenantId, operationId, run.getGovernanceRunId(),
                JsonUtils.toJsonString(result), now) == 1,
                "benefit-governance operation completion conflict");
        return result;
    }

    @Override
    public LegacyTradeBenefitGovernanceResult requireRun(String governanceRunId) {
        String runId = requireUuid(governanceRunId, "governanceRunId");
        LegacyTradeBenefitGovernanceRunDO run = mapper.selectRun(
                TenantContextHolder.getRequiredTenantId(), runId);
        require(run != null, "benefit-governance run does not exist");
        return toResult(run);
    }

    @Override
    public List<LegacyTradeBenefitGovernanceComponentView> listComponents(String governanceRunId) {
        String runId = requireUuid(governanceRunId, "governanceRunId");
        List<LegacyTradeBenefitGovernanceComponentDO> rows = mapper.selectComponents(
                TenantContextHolder.getRequiredTenantId(), runId);
        require(rows != null && !rows.isEmpty(), "benefit-governance component evidence does not exist");
        return rows.stream().map(LegacyTradeBenefitGovernanceServiceImpl::toComponentView).toList();
    }

    @Override
    public List<LegacyTradeBenefitGovernanceQuarantineView> listQuarantines(String governanceRunId) {
        String runId = requireUuid(governanceRunId, "governanceRunId");
        List<LegacyTradeBenefitGovernanceQuarantineDO> rows = mapper.selectQuarantines(
                TenantContextHolder.getRequiredTenantId(), runId);
        require(rows != null, "benefit-governance quarantine evidence does not exist");
        return rows.stream().map(LegacyTradeBenefitGovernanceServiceImpl::toQuarantineView).toList();
    }

    static LegacyTradeBenefitGovernanceComponentDO assessComponent(
            Long tenantId, LegacyTradeBenefitGovernanceCommand command,
            LegacyTradeBenefitGovernanceComponentSourceDO source, LocalDateTime now) {
        require(source != null && Objects.equals(tenantId, source.getTenantId())
                        && Objects.equals(command.getSourceMigrationRunId(), source.getSourceMigrationRunId()),
                "benefit-governance component source lineage is invalid");
        requireText(source.getComponentId(), "componentId", 36);
        requireText(source.getCandidateId(), "candidateId", 36);
        require(source.getLegacyOrderId() != null && source.getComponentAmountMinor() != null,
                "benefit-governance component identity or money is missing");
        require(Set.of("GENERIC_DISCOUNT", "COUPON", "POINT", "VIP").contains(source.getComponentType()),
                "benefit-governance component type is invalid");
        String sourceEvidenceHash = sourceComponentEvidenceHash(source);
        String currentStatus;
        String currentSnapshotHash = null;
        if (source.getSourceReference() == null) {
            currentStatus = "MISSING_SOURCE_REFERENCE";
        } else if (source.getSourceReference().startsWith("POINT_QUANTITY:")) {
            currentStatus = "NON_VERSIONED_ENTITLEMENT_QUANTITY_ONLY";
        } else if (source.getObservedSourceId() != null) {
            require(source.getObservedSourceTable() != null && source.getObservedSourceCreatedAt() != null
                            && source.getObservedSourceUpdatedAt() != null && source.getObservedSourceDeleted() != null,
                    "current benefit reference observation is incomplete");
            currentStatus = "CURRENT_REFERENCE_OBSERVED_NOT_HISTORICAL_VERSION";
            currentSnapshotHash = currentReferenceSnapshotHash(source);
        } else {
            currentStatus = "SOURCE_REFERENCE_NOT_FOUND";
        }

        boolean identityQualified = Objects.equals(source.getIdentityQualificationCount(), 1)
                && source.getIdentityQualificationId() != null
                && Objects.equals(sourceEvidenceHash, source.getIdentitySourceComponentEvidenceHash());
        String identityStatus = identityQualified ? "QUALIFIED"
                : "MISSING_SOURCE_REFERENCE".equals(currentStatus)
                || "NON_VERSIONED_ENTITLEMENT_QUANTITY_ONLY".equals(currentStatus)
                ? "BLOCKED_MISSING_SOURCE_REFERENCE"
                : "SOURCE_REFERENCE_NOT_FOUND".equals(currentStatus)
                ? "BLOCKED_SOURCE_REFERENCE_NOT_FOUND" : "BLOCKED_MISSING_HISTORICAL_VERSION";

        int fundingShares = Objects.requireNonNullElse(source.getFundingShareCount(), 0);
        long fundingAmount = Objects.requireNonNullElse(source.getFundingAmountMinor(), 0L);
        boolean fundingEvidenceMatches = Objects.equals(source.getFundingSourceEvidenceHashCount(), 1)
                && Objects.equals(sourceEvidenceHash, source.getFundingSourceComponentEvidenceHash());
        String fundingStatus;
        if (source.getComponentAmountMinor() <= 0) {
            fundingStatus = "BLOCKED_SOURCE_COMPONENT_NOT_POSITIVE";
        } else if (fundingShares == 0) {
            fundingStatus = "BLOCKED_MISSING_NAMED_FUNDER_BREAKDOWN";
        } else if (!fundingEvidenceMatches || fundingAmount != source.getComponentAmountMinor()) {
            fundingStatus = "BLOCKED_FUNDING_AMOUNT_MISMATCH";
        } else {
            fundingStatus = "QUALIFIED";
        }

        List<String> blockers = new ArrayList<>();
        if (!identityQualified) {
            if (source.getIdentityQualificationCount() != null && source.getIdentityQualificationCount() > 0) {
                blockers.add("IDENTITY_QUALIFICATION_EVIDENCE_MISMATCH");
            }
            switch (currentStatus) {
                case "MISSING_SOURCE_REFERENCE" -> blockers.add("SOURCE_REFERENCE_MISSING");
                case "NON_VERSIONED_ENTITLEMENT_QUANTITY_ONLY" -> blockers.add("ENTITLEMENT_VERSION_MISSING");
                case "SOURCE_REFERENCE_NOT_FOUND" -> blockers.add("SOURCE_REFERENCE_NOT_FOUND");
                default -> blockers.add("HISTORICAL_BENEFIT_VERSION_MISSING");
            }
        }
        if (!"QUALIFIED".equals(fundingStatus)) blockers.add(fundingStatus);
        blockers = blockers.stream().distinct().sorted().toList();
        boolean ready = identityQualified && "QUALIFIED".equals(fundingStatus);
        String evidenceHash = DigestUtil.sha256Hex(String.join("\u001f", sourceEvidenceHash, currentStatus,
                Objects.toString(currentSnapshotHash, ""), identityStatus,
                Objects.toString(identityQualified ? source.getIdentityQualificationId() : null, ""),
                Integer.toString(fundingShares), Long.toString(fundingAmount), fundingStatus,
                JsonUtils.toJsonString(blockers)));
        return new LegacyTradeBenefitGovernanceComponentDO()
                .setComponentGovernanceId(deterministicUuid(command.getGovernanceRunId()
                        + "|component|" + source.getComponentId()))
                .setTenantId(tenantId).setGovernanceRunId(command.getGovernanceRunId())
                .setSourceMigrationRunId(command.getSourceMigrationRunId()).setComponentId(source.getComponentId())
                .setCandidateId(source.getCandidateId()).setLegacyOrderId(source.getLegacyOrderId())
                .setComponentType(source.getComponentType()).setComponentAmountMinor(source.getComponentAmountMinor())
                .setSourceReference(source.getSourceReference()).setSourceComponentEvidenceHash(sourceEvidenceHash)
                .setCurrentReferenceStatus(currentStatus)
                .setObservedSourceTable(currentSnapshotHash == null ? null : source.getObservedSourceTable())
                .setObservedSourceId(currentSnapshotHash == null ? null : source.getObservedSourceId())
                .setObservedSourceCreatedAt(currentSnapshotHash == null ? null : source.getObservedSourceCreatedAt())
                .setObservedSourceUpdatedAt(currentSnapshotHash == null ? null : source.getObservedSourceUpdatedAt())
                .setObservedSourceStatus(currentSnapshotHash == null ? null : source.getObservedSourceStatus())
                .setObservedSourceDeleted(currentSnapshotHash == null ? null : source.getObservedSourceDeleted())
                .setObservedSourceSpuId(currentSnapshotHash == null ? null : source.getObservedSourceSpuId())
                .setCurrentReferenceSnapshotHash(currentSnapshotHash)
                .setIdentityQualificationId(identityQualified ? source.getIdentityQualificationId() : null)
                .setHistoricalIdentityStatus(identityStatus).setFundingShareCount(fundingShares)
                .setFundingAmountMinor(fundingAmount).setFundingResolutionStatus(fundingStatus)
                .setGovernanceStatus(ready ? "READY" : "BLOCKED")
                .setBlockerCodes(JsonUtils.toJsonString(blockers)).setGovernanceAdmissionAllowed(ready)
                .setCanonicalImportAllowed(false).setEvidenceHash(evidenceHash).setVersion(1L)
                .setAssessedAt(now).setCreatedAt(now).setUpdatedAt(now);
    }

    static LegacyTradeBenefitGovernanceQuarantineDO assessQuarantine(
            Long tenantId, LegacyTradeBenefitGovernanceCommand command,
            LegacyTradeBenefitGovernanceQuarantineSourceDO source, LocalDateTime now) {
        require(source != null && Objects.equals(tenantId, source.getTenantId())
                        && Objects.equals(command.getSourceMigrationRunId(), source.getSourceMigrationRunId()),
                "benefit-governance quarantine source lineage is invalid");
        require(Set.of("QUARANTINED_MONEY", "QUARANTINED_HEADER_ITEM").contains(source.getAssessmentStatus()),
                "benefit-governance quarantine source status is invalid");
        List<String> sourceReasons = JsonUtils.parseArray(source.getReasonCodes(), String.class);
        require(sourceReasons != null && !sourceReasons.isEmpty(), "quarantine source reason codes are missing");
        boolean decided = Objects.equals(source.getDecisionCount(), 1) && source.getDecisionId() != null
                && Objects.equals(source.getLegacySnapshotHash(), source.getDecisionSourceCandidateEvidenceHash());
        List<String> blockers = decided ? List.of() : List.of(
                source.getDecisionCount() != null && source.getDecisionCount() > 0
                        ? "QUARANTINE_DECISION_EVIDENCE_MISMATCH" : "QUARANTINE_DECISION_MISSING");
        String evidenceHash = DigestUtil.sha256Hex(String.join("\u001f", source.getLegacySnapshotHash(),
                source.getAssessmentStatus(), JsonUtils.toJsonString(sourceReasons),
                Objects.toString(decided ? source.getDecisionId() : null, ""), JsonUtils.toJsonString(blockers)));
        return new LegacyTradeBenefitGovernanceQuarantineDO()
                .setQuarantineGovernanceId(deterministicUuid(command.getGovernanceRunId()
                        + "|quarantine|" + source.getCandidateId()))
                .setTenantId(tenantId).setGovernanceRunId(command.getGovernanceRunId())
                .setSourceMigrationRunId(command.getSourceMigrationRunId()).setCandidateId(source.getCandidateId())
                .setLegacyOrderId(source.getLegacyOrderId()).setLegacyOrderNo(source.getLegacyOrderNo())
                .setSourceCandidateEvidenceHash(source.getLegacySnapshotHash())
                .setSourceAssessmentStatus(source.getAssessmentStatus())
                .setSourceReasonCodes(JsonUtils.toJsonString(sourceReasons))
                .setDecisionId(decided ? source.getDecisionId() : null)
                .setDecisionStatus(decided ? "DECIDED" : "OPEN")
                .setRecommendedAction(decided
                        ? "EXCLUDE_CONFIRMED_SOURCE_DEFECT" : "CORRECT_SOURCE_AND_REASSESS")
                .setBlockerCodes(JsonUtils.toJsonString(blockers)).setCanonicalImportAllowed(false)
                .setEvidenceHash(evidenceHash).setVersion(1L).setAssessedAt(now)
                .setCreatedAt(now).setUpdatedAt(now);
    }

    private void appendAssessmentEvents(Long tenantId, LegacyTradeBenefitGovernanceCommand command,
                                        LegacyTradeBenefitGovernanceRunDO run,
                                        List<LegacyTradeBenefitGovernanceComponentDO> components,
                                        List<LegacyTradeBenefitGovernanceQuarantineDO> quarantines) {
        Map<String, List<LegacyTradeBenefitGovernanceComponentDO>> componentsByCandidate = components.stream()
                .collect(Collectors.groupingBy(LegacyTradeBenefitGovernanceComponentDO::getCandidateId));
        Map<String, LegacyTradeBenefitGovernanceQuarantineDO> quarantineByCandidate = quarantines.stream()
                .collect(Collectors.toMap(LegacyTradeBenefitGovernanceQuarantineDO::getCandidateId, value -> value));
        Set<String> candidateIds = new TreeSet<>(componentsByCandidate.keySet());
        candidateIds.addAll(quarantineByCandidate.keySet());
        for (String candidateId : candidateIds) {
            List<LegacyTradeBenefitGovernanceComponentDO> candidateComponents =
                    componentsByCandidate.getOrDefault(candidateId, List.of());
            LegacyTradeBenefitGovernanceQuarantineDO quarantine = quarantineByCandidate.get(candidateId);
            Long legacyOrderId = !candidateComponents.isEmpty()
                    ? candidateComponents.get(0).getLegacyOrderId() : quarantine.getLegacyOrderId();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("governance_run_id", run.getGovernanceRunId());
            payload.put("source_migration_run_id", run.getSourceMigrationRunId());
            payload.put("candidate_id", candidateId);
            payload.put("legacy_order_id", legacyOrderId);
            payload.put("governance_evidence_hash", run.getGovernanceEvidenceHash());
            payload.put("run_source_component_count", run.getSourceComponentCount());
            payload.put("run_source_reference_present_count", run.getSourceReferencePresentCount());
            payload.put("run_current_reference_observed_count", run.getCurrentReferenceObservedCount());
            payload.put("run_historical_identity_qualified_count", run.getHistoricalIdentityQualifiedCount());
            payload.put("run_identity_blocked_count", run.getIdentityBlockedCount());
            payload.put("run_funding_qualified_count", run.getFundingQualifiedCount());
            payload.put("run_funding_blocked_count", run.getFundingBlockedCount());
            payload.put("run_source_quarantine_count", run.getSourceQuarantineCount());
            payload.put("run_quarantine_decided_count", run.getQuarantineDecidedCount());
            payload.put("run_quarantine_open_count", run.getQuarantineOpenCount());
            payload.put("run_governance_admitted_component_count", run.getGovernanceAdmittedComponentCount());
            payload.put("production_migration_enabled", false);
            payload.put("run_status", run.getStatus());
            payload.put("components", candidateComponents.stream().map(this::componentPayload).toList());
            payload.put("quarantine", quarantine == null ? null : quarantinePayload(quarantine));
            payload.put("policy_version", run.getPolicyVersion());
            payload.put("verification_ref", run.getEvidenceRef());
            payload.put("assessed_at", run.getAssessedAt().toInstant(ZoneOffset.UTC).toString());
            String idempotency = command.getIdempotencyKey() + ":candidate:" + candidateId;
            String candidateGovernanceId = deterministicUuid(run.getGovernanceRunId()
                    + "|candidate-governance|" + candidateId);
            outboxAppender.append(AppendDomainEventCommand.builder()
                    .eventId(deterministicUuid(tenantId + "|" + idempotency))
                    .eventType(GOVERNANCE_EVENT).schemaVersion(2).sourceSystem("cloudmold-order")
                    .tenantId(tenantId).aggregateType("legacy_trade_benefit_governance_readiness")
                    .aggregateId(candidateGovernanceId).aggregateVersion(1L).eventSequence((short) 1)
                    .occurredAt(command.getOccurredAt()).correlationId(command.getCorrelationId())
                    .causationId(command.getCausationId()).idempotencyKey(idempotency).payload(payload)
                    .headers(Map.of("governance_run_id", run.getGovernanceRunId(),
                            "candidate_id", candidateId,
                            "source_migration_run_id", run.getSourceMigrationRunId(),
                            "governance_evidence_hash", run.getGovernanceEvidenceHash()))
                    .destination("lakehouse").build());
        }
    }

    private Map<String, Object> componentPayload(LegacyTradeBenefitGovernanceComponentDO value) {
        Map<String, Object> component = new LinkedHashMap<>();
        component.put("component_governance_id", value.getComponentGovernanceId());
        component.put("component_id", value.getComponentId());
        component.put("component_type", value.getComponentType());
        component.put("component_amount_minor", value.getComponentAmountMinor());
        component.put("source_reference", value.getSourceReference());
        component.put("source_component_evidence_hash", value.getSourceComponentEvidenceHash());
        component.put("current_reference_status", value.getCurrentReferenceStatus());
        component.put("observed_source_table", value.getObservedSourceTable());
        component.put("observed_source_id", value.getObservedSourceId());
        component.put("observed_source_created_at", instant(value.getObservedSourceCreatedAt()));
        component.put("observed_source_updated_at", instant(value.getObservedSourceUpdatedAt()));
        component.put("observed_source_status", value.getObservedSourceStatus());
        component.put("observed_source_deleted", value.getObservedSourceDeleted());
        component.put("observed_source_spu_id", value.getObservedSourceSpuId());
        component.put("current_reference_snapshot_hash", value.getCurrentReferenceSnapshotHash());
        component.put("identity_qualification_id", value.getIdentityQualificationId());
        component.put("historical_identity_status", value.getHistoricalIdentityStatus());
        component.put("funding_share_count", value.getFundingShareCount());
        component.put("funding_amount_minor", value.getFundingAmountMinor());
        component.put("funding_resolution_status", value.getFundingResolutionStatus());
        component.put("governance_status", value.getGovernanceStatus());
        component.put("blocker_codes", JsonUtils.parseArray(value.getBlockerCodes(), String.class));
        component.put("governance_admission_allowed", value.getGovernanceAdmissionAllowed());
        component.put("canonical_import_allowed", false);
        component.put("evidence_hash", value.getEvidenceHash());
        return component;
    }

    private Map<String, Object> quarantinePayload(LegacyTradeBenefitGovernanceQuarantineDO value) {
        Map<String, Object> quarantine = new LinkedHashMap<>();
        quarantine.put("quarantine_governance_id", value.getQuarantineGovernanceId());
        quarantine.put("legacy_order_no", value.getLegacyOrderNo());
        quarantine.put("source_candidate_evidence_hash", value.getSourceCandidateEvidenceHash());
        quarantine.put("source_assessment_status", value.getSourceAssessmentStatus());
        quarantine.put("source_reason_codes", JsonUtils.parseArray(value.getSourceReasonCodes(), String.class));
        quarantine.put("decision_id", value.getDecisionId());
        quarantine.put("decision_status", value.getDecisionStatus());
        quarantine.put("recommended_action", value.getRecommendedAction());
        quarantine.put("blocker_codes", JsonUtils.parseArray(value.getBlockerCodes(), String.class));
        quarantine.put("canonical_import_allowed", false);
        quarantine.put("evidence_hash", value.getEvidenceHash());
        return quarantine;
    }

    static String sourceComponentEvidenceHash(LegacyTradeBenefitGovernanceComponentSourceDO source) {
        return DigestUtil.sha256Hex(String.join("\u001f", Objects.toString(source.getTenantId(), ""),
                Objects.toString(source.getSourceMigrationRunId(), ""), Objects.toString(source.getComponentId(), ""),
                Objects.toString(source.getCandidateId(), ""), Objects.toString(source.getLegacyOrderId(), ""),
                Objects.toString(source.getComponentType(), ""), Objects.toString(source.getComponentAmountMinor(), ""),
                Objects.toString(source.getSourceReference(), "")));
    }

    static String currentReferenceSnapshotHash(LegacyTradeBenefitGovernanceComponentSourceDO source) {
        return DigestUtil.sha256Hex(String.join("\u001f", Objects.toString(source.getObservedSourceTable(), ""),
                Objects.toString(source.getObservedSourceId(), ""),
                Objects.toString(source.getObservedSourceCreatedAt(), ""),
                Objects.toString(source.getObservedSourceUpdatedAt(), ""),
                Objects.toString(source.getObservedSourceStatus(), ""),
                Objects.toString(source.getObservedSourceDeleted(), ""),
                Objects.toString(source.getObservedSourceSpuId(), "")));
    }

    private static LegacyTradeBenefitGovernanceResult toResult(LegacyTradeBenefitGovernanceRunDO run) {
        return new LegacyTradeBenefitGovernanceResult().setGovernanceRunId(run.getGovernanceRunId())
                .setSourceMigrationRunId(run.getSourceMigrationRunId())
                .setGovernanceEvidenceHash(run.getGovernanceEvidenceHash())
                .setSourceComponentCount(run.getSourceComponentCount())
                .setSourceReferencePresentCount(run.getSourceReferencePresentCount())
                .setCurrentReferenceObservedCount(run.getCurrentReferenceObservedCount())
                .setHistoricalIdentityQualifiedCount(run.getHistoricalIdentityQualifiedCount())
                .setIdentityBlockedCount(run.getIdentityBlockedCount())
                .setFundingQualifiedCount(run.getFundingQualifiedCount())
                .setFundingBlockedCount(run.getFundingBlockedCount())
                .setSourceQuarantineCount(run.getSourceQuarantineCount())
                .setQuarantineDecidedCount(run.getQuarantineDecidedCount())
                .setQuarantineOpenCount(run.getQuarantineOpenCount())
                .setGovernanceAdmittedComponentCount(run.getGovernanceAdmittedComponentCount())
                .setProductionMigrationEnabled(run.getProductionMigrationEnabled()).setStatus(run.getStatus());
    }

    private static LegacyTradeBenefitGovernanceComponentView toComponentView(
            LegacyTradeBenefitGovernanceComponentDO value) {
        return new LegacyTradeBenefitGovernanceComponentView()
                .setComponentGovernanceId(value.getComponentGovernanceId())
                .setGovernanceRunId(value.getGovernanceRunId())
                .setSourceMigrationRunId(value.getSourceMigrationRunId()).setComponentId(value.getComponentId())
                .setCandidateId(value.getCandidateId()).setLegacyOrderId(value.getLegacyOrderId())
                .setComponentType(value.getComponentType()).setComponentAmountMinor(value.getComponentAmountMinor())
                .setSourceReference(value.getSourceReference())
                .setSourceComponentEvidenceHash(value.getSourceComponentEvidenceHash())
                .setCurrentReferenceStatus(value.getCurrentReferenceStatus())
                .setObservedSourceTable(value.getObservedSourceTable()).setObservedSourceId(value.getObservedSourceId())
                .setObservedSourceCreatedAt(toInstant(value.getObservedSourceCreatedAt()))
                .setObservedSourceUpdatedAt(toInstant(value.getObservedSourceUpdatedAt()))
                .setObservedSourceStatus(value.getObservedSourceStatus())
                .setObservedSourceDeleted(value.getObservedSourceDeleted())
                .setObservedSourceSpuId(value.getObservedSourceSpuId())
                .setCurrentReferenceSnapshotHash(value.getCurrentReferenceSnapshotHash())
                .setIdentityQualificationId(value.getIdentityQualificationId())
                .setHistoricalIdentityStatus(value.getHistoricalIdentityStatus())
                .setFundingShareCount(value.getFundingShareCount()).setFundingAmountMinor(value.getFundingAmountMinor())
                .setFundingResolutionStatus(value.getFundingResolutionStatus())
                .setGovernanceStatus(value.getGovernanceStatus())
                .setBlockerCodes(JsonUtils.parseArray(value.getBlockerCodes(), String.class))
                .setGovernanceAdmissionAllowed(value.getGovernanceAdmissionAllowed())
                .setCanonicalImportAllowed(value.getCanonicalImportAllowed()).setEvidenceHash(value.getEvidenceHash());
    }

    private static LegacyTradeBenefitGovernanceQuarantineView toQuarantineView(
            LegacyTradeBenefitGovernanceQuarantineDO value) {
        return new LegacyTradeBenefitGovernanceQuarantineView()
                .setQuarantineGovernanceId(value.getQuarantineGovernanceId())
                .setGovernanceRunId(value.getGovernanceRunId())
                .setSourceMigrationRunId(value.getSourceMigrationRunId()).setCandidateId(value.getCandidateId())
                .setLegacyOrderId(value.getLegacyOrderId()).setLegacyOrderNo(value.getLegacyOrderNo())
                .setSourceCandidateEvidenceHash(value.getSourceCandidateEvidenceHash())
                .setSourceAssessmentStatus(value.getSourceAssessmentStatus())
                .setSourceReasonCodes(JsonUtils.parseArray(value.getSourceReasonCodes(), String.class))
                .setDecisionId(value.getDecisionId()).setDecisionStatus(value.getDecisionStatus())
                .setRecommendedAction(value.getRecommendedAction())
                .setBlockerCodes(JsonUtils.parseArray(value.getBlockerCodes(), String.class))
                .setCanonicalImportAllowed(value.getCanonicalImportAllowed()).setEvidenceHash(value.getEvidenceHash());
    }

    private static <T> int count(Collection<T> values, java.util.function.Predicate<T> predicate) {
        return (int) values.stream().filter(predicate).count();
    }

    private static String instant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC).toString();
    }

    private static java.time.Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private static LegacyTradeBenefitGovernanceCommand normalize(LegacyTradeBenefitGovernanceCommand command) {
        require(command != null, "benefit-governance command is required");
        requireText(command.getIdempotencyKey(), "idempotencyKey", 128);
        if (command.getSourceEventId() != null) requireText(command.getSourceEventId(), "sourceEventId", 128);
        command.setGovernanceRunId(requireUuid(command.getGovernanceRunId(), "governanceRunId"));
        command.setSourceMigrationRunId(requireUuid(command.getSourceMigrationRunId(), "sourceMigrationRunId"));
        require(POLICY_VERSION.equals(command.getPolicyVersion()),
                "new benefit-governance assessments require policy v1");
        requireText(command.getEvidenceRef(), "evidenceRef", 256);
        command.setCorrelationId(requireUuid(command.getCorrelationId(), "correlationId"));
        if (command.getCausationId() != null) command.setCausationId(requireUuid(command.getCausationId(), "causationId"));
        require(command.getOccurredAt() != null, "occurredAt is required");
        return command;
    }

    private static String requireUuid(String value, String field) {
        requireText(value, field, 36);
        try {
            return UUID.fromString(value).toString();
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(field + " must be a UUID", error);
        }
    }

    private static String deterministicUuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static void requireText(String value, String field, int maxLength) {
        require(value != null && !value.isBlank() && value.length() <= maxLength,
                field + " is required and too long");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
