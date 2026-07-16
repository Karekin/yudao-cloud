package cn.iocoder.yudao.module.cloudmold.order.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("cloudmold_order_return_effect")
public class OrderReturnEffectDO {
    @TableId
    private String settlementEffectId;
    private Long tenantId;
    private String afterSaleId;
    private String afterSaleItemId;
    private String runId;
    private String orderId;
    private String orderItemId;
    private BigDecimal quantity;
    private Long grossAmountMinor;
    private Long benefitAmountMinor;
    private Long netAmountMinor;
    private Long inventoryOperationId;
    private Long inventoryLedgerTransactionId;
    private Long paymentRefundTransactionId;
    private String benefitReversalBatchId;
    private Long orderSettlementVersion;
    private Long itemSettlementVersion;
    private Boolean fullReturn;
    private String correlationId;
    private String causationId;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
}
