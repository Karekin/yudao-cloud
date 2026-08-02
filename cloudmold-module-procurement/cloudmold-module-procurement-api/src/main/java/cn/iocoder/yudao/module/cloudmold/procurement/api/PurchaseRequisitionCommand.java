package cn.iocoder.yudao.module.cloudmold.procurement.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseRequisitionCommand {
    private String idempotencyKey;
    private String runId;
    private String correlationId;
    private String causationId;
    private Instant occurredAt;
    private String requisitionId;
    private String requisitionCode;
    private String sourceBusinessType;
    private String sourceBusinessRef;
    private String reasonCode;
    private String remark;
    private List<LineDefinition> lines;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineDefinition {
        private String lineId;
        private Integer lineNumber;
        private String canonicalSkuId;
        private BigDecimal requestedQuantity;
        private String uomCode;
        private List<DeliveryScheduleDefinition> schedules;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeliveryScheduleDefinition {
        private String scheduleId;
        private Integer scheduleNumber;
        private String canonicalWarehouseId;
        private LocalDate requiredDeliveryDate;
        private BigDecimal scheduledQuantity;
    }
}
