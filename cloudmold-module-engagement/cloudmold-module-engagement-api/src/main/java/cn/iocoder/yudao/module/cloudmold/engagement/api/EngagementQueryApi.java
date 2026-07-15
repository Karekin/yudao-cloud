package cn.iocoder.yudao.module.cloudmold.engagement.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

public interface EngagementQueryApi {

    FavoriteView getFavorite(String principalId, String canonicalSpuId);

    NotificationCampaignView getNotificationCampaign(String campaignId);

    NotificationDeliveryView getNotificationDelivery(String deliveryId);

    CommunityContentView getCommunityContent(String contentId);

    ModerationCaseView getModerationCase(String moderationCaseId);

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    class FavoriteView {
        private String favoriteId;
        private String principalId;
        private String canonicalSpuId;
        private String status;
        private Long version;
        private String sourceSystem;
        private String sourceType;
        private String sourceId;
        private Instant updatedAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    class NotificationCampaignView {
        private String campaignId;
        private String campaignCode;
        private String campaignName;
        private String channel;
        private String status;
        private Long version;
        private Instant updatedAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    class NotificationDeliveryView {
        private String deliveryId;
        private String deliveryKey;
        private String campaignId;
        private String principalId;
        private String channel;
        private String status;
        private Integer attemptCount;
        private Integer receiptCount;
        private Long version;
        private Instant updatedAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    class CommunityContentView {
        private String contentId;
        private String authorPrincipalId;
        private String contentType;
        private String bodyRef;
        private String status;
        private Long version;
        private Instant updatedAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    class ModerationCaseView {
        private String moderationCaseId;
        private String contentId;
        private String reporterPrincipalId;
        private String moderatorPrincipalId;
        private String status;
        private String decision;
        private String decisionReasonCode;
        private Long version;
        private Instant updatedAt;
    }
}
