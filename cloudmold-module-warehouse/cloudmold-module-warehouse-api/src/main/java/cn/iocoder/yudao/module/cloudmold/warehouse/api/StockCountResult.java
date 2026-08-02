package cn.iocoder.yudao.module.cloudmold.warehouse.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockCountResult {
    private Long operationId;
    private String stockCountId;
    private String stockCountCode;
    private String status;
    private String currentStageCode;
    private String currentStageLabel;
    private Long aggregateVersion;
    private Long freezeLedgerTransactionId;
    private String batchId;
    private String batchNo;
    private Integer processedLineCount;
    private Integer differenceLineCount;
    private boolean duplicate;
}
