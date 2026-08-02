package cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("cloudmold_inventory_scrap_disposition_batch")
public class InventoryScrapDispositionBatchDO {
    private String batchId;
    private Long tenantId;
    private String scrapId;
    private String batchNo;
    private String dispositionType;
    private String proofType;
    private String proofRef;
    private String status;
    private BigDecimal totalDisposedQuantity;
    private Integer lineCount;
    private Long version;
    private String executedByPrincipalId;
    private String remark;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
