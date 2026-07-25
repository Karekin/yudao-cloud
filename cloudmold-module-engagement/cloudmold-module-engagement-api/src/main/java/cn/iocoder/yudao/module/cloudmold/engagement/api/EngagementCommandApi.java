package cn.iocoder.yudao.module.cloudmold.engagement.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

public interface EngagementCommandApi {

    EngagementCommandResult changeFavorite(ChangeFavoriteCommand command);

    EngagementCommandResult saveNotificationCampaign(SaveNotificationCampaignCommand command);

    EngagementCommandResult createNotificationDelivery(CreateNotificationDeliveryCommand command);

    EngagementCommandResult recordNotificationAttempt(RecordNotificationAttemptCommand command);

    EngagementCommandResult recordNotificationReceipt(RecordNotificationReceiptCommand command);

    EngagementCommandResult createCommunityContent(CreateCommunityContentCommand command);

    EngagementCommandResult transitionCommunityContent(TransitionCommunityContentCommand command);

    EngagementCommandResult recordCommunityInteraction(RecordCommunityInteractionCommand command);

    EngagementCommandResult changeCommunityReaction(ChangeCommunityReactionCommand command);

    EngagementCommandResult openModerationCase(OpenModerationCaseCommand command);

    EngagementCommandResult decideModerationCase(DecideModerationCaseCommand command);

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    class ChangeFavoriteCommand {
        private String idempotencyKey;
        private String runId;
        private String favoriteId;
        private String principalId;
        private String canonicalSpuId;
        private String desiredStatus;
        private Long expectedVersion;
        private String sourceSystem;
        private String sourceType;
        private String sourceId;
        private String correlationId;
        private String causationId;
        private Instant occurredAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    class SaveNotificationCampaignCommand {
        private String idempotencyKey;
        private String runId;
        private String campaignId;
        private String campaignCode;
        private String campaignName;
        private String channel;
        private String desiredStatus;
        private Long expectedVersion;
        private String sourceSystem;
        private String sourceType;
        private String sourceId;
        private String correlationId;
        private String causationId;
        private Instant occurredAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    class CreateNotificationDeliveryCommand {
        private String idempotencyKey;
        private String runId;
        private String deliveryId;
        private String deliveryKey;
        private String campaignId;
        private String principalId;
        private String channel;
        private String destinationToken;
        private String correlationId;
        private String causationId;
        private Instant occurredAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    class RecordNotificationAttemptCommand {
        private String idempotencyKey;
        private String runId;
        private String attemptId;
        private String deliveryId;
        private Long expectedVersion;
        private Integer attemptNo;
        private String providerCode;
        private String providerReference;
        private String outcome;
        private String correlationId;
        private String causationId;
        private Instant occurredAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    class RecordNotificationReceiptCommand {
        private String idempotencyKey;
        private String runId;
        private String receiptId;
        private String deliveryId;
        private Long expectedVersion;
        private String externalReceiptId;
        private String receiptStatus;
        private String correlationId;
        private String causationId;
        private Instant occurredAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    class CreateCommunityContentCommand {
        private String idempotencyKey;
        private String runId;
        private String contentId;
        private String authorPrincipalId;
        private String contentType;
        private String bodyRef;
        private String bodyKeyId;
        private byte[] bodyIv;
        private byte[] bodyCiphertext;
        private String bodyDigestSha256;
        private String canonicalSpuId;
        private String canonicalSkuId;
        private String listingId;
        private String listingOfferId;
        private String desiredStatus;
        private String sourceSystem;
        private String sourceType;
        private String sourceId;
        private String correlationId;
        private String causationId;
        private Instant occurredAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    class TransitionCommunityContentCommand {
        private String idempotencyKey;
        private String runId;
        private String contentId;
        private String actorPrincipalId;
        private String desiredStatus;
        private Long expectedVersion;
        private String reasonCode;
        private String correlationId;
        private String causationId;
        private Instant occurredAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    class RecordCommunityInteractionCommand {
        private String idempotencyKey;
        private String runId;
        private String interactionId;
        private String actorPrincipalId;
        private String interactionType;
        private String targetType;
        private String targetId;
        private String payloadRef;
        private String payloadKeyId;
        private byte[] payloadIv;
        private byte[] payloadCiphertext;
        private String payloadDigestSha256;
        private String correlationId;
        private String causationId;
        private Instant occurredAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    class ChangeCommunityReactionCommand {
        private String idempotencyKey;
        private String runId;
        private String reactionId;
        private String actorPrincipalId;
        private String reactionType;
        private String targetType;
        private String targetId;
        private String desiredStatus;
        private Long expectedVersion;
        private String correlationId;
        private String causationId;
        private Instant occurredAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    class OpenModerationCaseCommand {
        private String idempotencyKey;
        private String runId;
        private String moderationCaseId;
        private String contentId;
        private String reporterPrincipalId;
        private String reportReasonCode;
        private String evidenceRef;
        private String correlationId;
        private String causationId;
        private Instant occurredAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    class DecideModerationCaseCommand {
        private String idempotencyKey;
        private String runId;
        private String moderationCaseId;
        private String moderatorPrincipalId;
        private String decision;
        private String decisionReasonCode;
        private Long expectedVersion;
        private String correlationId;
        private String causationId;
        private Instant occurredAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    class EngagementCommandResult {
        private Long operationId;
        private String aggregateId;
        private String aggregateType;
        private String status;
        private Long aggregateVersion;
        private Boolean duplicate;
    }
}
