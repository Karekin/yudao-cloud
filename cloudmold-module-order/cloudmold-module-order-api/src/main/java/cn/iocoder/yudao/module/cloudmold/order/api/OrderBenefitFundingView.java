package cn.iocoder.yudao.module.cloudmold.order.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderBenefitFundingView {
    private String benefitFundingId;
    private String fundingKey;
    private String funderType;
    private String funderId;
    private Long amountMinor;
    private String currencyCode;
}
