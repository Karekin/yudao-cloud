package cn.iocoder.yudao.module.cloudmold.supplyplanning.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Frozen, governed inputs for converting one approved replenishment recommendation.
 *
 * <p>The view is a proposal, not evidence that a purchase or transfer draft exists.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplenishmentExecutionProposalView implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String proposalId;
    private String recommendationId;
    private Long expectedRecommendationVersion;
    private String targetType;
    private String ownerType;
    private String ownerId;
    private String sourceWarehouseId;
    private String targetWarehouseId;
    private String proposedByPrincipalId;
    private String policyCode;
    private String policySha256;
    private LocalDateTime proposedAt;
}
