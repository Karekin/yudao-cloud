package cn.iocoder.yudao.module.cloudmold.order.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Exact economic funder share for one Order benefit allocation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderBenefitFundingCommand {
    private String fundingKey;
    private String funderType;
    private String funderId;
    private Long amountMinor;
}
