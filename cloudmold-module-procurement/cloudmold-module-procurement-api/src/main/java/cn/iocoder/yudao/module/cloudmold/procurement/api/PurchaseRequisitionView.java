package cn.iocoder.yudao.module.cloudmold.procurement.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseRequisitionView {
    private String requisitionId;
    private String requisitionCode;
    private String sourceBusinessType;
    private String sourceBusinessRef;
    private String status;
    private Long version;
    private String reasonCode;
    private String remark;
    private List<LineView> lines;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineView {
        private String lineId;
        private Integer lineNumber;
        private String canonicalSkuId;
        private BigDecimal requestedQuantity;
        private String uomCode;
        private List<DeliveryScheduleView> schedules;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeliveryScheduleView {
        private String scheduleId;
        private Integer scheduleNumber;
        private String canonicalWarehouseId;
        private LocalDate requiredDeliveryDate;
        private BigDecimal scheduledQuantity;
    }
}
