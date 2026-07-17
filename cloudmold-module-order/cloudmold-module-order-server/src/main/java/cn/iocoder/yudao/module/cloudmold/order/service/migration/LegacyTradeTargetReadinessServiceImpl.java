package cn.iocoder.yudao.module.cloudmold.order.service.migration;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.order.api.migration.*;
import cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.migration.*;
import cn.iocoder.yudao.module.cloudmold.order.dal.mysql.migration.LegacyTradeTargetReadinessMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LegacyTradeTargetReadinessServiceImpl implements LegacyTradeTargetReadinessApi {

    static final String READINESS_EVENT = "order.migration.legacy_trade_target_readiness_assessed";
    static final String POLICY_VERSION = "legacy-trade-target-readiness-v2";
    private static final int OPERATION_SUCCEEDED = 10;

    private final LegacyTradeTargetReadinessMapper mapper;
    private final OutboxAppender outboxAppender;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LegacyTradeTargetReadinessResult assess(LegacyTradeTargetReadinessCommand rawCommand) {
        LegacyTradeTargetReadinessCommand command = normalize(rawCommand);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        String requestHash = DigestUtil.sha256Hex(tenantId + "|" + JsonUtils.toJsonString(command));
        String attemptToken = UUID.randomUUID().toString();
        mapper.insertOrResolveOperation(tenantId, command.getIdempotencyKey(), command.getSourceEventId(),
                requestHash, attemptToken, now);
        Long operationId = mapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve target-readiness operation");
        LegacyTradeTargetReadinessOperationDO operation = mapper.selectOperationForUpdate(tenantId, operationId);
        require(operation != null, "target-readiness operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(requestHash, operation.getRequestHash()),
                    "idempotency key or source event conflicts with another target-readiness assessment");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing target-readiness assessment is not complete");
            LegacyTradeTargetReadinessResult replay = JsonUtils.parseObject(
                    operation.getResultJson(), LegacyTradeTargetReadinessResult.class);
            replay.setDuplicate(true);
            return replay;
        }

        LegacyTradeBenefitMigrationRunDO sourceRun = mapper.selectSourceRun(tenantId,
                command.getSourceMigrationRunId());
        require(sourceRun != null, "source legacy Trade assessment run does not exist");
        require("legacy-trade-benefit-v4".equals(sourceRun.getPolicyVersion())
                        && Boolean.TRUE.equals(sourceRun.getItemEvidenceComplete()),
                "target readiness requires a complete immutable buyer-lineage v4 source assessment");
        List<LegacyTradeTargetReadinessOrderSourceDO> sourceOrders = mapper.selectSourceOrders(
                tenantId, command.getSourceMigrationRunId());
        List<LegacyTradeTargetReadinessItemSourceDO> sourceItems = mapper.selectSourceItems(
                tenantId, command.getSourceMigrationRunId());
        require(sourceOrders != null && !sourceOrders.isEmpty(),
                "target readiness requires the complete non-empty source Order denominator");
        require(sourceItems != null && !sourceItems.isEmpty(),
                "target readiness requires the complete non-empty source Order Item denominator");

        Map<Long, String> orderReadinessIds = sourceOrders.stream().collect(Collectors.toMap(
                LegacyTradeTargetReadinessOrderSourceDO::getLegacyOrderId,
                source -> deterministicUuid(command.getTargetReadinessRunId()
                        + "|order|" + source.getLegacyOrderId())));
        List<LegacyTradeTargetReadinessItemDO> items = sourceItems.stream()
                .map(source -> assessItem(tenantId, command, source,
                        requireOrderReadinessId(orderReadinessIds, source.getLegacyOrderId()), now))
                .toList();
        Map<Long, List<LegacyTradeTargetReadinessItemDO>> itemsByOrder = items.stream()
                .collect(Collectors.groupingBy(LegacyTradeTargetReadinessItemDO::getLegacyOrderId));
        List<LegacyTradeTargetReadinessOrderDO> orders = sourceOrders.stream()
                .map(source -> assessOrder(tenantId, command, source,
                        itemsByOrder.getOrDefault(source.getLegacyOrderId(), List.of()), now))
                .toList();
        require(orders.size() == sourceRun.getSourceOrderCount(),
                "target-readiness Order denominator differs from the immutable source run");
        require(items.size() == sourceRun.getSourceItemCount(),
                "target-readiness Order Item denominator differs from the immutable source run");

        int excludedOrderCount = (int) orders.stream()
                .filter(value -> "EXCLUDED".equals(value.getMappingReadinessStatus())).count();
        int activeOrderCount = orders.size() - excludedOrderCount;
        int excludedItemCount = (int) items.stream()
                .filter(value -> "EXCLUDED".equals(value.getMappingReadinessStatus())).count();
        int activeItemCount = items.size() - excludedItemCount;
        int admittedOrderCount = (int) orders.stream()
                .filter(value -> Boolean.TRUE.equals(value.getMappingAdmissionAllowed())).count();
        int buyerResolvedCount = countActiveOrders(orders, value -> "RESOLVED".equals(value.getBuyerIdentityStatus()));
        int orderMappingCount = countActiveOrders(orders, value -> "QUALIFIED".equals(value.getOrderMappingStatus()));
        int lifecycleMappingCount = countActiveOrders(orders,
                value -> "QUALIFIED".equals(value.getLifecycleMappingStatus()));
        int fullyMappedItemCount = (int) items.stream()
                .filter(value -> "READY".equals(value.getMappingReadinessStatus())).count();
        String evidenceHash = DigestUtil.sha256Hex(
                java.util.stream.Stream.concat(orders.stream().map(LegacyTradeTargetReadinessOrderDO::getEvidenceHash),
                                items.stream().map(LegacyTradeTargetReadinessItemDO::getEvidenceHash))
                        .sorted().reduce("", (left, right) -> left + "\n" + right));
        LegacyTradeTargetReadinessRunDO run = new LegacyTradeTargetReadinessRunDO()
                .setTargetReadinessRunId(command.getTargetReadinessRunId()).setTenantId(tenantId)
                .setSourceMigrationRunId(command.getSourceMigrationRunId())
                .setPolicyVersion(command.getPolicyVersion()).setEvidenceRef(command.getEvidenceRef())
                .setTargetMappingEvidenceHash(evidenceHash).setSourceOrderCount(orders.size())
                .setActiveOrderCount(activeOrderCount).setExcludedOrderCount(excludedOrderCount)
                .setSourceItemCount(items.size()).setActiveItemCount(activeItemCount)
                .setExcludedItemCount(excludedItemCount).setBuyerResolvedOrderCount(buyerResolvedCount)
                .setOrderMappingQualifiedCount(orderMappingCount)
                .setLifecycleMappingQualifiedCount(lifecycleMappingCount)
                .setFullyMappedItemCount(fullyMappedItemCount).setMappingAdmittedOrderCount(admittedOrderCount)
                .setMappingBlockedOrderCount(activeOrderCount - admittedOrderCount)
                .setCanonicalImportAllowedOrderCount(0).setProductionMigrationEnabled(false)
                .setStatus("BLOCKED_REQUIRES_EXPLICIT_TARGET_MAPPINGS").setVersion(1L)
                .setAssessedAt(now).setCreatedAt(now).setUpdatedAt(now);

        require(mapper.insertRun(run) == 1, "failed to persist target-readiness run");
        for (LegacyTradeTargetReadinessOrderDO order : orders) {
            require(mapper.insertOrder(order) == 1, "failed to persist target-readiness Order evidence");
        }
        for (LegacyTradeTargetReadinessItemDO item : items) {
            require(mapper.insertItem(item) == 1, "failed to persist target-readiness Order Item evidence");
        }
        for (LegacyTradeTargetReadinessOrderDO order : orders) {
            appendAssessmentEvent(tenantId, command, run, order,
                    itemsByOrder.getOrDefault(order.getLegacyOrderId(), List.of()));
        }
        LegacyTradeTargetReadinessResult result = toResult(run).setOperationId(operationId);
        require(mapper.markOperationSucceeded(tenantId, operationId, run.getTargetReadinessRunId(),
                JsonUtils.toJsonString(result), now) == 1, "target-readiness operation completion conflict");
        return result;
    }

    @Override
    public LegacyTradeTargetReadinessResult requireRun(String targetReadinessRunId) {
        String runId = requireUuid(targetReadinessRunId, "targetReadinessRunId");
        LegacyTradeTargetReadinessRunDO run = mapper.selectRun(TenantContextHolder.getRequiredTenantId(), runId);
        require(run != null, "target-readiness run does not exist");
        return toResult(run);
    }

    @Override
    public List<LegacyTradeTargetReadinessOrderView> listOrders(String targetReadinessRunId) {
        String runId = requireUuid(targetReadinessRunId, "targetReadinessRunId");
        List<LegacyTradeTargetReadinessOrderDO> rows = mapper.selectOrders(
                TenantContextHolder.getRequiredTenantId(), runId);
        require(rows != null && !rows.isEmpty(), "target-readiness Order evidence does not exist");
        return rows.stream().map(LegacyTradeTargetReadinessServiceImpl::toOrderView).toList();
    }

    @Override
    public List<LegacyTradeTargetReadinessItemView> listItems(String targetReadinessRunId) {
        String runId = requireUuid(targetReadinessRunId, "targetReadinessRunId");
        List<LegacyTradeTargetReadinessItemDO> rows = mapper.selectItems(
                TenantContextHolder.getRequiredTenantId(), runId);
        require(rows != null && !rows.isEmpty(), "target-readiness Order Item evidence does not exist");
        return rows.stream().map(LegacyTradeTargetReadinessServiceImpl::toItemView).toList();
    }

    static LegacyTradeTargetReadinessItemDO assessItem(Long tenantId, LegacyTradeTargetReadinessCommand command,
                                                        LegacyTradeTargetReadinessItemSourceDO source,
                                                        String orderReadinessId, LocalDateTime now) {
        require(Objects.equals(tenantId, source.getTenantId()), "target-readiness item tenant mismatch");
        String spuStatus = resolutionStatus(source.getSpuMappingCount());
        String skuStatus = resolutionStatus(source.getSkuMappingCount());
        String itemPlanStatus = resolutionStatus(source.getOrderItemMappingCount());
        boolean excluded = Boolean.TRUE.equals(source.getDeleted()) || Boolean.TRUE.equals(source.getOrderDeleted());
        boolean productIdentityExact = Objects.equals(source.getProductIdentityQualificationCount(), 1)
                && Objects.equals(source.getLegacyOrderItemId(), source.getQualifiedLegacyOrderItemId())
                && Objects.equals(source.getLegacySpuId(), source.getHistoricalSpuId())
                && Objects.equals(source.getLegacySkuId(), source.getHistoricalSkuId())
                && Objects.equals(source.getLegacyItemSnapshotHash(), source.getProductIdentitySourceItemEvidenceHash())
                && source.getHistoricalProductSnapshotHash() != null
                && source.getHistoricalProductSnapshotHash().matches("[0-9a-f]{64}");
        String historicalProductIdentityStatus = productIdentityExact ? "QUALIFIED"
                : (Objects.equals(source.getProductIdentityQualificationCount(), 0)
                || source.getProductIdentityQualificationCount() == null) ? "MISSING" : "AMBIGUOUS";
        boolean moneyExact = source.getItemQuantity() != null && source.getItemQuantity() > 0
                && source.getUnitPriceMinor() != null && source.getUnitPriceMinor() >= 0
                && source.getGrossAmountMinor() != null
                && source.getGrossAmountMinor() == source.getUnitPriceMinor() * source.getItemQuantity()
                && nonnegative(source.getGenericDiscountAmountMinor(), source.getCouponAmountMinor(),
                source.getPointAmountMinor(), source.getVipAmountMinor(), source.getDeliveryAmountMinor(),
                source.getPayAmountMinor())
                && source.getAdjustAmountMinor() != null
                && source.getGrossAmountMinor() + source.getDeliveryAmountMinor() + source.getAdjustAmountMinor()
                == source.getGenericDiscountAmountMinor() + source.getCouponAmountMinor()
                + source.getPointAmountMinor() + source.getVipAmountMinor() + source.getPayAmountMinor();
        List<String> blockers = new ArrayList<>();
        if (excluded) blockers.add("SOURCE_DELETED");
        if (!"SOURCE_IDS_PRESENT".equals(source.getSourceProductIdentityStatus())) {
            blockers.add("SOURCE_PRODUCT_IDS_MISSING");
        }
        if (!excluded && !productIdentityExact) {
            blockers.add("MISSING".equals(historicalProductIdentityStatus)
                    ? "HISTORICAL_PRODUCT_IDENTITY_MISSING"
                    : "HISTORICAL_PRODUCT_IDENTITY_AMBIGUOUS_OR_INVALID");
        }
        addResolutionBlocker(blockers, "SPU_MAPPING", spuStatus);
        addResolutionBlocker(blockers, "SKU_MAPPING", skuStatus);
        addResolutionBlocker(blockers, "ORDER_ITEM_MAPPING", itemPlanStatus);
        if ("QUALIFIED".equals(spuStatus) && "QUALIFIED".equals(skuStatus)
                && !Objects.equals(source.getCanonicalSpuId(), source.getCanonicalSkuSpuId())) {
            blockers.add("CATALOG_SPU_SKU_RELATION_MISMATCH");
        }
        if ("QUALIFIED".equals(itemPlanStatus)
                && !Objects.equals(source.getLegacyItemSnapshotHash(), source.getOrderItemMappingSourceSnapshotHash())) {
            blockers.add("ORDER_ITEM_MAPPING_SOURCE_SNAPSHOT_MISMATCH");
        }
        if (!moneyExact && !excluded) blockers.add("ITEM_MONEY_NOT_EXACT");
        blockers = blockers.stream().distinct().sorted().toList();
        boolean ready = !excluded && blockers.isEmpty();
        String readiness = excluded ? "EXCLUDED" : ready ? "READY" : "BLOCKED";
        String evidenceHash = DigestUtil.sha256Hex(String.join("\u001f",
                Objects.toString(source.getLegacyItemSnapshotHash(), ""), spuStatus,
                historicalProductIdentityStatus,
                Objects.toString(source.getProductIdentityQualificationId(), ""),
                Objects.toString(source.getHistoricalProductSnapshotHash(), ""),
                Objects.toString(source.getSpuMappingId(), ""), Objects.toString(source.getSpuMappingVersion(), ""),
                skuStatus, Objects.toString(source.getSkuMappingId(), ""),
                Objects.toString(source.getSkuMappingVersion(), ""), itemPlanStatus,
                Objects.toString(source.getOrderItemMappingPlanId(), ""),
                Objects.toString(source.getOrderItemMappingVersion(), ""),
                moneyExact ? "EXACT" : excluded ? "EXCLUDED" : "INVALID",
                JsonUtils.toJsonString(blockers)));
        return new LegacyTradeTargetReadinessItemDO()
                .setItemReadinessId(deterministicUuid(command.getTargetReadinessRunId()
                        + "|item|" + source.getLegacyOrderItemId()))
                .setTenantId(tenantId).setTargetReadinessRunId(command.getTargetReadinessRunId())
                .setOrderReadinessId(orderReadinessId).setLegacyOrderId(source.getLegacyOrderId())
                .setLegacyOrderItemId(source.getLegacyOrderItemId()).setItemEvidenceId(source.getItemEvidenceId())
                .setLegacyItemSnapshotHash(source.getLegacyItemSnapshotHash())
                .setProductIdentityQualificationId(productIdentityExact
                        ? source.getProductIdentityQualificationId() : null)
                .setHistoricalProductIdentityStatus(historicalProductIdentityStatus)
                .setSpuMappingId("QUALIFIED".equals(spuStatus) ? source.getSpuMappingId() : null)
                .setCanonicalSpuId("QUALIFIED".equals(spuStatus) ? source.getCanonicalSpuId() : null)
                .setSpuMappingVersion("QUALIFIED".equals(spuStatus) ? source.getSpuMappingVersion() : null)
                .setSpuMappingStatus(spuStatus)
                .setSkuMappingId("QUALIFIED".equals(skuStatus) ? source.getSkuMappingId() : null)
                .setCanonicalSkuId("QUALIFIED".equals(skuStatus) ? source.getCanonicalSkuId() : null)
                .setSkuMappingVersion("QUALIFIED".equals(skuStatus) ? source.getSkuMappingVersion() : null)
                .setSkuMappingStatus(skuStatus)
                .setOrderItemMappingPlanId("QUALIFIED".equals(itemPlanStatus)
                        ? source.getOrderItemMappingPlanId() : null)
                .setPlannedOrderItemId("QUALIFIED".equals(itemPlanStatus)
                        ? source.getPlannedOrderItemId() : null)
                .setPlannedOrderId("QUALIFIED".equals(itemPlanStatus) ? source.getPlannedOrderId() : null)
                .setOrderItemMappingVersion("QUALIFIED".equals(itemPlanStatus)
                        ? source.getOrderItemMappingVersion() : null)
                .setOrderItemMappingStatus(itemPlanStatus)
                .setMoneyReconciliationStatus(excluded ? "EXCLUDED" : moneyExact ? "EXACT" : "INVALID")
                .setMappingReadinessStatus(readiness).setBlockerCodes(JsonUtils.toJsonString(blockers))
                .setMappingAdmissionAllowed(ready).setCanonicalImportAllowed(false).setEvidenceHash(evidenceHash)
                .setVersion(1L).setAssessedAt(now).setCreatedAt(now).setUpdatedAt(now);
    }

    static LegacyTradeTargetReadinessOrderDO assessOrder(Long tenantId, LegacyTradeTargetReadinessCommand command,
                                                          LegacyTradeTargetReadinessOrderSourceDO source,
                                                          List<LegacyTradeTargetReadinessItemDO> items,
                                                          LocalDateTime now) {
        require(Objects.equals(tenantId, source.getTenantId()), "target-readiness order tenant mismatch");
        String orderMappingStatus = resolutionStatus(source.getOrderMappingCount());
        String lifecycleStatus = resolutionStatus(source.getLifecycleMappingCount());
        boolean excluded = Boolean.TRUE.equals(source.getDeleted());
        boolean moneyExact = !Boolean.TRUE.equals(source.getNegativeMoney())
                && !Boolean.TRUE.equals(source.getHeaderMoneyMismatch())
                && !Boolean.TRUE.equals(source.getHeaderItemMismatch())
                && Objects.equals(source.getInvalidItemMoneyCount(), 0);
        List<LegacyTradeTargetReadinessItemDO> activeItems = items.stream()
                .filter(item -> !"EXCLUDED".equals(item.getMappingReadinessStatus())).toList();
        int fullyMappedItems = (int) activeItems.stream()
                .filter(item -> "READY".equals(item.getMappingReadinessStatus())).count();
        List<String> blockers = new ArrayList<>();
        if (excluded) blockers.add("SOURCE_DELETED");
        addResolutionBlocker(blockers, "BUYER_IDENTITY", source.getBuyerIdentityStatus());
        addResolutionBlocker(blockers, "ORDER_MAPPING", orderMappingStatus);
        addResolutionBlocker(blockers, "LIFECYCLE_MAPPING", lifecycleStatus);
        if ("QUALIFIED".equals(orderMappingStatus)
                && !Objects.equals(source.getLegacySnapshotHash(), source.getOrderMappingSourceSnapshotHash())) {
            blockers.add("ORDER_MAPPING_SOURCE_SNAPSHOT_MISMATCH");
        }
        if (!moneyExact && !excluded) blockers.add("ORDER_MONEY_NOT_EXACT");
        if (!excluded && activeItems.isEmpty()) blockers.add("ACTIVE_ORDER_ITEM_MISSING");
        if (fullyMappedItems != activeItems.size()) blockers.add("ORDER_ITEM_TARGET_MAPPING_INCOMPLETE");
        if ("QUALIFIED".equals(orderMappingStatus) && activeItems.stream()
                .filter(item -> "QUALIFIED".equals(item.getOrderItemMappingStatus()))
                .anyMatch(item -> !Objects.equals(source.getPlannedOrderId(), item.getPlannedOrderId()))) {
            blockers.add("ORDER_ITEM_PLANNED_ORDER_MISMATCH");
        }
        blockers = blockers.stream().distinct().sorted().toList();
        boolean ready = !excluded && blockers.isEmpty();
        String readiness = excluded ? "EXCLUDED" : ready ? "READY" : "BLOCKED";
        String evidenceHash = DigestUtil.sha256Hex(String.join("\u001f",
                source.getLegacySnapshotHash(), Objects.toString(source.getBuyerIdentityStatus(), ""),
                Objects.toString(source.getBuyerIdentityVersion(), ""), orderMappingStatus,
                Objects.toString(source.getOrderMappingPlanId(), ""),
                Objects.toString(source.getOrderMappingVersion(), ""), lifecycleStatus,
                Objects.toString(source.getStatusMappingId(), ""),
                Objects.toString(source.getLifecycleMappingVersion(), ""),
                moneyExact ? "EXACT" : excluded ? "EXCLUDED" : "INVALID",
                Integer.toString(activeItems.size()), Integer.toString(fullyMappedItems),
                items.stream().map(LegacyTradeTargetReadinessItemDO::getEvidenceHash).sorted()
                        .reduce("", (left, right) -> left + "\n" + right),
                JsonUtils.toJsonString(blockers)));
        return new LegacyTradeTargetReadinessOrderDO()
                .setOrderReadinessId(deterministicUuid(command.getTargetReadinessRunId()
                        + "|order|" + source.getLegacyOrderId()))
                .setTenantId(tenantId).setTargetReadinessRunId(command.getTargetReadinessRunId())
                .setSourceMigrationRunId(command.getSourceMigrationRunId()).setCandidateId(source.getCandidateId())
                .setLegacyOrderId(source.getLegacyOrderId()).setLegacySnapshotHash(source.getLegacySnapshotHash())
                .setBuyerIdentityStatus(source.getBuyerIdentityStatus())
                .setBuyerSourceIdentityId(source.getBuyerSourceIdentityId())
                .setBuyerPrincipalId(source.getBuyerPrincipalId()).setBuyerIdentityVersion(source.getBuyerIdentityVersion())
                .setOrderMappingPlanId("QUALIFIED".equals(orderMappingStatus) ? source.getOrderMappingPlanId() : null)
                .setPlannedOrderId("QUALIFIED".equals(orderMappingStatus) ? source.getPlannedOrderId() : null)
                .setOrderMappingVersion("QUALIFIED".equals(orderMappingStatus) ? source.getOrderMappingVersion() : null)
                .setOrderMappingStatus(orderMappingStatus)
                .setStatusMappingId("QUALIFIED".equals(lifecycleStatus) ? source.getStatusMappingId() : null)
                .setCanonicalOrderStatus("QUALIFIED".equals(lifecycleStatus)
                        ? source.getCanonicalOrderStatus() : null)
                .setLifecycleMappingVersion("QUALIFIED".equals(lifecycleStatus)
                        ? source.getLifecycleMappingVersion() : null)
                .setLifecycleMappingStatus(lifecycleStatus)
                .setMoneyReconciliationStatus(excluded ? "EXCLUDED" : moneyExact ? "EXACT" : "INVALID")
                .setActiveItemCount(activeItems.size()).setFullyMappedItemCount(fullyMappedItems)
                .setMappingReadinessStatus(readiness).setBlockerCodes(JsonUtils.toJsonString(blockers))
                .setMappingAdmissionAllowed(ready).setCanonicalImportAllowed(false).setEvidenceHash(evidenceHash)
                .setVersion(1L).setAssessedAt(now).setCreatedAt(now).setUpdatedAt(now);
    }

    private void appendAssessmentEvent(Long tenantId, LegacyTradeTargetReadinessCommand command,
                                       LegacyTradeTargetReadinessRunDO run,
                                       LegacyTradeTargetReadinessOrderDO order,
                                       List<LegacyTradeTargetReadinessItemDO> items) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("target_readiness_run_id", run.getTargetReadinessRunId());
        payload.put("source_migration_run_id", run.getSourceMigrationRunId());
        payload.put("order_readiness_id", order.getOrderReadinessId());
        payload.put("candidate_id", order.getCandidateId());
        payload.put("legacy_order_id", order.getLegacyOrderId());
        payload.put("buyer_identity_status", order.getBuyerIdentityStatus());
        payload.put("buyer_principal_id", order.getBuyerPrincipalId());
        payload.put("order_mapping_plan_id", order.getOrderMappingPlanId());
        payload.put("planned_order_id", order.getPlannedOrderId());
        payload.put("order_mapping_status", order.getOrderMappingStatus());
        payload.put("canonical_order_status", order.getCanonicalOrderStatus());
        payload.put("lifecycle_mapping_status", order.getLifecycleMappingStatus());
        payload.put("money_reconciliation_status", order.getMoneyReconciliationStatus());
        payload.put("active_item_count", order.getActiveItemCount());
        payload.put("fully_mapped_item_count", order.getFullyMappedItemCount());
        payload.put("mapping_readiness_status", order.getMappingReadinessStatus());
        payload.put("blocker_codes", JsonUtils.parseArray(order.getBlockerCodes(), String.class));
        payload.put("mapping_admission_allowed", order.getMappingAdmissionAllowed());
        payload.put("canonical_import_allowed", false);
        payload.put("evidence_hash", order.getEvidenceHash());
        payload.put("items", items.stream().map(item -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("item_readiness_id", item.getItemReadinessId());
            value.put("legacy_order_item_id", item.getLegacyOrderItemId());
            value.put("product_identity_qualification_id", item.getProductIdentityQualificationId());
            value.put("historical_product_identity_status", item.getHistoricalProductIdentityStatus());
            value.put("spu_mapping_status", item.getSpuMappingStatus());
            value.put("canonical_spu_id", item.getCanonicalSpuId());
            value.put("sku_mapping_status", item.getSkuMappingStatus());
            value.put("canonical_sku_id", item.getCanonicalSkuId());
            value.put("order_item_mapping_status", item.getOrderItemMappingStatus());
            value.put("planned_order_item_id", item.getPlannedOrderItemId());
            value.put("planned_order_id", item.getPlannedOrderId());
            value.put("money_reconciliation_status", item.getMoneyReconciliationStatus());
            value.put("mapping_readiness_status", item.getMappingReadinessStatus());
            value.put("blocker_codes", JsonUtils.parseArray(item.getBlockerCodes(), String.class));
            value.put("mapping_admission_allowed", item.getMappingAdmissionAllowed());
            value.put("canonical_import_allowed", false);
            value.put("evidence_hash", item.getEvidenceHash());
            return value;
        }).toList());
        payload.put("run_target_mapping_evidence_hash", run.getTargetMappingEvidenceHash());
        payload.put("policy_version", run.getPolicyVersion());
        payload.put("verification_ref", run.getEvidenceRef());
        payload.put("assessed_at", order.getAssessedAt().toInstant(ZoneOffset.UTC).toString());
        String idempotency = command.getIdempotencyKey() + ":order:" + order.getLegacyOrderId();
        outboxAppender.append(AppendDomainEventCommand.builder()
                .eventId(deterministicUuid(tenantId + "|" + idempotency))
                .eventType(READINESS_EVENT).schemaVersion(2).sourceSystem("cloudmold-order")
                .tenantId(tenantId).aggregateType("legacy_trade_target_readiness")
                .aggregateId(order.getOrderReadinessId()).aggregateVersion(1L).eventSequence((short) 1)
                .occurredAt(command.getOccurredAt()).correlationId(command.getCorrelationId())
                .causationId(command.getCausationId()).idempotencyKey(idempotency).payload(payload)
                .headers(Map.of("target_readiness_run_id", run.getTargetReadinessRunId(),
                        "source_migration_run_id", run.getSourceMigrationRunId(),
                        "target_mapping_evidence_hash", run.getTargetMappingEvidenceHash()))
                .destination("lakehouse").build());
    }

    private static LegacyTradeTargetReadinessCommand normalize(LegacyTradeTargetReadinessCommand raw) {
        require(raw != null, "target-readiness command is required");
        return new LegacyTradeTargetReadinessCommand()
                .setIdempotencyKey(requireText(raw.getIdempotencyKey(), "idempotencyKey", 128))
                .setSourceEventId(optionalText(raw.getSourceEventId(), "sourceEventId", 128))
                .setTargetReadinessRunId(requireUuid(raw.getTargetReadinessRunId(), "targetReadinessRunId"))
                .setSourceMigrationRunId(requireUuid(raw.getSourceMigrationRunId(), "sourceMigrationRunId"))
                .setPolicyVersion(requirePolicy(raw.getPolicyVersion()))
                .setEvidenceRef(requireText(raw.getEvidenceRef(), "evidenceRef", 256))
                .setCorrelationId(requireUuid(raw.getCorrelationId(), "correlationId"))
                .setCausationId(optionalUuid(raw.getCausationId(), "causationId"))
                .setOccurredAt(Objects.requireNonNull(raw.getOccurredAt(), "occurredAt is required"));
    }

    private static String requirePolicy(String value) {
        String policy = requireText(value, "policyVersion", 64);
        require(POLICY_VERSION.equals(policy), "new target-readiness assessments require policy v2");
        return policy;
    }

    private static LegacyTradeTargetReadinessResult toResult(LegacyTradeTargetReadinessRunDO run) {
        return new LegacyTradeTargetReadinessResult()
                .setTargetReadinessRunId(run.getTargetReadinessRunId())
                .setSourceMigrationRunId(run.getSourceMigrationRunId())
                .setTargetMappingEvidenceHash(run.getTargetMappingEvidenceHash())
                .setSourceOrderCount(run.getSourceOrderCount()).setActiveOrderCount(run.getActiveOrderCount())
                .setExcludedOrderCount(run.getExcludedOrderCount()).setSourceItemCount(run.getSourceItemCount())
                .setActiveItemCount(run.getActiveItemCount()).setExcludedItemCount(run.getExcludedItemCount())
                .setBuyerResolvedOrderCount(run.getBuyerResolvedOrderCount())
                .setOrderMappingQualifiedCount(run.getOrderMappingQualifiedCount())
                .setLifecycleMappingQualifiedCount(run.getLifecycleMappingQualifiedCount())
                .setFullyMappedItemCount(run.getFullyMappedItemCount())
                .setMappingAdmittedOrderCount(run.getMappingAdmittedOrderCount())
                .setMappingBlockedOrderCount(run.getMappingBlockedOrderCount())
                .setCanonicalImportAllowedOrderCount(run.getCanonicalImportAllowedOrderCount())
                .setProductionMigrationEnabled(run.getProductionMigrationEnabled()).setStatus(run.getStatus());
    }

    private static LegacyTradeTargetReadinessOrderView toOrderView(LegacyTradeTargetReadinessOrderDO value) {
        return new LegacyTradeTargetReadinessOrderView().setOrderReadinessId(value.getOrderReadinessId())
                .setTargetReadinessRunId(value.getTargetReadinessRunId())
                .setSourceMigrationRunId(value.getSourceMigrationRunId()).setCandidateId(value.getCandidateId())
                .setLegacyOrderId(value.getLegacyOrderId()).setBuyerIdentityStatus(value.getBuyerIdentityStatus())
                .setBuyerPrincipalId(value.getBuyerPrincipalId()).setOrderMappingPlanId(value.getOrderMappingPlanId())
                .setPlannedOrderId(value.getPlannedOrderId()).setOrderMappingStatus(value.getOrderMappingStatus())
                .setCanonicalOrderStatus(value.getCanonicalOrderStatus())
                .setLifecycleMappingStatus(value.getLifecycleMappingStatus())
                .setMoneyReconciliationStatus(value.getMoneyReconciliationStatus())
                .setActiveItemCount(value.getActiveItemCount()).setFullyMappedItemCount(value.getFullyMappedItemCount())
                .setMappingReadinessStatus(value.getMappingReadinessStatus())
                .setBlockerCodes(JsonUtils.parseArray(value.getBlockerCodes(), String.class))
                .setMappingAdmissionAllowed(value.getMappingAdmissionAllowed())
                .setCanonicalImportAllowed(value.getCanonicalImportAllowed()).setEvidenceHash(value.getEvidenceHash())
                .setAssessedAt(value.getAssessedAt().toInstant(ZoneOffset.UTC));
    }

    private static LegacyTradeTargetReadinessItemView toItemView(LegacyTradeTargetReadinessItemDO value) {
        return new LegacyTradeTargetReadinessItemView().setItemReadinessId(value.getItemReadinessId())
                .setTargetReadinessRunId(value.getTargetReadinessRunId())
                .setOrderReadinessId(value.getOrderReadinessId()).setLegacyOrderId(value.getLegacyOrderId())
                .setLegacyOrderItemId(value.getLegacyOrderItemId()).setItemEvidenceId(value.getItemEvidenceId())
                .setProductIdentityQualificationId(value.getProductIdentityQualificationId())
                .setHistoricalProductIdentityStatus(value.getHistoricalProductIdentityStatus())
                .setSpuMappingStatus(value.getSpuMappingStatus()).setCanonicalSpuId(value.getCanonicalSpuId())
                .setSkuMappingStatus(value.getSkuMappingStatus()).setCanonicalSkuId(value.getCanonicalSkuId())
                .setOrderItemMappingStatus(value.getOrderItemMappingStatus())
                .setPlannedOrderItemId(value.getPlannedOrderItemId()).setPlannedOrderId(value.getPlannedOrderId())
                .setMoneyReconciliationStatus(value.getMoneyReconciliationStatus())
                .setMappingReadinessStatus(value.getMappingReadinessStatus())
                .setBlockerCodes(JsonUtils.parseArray(value.getBlockerCodes(), String.class))
                .setMappingAdmissionAllowed(value.getMappingAdmissionAllowed())
                .setCanonicalImportAllowed(value.getCanonicalImportAllowed()).setEvidenceHash(value.getEvidenceHash());
    }

    private static int countActiveOrders(List<LegacyTradeTargetReadinessOrderDO> orders,
                                         java.util.function.Predicate<LegacyTradeTargetReadinessOrderDO> predicate) {
        return (int) orders.stream().filter(value -> !"EXCLUDED".equals(value.getMappingReadinessStatus()))
                .filter(predicate).count();
    }

    private static String resolutionStatus(Integer count) {
        return count == null || count == 0 ? "MISSING" : count == 1 ? "QUALIFIED" : "AMBIGUOUS";
    }

    private static void addResolutionBlocker(List<String> blockers, String prefix, String status) {
        if (!"QUALIFIED".equals(status) && !"RESOLVED".equals(status)) {
            blockers.add(prefix + "_" + ("AMBIGUOUS".equals(status) ? "AMBIGUOUS" : "MISSING"));
        }
    }

    private static boolean nonnegative(Long... values) {
        return Arrays.stream(values).allMatch(value -> value != null && value >= 0);
    }

    private static String requireOrderReadinessId(Map<Long, String> ids, Long orderId) {
        String value = ids.get(orderId);
        require(value != null, "Order Item references an Order outside the target-readiness denominator");
        return value;
    }

    private static String requireText(String value, String name, int max) {
        require(value != null && !value.isBlank(), name + " is required");
        String normalized = value.trim();
        require(normalized.length() <= max, name + " exceeds " + max + " characters");
        return normalized;
    }

    private static String optionalText(String value, String name, int max) {
        return value == null ? null : requireText(value, name, max);
    }

    private static String requireUuid(String value, String name) {
        String normalized = requireText(value, name, 36).toLowerCase(Locale.ROOT);
        try {
            return UUID.fromString(normalized).toString();
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(name + " must be a UUID", ex);
        }
    }

    private static String optionalUuid(String value, String name) {
        return value == null ? null : requireUuid(value, name);
    }

    private static String deterministicUuid(String seed) {
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
