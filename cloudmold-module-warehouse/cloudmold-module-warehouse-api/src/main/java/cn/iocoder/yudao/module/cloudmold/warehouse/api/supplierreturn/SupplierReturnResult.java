package cn.iocoder.yudao.module.cloudmold.warehouse.api.supplierreturn;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierReturnResult {
    private Long operationId;
    private String returnId;
    private String returnCode;
    private String status;
    private Long aggregateVersion;
    private String currentStageCode;
    private String currentStageLabel;
    private String batchId;
    private String batchNo;
    private Integer processedLineCount;
    private boolean duplicate;
}
