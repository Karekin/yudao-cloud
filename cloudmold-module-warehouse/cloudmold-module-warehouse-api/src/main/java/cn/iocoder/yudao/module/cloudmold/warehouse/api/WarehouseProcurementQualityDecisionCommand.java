package cn.iocoder.yudao.module.cloudmold.warehouse.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarehouseProcurementQualityDecisionCommand {
    private String idempotencyKey;
    private String sourceEventId;
    private String qualityDecisionId;
    private Long decisionVersion;
    private String inspectionSplitId;
    private WarehouseProcurementQualityDisposition disposition;
    private String receiptId;
    private String receiptLineId;
    private String procurementOrderId;
    private String procurementOrderItemId;
    private String deliveryScheduleId;
    private String warehouseId;
    private String locationId;
    private String lotId;
    private String baseUomCode;
    private BigDecimal quantity;
    private String evidenceRef;
    private String correlationId;
    private String causationId;
    private Instant occurredAt;
}
