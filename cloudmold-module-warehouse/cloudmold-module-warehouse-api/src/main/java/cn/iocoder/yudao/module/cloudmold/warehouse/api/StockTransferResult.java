package cn.iocoder.yudao.module.cloudmold.warehouse.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockTransferResult {
    private Long operationId;
    private String requestId;
    private String requestCode;
    private String requestStatus;
    private String orderId;
    private String orderCode;
    private String orderStatus;
    private String batchId;
    private String batchNo;
    private String batchType;
    private Integer processedLineCount;
    private String currentStageCode;
    private String currentStageLabel;
    private Long aggregateVersion;
    private boolean duplicate;
}
