package cn.iocoder.yudao.module.cloudmold.order.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderBenefitAllocationView {
    private String benefitAllocationId;
    private String allocationKey;
    private String orderItemId;
    private String lineKey;
    private Long amountMinor;
    private Long returnAmountMinor;
    private String currencyCode;
    private List<OrderBenefitFundingView> funding;
}
