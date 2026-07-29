package cn.iocoder.yudao.module.cloudmold.crossborder.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrossBorderCommand implements Serializable {
    private CrossBorderOperation operation;
    private String idempotencyKey;
    private String runId;
    private String correlationId;
    private Instant occurredAt;
    private String caseId;
    private Long expectedVersion;
    private String orderId;
    private String fulfillmentId;
    private String tradeMode;
    private String originCountry;
    private String destinationCountry;
    private ComplianceAssessment assessment;
    private RouteDefinition route;
    private String approvalRef;
    private DeclarationDefinition declaration;
    private DocumentsDefinition documents;
    private BookingDefinition booking;
    private LabelDefinition label;
    private String handoverRef;
    private String customsDeclarationRef;
    private String customsReleaseRef;
    private String deliveryEvidenceRef;
    private String closeReason;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComplianceAssessment implements Serializable {
        private List<String> facts;
        private List<String> options;
        private String recommendation;
        private List<String> risks;
        private BigDecimal confidence;
        private List<String> missingFacts;
        private String evidenceRef;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RouteDefinition implements Serializable {
        private String routeCode;
        private String carrierCode;
        private String serviceLevel;
        private Integer slaDays;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeclarationDefinition implements Serializable {
        private String declarationId;
        private String hsCode;
        private String goodsDescription;
        private BigDecimal quantity;
        private Long declaredAmountMinor;
        private String currency;
        private String evidenceRef;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentsDefinition implements Serializable {
        private String orderRef;
        private String paymentRef;
        private String logisticsRef;
        private String validationEvidenceRef;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BookingDefinition implements Serializable {
        private String bookingRef;
        private String carrierCode;
        private String serviceLevel;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LabelDefinition implements Serializable {
        private String labelRef;
        private String trackingNumber;
    }
}
