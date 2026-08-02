package cn.iocoder.yudao.module.cloudmold.procurement.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseRequisitionResult {
    private Long operationId;
    private boolean duplicate;
    private String aggregateType;
    private String requisitionId;
    private String requisitionCode;
    private Long aggregateVersion;
    private String status;
}
