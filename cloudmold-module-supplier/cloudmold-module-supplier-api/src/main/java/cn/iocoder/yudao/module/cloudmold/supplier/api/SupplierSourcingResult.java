package cn.iocoder.yudao.module.cloudmold.supplier.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierSourcingResult {
    private Long operationId;
    private Boolean duplicate;
    private String aggregateType;
    private String aggregateId;
    private Long aggregateVersion;
    private String status;
    private String supplierId;
    private String sourcingCaseId;
    private String quoteId;
    private String evaluationId;
}
