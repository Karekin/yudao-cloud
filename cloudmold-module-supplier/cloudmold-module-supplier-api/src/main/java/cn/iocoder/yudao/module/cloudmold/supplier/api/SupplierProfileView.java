package cn.iocoder.yudao.module.cloudmold.supplier.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierProfileView {
    private String supplierId;
    private String supplierCode;
    private String supplierName;
    private String countryCode;
    private String capabilitySummary;
    private String riskLevel;
    private String status;
    private String admissionStatus;
    private String qualificationEvidenceSha256;
    private String riskEvidenceSha256;
    private Long version;
    private String createdByPrincipalId;
    private String admittedByPrincipalId;
    private LocalDateTime createdAt;
    private LocalDateTime admittedAt;
}
