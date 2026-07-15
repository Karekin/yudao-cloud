package cn.iocoder.yudao.module.cloudmold.promotion.api;

import lombok.*;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionCommand {
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

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CampaignDefinition {
        private String campaignId;
        private String campaignCode;
        private String campaignKind;
        private String name;
        private Instant startsAt;
        private Instant endsAt;
        private Long expectedVersion;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CouponTemplateDefinition {
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
    public static class CouponEntitlementDefinition {
        private String entitlementId;
        private String entitlementCode;
        private String templateId;
        private String principalId;
        private String orderRef;
        private String reason;
        private Long expectedVersion;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class AdvertisingPlacementDefinition {
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
    public static class AdvertisingInteractionDefinition {
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
}
