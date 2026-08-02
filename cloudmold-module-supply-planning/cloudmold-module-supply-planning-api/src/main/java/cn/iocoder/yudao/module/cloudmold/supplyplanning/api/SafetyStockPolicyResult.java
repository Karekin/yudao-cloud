package cn.iocoder.yudao.module.cloudmold.supplyplanning.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SafetyStockPolicyResult {
    private Long operationId;
    private boolean duplicate;
    private String aggregateType;
    private String aggregateId;
    private Long aggregateVersion;
    private String status;
    /** Present when publishing creates the immutable policy-version authority. */
    private String policyVersionId;
}
