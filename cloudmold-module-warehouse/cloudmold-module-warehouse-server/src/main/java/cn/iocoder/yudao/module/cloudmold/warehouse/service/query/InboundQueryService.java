package cn.iocoder.yudao.module.cloudmold.warehouse.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.InboundQueryApi;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.InboundPutawayView;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.InboundReceiptProgressView;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.AsnDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.AsnLineDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.PutawayDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.PutawayLineDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.ReceiptDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.ReceiptLineDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.AsnLineMapper;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.AsnMapper;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.PutawayMapper;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.PutawayLineMapper;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.ReceiptLineMapper;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.ReceiptMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class InboundQueryService implements InboundQueryApi {

    private static final BigDecimal ZERO = new BigDecimal("0.000000");

    private final AsnMapper asnMapper;
    private final AsnLineMapper asnLineMapper;
    private final ReceiptMapper receiptMapper;
    private final ReceiptLineMapper receiptLineMapper;
    private final PutawayMapper putawayMapper;
    private final PutawayLineMapper putawayLineMapper;

    @Override
    public InboundStageView requireInboundStage(String procurementOrderId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        List<AsnDO> asns = asnMapper.selectByProcurementOrderId(tenantId, requireText(procurementOrderId, "procurementOrderId"));
        if (asns == null || asns.isEmpty()) {
            return new InboundStageView(procurementOrderId, null, null, null, null, null, null, false);
        }
        AsnDO asn = asns.get(asns.size() - 1);
        List<AsnLineDO> lines = new java.util.ArrayList<>();
        List<ReceiptDO> receipts = new java.util.ArrayList<>();
        for (AsnDO candidate : asns) {
            lines.addAll(asnLineMapper.selectByAsn(tenantId, candidate.getAsnId()));
            receipts.addAll(receiptMapper.selectByAsn(tenantId, candidate.getAsnId()));
        }
        ReceiptDO latestReceipt = receipts.isEmpty() ? null : receipts.get(receipts.size() - 1);
        boolean allReceived = !lines.isEmpty() && lines.stream()
                .allMatch(line -> "FULL_RECEIVED_PENDING_QUALITY".equals(line.getStatus()));
        boolean allPutAway = !receipts.isEmpty() && receipts.stream()
                .allMatch(receipt -> "PUTAWAY_COMPLETED".equals(receipt.getStatus()));
        if ("CANCELLED".equals(asn.getStatus())) {
            return new InboundStageView(procurementOrderId, asn.getAsnId(), asn.getStatus(),
                    latestReceipt == null ? null : latestReceipt.getReceiptId(),
                    latestReceipt == null ? null : latestReceipt.getStatus(),
                    null, "ASN cancelled", true);
        }
        if (allReceived && allPutAway) {
            return new InboundStageView(procurementOrderId, asn.getAsnId(), asn.getStatus(),
                    latestReceipt == null ? null : latestReceipt.getReceiptId(),
                    latestReceipt == null ? null : latestReceipt.getStatus(),
                    null, "Inbound completed", true);
        }
        if (latestReceipt != null && Set.of("PENDING_QUALITY", "PARTIAL_QUALITY_DECIDED",
                "QUALITY_ACCEPTED", "QUALITY_MIXED", "PARTIALLY_PUTAWAY").contains(latestReceipt.getStatus())) {
            return new InboundStageView(procurementOrderId, asn.getAsnId(), asn.getStatus(),
                    latestReceipt.getReceiptId(), latestReceipt.getStatus(),
                    "PUTAWAY_COMPLETED", "Waiting for putaway completion", false);
        }
        return new InboundStageView(procurementOrderId, asn.getAsnId(), asn.getStatus(),
                latestReceipt == null ? null : latestReceipt.getReceiptId(),
                latestReceipt == null ? null : latestReceipt.getStatus(),
                "WAREHOUSE_RECEIPT_COMPLETED", "Waiting for warehouse receipt completion", false);
    }

    @Override
    public InboundReceiptProgressView requireReceiptProgress(String procurementOrderId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        List<AsnDO> asns = asnMapper.selectByProcurementOrderId(tenantId, requireText(procurementOrderId, "procurementOrderId"));
        if (asns == null || asns.isEmpty()) {
            throw new IllegalArgumentException("procurement inbound ASN not found");
        }
        return toProgress(tenantId, asns);
    }

    public PageResult<InboundReceiptPageItem> getReceiptPage(InboundReceiptPageReqVO request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String keyword = trimToNull(request.getKeyword());
        String receiptId = trimToNull(request.getReceiptId());
        String receiptNo = trimToNull(request.getReceiptNo());
        String procurementOrderId = trimToNull(request.getProcurementOrderId());
        String supplierId = trimToNull(request.getSupplierId());
        String warehouseId = trimToNull(request.getWarehouseId());
        String status = trimToNull(request.getStatus());
        long total = receiptMapper.countPage(tenantId, keyword, receiptId, receiptNo, procurementOrderId,
                supplierId, warehouseId, status);
        if (total == 0) {
            return PageResult.empty();
        }
        long offset = (long) (request.getPageNo() - 1) * request.getPageSize();
        return new PageResult<>(receiptMapper.selectPage(tenantId, keyword, receiptId, receiptNo,
                procurementOrderId, supplierId, warehouseId, status, offset, request.getPageSize()), total);
    }

    public InboundReceiptProgressView.ReceiptView requireReceiptDetail(String receiptId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        ReceiptDO receipt = receiptMapper.selectCurrent(tenantId, requireText(receiptId, "receiptId"));
        if (receipt == null) {
            throw new IllegalArgumentException("procurement receipt not found");
        }
        InboundReceiptProgressView progress = requireReceiptProgress(receipt.getProcurementOrderId());
        return progress.getReceipts().stream()
                .filter(item -> item.getReceiptId().equals(receiptId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("procurement receipt not found"));
    }

    public PageResult<InboundPutawayPageItem> getPutawayPage(InboundPutawayPageReqVO request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String keyword = trimToNull(request.getKeyword());
        String receiptId = trimToNull(request.getReceiptId());
        String procurementOrderId = trimToNull(request.getProcurementOrderId());
        String warehouseId = trimToNull(request.getWarehouseId());
        String status = trimToNull(request.getStatus());
        long total = putawayMapper.countPage(tenantId, keyword, receiptId, procurementOrderId, warehouseId, status);
        if (total == 0) {
            return PageResult.empty();
        }
        long offset = (long) (request.getPageNo() - 1) * request.getPageSize();
        return new PageResult<>(putawayMapper.selectPage(tenantId, keyword, receiptId, procurementOrderId,
                warehouseId, status, offset, request.getPageSize()), total);
    }

    public InboundPutawayView requirePutawayDetail(String putawayId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        PutawayDO putaway = putawayMapper.selectCurrent(tenantId, requireText(putawayId, "putawayId"));
        if (putaway == null) {
            throw new IllegalArgumentException("procurement putaway not found");
        }
        ReceiptDO receipt = receiptMapper.selectCurrent(tenantId, putaway.getReceiptId());
        if (receipt == null) {
            throw new IllegalStateException("putaway receipt authority is missing");
        }
        List<PutawayLineDO> lines = putawayLineMapper.selectByPutaway(tenantId, putawayId);
        if (lines == null || lines.isEmpty()) {
            throw new IllegalStateException("putaway has no immutable lines");
        }
        return InboundPutawayView.builder()
                .putawayId(putaway.getPutawayId()).receiptId(putaway.getReceiptId())
                .receiptNo(receipt.getReceiptNo()).procurementOrderId(receipt.getProcurementOrderId())
                .supplierId(receipt.getSupplierId()).warehouseId(putaway.getWarehouseId())
                .status(putaway.getStatus()).version(putaway.getVersion())
                .createdAt(putaway.getCreatedAt()).updatedAt(putaway.getUpdatedAt())
                .lines(lines.stream().map(line -> InboundPutawayView.LineView.builder()
                        .putawayLineId(line.getPutawayLineId()).receiptLineId(line.getReceiptLineId())
                        .sourceLocationId(line.getSourceLocationId()).targetLocationId(line.getTargetLocationId())
                        .canonicalSkuId(line.getCanonicalSkuId()).ownerType(line.getOwnerType()).ownerId(line.getOwnerId())
                        .lotId(line.getLotId()).baseUomCode(line.getBaseUomCode())
                        .putawayQuantity(line.getPutawayQuantity())
                        .cumulativePutawayQuantity(line.getCumulativePutawayQuantity())
                        .status(line.getStatus()).version(line.getVersion())
                        .inventoryOperationId(stringValue(line.getInventoryOperationId()))
                        .inventoryLedgerTransactionId(stringValue(line.getInventoryLedgerTxId()))
                        .inventoryMovementGroupId(line.getInventoryMovementGroupId())
                        .inventoryTargetBalanceId(line.getInventoryTargetBalanceId())
                        .createdAt(line.getCreatedAt()).updatedAt(line.getUpdatedAt()).build()).toList())
                .build();
    }

    private InboundReceiptProgressView toProgress(Long tenantId, List<AsnDO> asns) {
        AsnDO latestAsn = asns.get(asns.size() - 1);
        List<AsnLineDO> lines = new java.util.ArrayList<>();
        List<ReceiptDO> receipts = new java.util.ArrayList<>();
        for (AsnDO asn : asns) {
            lines.addAll(asnLineMapper.selectByAsn(tenantId, asn.getAsnId()));
            receipts.addAll(receiptMapper.selectByAsn(tenantId, asn.getAsnId()));
        }
        InboundStageView stage = requireInboundStage(latestAsn.getProcurementOrderId());
        BigDecimal totalScheduled = lines.stream().map(AsnLineDO::getScheduledQuantity).reduce(ZERO, BigDecimal::add);
        BigDecimal totalReceived = lines.stream().map(AsnLineDO::getReceivedQuantity).reduce(ZERO, BigDecimal::add);
        BigDecimal totalPending = lines.stream().map(AsnLineDO::getPendingQualityQuantity).reduce(ZERO, BigDecimal::add);
        return InboundReceiptProgressView.builder()
                .procurementOrderId(latestAsn.getProcurementOrderId())
                .asnId(latestAsn.getAsnId()).asnNo(latestAsn.getAsnNo()).asnStatus(latestAsn.getStatus()).asnVersion(latestAsn.getVersion())
                .supplierId(latestAsn.getSupplierId()).warehouseId(latestAsn.getWarehouseId())
                .totalScheduledQuantity(totalScheduled).totalReceivedQuantity(totalReceived)
                .totalPendingQualityQuantity(totalPending).receiptCount(receipts.size())
                .nextWaitingEventCode(stage.nextWaitingEventCode())
                .nextWaitingEventLabel(stage.nextWaitingEventLabel())
                .terminal(stage.terminal())
                .lines(lines.stream().map(line -> InboundReceiptProgressView.AsnLineView.builder()
                        .asnLineId(line.getAsnLineId()).lineNo(line.getLineNo())
                        .procurementOrderItemId(line.getProcurementOrderItemId())
                        .deliveryScheduleId(line.getDeliveryScheduleId()).poReleaseVersion(line.getPoReleaseVersion())
                        .fulfillmentVersion(line.getFulfillmentVersion()).supplierId(line.getSupplierId())
                        .warehouseId(line.getWarehouseId()).receiptLocationId(line.getReceiptLocationId())
                        .canonicalSkuId(line.getCanonicalSkuId()).ownerType(line.getOwnerType()).ownerId(line.getOwnerId())
                        .baseUomCode(line.getBaseUomCode()).scheduledQuantity(line.getScheduledQuantity())
                        .allowedOverReceiptQuantity(line.getAllowedOverReceiptQuantity())
                        .receivedQuantity(line.getReceivedQuantity()).pendingQualityQuantity(line.getPendingQualityQuantity())
                        .valuationPolicyId(line.getValuationPolicyId())
                        .valuationPolicyVersion(line.getValuationPolicyVersion())
                        .valuationPolicyHash(line.getValuationPolicyHash())
                        .unitCostAmountMinor(stringValue(line.getUnitCostAmountMinor())).currencyCode(line.getCurrencyCode())
                        .roundingPolicyCode(line.getRoundingPolicyCode())
                        .tolerancePolicyVersion(line.getTolerancePolicyVersion())
                        .tolerancePolicyHash(line.getTolerancePolicyHash()).status(line.getStatus()).build()).toList())
                .receipts(receipts.stream().map(receipt -> InboundReceiptProgressView.ReceiptView.builder()
                        .receiptId(receipt.getReceiptId()).receiptNo(receipt.getReceiptNo())
                        .status(receipt.getStatus()).version(receipt.getVersion()).remark(receipt.getRemark())
                        .createdAt(receipt.getCreatedAt())
                        .lines(receiptLineMapper.selectByReceipt(tenantId, receipt.getReceiptId()).stream()
                                .map(line -> InboundReceiptProgressView.ReceiptLineView.builder()
                                        .receiptLineId(line.getReceiptLineId()).lineNo(line.getLineNo())
                                        .asnLineId(line.getAsnLineId()).procurementOrderItemId(line.getProcurementOrderItemId())
                                        .deliveryScheduleId(line.getDeliveryScheduleId()).poReleaseVersion(line.getPoReleaseVersion())
                                        .fulfillmentVersionBefore(line.getFulfillmentVersionBefore())
                                        .fulfillmentVersionAfter(line.getFulfillmentVersionAfter())
                                        .supplierId(line.getSupplierId()).warehouseId(line.getWarehouseId())
                                        .receiptLocationId(line.getReceiptLocationId()).canonicalSkuId(line.getCanonicalSkuId())
                                        .ownerType(line.getOwnerType()).ownerId(line.getOwnerId())
                                        .baseUomCode(line.getBaseUomCode()).lotId(line.getLotId())
                                        .qualityStatus(line.getQualityStatus()).qualityInspectionId(line.getQualityInspectionId())
                                        .receivedQuantity(line.getReceivedQuantity()).pendingQualityQuantity(line.getPendingQualityQuantity())
                                        .acceptedQuantity(line.getAcceptedQuantity()).rejectedQuantity(line.getRejectedQuantity())
                                        .quarantinedQuantity(line.getQuarantinedQuantity())
                                        .cumulativePutawayQuantity(line.getCumulativePutawayQuantity()).version(line.getVersion())
                                        .valuationPolicyId(line.getValuationPolicyId())
                                        .valuationPolicyVersion(line.getValuationPolicyVersion())
                                        .valuationPolicyHash(line.getValuationPolicyHash())
                                        .unitCostAmountMinor(stringValue(line.getUnitCostAmountMinor()))
                                        .movementCostAmountMinor(stringValue(line.getMovementCostAmountMinor())).currencyCode(line.getCurrencyCode())
                                        .roundingPolicyCode(line.getRoundingPolicyCode())
                                        .tolerancePolicyVersion(line.getTolerancePolicyVersion())
                                        .tolerancePolicyHash(line.getTolerancePolicyHash())
                                        .inventoryOperationId(stringValue(line.getInventoryOperationId()))
                                        .inventoryLedgerTxId(stringValue(line.getInventoryLedgerTxId()))
                                        .inventoryBalanceId(line.getInventoryBalanceId())
                                        .financeReceiptEvidenceOperationId(stringValue(line.getFinanceReceiptEvidenceOperationId()))
                                        .financeReceiptEvidenceId(line.getFinanceReceiptEvidenceId())
                                        .financeReceiptEvidenceVersion(line.getFinanceReceiptEvidenceVersion()).build())
                                .toList())
                        .build()).toList())
                .build();
    }

    private static String requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String stringValue(Long value) {
        return value == null ? null : value.toString();
    }

    @Data
    public static class InboundReceiptPageReqVO extends PageParam {
        private String keyword;
        private String receiptId;
        private String receiptNo;
        private String procurementOrderId;
        private String supplierId;
        private String warehouseId;
        private String status;
    }

    @Data
    public static class InboundPutawayPageReqVO extends PageParam {
        private String keyword;
        private String receiptId;
        private String procurementOrderId;
        private String warehouseId;
        private String status;
    }

    public record InboundReceiptPageItem(String receiptId, String receiptNo, String procurementOrderId,
                                         String asnId, String supplierId, String warehouseId, String status,
                                         Long version, BigDecimal totalReceivedQuantity,
                                         BigDecimal totalPendingQualityQuantity, BigDecimal totalAcceptedQuantity,
                                         BigDecimal totalRejectedQuantity, BigDecimal totalQuarantinedQuantity,
                                         BigDecimal totalPutawayQuantity, Long lineCount,
                                         java.time.LocalDateTime createdAt, java.time.LocalDateTime updatedAt) {
    }
}
