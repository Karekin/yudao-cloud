package cn.iocoder.yudao.module.cloudmold.supplier.dal.dataobject;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

public final class SupplierProfileRecords {
    private SupplierProfileRecords() {
    }

    @Data
    @Accessors(chain = true)
    public static class Operation {
        private Long operationId;
        private Long tenantId;
        private String idempotencyKey;
        private String commandType;
        private String requestHash;
        private String attemptToken;
        private Integer status;
        private String aggregateType;
        private String aggregateId;
        private String resultJson;
    }

    @Data
    @Accessors(chain = true)
    public static class Profile {
        private String supplierId;
        private Long tenantId;
        private String supplierCode;
        private String supplierName;
        private String countryCode;
        private String capabilitySummary;
        private String riskLevel;
        private String status;
        private String admissionStatus;
        private String qualificationEvidenceSha256;
        private String riskEvidenceSha256;
        private String createdByPrincipalId;
        private String submittedByPrincipalId;
        private String admittedByPrincipalId;
        private String reasonCode;
        private Long version;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private LocalDateTime admissionSubmittedAt;
        private LocalDateTime admittedAt;
    }
}
