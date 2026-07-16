package cn.iocoder.yudao.module.cloudmold.order.service.migration;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.order.api.migration.*;
import cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.migration.*;
import cn.iocoder.yudao.module.cloudmold.order.dal.mysql.migration.LegacyTradeBenefitMigrationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class LegacyTradeBenefitMigrationServiceImpl implements LegacyTradeBenefitMigrationApi {

    static final String ASSESSMENT_EVENT = "order.migration.legacy_trade_benefit_assessed";
    private static final int OPERATION_SUCCEEDED = 10;
    private static final String FUNDING_MISSING = "MISSING_NAMED_FUNDER_BREAKDOWN";

    private final LegacyTradeBenefitMigrationMapper mapper;
    private final OutboxAppender outboxAppender;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LegacyTradeBenefitAssessmentResult assess(LegacyTradeBenefitAssessmentCommand rawCommand) {
        LegacyTradeBenefitAssessmentCommand command = normalize(rawCommand);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        String requestHash = DigestUtil.sha256Hex(tenantId + "|" + JsonUtils.toJsonString(command));
        String attemptToken = UUID.randomUUID().toString();
        mapper.insertOrResolveOperation(tenantId, command.getIdempotencyKey(), command.getSourceEventId(),
                requestHash, attemptToken, now);
        Long operationId = mapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve legacy Trade benefit operation");
        LegacyTradeBenefitMigrationOperationDO operation = mapper.selectOperationForUpdate(tenantId, operationId);
        require(operation != null, "legacy Trade benefit operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(requestHash, operation.getRequestHash()),
                    "idempotency key or source event conflicts with another assessment");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing legacy Trade benefit assessment is not complete");
            LegacyTradeBenefitAssessmentResult replay = JsonUtils.parseObject(operation.getResultJson(),
                    LegacyTradeBenefitAssessmentResult.class);
            replay.setDuplicate(true);
            return replay;
        }

        List<LegacyTradeOrderAssessmentSourceDO> sourceRows = mapper.selectSourceOrders(tenantId);
        require(sourceRows != null && !sourceRows.isEmpty(),
                "legacy Trade benefit assessment requires non-empty source Orders");
        List<LegacyTradeBenefitMigrationCandidateDO> candidates = new ArrayList<>(sourceRows.size());
        List<LegacyTradeBenefitMigrationComponentDO> components = new ArrayList<>();
        for (LegacyTradeOrderAssessmentSourceDO source : sourceRows) {
            LegacyTradeBenefitMigrationCandidateDO candidate = assessCandidate(
                    tenantId, command.getMigrationRunId(), source, now);
            candidates.add(candidate);
            components.addAll(assessComponents(candidate, source, now));
        }
        require(!components.isEmpty(), "legacy Trade benefit assessment requires non-empty benefit evidence");

        String sourceSnapshotHash = DigestUtil.sha256Hex(candidates.stream()
                .map(LegacyTradeBenefitMigrationCandidateDO::getLegacySnapshotHash)
                .sorted().reduce("", (left, right) -> left + "\n" + right));
        int deletedCount = count(candidates, "DELETED_EXCLUDED");
        int noBenefitCount = count(candidates, "NO_BENEFIT");
        int pendingCount = count(candidates, "BENEFIT_REQUIRES_IDENTITY_AND_FUNDING");
        int quarantinedCount = (int) candidates.stream()
                .filter(value -> value.getAssessmentStatus().startsWith("QUARANTINED_")).count();
        int nonDeletedCount = candidates.size() - deletedCount;
        long sourceBenefitAmount = candidates.stream().filter(value -> !Boolean.TRUE.equals(value.getDeleted()))
                .mapToLong(LegacyTradeBenefitMigrationCandidateDO::headerBenefitAmountMinor).sum();
        long componentAmount = components.stream()
                .mapToLong(LegacyTradeBenefitMigrationComponentDO::getComponentAmountMinor).sum();
        require(sourceBenefitAmount == componentAmount, "legacy Trade benefit component amount is not conserved");
        LocalDateTime watermark = sourceRows.stream().map(LegacyTradeOrderAssessmentSourceDO::getSourceUpdatedAt)
                .filter(Objects::nonNull).max(LocalDateTime::compareTo)
                .orElseThrow(() -> new IllegalArgumentException("legacy Trade source watermark is missing"));

        LegacyTradeBenefitMigrationRunDO run = new LegacyTradeBenefitMigrationRunDO()
                .setMigrationRunId(command.getMigrationRunId()).setTenantId(tenantId)
                .setSourceScope("LOCAL_YUDAO_TRADE_CURRENT").setPolicyVersion(command.getPolicyVersion())
                .setEvidenceRef(command.getEvidenceRef()).setSourceSnapshotHash(sourceSnapshotHash)
                .setSourceWatermark(watermark).setSourceOrderCount(candidates.size())
                .setNonDeletedOrderCount(nonDeletedCount).setDeletedExcludedCount(deletedCount)
                .setNoBenefitOrderCount(noBenefitCount).setBenefitEvidencePendingOrderCount(pendingCount)
                .setQuarantinedOrderCount(quarantinedCount).setBenefitComponentCount(components.size())
                .setSourceBenefitAmountMinor(sourceBenefitAmount).setComponentAmountMinor(componentAmount)
                .setUnresolvedIdentityCount(components.size()).setUnresolvedFundingCount(components.size())
                .setImportAllowedComponentCount(0).setProductionMigrationEnabled(false)
                .setStatus("BLOCKED_REQUIRES_GOVERNED_EVIDENCE").setVersion(1L)
                .setAssessedAt(now).setCreatedAt(now).setUpdatedAt(now);
        require(nonDeletedCount == noBenefitCount + pendingCount + quarantinedCount,
                "legacy Trade assessment denominator is not conserved");
        require(mapper.insertRun(run) == 1, "failed to persist legacy Trade benefit assessment run");
        Map<String, List<LegacyTradeBenefitMigrationComponentDO>> componentsByCandidate = new HashMap<>();
        for (LegacyTradeBenefitMigrationCandidateDO candidate : candidates) {
            require(mapper.insertCandidate(candidate) == 1,
                    "failed to persist legacy Trade benefit assessment candidate");
        }
        for (LegacyTradeBenefitMigrationComponentDO component : components) {
            require(mapper.insertComponent(component) == 1,
                    "failed to persist legacy Trade benefit assessment component");
            componentsByCandidate.computeIfAbsent(component.getCandidateId(), ignored -> new ArrayList<>())
                    .add(component);
        }
        for (LegacyTradeBenefitMigrationCandidateDO candidate : candidates) {
            appendAssessmentEvent(tenantId, command, run, candidate,
                    componentsByCandidate.getOrDefault(candidate.getCandidateId(), List.of()));
        }

        LegacyTradeBenefitAssessmentResult result = toResult(run).setOperationId(operationId);
        require(mapper.markOperationSucceeded(tenantId, operationId, run.getMigrationRunId(),
                JsonUtils.toJsonString(result), now) == 1,
                "legacy Trade benefit operation completion conflict");
        return result;
    }

    @Override
    public LegacyTradeBenefitAssessmentResult requireRun(String migrationRunId) {
        String runId = requireUuid(migrationRunId, "migrationRunId");
        LegacyTradeBenefitMigrationRunDO run = mapper.selectRun(
                TenantContextHolder.getRequiredTenantId(), runId);
        require(run != null, "legacy Trade benefit assessment run does not exist");
        return toResult(run);
    }

    @Override
    public List<LegacyTradeBenefitCandidateView> listCandidates(String migrationRunId) {
        String runId = requireUuid(migrationRunId, "migrationRunId");
        List<LegacyTradeBenefitMigrationCandidateDO> rows = mapper.selectCandidates(
                TenantContextHolder.getRequiredTenantId(), runId);
        require(rows != null && !rows.isEmpty(), "legacy Trade benefit candidates do not exist");
        return rows.stream().map(LegacyTradeBenefitMigrationServiceImpl::toCandidateView).toList();
    }

    @Override
    public List<LegacyTradeBenefitComponentView> listComponents(String migrationRunId) {
        String runId = requireUuid(migrationRunId, "migrationRunId");
        List<LegacyTradeBenefitMigrationComponentDO> rows = mapper.selectComponents(
                TenantContextHolder.getRequiredTenantId(), runId);
        require(rows != null && !rows.isEmpty(), "legacy Trade benefit components do not exist");
        return rows.stream().map(LegacyTradeBenefitMigrationServiceImpl::toComponentView).toList();
    }

    static LegacyTradeBenefitMigrationCandidateDO assessCandidate(Long tenantId, String runId,
                                                                   LegacyTradeOrderAssessmentSourceDO source,
                                                                   LocalDateTime now) {
        normalizeSource(source);
        require(Objects.equals(tenantId, source.getTenantId()),
                "legacy Trade source tenant does not match assessment tenant");
        boolean negativeMoney = source.getHeaderGrossAmountMinor() < 0
                || source.getHeaderGenericDiscountAmountMinor() < 0 || source.getHeaderCouponAmountMinor() < 0
                || source.getHeaderPointAmountMinor() < 0 || source.getHeaderVipAmountMinor() < 0
                || source.getHeaderDeliveryAmountMinor() < 0 || source.getHeaderPayAmountMinor() < 0;
        boolean headerMoneyMismatch = source.getHeaderGrossAmountMinor()
                + source.getHeaderDeliveryAmountMinor() + source.getHeaderAdjustAmountMinor()
                != source.getHeaderGenericDiscountAmountMinor() + source.getHeaderCouponAmountMinor()
                + source.getHeaderPointAmountMinor() + source.getHeaderVipAmountMinor()
                + source.getHeaderPayAmountMinor();
        boolean headerItemMismatch = source.getHeaderQuantity() < 0 || source.getItemRowCount() <= 0
                || source.getItemQuantity() < 0
                || !Objects.equals(source.getHeaderQuantity(), source.getItemQuantity())
                || !Objects.equals(source.getHeaderGrossAmountMinor(), source.getItemGrossAmountMinor())
                || !Objects.equals(source.getHeaderGenericDiscountAmountMinor(), source.getItemGenericDiscountAmountMinor())
                || !Objects.equals(source.getHeaderCouponAmountMinor(), source.getItemCouponAmountMinor())
                || !Objects.equals(source.getHeaderPointAmountMinor(), source.getItemPointAmountMinor())
                || !Objects.equals(source.getHeaderVipAmountMinor(), source.getItemVipAmountMinor())
                || !Objects.equals(source.getHeaderDeliveryAmountMinor(), source.getItemDeliveryAmountMinor())
                || !Objects.equals(source.getHeaderAdjustAmountMinor(), source.getItemAdjustAmountMinor())
                || !Objects.equals(source.getHeaderPayAmountMinor(), source.getItemPayAmountMinor());
        long benefitAmount = source.getHeaderGenericDiscountAmountMinor() + source.getHeaderCouponAmountMinor()
                + source.getHeaderPointAmountMinor() + source.getHeaderVipAmountMinor();
        String status;
        List<String> reasons = new ArrayList<>();
        if (Boolean.TRUE.equals(source.getDeleted())) {
            status = "DELETED_EXCLUDED";
            reasons.add("SOURCE_DELETED");
        } else if (negativeMoney || headerMoneyMismatch || source.getInvalidItemMoneyCount() != 0) {
            status = "QUARANTINED_MONEY";
            if (negativeMoney) reasons.add("NEGATIVE_MONEY");
            if (headerMoneyMismatch) reasons.add("HEADER_MONEY_MISMATCH");
            if (source.getInvalidItemMoneyCount() != 0) reasons.add("ITEM_MONEY_MISMATCH");
        } else if (headerItemMismatch) {
            status = "QUARANTINED_HEADER_ITEM";
            reasons.add("HEADER_ITEM_MISMATCH");
        } else if (benefitAmount == 0) {
            status = "NO_BENEFIT";
        } else {
            status = "BENEFIT_REQUIRES_IDENTITY_AND_FUNDING";
            reasons.addAll(List.of("EXACT_ORDER_MAPPING_MISSING", "EXACT_ITEM_MAPPING_MISSING",
                    "NAMED_FUNDING_BREAKDOWN_MISSING", "VERSIONED_BENEFIT_IDENTITY_MISSING"));
        }
        reasons = reasons.stream().distinct().sorted().toList();
        String snapshotHash = sourceSnapshotHash(source);
        String candidateId = deterministicUuid(runId + "|legacy-trade-order|" + source.getLegacyOrderId());
        return new LegacyTradeBenefitMigrationCandidateDO().setCandidateId(candidateId).setTenantId(tenantId)
                .setMigrationRunId(runId).setLegacyOrderId(source.getLegacyOrderId())
                .setLegacyOrderNo(source.getLegacyOrderNo()).setLegacySnapshotHash(snapshotHash)
                .setSourceUpdatedAt(source.getSourceUpdatedAt()).setDeleted(source.getDeleted())
                .setHeaderQuantity(source.getHeaderQuantity()).setItemRowCount(source.getItemRowCount())
                .setItemQuantity(source.getItemQuantity()).setHeaderGrossAmountMinor(source.getHeaderGrossAmountMinor())
                .setHeaderGenericDiscountAmountMinor(source.getHeaderGenericDiscountAmountMinor())
                .setHeaderCouponAmountMinor(source.getHeaderCouponAmountMinor())
                .setHeaderPointAmountMinor(source.getHeaderPointAmountMinor())
                .setHeaderVipAmountMinor(source.getHeaderVipAmountMinor())
                .setHeaderDeliveryAmountMinor(source.getHeaderDeliveryAmountMinor())
                .setHeaderAdjustAmountMinor(source.getHeaderAdjustAmountMinor())
                .setHeaderPayAmountMinor(source.getHeaderPayAmountMinor())
                .setItemGrossAmountMinor(source.getItemGrossAmountMinor())
                .setItemGenericDiscountAmountMinor(source.getItemGenericDiscountAmountMinor())
                .setItemCouponAmountMinor(source.getItemCouponAmountMinor())
                .setItemPointAmountMinor(source.getItemPointAmountMinor())
                .setItemVipAmountMinor(source.getItemVipAmountMinor())
                .setItemDeliveryAmountMinor(source.getItemDeliveryAmountMinor())
                .setItemAdjustAmountMinor(source.getItemAdjustAmountMinor())
                .setItemPayAmountMinor(source.getItemPayAmountMinor())
                .setInvalidItemMoneyCount(source.getInvalidItemMoneyCount()).setNegativeMoney(negativeMoney)
                .setHeaderMoneyMismatch(headerMoneyMismatch).setHeaderItemMismatch(headerItemMismatch)
                .setAssessmentStatus(status).setReasonCodes(JsonUtils.toJsonString(reasons))
                .setCanonicalImportAllowed(false).setVersion(1L).setAssessedAt(now)
                .setCreatedAt(now).setUpdatedAt(now);
    }

    static List<LegacyTradeBenefitMigrationComponentDO> assessComponents(
            LegacyTradeBenefitMigrationCandidateDO candidate, LegacyTradeOrderAssessmentSourceDO source,
            LocalDateTime now) {
        if (Boolean.TRUE.equals(candidate.getDeleted())) return List.of();
        List<LegacyTradeBenefitMigrationComponentDO> values = new ArrayList<>(4);
        addComponent(values, candidate, "GENERIC_DISCOUNT", source.getHeaderGenericDiscountAmountMinor(),
                genericSourceReference(source), genericIdentityStatus(source), now);
        addComponent(values, candidate, "COUPON", source.getHeaderCouponAmountMinor(),
                positiveReference("COUPON", source.getLegacyCouponId()),
                positive(source.getLegacyCouponId()) ? "SOURCE_REFERENCE_WITHOUT_VERSION" : "MISSING_SOURCE_REFERENCE", now);
        addComponent(values, candidate, "POINT", source.getHeaderPointAmountMinor(),
                source.getLegacyUsedPointQuantity() != null && source.getLegacyUsedPointQuantity() > 0
                        ? "POINT_QUANTITY:" + source.getLegacyUsedPointQuantity() : null,
                source.getLegacyUsedPointQuantity() != null && source.getLegacyUsedPointQuantity() > 0
                        ? "MISSING_ENTITLEMENT_VERSION" : "MISSING_SOURCE_REFERENCE", now);
        addComponent(values, candidate, "VIP", source.getHeaderVipAmountMinor(), null,
                "MISSING_BENEFIT_VERSION", now);
        return values;
    }

    private static void addComponent(List<LegacyTradeBenefitMigrationComponentDO> values,
                                     LegacyTradeBenefitMigrationCandidateDO candidate, String type, Long amount,
                                     String sourceReference, String identityStatus, LocalDateTime now) {
        if (amount == null || amount == 0) return;
        String componentId = deterministicUuid(candidate.getMigrationRunId() + "|" + candidate.getLegacyOrderId()
                + "|" + type);
        values.add(new LegacyTradeBenefitMigrationComponentDO().setComponentId(componentId)
                .setTenantId(candidate.getTenantId()).setMigrationRunId(candidate.getMigrationRunId())
                .setCandidateId(candidate.getCandidateId()).setLegacyOrderId(candidate.getLegacyOrderId())
                .setComponentType(type).setComponentAmountMinor(amount).setSourceReference(sourceReference)
                .setIdentityResolutionStatus(identityStatus).setFundingResolutionStatus(FUNDING_MISSING)
                .setCanonicalImportAllowed(false).setVersion(1L).setCreatedAt(now).setUpdatedAt(now));
    }

    private void appendAssessmentEvent(Long tenantId, LegacyTradeBenefitAssessmentCommand command,
                                       LegacyTradeBenefitMigrationRunDO run,
                                       LegacyTradeBenefitMigrationCandidateDO candidate,
                                       List<LegacyTradeBenefitMigrationComponentDO> components) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("migration_run_id", run.getMigrationRunId());
        payload.put("candidate_id", candidate.getCandidateId());
        payload.put("source_scope", run.getSourceScope());
        payload.put("legacy_order_id", candidate.getLegacyOrderId());
        payload.put("legacy_order_no", candidate.getLegacyOrderNo());
        payload.put("source_updated_at", candidate.getSourceUpdatedAt().toInstant(ZoneOffset.UTC).toString());
        payload.put("source_snapshot_hash", candidate.getLegacySnapshotHash());
        payload.put("is_deleted", candidate.getDeleted());
        payload.put("header_quantity", candidate.getHeaderQuantity());
        payload.put("item_row_count", candidate.getItemRowCount());
        payload.put("item_quantity", candidate.getItemQuantity());
        payload.put("header_gross_amount_minor", candidate.getHeaderGrossAmountMinor());
        payload.put("header_benefit_amount_minor", candidate.headerBenefitAmountMinor());
        payload.put("header_pay_amount_minor", candidate.getHeaderPayAmountMinor());
        payload.put("item_gross_amount_minor", candidate.getItemGrossAmountMinor());
        payload.put("item_benefit_amount_minor", candidate.itemBenefitAmountMinor());
        payload.put("item_pay_amount_minor", candidate.getItemPayAmountMinor());
        payload.put("negative_money", candidate.getNegativeMoney());
        payload.put("header_money_mismatch", candidate.getHeaderMoneyMismatch());
        payload.put("header_item_mismatch", candidate.getHeaderItemMismatch());
        payload.put("invalid_item_money_count", candidate.getInvalidItemMoneyCount());
        payload.put("assessment_status", candidate.getAssessmentStatus());
        payload.put("blocker_codes", JsonUtils.parseArray(candidate.getReasonCodes(), String.class));
        payload.put("canonical_import_allowed", false);
        payload.put("components", components.stream().map(value -> {
            Map<String, Object> component = new LinkedHashMap<>();
            component.put("component_id", value.getComponentId());
            component.put("component_type", value.getComponentType());
            component.put("amount_minor", value.getComponentAmountMinor());
            component.put("source_reference", value.getSourceReference());
            component.put("identity_resolution_status", value.getIdentityResolutionStatus());
            component.put("funding_resolution_status", value.getFundingResolutionStatus());
            component.put("canonical_import_allowed", false);
            return component;
        }).toList());
        payload.put("policy_version", run.getPolicyVersion());
        payload.put("verification_ref", run.getEvidenceRef());
        payload.put("assessed_at", candidate.getAssessedAt().toInstant(ZoneOffset.UTC).toString());
        String idempotency = command.getIdempotencyKey() + ":order:" + candidate.getLegacyOrderId();
        outboxAppender.append(AppendDomainEventCommand.builder()
                .eventId(deterministicUuid(tenantId + "|" + idempotency))
                .eventType(ASSESSMENT_EVENT).schemaVersion(1).sourceSystem("cloudmold-order")
                .tenantId(tenantId).aggregateType("legacy_trade_benefit_migration_assessment")
                .aggregateId(candidate.getCandidateId()).aggregateVersion(1L).eventSequence((short) 1)
                .occurredAt(command.getOccurredAt()).correlationId(command.getCorrelationId())
                .causationId(command.getCausationId()).idempotencyKey(idempotency).payload(payload)
                .headers(Map.of("migration_run_id", run.getMigrationRunId(),
                        "source_snapshot_hash", run.getSourceSnapshotHash()))
                .destination("lakehouse").build());
    }

    private static LegacyTradeBenefitAssessmentResult toResult(LegacyTradeBenefitMigrationRunDO run) {
        return new LegacyTradeBenefitAssessmentResult().setMigrationRunId(run.getMigrationRunId())
                .setSourceSnapshotHash(run.getSourceSnapshotHash()).setSourceOrderCount(run.getSourceOrderCount())
                .setNonDeletedOrderCount(run.getNonDeletedOrderCount())
                .setDeletedExcludedCount(run.getDeletedExcludedCount())
                .setNoBenefitOrderCount(run.getNoBenefitOrderCount())
                .setBenefitEvidencePendingOrderCount(run.getBenefitEvidencePendingOrderCount())
                .setQuarantinedOrderCount(run.getQuarantinedOrderCount())
                .setBenefitComponentCount(run.getBenefitComponentCount())
                .setSourceBenefitAmountMinor(run.getSourceBenefitAmountMinor())
                .setComponentAmountMinor(run.getComponentAmountMinor())
                .setUnresolvedIdentityCount(run.getUnresolvedIdentityCount())
                .setUnresolvedFundingCount(run.getUnresolvedFundingCount())
                .setImportAllowedComponentCount(run.getImportAllowedComponentCount())
                .setProductionMigrationEnabled(run.getProductionMigrationEnabled()).setStatus(run.getStatus());
    }

    private static LegacyTradeBenefitCandidateView toCandidateView(LegacyTradeBenefitMigrationCandidateDO value) {
        return new LegacyTradeBenefitCandidateView().setCandidateId(value.getCandidateId())
                .setMigrationRunId(value.getMigrationRunId()).setLegacyOrderId(value.getLegacyOrderId())
                .setLegacyOrderNo(value.getLegacyOrderNo()).setLegacySnapshotHash(value.getLegacySnapshotHash())
                .setDeleted(value.getDeleted()).setHeaderQuantity(value.getHeaderQuantity())
                .setItemRowCount(value.getItemRowCount()).setItemQuantity(value.getItemQuantity())
                .setHeaderBenefitAmountMinor(value.headerBenefitAmountMinor())
                .setItemBenefitAmountMinor(value.itemBenefitAmountMinor()).setNegativeMoney(value.getNegativeMoney())
                .setHeaderMoneyMismatch(value.getHeaderMoneyMismatch())
                .setHeaderItemMismatch(value.getHeaderItemMismatch())
                .setInvalidItemMoneyCount(value.getInvalidItemMoneyCount())
                .setAssessmentStatus(value.getAssessmentStatus())
                .setReasonCodes(JsonUtils.parseArray(value.getReasonCodes(), String.class))
                .setCanonicalImportAllowed(value.getCanonicalImportAllowed())
                .setSourceUpdatedAt(value.getSourceUpdatedAt().toInstant(ZoneOffset.UTC))
                .setAssessedAt(value.getAssessedAt().toInstant(ZoneOffset.UTC));
    }

    private static LegacyTradeBenefitComponentView toComponentView(LegacyTradeBenefitMigrationComponentDO value) {
        return new LegacyTradeBenefitComponentView().setComponentId(value.getComponentId())
                .setMigrationRunId(value.getMigrationRunId()).setCandidateId(value.getCandidateId())
                .setLegacyOrderId(value.getLegacyOrderId()).setComponentType(value.getComponentType())
                .setComponentAmountMinor(value.getComponentAmountMinor())
                .setSourceReference(value.getSourceReference())
                .setIdentityResolutionStatus(value.getIdentityResolutionStatus())
                .setFundingResolutionStatus(value.getFundingResolutionStatus())
                .setCanonicalImportAllowed(value.getCanonicalImportAllowed());
    }

    static String sourceSnapshotHash(LegacyTradeOrderAssessmentSourceDO source) {
        return DigestUtil.sha256Hex(String.join("\u001f",
                Objects.toString(source.getTenantId(), ""), Objects.toString(source.getLegacyOrderId(), ""),
                Objects.toString(source.getLegacyOrderNo(), ""), Objects.toString(source.getSourceUpdatedAt(), ""),
                Objects.toString(source.getDeleted(), ""), Objects.toString(source.getHeaderQuantity(), ""),
                Objects.toString(source.getItemRowCount(), ""), Objects.toString(source.getItemQuantity(), ""),
                Objects.toString(source.getHeaderGrossAmountMinor(), ""),
                Objects.toString(source.getHeaderGenericDiscountAmountMinor(), ""),
                Objects.toString(source.getHeaderCouponAmountMinor(), ""),
                Objects.toString(source.getHeaderPointAmountMinor(), ""),
                Objects.toString(source.getHeaderVipAmountMinor(), ""),
                Objects.toString(source.getHeaderDeliveryAmountMinor(), ""),
                Objects.toString(source.getHeaderAdjustAmountMinor(), ""),
                Objects.toString(source.getHeaderPayAmountMinor(), ""),
                Objects.toString(source.getItemGrossAmountMinor(), ""),
                Objects.toString(source.getItemGenericDiscountAmountMinor(), ""),
                Objects.toString(source.getItemCouponAmountMinor(), ""),
                Objects.toString(source.getItemPointAmountMinor(), ""),
                Objects.toString(source.getItemVipAmountMinor(), ""),
                Objects.toString(source.getItemDeliveryAmountMinor(), ""),
                Objects.toString(source.getItemAdjustAmountMinor(), ""),
                Objects.toString(source.getItemPayAmountMinor(), ""),
                Objects.toString(source.getInvalidItemMoneyCount(), ""),
                Objects.toString(source.getLegacyCouponId(), ""),
                Objects.toString(source.getLegacyUsedPointQuantity(), ""),
                Objects.toString(source.getLegacySeckillActivityId(), ""),
                Objects.toString(source.getLegacyBargainActivityId(), ""),
                Objects.toString(source.getLegacyCombinationActivityId(), ""),
                Objects.toString(source.getLegacyPointActivityId(), "")));
    }

    private static String genericIdentityStatus(LegacyTradeOrderAssessmentSourceDO source) {
        int count = genericReferenceCount(source);
        if (count == 0) return "MISSING_SOURCE_REFERENCE";
        if (count > 1) return "AMBIGUOUS_SOURCE_REFERENCE";
        return "SOURCE_REFERENCE_WITHOUT_VERSION";
    }

    private static String genericSourceReference(LegacyTradeOrderAssessmentSourceDO source) {
        if (genericReferenceCount(source) != 1) return null;
        if (positive(source.getLegacySeckillActivityId())) return "SECKILL:" + source.getLegacySeckillActivityId();
        if (positive(source.getLegacyBargainActivityId())) return "BARGAIN:" + source.getLegacyBargainActivityId();
        if (positive(source.getLegacyCombinationActivityId())) return "COMBINATION:" + source.getLegacyCombinationActivityId();
        return "POINT_ACTIVITY:" + source.getLegacyPointActivityId();
    }

    private static int genericReferenceCount(LegacyTradeOrderAssessmentSourceDO source) {
        return (positive(source.getLegacySeckillActivityId()) ? 1 : 0)
                + (positive(source.getLegacyBargainActivityId()) ? 1 : 0)
                + (positive(source.getLegacyCombinationActivityId()) ? 1 : 0)
                + (positive(source.getLegacyPointActivityId()) ? 1 : 0);
    }

    private static boolean positive(Long value) {
        return value != null && value > 0;
    }

    private static String positiveReference(String prefix, Long value) {
        return positive(value) ? prefix + ":" + value : null;
    }

    private static int count(List<LegacyTradeBenefitMigrationCandidateDO> candidates, String status) {
        return (int) candidates.stream().filter(value -> status.equals(value.getAssessmentStatus())).count();
    }

    private static void normalizeSource(LegacyTradeOrderAssessmentSourceDO source) {
        require(source != null && source.getLegacyOrderId() != null && source.getTenantId() != null,
                "legacy Trade source identity is missing");
        require(source.getLegacyOrderNo() != null && !source.getLegacyOrderNo().isBlank()
                        && source.getLegacyOrderNo().length() <= 64,
                "legacy Trade order number is missing or too long");
        require(source.getSourceUpdatedAt() != null, "legacy Trade source watermark is missing");
        Object[] required = {source.getDeleted(), source.getHeaderQuantity(), source.getItemRowCount(),
                source.getItemQuantity(), source.getHeaderGrossAmountMinor(),
                source.getHeaderGenericDiscountAmountMinor(), source.getHeaderCouponAmountMinor(),
                source.getHeaderPointAmountMinor(), source.getHeaderVipAmountMinor(),
                source.getHeaderDeliveryAmountMinor(), source.getHeaderAdjustAmountMinor(),
                source.getHeaderPayAmountMinor(), source.getItemGrossAmountMinor(),
                source.getItemGenericDiscountAmountMinor(), source.getItemCouponAmountMinor(),
                source.getItemPointAmountMinor(), source.getItemVipAmountMinor(),
                source.getItemDeliveryAmountMinor(), source.getItemAdjustAmountMinor(),
                source.getItemPayAmountMinor(), source.getInvalidItemMoneyCount()};
        require(Arrays.stream(required).noneMatch(Objects::isNull),
                "legacy Trade source money evidence is incomplete");
    }

    private static LegacyTradeBenefitAssessmentCommand normalize(LegacyTradeBenefitAssessmentCommand command) {
        require(command != null, "legacy Trade benefit assessment command is required");
        requireText(command.getIdempotencyKey(), "idempotencyKey", 128);
        if (command.getSourceEventId() != null) requireText(command.getSourceEventId(), "sourceEventId", 128);
        command.setMigrationRunId(requireUuid(command.getMigrationRunId(), "migrationRunId"));
        requireText(command.getPolicyVersion(), "policyVersion", 32);
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
