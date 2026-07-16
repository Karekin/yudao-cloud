package cn.iocoder.yudao.module.cloudmold.order.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderAfterSaleSettlementCommand {
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
    private String correlationId;
    private String causationId;
    private Instant occurredAt;
}
