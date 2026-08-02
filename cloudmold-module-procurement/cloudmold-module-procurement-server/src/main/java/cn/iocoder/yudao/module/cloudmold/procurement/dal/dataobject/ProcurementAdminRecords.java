package cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject;

import lombok.Data;
import lombok.experimental.Accessors;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class ProcurementAdminRecords {
    private ProcurementAdminRecords() {}
    @Data @Accessors(chain=true) public static class RequisitionSummary { private Long aggregateVersion; private LocalDateTime approvedAt; private Integer lineCount; private String requisitionCode; private String requisitionId; private String requestedByPrincipalId; private LocalDate requiredDeliveryDate; private String status; private LocalDateTime submittedAt; private BigDecimal totalRequestedQuantity; private String uomCode; private LocalDateTime updatedAt; }
    @Data @Accessors(chain=true) public static class RfqSummary { private Long aggregateVersion; private String eventCode; private String eventId; private Integer invitationCount; private Integer lineCount; private LocalDateTime quotationDeadline; private Integer quotationRevisionCount; private String requisitionCode; private String requisitionId; private String status; private String title; private LocalDateTime updatedAt; }
    @Data @Accessors(chain=true) public static class QuotationSummary { private Long aggregateVersion; private String currencyCode; private String eventCode; private String eventId; private Long grossAmountMinor; private Integer lineCount; private String quotationCode; private String quotationId; private Integer revisionNumber; private String status; private String supplierId; private LocalDateTime submittedAt; private LocalDateTime updatedAt; }
    @Data @Accessors(chain=true) public static class AwardSummary { private Long aggregateVersion; private String awardCode; private String awardId; private BigDecimal awardedQuantity; private Long eventAggregateVersion; private String eventCode; private String eventId; private Integer lineCount; private Integer purchaseOrderCount; private String status; private Integer supplierCount; private LocalDateTime updatedAt; }
    @Data @Accessors(chain=true) public static class OrderSummary { private Long aggregateVersion; private String awardCode; private String awardId; private String currencyCode; private Long grossAmountMinor; private Integer lineCount; private String orderCode; private String orderId; private BigDecimal orderedQuantity; private BigDecimal qualifiedQuantity; private BigDecimal receivedQuantity; private Integer scheduleCount; private String status; private String supplierId; private LocalDateTime updatedAt; }
    @Data @Accessors(chain=true) public static class AwardLineDetail { private String awardLineId; private Long awardedGrossAmountMinor; private BigDecimal awardedQuantity; private String canonicalSkuId; private Integer lineNumber; private LocalDate promisedDeliveryDate; private String quotationId; private String quotationLineId; private Integer quotationRevisionNumber; private String requisitionId; private String requisitionLineId; private String requisitionScheduleId; private Integer score; private String sourcingLineId; private String sourcingScheduleId; private String supplierId; private BigDecimal unitNetPriceMinor; private String uomCode; }
}
