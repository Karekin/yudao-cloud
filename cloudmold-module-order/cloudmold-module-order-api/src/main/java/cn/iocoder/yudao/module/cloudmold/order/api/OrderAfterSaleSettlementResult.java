package cn.iocoder.yudao.module.cloudmold.order.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderAfterSaleSettlementResult {
    private String settlementEffectId;
    private String orderId;
    private String orderItemId;
    private BigDecimal returnedQuantity;
    private BigDecimal remainingQuantity;
    private Long refundedNetAmountMinor;
    private Long reversedBenefitAmountMinor;
    private Long orderSettlementVersion;
    private Long itemSettlementVersion;
    private Long orderVersion;
    private Boolean fullReturn;
    private Boolean duplicate;
}
