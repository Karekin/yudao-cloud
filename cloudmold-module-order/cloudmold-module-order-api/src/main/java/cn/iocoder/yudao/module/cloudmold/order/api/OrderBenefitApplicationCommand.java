package cn.iocoder.yudao.module.cloudmold.order.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Immutable commercial benefit applied when an Order is placed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderBenefitApplicationCommand {
    private String applicationKey;
    private String benefitType;
    private String benefitSourceType;
    private String benefitSourceId;
    private Long benefitSourceVersion;
    private String entitlementId;
    private Long amountMinor;
    private String calculationDigest;
    private List<OrderBenefitAllocationCommand> allocations;
}
