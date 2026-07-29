package cn.iocoder.yudao.module.cloudmold.promotion.api;

import lombok.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionCommand implements Serializable {
    private PromotionOperation operation;
    private String idempotencyKey;
    private String correlationId;
    private String causationId;
    private Instant occurredAt;
    private CampaignDefinition campaign;
    private CouponTemplateDefinition couponTemplate;
    private CouponEntitlementDefinition couponEntitlement;
    private AdvertisingPlacementDefinition advertisingPlacement;
    private AdvertisingInteractionDefinition advertisingInteraction;
    private AdvertisingLedgerDefinition advertisingLedger;
    private PromotionExperimentResultDefinition promotionExperimentResult;
    private GrowthExperimentDefinition growthExperiment;
    private GrowthExperimentExposureDefinition growthExperimentExposure;
    private GrowthExperimentMetricSnapshotDefinition growthExperimentMetricSnapshot;
    private GrowthExperimentConclusionDefinition growthExperimentConclusion;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CampaignDefinition implements Serializable {
        private String campaignId;
        private String campaignCode;
        private String campaignKind;
        private String name;
        private Instant startsAt;
        private Instant endsAt;
        private Long expectedVersion;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CouponTemplateDefinition implements Serializable {
        private String templateId;
        private String templateCode;
        private String campaignId;
        private String title;
        private String benefitType;
        private Long faceAmountMinor;
        private Long thresholdMinor;
        private Integer discountBasisPoints;
        private Long capAmountMinor;
        private String currencyCode;
        private String funderType;
        private String merchantId;
        private Instant validFrom;
        private Instant validTo;
        private Long expectedVersion;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CouponEntitlementDefinition implements Serializable {
        private String entitlementId;
        private String entitlementCode;
        private String templateId;
        private String principalId;
        private String orderRef;
        private String reason;
        private Long expectedVersion;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class AdvertisingPlacementDefinition implements Serializable {
        private String placementId;
        private String placementCode;
        private String campaignId;
        private String name;
        private String channelCode;
        private String pageCode;
        private String slotCode;
        private String creativeRef;
        private Instant validFrom;
        private Instant validTo;
        private Long expectedVersion;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class AdvertisingInteractionDefinition implements Serializable {
        private String interactionId;
        private String deduplicationKey;
        private String placementId;
        private String principalId;
        private String sessionId;
        private String sourceInteractionId;
        private String orderRef;
        private Long attributionAmountMinor;
        private String currencyCode;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class AdvertisingLedgerDefinition implements Serializable {
        private String ledgerEntryId;
        private String ledgerEntryCode;
        private String campaignId;
        private String placementId;
        private String merchantId;
        private String entryType;
        private String chargeModel;
        private String revenueType;
        private String sourceInteractionId;
        private String orderRef;
        private Long amountMinor;
        private String currencyCode;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PromotionExperimentResultDefinition implements Serializable {
        private String experimentId;
        private String experimentCode;
        private String campaignId;
        private String merchantId;
        private Instant measuredFrom;
        private Instant measuredTo;
        private Long baselineContributionProfitMinor;
        private Long treatmentContributionProfitMinor;
        private Long incrementalContributionProfitMinor;
        private Long promotionCostMinor;
        private Integer eligiblePopulationCount;
        private Integer treatmentPopulationCount;
        private Integer controlPopulationCount;
        private String currencyCode;
        private String methodologyRef;
        private Long expectedVersion;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class GrowthExperimentDefinition implements Serializable {
        private String experimentId;
        private String experimentCode;
        private String campaignId;
        private String name;
        private String hypothesis;
        private String primaryMetricCode;
        private Integer minimumSampleSizePerVariant;
        private Instant startsAt;
        private Instant endsAt;
        private List<GrowthExperimentVariantDefinition> variants;
        private Long expectedVersion;
        private String reason;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class GrowthExperimentVariantDefinition implements Serializable {
        private String variantCode;
        private String variantKind;
        private Integer allocationBasisPoints;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class GrowthExperimentExposureDefinition implements Serializable {
        private String exposureId;
        private String exposureKey;
        private String experimentId;
        private String variantCode;
        private String principalId;
        private String assignmentVersion;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class GrowthExperimentMetricSnapshotDefinition implements Serializable {
        private String snapshotId;
        private String snapshotKey;
        private String experimentId;
        private String variantCode;
        private String metricCode;
        private Instant measuredFrom;
        private Instant measuredTo;
        private Integer sampleCount;
        private Long metricValueMicros;
        private Instant dataFreshUntil;
        private String evidenceRef;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class GrowthExperimentConclusionDefinition implements Serializable {
        private String experimentId;
        private String decision;
        private Integer confidenceBasisPoints;
        private String guardrailStatus;
        private String evidenceRef;
        private Long expectedVersion;
        private String reason;
    }
}
