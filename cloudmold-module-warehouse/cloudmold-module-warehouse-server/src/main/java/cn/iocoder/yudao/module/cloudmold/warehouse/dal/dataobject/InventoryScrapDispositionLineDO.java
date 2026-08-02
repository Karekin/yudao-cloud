package cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("cloudmold_inventory_scrap_disposition_line")
public class InventoryScrapDispositionLineDO {
    private String dispositionLineId;
    private Long tenantId;
    private String batchId;
    private String scrapId;
    private String scrapLineId;
    private Integer lineNumber;
    private String canonicalSkuId;
    private String locationId;
    private String lotId;
    private String stockStatus;
    private String qualityStatus;
    private String baseUomCode;
    private BigDecimal disposedQuantity;
    private BigDecimal cumulativeDisposedQuantity;
    private String inventoryIdempotencyKey;
    private Long inventoryOperationId;
    private Long inventoryLedgerTransactionId;
    private String inventoryBalanceId;
    private String status;
    private Long version;
    private String remark;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
