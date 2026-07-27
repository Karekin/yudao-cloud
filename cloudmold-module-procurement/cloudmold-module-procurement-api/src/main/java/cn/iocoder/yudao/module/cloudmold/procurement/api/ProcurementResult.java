package cn.iocoder.yudao.module.cloudmold.procurement.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcurementResult {
    private Long operationId;
    private boolean duplicate;
    private String aggregateType;
    private String aggregateId;
    private Long aggregateVersion;
    private String status;
    private String orderCode;
    private String sourceBusinessRef;
    private String projectionSourceSystem;
    private String projectionDocumentType;
    private String projectionExternalDocumentId;
    private String projectionExternalDocumentNo;
    private String projectionDocumentStatus;
}
