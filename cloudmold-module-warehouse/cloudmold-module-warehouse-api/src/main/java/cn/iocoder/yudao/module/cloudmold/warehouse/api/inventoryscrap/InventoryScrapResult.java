package cn.iocoder.yudao.module.cloudmold.warehouse.api.inventoryscrap;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryScrapResult {
    private Long operationId;
    private String scrapId;
    private String scrapCode;
    private String scrapStatus;
    private String batchId;
    private String batchNo;
    private Integer processedLineCount;
    private Long aggregateVersion;
    private boolean duplicate;
}
