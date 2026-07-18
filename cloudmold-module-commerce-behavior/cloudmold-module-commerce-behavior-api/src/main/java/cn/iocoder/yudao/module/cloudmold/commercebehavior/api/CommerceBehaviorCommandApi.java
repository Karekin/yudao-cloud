package cn.iocoder.yudao.module.cloudmold.commercebehavior.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

public interface CommerceBehaviorCommandApi {

    CommerceBehaviorCommandResult startSession(StartCommerceSessionCommand command);

    CommerceBehaviorCommandResult linkSessionIdentity(LinkCommerceSessionIdentityCommand command);

    CommerceBehaviorCommandResult recordBehavior(RecordCommerceBehaviorCommand command);

    CommerceBehaviorCommandResult attributePaidOrder(AttributePaidOrderCommand command);

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class StartCommerceSessionCommand {
        private String idempotencyKey;
        private String runId;
        private String sessionId;
        private String channelCode;
        private String entrypointCode;
        private String sourceSystem;
        private String sourceType;
        private String sourceId;
        private String correlationId;
        private String causationId;
        private Instant occurredAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class LinkCommerceSessionIdentityCommand {
        private String idempotencyKey;
        private String runId;
        private String sessionId;
        private String principalId;
        private Long expectedSessionVersion;
        private String sourceSystem;
        private String sourceType;
        private String sourceId;
        private String correlationId;
        private String causationId;
        private Instant occurredAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class RecordCommerceBehaviorCommand {
        private String idempotencyKey;
        private String runId;
        private String behaviorId;
        private String sessionId;
        private String behaviorType;
        private String principalId;
        private Long expectedSessionVersion;
        private String canonicalSpuId;
        private String skuId;
        private String listingId;
        private String listingOfferId;
        private Long expectedPriceMinor;
        private String currencyCode;
        private String searchToken;
        private String resultSetToken;
        private Integer resultPosition;
        private Integer quantity;
        private String checkoutToken;
        private String sourceSystem;
        private String sourceType;
        private String sourceId;
        private String correlationId;
        private String causationId;
        private Instant occurredAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class AttributePaidOrderCommand {
        private String idempotencyKey;
        private String runId;
        private String attributionId;
        private String sessionId;
        private Long expectedSessionVersion;
        private String checkoutToken;
        private String orderId;
        private String paymentId;
        private String sourceSystem;
        private String sourceType;
        private String sourceId;
        private String correlationId;
        private String causationId;
        private Instant occurredAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class CommerceBehaviorCommandResult {
        private Long operationId;
        private String aggregateId;
        private String aggregateType;
        private String status;
        private Long aggregateVersion;
        private Boolean duplicate;
    }
}
