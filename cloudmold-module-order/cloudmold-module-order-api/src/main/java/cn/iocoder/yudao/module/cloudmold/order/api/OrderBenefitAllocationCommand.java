package cn.iocoder.yudao.module.cloudmold.order.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Exact allocation of one benefit application to one immutable Order line.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderBenefitAllocationCommand {
    private String allocationKey;
    private String lineKey;
    private Long amountMinor;
    private List<OrderBenefitFundingCommand> funding;
}
