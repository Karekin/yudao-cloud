package cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("cloudmold_receipt_line")
public class ReceiptLineDO {
    private String receiptLineId;
    private Long tenantId;
    private String receiptId;
    private String asnLineId;
    private String canonicalSkuId;
    private String ownerType;
    private String ownerId;
    private String baseUomCode;
    private BigDecimal expectedQuantity;
    private BigDecimal receivedQuantity;
    private BigDecimal shortQuantity;
    private BigDecimal overQuantity;
    private Long unitCostAmountMinor;
    private String currencyCode;
    private String stagingLocationId;
    private Integer qcRequired;
    private String qualityTaskId;
    /** 库存幂等键：uk 保证每行库存 RECEIVE 恰好一次 */
    private String inventoryIdempotencyKey;
    private Long inventoryOperationId;
    private Long inventoryLedgerTxId;
    private String inventoryBalanceId;
    private LocalDateTime createdAt;
}
