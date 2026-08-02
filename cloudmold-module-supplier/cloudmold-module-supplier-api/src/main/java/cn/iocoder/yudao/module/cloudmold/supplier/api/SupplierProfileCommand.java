package cn.iocoder.yudao.module.cloudmold.supplier.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierProfileCommand {
    private SupplierProfileOperation operation;
    private String idempotencyKey;
    private String runId;
    private String correlationId;
    private String causationId;
    private Instant occurredAt;
    private SupplierDefinition supplier;
    private AdmissionDefinition admission;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SupplierDefinition {
        private String supplierId;
        private String supplierCode;
        private String supplierName;
        private String countryCode;
        private String capabilitySummary;
        private String riskLevel;
        private String reasonCode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdmissionDefinition {
        private String supplierId;
        private String qualificationEvidenceSha256;
        private String riskEvidenceSha256;
        private String reasonCode;
        private Long expectedVersion;
    }
}
