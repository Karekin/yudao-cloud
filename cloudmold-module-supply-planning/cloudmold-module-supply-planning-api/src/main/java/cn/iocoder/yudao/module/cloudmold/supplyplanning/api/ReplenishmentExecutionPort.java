package cn.iocoder.yudao.module.cloudmold.supplyplanning.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Creates a real downstream draft for an approved replenishment recommendation.
 * Implementations must be idempotent and must never approve or complete the draft.
 */
public interface ReplenishmentExecutionPort {

    ExecutionResult createDraft(ExecutionCommand command);

    record ExecutionCommand(
            String idempotencyKey,
            String conversionId,
            String targetType,
            String canonicalSkuId,
            String canonicalWarehouseId,
            BigDecimal quantity,
            String uomCode,
            LocalDate needByDate,
            String mappingEvidenceSha256,
            Long supplierId,
            Long accountId,
            Long erpProductId,
            Long erpProductUnitId,
            Long unitCostMinor,
            BigDecimal taxPercent,
            Long sourceWarehouseId,
            Long targetWarehouseId,
            Long wmsSkuId,
            Instant occurredAt) {
    }

    record ExecutionResult(
            String sourceSystem,
            String documentType,
            String externalDocumentId,
            String externalDocumentNo,
            String status,
            String nextWaitingEventCode,
            String nextWaitingEventLabel) {
    }
}
