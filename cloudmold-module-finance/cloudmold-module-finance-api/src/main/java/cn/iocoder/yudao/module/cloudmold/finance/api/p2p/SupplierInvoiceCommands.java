package cn.iocoder.yudao.module.cloudmold.finance.api.p2p;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class SupplierInvoiceCommands {
    private SupplierInvoiceCommands() {
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Create implements Serializable {
        private FinanceCommandEnvelope envelope;
        private String supplierInvoiceId;
        private String invoiceCode;
        private String legalEntityId;
        private String ledgerId;
        private String accountingPeriodId;
        private String supplierId;
        private String supplierInvoiceNumber;
        private String invoiceType;
        private String currencyCode;
        private LocalDate issueDate;
        private LocalDate accountingDate;
        private LocalDate dueDate;
        private Long netAmountMinor;
        private Long taxAmountMinor;
        private Long grossAmountMinor;
        private String evidenceSha256;
        private String paymentTermId;
        private Long paymentTermVersion;
        private String postingRuleId;
        private Long postingRuleVersion;
        private List<Line> lines;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Line implements Serializable {
        private String invoiceLineId;
        private Integer lineNumber;
        private String purchaseOrderId;
        private String purchaseOrderItemId;
        private String skuId;
        private BigDecimal quantity;
        private String unitOfMeasure;
        private BigDecimal unitNetPrice;
        private Long netAmountMinor;
        private String taxCode;
        private BigDecimal taxRate;
        private Long taxAmountMinor;
        private Long grossAmountMinor;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Transition implements Serializable {
        private FinanceCommandEnvelope envelope;
        private String supplierInvoiceId;
        private Long expectedVersion;
        private String reasonCode;
        private List<JournalDimensionAssignment> journalDimensions;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Match implements Serializable {
        private FinanceCommandEnvelope envelope;
        private String supplierInvoiceId;
        private Long expectedVersion;
        private String matchPolicyId;
        private Long matchPolicyVersion;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ApproveOverride implements Serializable {
        private FinanceCommandEnvelope envelope;
        private String exceptionId;
        private Long expectedVersion;
        private String resolutionEvidenceSha256;
        private String reasonCode;
    }
}
