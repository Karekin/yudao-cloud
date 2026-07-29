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
public class CrossBorderCaseView implements Serializable {
    private String caseId;
    private String caseNo;
    private String orderId;
    private String fulfillmentId;
    private String tradeMode;
    private String originCountry;
    private String destinationCountry;
    private String status;
    private String customsStatus;
    private String deliveryStatus;
    private Long version;
    private String approvalRef;
    private String handoverRef;
    private String customsDeclarationRef;
    private String customsReleaseRef;
    private String deliveryEvidenceRef;
    private String closeReason;
    private ComplianceAssessmentView assessment;
    private RouteView route;
    private DeclarationView declaration;
    private DocumentsView documents;
    private BookingView booking;
    private LabelView label;
    private List<HistoryView> history;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComplianceAssessmentView implements Serializable {
        private List<String> facts;
        private List<String> options;
        private String recommendation;
        private List<String> risks;
        private BigDecimal confidence;
        private List<String> missingFacts;
        private String evidenceRef;
        private Instant assessedAt;
        private String assessedByPrincipalId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RouteView implements Serializable {
        private String routeCode;
        private String carrierCode;
        private String serviceLevel;
        private Integer slaDays;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeclarationView implements Serializable {
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
    public static class DocumentsView implements Serializable {
        private String orderRef;
        private String paymentRef;
        private String logisticsRef;
        private String validationEvidenceRef;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BookingView implements Serializable {
        private String bookingRef;
        private String carrierCode;
        private String serviceLevel;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LabelView implements Serializable {
        private String labelRef;
        private String trackingNumber;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HistoryView implements Serializable {
        private Long aggregateVersion;
        private String operation;
        private String previousStatus;
        private String currentStatus;
        private String actorPrincipalId;
        private Instant occurredAt;
        private Instant recordedAt;
        private String detailJson;
    }
}
