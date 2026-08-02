package cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("cloudmold_inventory_scrap_disposition_operation_v1")
public class InventoryScrapDispositionOperationDO {
    @TableId(type = IdType.AUTO)
    private Long operationId;
    private Long tenantId;
    private String idempotencyKey;
    private String sourceEventId;
    private String dispositionLineId;
    private String scrapDocumentId;
    private String scrapLineId;
    private String dispositionBatchId;
    private String requestHash;
    private String attemptToken;
    private Integer status;
    private Long ledgerTransactionId;
    private String balanceId;
    private Long aggregateVersion;
    private BigDecimal onHandQuantity;
    private BigDecimal disposedQuantity;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
