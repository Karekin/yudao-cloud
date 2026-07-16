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
public class OrderBenefitApplicationView {
    private String benefitApplicationId;
    private String applicationKey;
    private String benefitType;
    private String benefitSourceType;
    private String benefitSourceId;
    private Long benefitSourceVersion;
    private String entitlementId;
    private Long amountMinor;
    private String currencyCode;
    private String calculationDigest;
    private Long version;
    private List<OrderBenefitAllocationView> allocations;
}
