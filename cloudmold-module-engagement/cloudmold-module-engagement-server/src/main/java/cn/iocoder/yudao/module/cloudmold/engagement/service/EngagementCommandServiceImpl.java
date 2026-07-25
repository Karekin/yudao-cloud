package cn.iocoder.yudao.module.cloudmold.engagement.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.engagement.api.EngagementCommandApi;
import cn.iocoder.yudao.module.cloudmold.engagement.api.reference.CatalogSpuReferenceValidationPort;
import cn.iocoder.yudao.module.cloudmold.engagement.api.reference.PrincipalReferenceValidationPort;
import cn.iocoder.yudao.module.cloudmold.engagement.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.engagement.dal.mysql.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
@RequiredArgsConstructor
public class EngagementCommandServiceImpl implements EngagementCommandApi {

    static final int OPERATION_SUCCEEDED = 10;
    private static final String SOURCE_SYSTEM = "cloudmold-engagement";
    private static final Set<String> CAMPAIGN_STATUSES = Set.of("DRAFT", "ACTIVE", "PAUSED", "COMPLETED");
    private static final Set<String> CHANNELS = Set.of("SMS", "APP_PUSH");
    private static final Set<String> CONTENT_TRANSITION_STATUSES = Set.of("PENDING_MODERATION", "PUBLISHED");

    private final EngagementOperationMapper operationMapper;
    private final FavoriteMapper favoriteMapper;
    private final NotificationCampaignMapper campaignMapper;
    private final NotificationDeliveryMapper deliveryMapper;
    private final CommunityMapper communityMapper;
    private final PrincipalReferenceValidationPort principalReferenceValidationPort;
    private final CatalogSpuReferenceValidationPort catalogSpuReferenceValidationPort;
    private final OutboxAppender outboxAppender;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EngagementCommandResult changeFavorite(ChangeFavoriteCommand command) {
        validateCommon(command, command == null ? null : command.getIdempotencyKey(),
                command == null ? null : command.getRunId(), command == null ? null : command.getOccurredAt(),
                command == null ? null : command.getCorrelationId(), command == null ? null : command.getCausationId());
        requireUuid(command.getPrincipalId(), "principalId");
        requireUuid(command.getCanonicalSpuId(), "canonicalSpuId");
        require(Set.of("ACTIVE", "REMOVED").contains(command.getDesiredStatus()),
                "desiredStatus must be ACTIVE or REMOVED");
        requireSourceTriple(command.getSourceSystem(), command.getSourceType(), command.getSourceId());
        return execute("CHANGE_FAVORITE", command.getIdempotencyKey(), command, context -> {
            principalReferenceValidationPort.requireActive(context.tenantId, command.getPrincipalId());
            catalogSpuReferenceValidationPort.requireActive(context.tenantId, command.getCanonicalSpuId());
            FavoriteDO row = favoriteMapper.selectForUpdate(context.tenantId, command.getPrincipalId(),
                    command.getCanonicalSpuId());
            String previous = null;
            if (row == null) {
                require("ACTIVE".equals(command.getDesiredStatus()), "a missing favorite can only be collected");
                requireUuid(command.getFavoriteId(), "favoriteId");
                require(command.getExpectedVersion() == null, "expectedVersion must be absent for a new favorite");
                row = new FavoriteDO().setFavoriteId(command.getFavoriteId()).setTenantId(context.tenantId)
                        .setPrincipalId(command.getPrincipalId()).setCanonicalSpuId(command.getCanonicalSpuId())
                        .setStatus("ACTIVE").setVersion(1L).setSourceSystem(command.getSourceSystem())
                        .setSourceType(command.getSourceType()).setSourceId(command.getSourceId())
                        .setCreatedAt(context.now).setUpdatedAt(context.now);
                require(favoriteMapper.insert(row) == 1, "failed to create favorite");
            } else {
                require(command.getFavoriteId() == null || Objects.equals(row.getFavoriteId(), command.getFavoriteId()),
                        "favoriteId does not match the business key");
                require(command.getExpectedVersion() != null && Objects.equals(command.getExpectedVersion(), row.getVersion()),
                        "favorite version conflict");
                require(!Objects.equals(row.getStatus(), command.getDesiredStatus()), "favorite is already in desired status");
                previous = row.getStatus();
                require(favoriteMapper.updateStatus(context.tenantId, row.getFavoriteId(), row.getVersion(),
                        command.getDesiredStatus(), context.now) == 1, "favorite transition conflict");
                row.setStatus(command.getDesiredStatus()).setVersion(row.getVersion() + 1).setUpdatedAt(context.now);
            }
            String behaviorType = "ACTIVE".equals(row.getStatus()) ? "COLLECT" : "UNCOLLECT";
            FavoriteBehaviorDO behavior = new FavoriteBehaviorDO().setBehaviorId(UUID.randomUUID().toString())
                    .setTenantId(context.tenantId).setFavoriteId(row.getFavoriteId())
                    .setPrincipalId(row.getPrincipalId()).setCanonicalSpuId(row.getCanonicalSpuId())
                    .setBehaviorType(behaviorType).setFavoriteVersion(row.getVersion())
                    .setSourceSystem(row.getSourceSystem()).setSourceType(row.getSourceType()).setSourceId(row.getSourceId())
                    .setOccurredAt(utc(command.getOccurredAt())).setCreatedAt(context.now);
            require(favoriteMapper.insertBehavior(behavior) == 1, "failed to persist favorite behavior");
            Map<String, Object> statePayload = payload("run_id", command.getRunId(), "favorite_id", row.getFavoriteId(),
                    "principal_id", row.getPrincipalId(), "canonical_spu_id", row.getCanonicalSpuId(),
                    "previous_status", previous, "current_status", row.getStatus(), "behavior_type", behaviorType,
                    "source_system", row.getSourceSystem(), "source_type", row.getSourceType(), "source_id", row.getSourceId());
            append(context.tenantId, "engagement.favorite.status_changed", "engagement_favorite", row.getFavoriteId(),
                    row.getVersion(), (short) 1, command.getOccurredAt(), command.getCorrelationId(), command.getCausationId(),
                    command.getIdempotencyKey(), statePayload, "CHANGE_FAVORITE");
            Map<String, Object> behaviorPayload = payload("run_id", command.getRunId(), "behavior_id", behavior.getBehaviorId(),
                    "favorite_id", row.getFavoriteId(), "principal_id", row.getPrincipalId(),
                    "canonical_spu_id", row.getCanonicalSpuId(), "behavior_type", behaviorType,
                    "favorite_status", row.getStatus());
            append(context.tenantId, "engagement.favorite.behavior_recorded", "engagement_favorite", row.getFavoriteId(),
                    row.getVersion(), (short) 2, command.getOccurredAt(), command.getCorrelationId(), command.getCausationId(),
                    command.getIdempotencyKey(), behaviorPayload, "CHANGE_FAVORITE");
            return result(context.operationId, row.getFavoriteId(), "engagement_favorite", row.getStatus(), row.getVersion());
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EngagementCommandResult saveNotificationCampaign(SaveNotificationCampaignCommand command) {
        validateCommon(command, command == null ? null : command.getIdempotencyKey(), command == null ? null : command.getRunId(),
                command == null ? null : command.getOccurredAt(), command == null ? null : command.getCorrelationId(),
                command == null ? null : command.getCausationId());
        requireUuid(command.getCampaignId(), "campaignId");
        requireText(command.getCampaignCode(), "campaignCode", 64);
        requireText(command.getCampaignName(), "campaignName", 128);
        require(CHANNELS.contains(command.getChannel()), "channel must be SMS or APP_PUSH");
        require(CAMPAIGN_STATUSES.contains(command.getDesiredStatus()), "invalid campaign status");
        requireSourceTriple(command.getSourceSystem(), command.getSourceType(), command.getSourceId());
        return execute("SAVE_NOTIFICATION_CAMPAIGN", command.getIdempotencyKey(), command, context -> {
            NotificationCampaignDO row = campaignMapper.selectForUpdate(context.tenantId, command.getCampaignId());
            String previous = null;
            if (row == null) {
                require(command.getExpectedVersion() == null, "expectedVersion must be absent for a new campaign");
                require(Set.of("DRAFT", "ACTIVE").contains(command.getDesiredStatus()),
                        "a new campaign must be DRAFT or ACTIVE");
                require(campaignMapper.selectByBusinessKey(context.tenantId, command.getCampaignCode()) == null,
                        "campaignCode already exists");
                row = new NotificationCampaignDO().setCampaignId(command.getCampaignId()).setTenantId(context.tenantId)
                        .setCampaignCode(command.getCampaignCode()).setCampaignName(command.getCampaignName())
                        .setChannel(command.getChannel()).setStatus(command.getDesiredStatus()).setVersion(1L)
                        .setSourceSystem(command.getSourceSystem()).setSourceType(command.getSourceType())
                        .setSourceId(command.getSourceId()).setCreatedAt(context.now).setUpdatedAt(context.now);
                require(campaignMapper.insert(row) == 1, "failed to create campaign");
            } else {
                require(Objects.equals(row.getCampaignCode(), command.getCampaignCode()), "campaignCode is immutable");
                require(Objects.equals(row.getChannel(), command.getChannel()), "campaign channel is immutable");
                require(sourceEquals(row, command), "campaign qualified source is immutable");
                require(Objects.equals(row.getVersion(), command.getExpectedVersion()), "campaign version conflict");
                require(validCampaignTransition(row.getStatus(), command.getDesiredStatus()), "invalid campaign transition");
                previous = row.getStatus();
                require(campaignMapper.updateCampaign(context.tenantId, row.getCampaignId(), row.getVersion(),
                        command.getCampaignName(), command.getDesiredStatus(), context.now) == 1,
                        "campaign transition conflict");
                row.setCampaignName(command.getCampaignName()).setStatus(command.getDesiredStatus())
                        .setVersion(row.getVersion() + 1).setUpdatedAt(context.now);
            }
            Map<String, Object> value = payload("run_id", command.getRunId(), "campaign_id", row.getCampaignId(),
                    "campaign_code", row.getCampaignCode(), "campaign_name", row.getCampaignName(), "channel", row.getChannel(),
                    "previous_status", previous, "current_status", row.getStatus(), "source_system", row.getSourceSystem(),
                    "source_type", row.getSourceType(), "source_id", row.getSourceId());
            append(context.tenantId, "engagement.notification.campaign_status_changed", "notification_campaign",
                    row.getCampaignId(), row.getVersion(), (short) 1, command.getOccurredAt(), command.getCorrelationId(),
                    command.getCausationId(), command.getIdempotencyKey(), value, "SAVE_NOTIFICATION_CAMPAIGN");
            return result(context.operationId, row.getCampaignId(), "notification_campaign", row.getStatus(), row.getVersion());
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EngagementCommandResult createNotificationDelivery(CreateNotificationDeliveryCommand command) {
        validateCommon(command, command == null ? null : command.getIdempotencyKey(), command == null ? null : command.getRunId(),
                command == null ? null : command.getOccurredAt(), command == null ? null : command.getCorrelationId(),
                command == null ? null : command.getCausationId());
        requireUuid(command.getDeliveryId(), "deliveryId"); requireUuid(command.getCampaignId(), "campaignId");
        requireUuid(command.getPrincipalId(), "principalId"); requireText(command.getDeliveryKey(), "deliveryKey", 128);
        require(CHANNELS.contains(command.getChannel()), "channel must be SMS or APP_PUSH");
        requireText(command.getDestinationToken(), "destinationToken", 512);
        return execute("CREATE_NOTIFICATION_DELIVERY", command.getIdempotencyKey(), command, context -> {
            principalReferenceValidationPort.requireActive(context.tenantId, command.getPrincipalId());
            NotificationCampaignDO campaign = campaignMapper.selectByTenantAndId(context.tenantId, command.getCampaignId());
            require(campaign != null && "ACTIVE".equals(campaign.getStatus()), "campaign is not active");
            require(Objects.equals(campaign.getChannel(), command.getChannel()), "delivery channel does not match campaign");
            require(deliveryMapper.selectByBusinessKey(context.tenantId, command.getDeliveryKey()) == null,
                    "deliveryKey already exists");
            NotificationDeliveryDO row = new NotificationDeliveryDO().setDeliveryId(command.getDeliveryId())
                    .setTenantId(context.tenantId).setDeliveryKey(command.getDeliveryKey()).setCampaignId(command.getCampaignId())
                    .setPrincipalId(command.getPrincipalId()).setChannel(command.getChannel())
                    .setDestinationToken(command.getDestinationToken()).setStatus("QUEUED")
                    .setAttemptCount(0).setReceiptCount(0).setVersion(1L).setOccurredAt(utc(command.getOccurredAt()))
                    .setCreatedAt(context.now).setUpdatedAt(context.now);
            require(deliveryMapper.insert(row) == 1, "failed to create delivery");
            appendDeliveryStatus(context, row, null, command.getRunId(), null, command.getOccurredAt(),
                    command.getCorrelationId(), command.getCausationId(), command.getIdempotencyKey(), (short) 1,
                    "CREATE_NOTIFICATION_DELIVERY");
            return result(context.operationId, row.getDeliveryId(), "notification_delivery", row.getStatus(), row.getVersion());
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EngagementCommandResult recordNotificationAttempt(RecordNotificationAttemptCommand command) {
        validateCommon(command, command == null ? null : command.getIdempotencyKey(), command == null ? null : command.getRunId(),
                command == null ? null : command.getOccurredAt(), command == null ? null : command.getCorrelationId(),
                command == null ? null : command.getCausationId());
        requireUuid(command.getAttemptId(), "attemptId"); requireUuid(command.getDeliveryId(), "deliveryId");
        requirePositive(command.getExpectedVersion(), "expectedVersion");
        require(command.getAttemptNo() != null && command.getAttemptNo() > 0, "attemptNo must be positive");
        requireText(command.getProviderCode(), "providerCode", 64);
        require(Set.of("ACCEPTED", "REJECTED", "FAILED").contains(command.getOutcome()), "invalid attempt outcome");
        return execute("RECORD_NOTIFICATION_ATTEMPT", command.getIdempotencyKey(), command, context -> {
            NotificationDeliveryDO row = requireDelivery(context.tenantId, command.getDeliveryId());
            require(Objects.equals(row.getVersion(), command.getExpectedVersion()), "delivery version conflict");
            require(command.getAttemptNo() == row.getAttemptCount() + 1, "attemptNo is not the next sequence");
            require(Set.of("QUEUED", "FAILED").contains(row.getStatus()), "delivery cannot accept another attempt");
            String previous = row.getStatus();
            String current = "ACCEPTED".equals(command.getOutcome()) ? "SENT" : "FAILED";
            NotificationDeliveryAttemptDO attempt = new NotificationDeliveryAttemptDO().setAttemptId(command.getAttemptId())
                    .setTenantId(context.tenantId).setDeliveryId(row.getDeliveryId()).setAttemptNo(command.getAttemptNo())
                    .setProviderCode(command.getProviderCode()).setProviderReference(command.getProviderReference())
                    .setOutcome(command.getOutcome()).setOccurredAt(utc(command.getOccurredAt())).setCreatedAt(context.now);
            require(deliveryMapper.insertAttempt(attempt) == 1, "failed to persist notification attempt");
            require(deliveryMapper.advanceAttempt(context.tenantId, row.getDeliveryId(), row.getVersion(), current,
                    context.now) == 1, "delivery attempt transition conflict");
            row.setStatus(current).setAttemptCount(row.getAttemptCount() + 1).setVersion(row.getVersion() + 1)
                    .setUpdatedAt(context.now);
            appendDeliveryStatus(context, row, previous, command.getRunId(), command.getProviderReference(),
                    command.getOccurredAt(), command.getCorrelationId(), command.getCausationId(), command.getIdempotencyKey(),
                    (short) 1, "RECORD_NOTIFICATION_ATTEMPT");
            Map<String, Object> value = payload("run_id", command.getRunId(), "attempt_id", attempt.getAttemptId(),
                    "delivery_id", row.getDeliveryId(), "campaign_id", row.getCampaignId(), "attempt_no", attempt.getAttemptNo(),
                    "outcome", attempt.getOutcome(), "provider_code", attempt.getProviderCode(),
                    "provider_reference", attempt.getProviderReference(), "delivery_status", row.getStatus());
            append(context.tenantId, "engagement.notification.delivery_attempt_recorded", "notification_delivery",
                    row.getDeliveryId(), row.getVersion(), (short) 2, command.getOccurredAt(), command.getCorrelationId(),
                    command.getCausationId(), command.getIdempotencyKey(), value, "RECORD_NOTIFICATION_ATTEMPT");
            return result(context.operationId, row.getDeliveryId(), "notification_delivery", row.getStatus(), row.getVersion());
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EngagementCommandResult recordNotificationReceipt(RecordNotificationReceiptCommand command) {
        validateCommon(command, command == null ? null : command.getIdempotencyKey(), command == null ? null : command.getRunId(),
                command == null ? null : command.getOccurredAt(), command == null ? null : command.getCorrelationId(),
                command == null ? null : command.getCausationId());
        requireUuid(command.getReceiptId(), "receiptId"); requireUuid(command.getDeliveryId(), "deliveryId");
        requirePositive(command.getExpectedVersion(), "expectedVersion");
        requireText(command.getExternalReceiptId(), "externalReceiptId", 128);
        require(Set.of("DELIVERED", "FAILED", "OPENED", "CLICKED").contains(command.getReceiptStatus()),
                "invalid receiptStatus");
        return execute("RECORD_NOTIFICATION_RECEIPT", command.getIdempotencyKey(), command, context -> {
            NotificationDeliveryDO row = requireDelivery(context.tenantId, command.getDeliveryId());
            require(Objects.equals(row.getVersion(), command.getExpectedVersion()), "delivery version conflict");
            require(validReceiptTransition(row.getStatus(), command.getReceiptStatus()), "invalid delivery receipt transition");
            String previous = row.getStatus();
            NotificationDeliveryReceiptDO receipt = new NotificationDeliveryReceiptDO().setReceiptId(command.getReceiptId())
                    .setTenantId(context.tenantId).setDeliveryId(row.getDeliveryId())
                    .setExternalReceiptId(command.getExternalReceiptId()).setReceiptStatus(command.getReceiptStatus())
                    .setOccurredAt(utc(command.getOccurredAt())).setCreatedAt(context.now);
            require(deliveryMapper.insertReceipt(receipt) == 1, "failed to persist notification receipt");
            require(deliveryMapper.advanceReceipt(context.tenantId, row.getDeliveryId(), row.getVersion(),
                    command.getReceiptStatus(), context.now) == 1, "delivery receipt transition conflict");
            row.setStatus(command.getReceiptStatus()).setReceiptCount(row.getReceiptCount() + 1)
                    .setVersion(row.getVersion() + 1).setUpdatedAt(context.now);
            appendDeliveryStatus(context, row, previous, command.getRunId(), null, command.getOccurredAt(),
                    command.getCorrelationId(), command.getCausationId(), command.getIdempotencyKey(), (short) 1,
                    "RECORD_NOTIFICATION_RECEIPT");
            Map<String, Object> value = payload("run_id", command.getRunId(), "receipt_id", receipt.getReceiptId(),
                    "delivery_id", row.getDeliveryId(), "campaign_id", row.getCampaignId(),
                    "external_receipt_id", receipt.getExternalReceiptId(), "receipt_status", receipt.getReceiptStatus(),
                    "delivery_status", row.getStatus(), "received_at", command.getOccurredAt());
            append(context.tenantId, "engagement.notification.delivery_receipt_recorded", "notification_delivery",
                    row.getDeliveryId(), row.getVersion(), (short) 2, command.getOccurredAt(), command.getCorrelationId(),
                    command.getCausationId(), command.getIdempotencyKey(), value, "RECORD_NOTIFICATION_RECEIPT");
            return result(context.operationId, row.getDeliveryId(), "notification_delivery", row.getStatus(), row.getVersion());
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EngagementCommandResult createCommunityContent(CreateCommunityContentCommand command) {
        validateCommon(command, command == null ? null : command.getIdempotencyKey(), command == null ? null : command.getRunId(),
                command == null ? null : command.getOccurredAt(), command == null ? null : command.getCorrelationId(),
                command == null ? null : command.getCausationId());
        requireUuid(command.getContentId(), "contentId"); requireUuid(command.getAuthorPrincipalId(), "authorPrincipalId");
        require("POST".equals(command.getContentType()), "community content must be POST; comments are interactions");
        requireText(command.getBodyRef(), "bodyRef", 512);
        requireText(command.getBodyKeyId(), "bodyKeyId", 128);
        require(command.getBodyIv() != null && command.getBodyIv().length == 12, "bodyIv must be 12 bytes");
        require(command.getBodyCiphertext() != null && command.getBodyCiphertext().length > 16
                && command.getBodyCiphertext().length <= 65535, "bodyCiphertext is invalid");
        requireSha256(command.getBodyDigestSha256(), "bodyDigestSha256");
        requireUuid(command.getCanonicalSpuId(), "canonicalSpuId");
        requireUuid(command.getCanonicalSkuId(), "canonicalSkuId");
        requireUuid(command.getListingId(), "listingId");
        requireUuid(command.getListingOfferId(), "listingOfferId");
        require("DRAFT".equals(command.getDesiredStatus()), "new community content must start as DRAFT");
        requireOptionalSourceTriple(command.getSourceSystem(), command.getSourceType(), command.getSourceId());
        return execute("CREATE_COMMUNITY_CONTENT", command.getIdempotencyKey(), command, context -> {
            principalReferenceValidationPort.requireActive(context.tenantId, command.getAuthorPrincipalId());
            require(communityMapper.selectContent(context.tenantId, command.getContentId()) == null,
                    "community content already exists");
            CommunityContentDO row = new CommunityContentDO().setContentId(command.getContentId())
                    .setTenantId(context.tenantId).setAuthorPrincipalId(command.getAuthorPrincipalId())
                    .setContentType(command.getContentType()).setBodyRef(command.getBodyRef())
                    .setBodyKeyId(command.getBodyKeyId()).setBodyIv(command.getBodyIv())
                    .setBodyCiphertext(command.getBodyCiphertext())
                    .setBodyDigestSha256(command.getBodyDigestSha256())
                    .setCanonicalSpuId(command.getCanonicalSpuId()).setCanonicalSkuId(command.getCanonicalSkuId())
                    .setListingId(command.getListingId()).setListingOfferId(command.getListingOfferId())
                    .setStatus("DRAFT").setVersion(1L).setSourceSystem(command.getSourceSystem())
                    .setSourceType(command.getSourceType()).setSourceId(command.getSourceId())
                    .setCreatedAt(context.now).setUpdatedAt(context.now);
            require(communityMapper.insert(row) == 1, "failed to create community content");
            Map<String, Object> value = payload("run_id", command.getRunId(), "content_id", row.getContentId(),
                    "author_principal_id", row.getAuthorPrincipalId(), "content_type", row.getContentType(),
                    "body_ref", row.getBodyRef(), "body_digest_sha256", row.getBodyDigestSha256(),
                    "canonical_spu_id", row.getCanonicalSpuId(), "canonical_sku_id", row.getCanonicalSkuId(),
                    "listing_id", row.getListingId(), "listing_offer_id", row.getListingOfferId(),
                    "previous_status", null, "current_status", row.getStatus(),
                    "source_system", row.getSourceSystem(), "source_type", row.getSourceType(), "source_id", row.getSourceId());
            append(context.tenantId, "engagement.community.content_status_changed", "community_content", row.getContentId(),
                    row.getVersion(), (short) 1, command.getOccurredAt(), command.getCorrelationId(), command.getCausationId(),
                    command.getIdempotencyKey(), value, "CREATE_COMMUNITY_CONTENT");
            return result(context.operationId, row.getContentId(), "community_content", row.getStatus(), row.getVersion());
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EngagementCommandResult transitionCommunityContent(TransitionCommunityContentCommand command) {
        validateCommon(command, command == null ? null : command.getIdempotencyKey(),
                command == null ? null : command.getRunId(), command == null ? null : command.getOccurredAt(),
                command == null ? null : command.getCorrelationId(), command == null ? null : command.getCausationId());
        requireUuid(command.getContentId(), "contentId");
        requireUuid(command.getActorPrincipalId(), "actorPrincipalId");
        require(CONTENT_TRANSITION_STATUSES.contains(command.getDesiredStatus()), "invalid content transition status");
        requirePositive(command.getExpectedVersion(), "expectedVersion");
        requireText(command.getReasonCode(), "reasonCode", 64);
        return execute("TRANSITION_COMMUNITY_CONTENT", command.getIdempotencyKey(), command, context -> {
            principalReferenceValidationPort.requireActive(context.tenantId, command.getActorPrincipalId());
            CommunityContentDO row = communityMapper.selectContentForUpdate(context.tenantId, command.getContentId());
            require(row != null, "community content does not exist");
            require(Objects.equals(row.getVersion(), command.getExpectedVersion()), "community content version conflict");
            String previous = row.getStatus();
            require(("DRAFT".equals(previous) && "PENDING_MODERATION".equals(command.getDesiredStatus()))
                            || ("PENDING_MODERATION".equals(previous) && "PUBLISHED".equals(command.getDesiredStatus())),
                    "invalid community content transition");
            require(communityMapper.updateContentStatus(context.tenantId, row.getContentId(), row.getVersion(),
                    command.getDesiredStatus(), context.now) == 1, "community content transition conflict");
            row.setStatus(command.getDesiredStatus()).setVersion(row.getVersion() + 1).setUpdatedAt(context.now);
            Map<String, Object> value = contentStatusPayload(command.getRunId(), row, previous);
            value.put("transition_actor_principal_id", command.getActorPrincipalId());
            value.put("reason_code", command.getReasonCode());
            append(context.tenantId, "engagement.community.content_status_changed", "community_content",
                    row.getContentId(), row.getVersion(), (short) 1, command.getOccurredAt(),
                    command.getCorrelationId(), command.getCausationId(), command.getIdempotencyKey(), value,
                    "TRANSITION_COMMUNITY_CONTENT");
            return result(context.operationId, row.getContentId(), "community_content", row.getStatus(), row.getVersion());
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EngagementCommandResult recordCommunityInteraction(RecordCommunityInteractionCommand command) {
        validateCommon(command, command == null ? null : command.getIdempotencyKey(), command == null ? null : command.getRunId(),
                command == null ? null : command.getOccurredAt(), command == null ? null : command.getCorrelationId(),
                command == null ? null : command.getCausationId());
        requireUuid(command.getInteractionId(), "interactionId"); requireUuid(command.getActorPrincipalId(), "actorPrincipalId");
        require(Set.of("COMMENT", "LIKE", "SHARE", "FOLLOW").contains(command.getInteractionType()),
                "invalid interactionType");
        String requiredTarget = "FOLLOW".equals(command.getInteractionType()) ? "PRINCIPAL" : "CONTENT";
        require(Objects.equals(requiredTarget, command.getTargetType()), "interaction targetType does not match interactionType");
        requireUuid(command.getTargetId(), "targetId");
        if ("COMMENT".equals(command.getInteractionType())) {
            requireText(command.getPayloadRef(), "payloadRef", 512);
            requireText(command.getPayloadKeyId(), "payloadKeyId", 128);
            require(command.getPayloadIv() != null && command.getPayloadIv().length == 12,
                    "payloadIv must be 12 bytes");
            require(command.getPayloadCiphertext() != null && command.getPayloadCiphertext().length > 16
                    && command.getPayloadCiphertext().length <= 65535, "payloadCiphertext is invalid");
            requireSha256(command.getPayloadDigestSha256(), "payloadDigestSha256");
        }
        return execute("RECORD_COMMUNITY_INTERACTION", command.getIdempotencyKey(), command, context -> {
            principalReferenceValidationPort.requireActive(context.tenantId, command.getActorPrincipalId());
            if ("PRINCIPAL".equals(command.getTargetType())) {
                principalReferenceValidationPort.requireActive(context.tenantId, command.getTargetId());
            } else {
                CommunityContentDO content = communityMapper.selectContent(context.tenantId, command.getTargetId());
                require(content != null && "PUBLISHED".equals(content.getStatus()), "target content is not published");
            }
            CommunityInteractionDO row = new CommunityInteractionDO().setInteractionId(command.getInteractionId())
                    .setTenantId(context.tenantId).setActorPrincipalId(command.getActorPrincipalId())
                    .setInteractionType(command.getInteractionType()).setTargetType(command.getTargetType())
                    .setTargetId(command.getTargetId()).setPayloadRef(command.getPayloadRef())
                    .setPayloadKeyId(command.getPayloadKeyId()).setPayloadIv(command.getPayloadIv())
                    .setPayloadCiphertext(command.getPayloadCiphertext())
                    .setPayloadDigestSha256(command.getPayloadDigestSha256())
                    .setOccurredAt(utc(command.getOccurredAt())).setCreatedAt(context.now);
            require(communityMapper.insertInteraction(row) == 1, "failed to persist community interaction");
            Map<String, Object> value = payload("run_id", command.getRunId(), "interaction_id", row.getInteractionId(),
                    "actor_principal_id", row.getActorPrincipalId(), "interaction_type", row.getInteractionType(),
                    "target_type", row.getTargetType(), "target_id", row.getTargetId(),
                    "payload_ref", row.getPayloadRef(), "payload_digest_sha256", row.getPayloadDigestSha256());
            append(context.tenantId, "engagement.community.interaction_recorded", "community_interaction",
                    row.getInteractionId(), 1L, (short) 1, command.getOccurredAt(), command.getCorrelationId(),
                    command.getCausationId(), command.getIdempotencyKey(), value, "RECORD_COMMUNITY_INTERACTION");
            return result(context.operationId, row.getInteractionId(), "community_interaction", "RECORDED", 1L);
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EngagementCommandResult changeCommunityReaction(ChangeCommunityReactionCommand command) {
        validateCommon(command, command == null ? null : command.getIdempotencyKey(),
                command == null ? null : command.getRunId(), command == null ? null : command.getOccurredAt(),
                command == null ? null : command.getCorrelationId(), command == null ? null : command.getCausationId());
        requireUuid(command.getActorPrincipalId(), "actorPrincipalId");
        require("LIKE".equals(command.getReactionType()), "reactionType must be LIKE");
        require("CONTENT".equals(command.getTargetType()), "reaction targetType must be CONTENT");
        requireUuid(command.getTargetId(), "targetId");
        require(Set.of("ACTIVE", "REMOVED").contains(command.getDesiredStatus()),
                "reaction status must be ACTIVE or REMOVED");
        return execute("CHANGE_COMMUNITY_REACTION", command.getIdempotencyKey(), command, context -> {
            principalReferenceValidationPort.requireActive(context.tenantId, command.getActorPrincipalId());
            CommunityContentDO content = communityMapper.selectContent(context.tenantId, command.getTargetId());
            require(content != null && "PUBLISHED".equals(content.getStatus()), "target content is not published");
            CommunityReactionStateDO row = communityMapper.selectReactionForUpdate(context.tenantId,
                    command.getActorPrincipalId(), command.getReactionType(), command.getTargetType(),
                    command.getTargetId());
            String previous = null;
            if (row == null) {
                require("ACTIVE".equals(command.getDesiredStatus()), "a missing reaction can only become ACTIVE");
                requireUuid(command.getReactionId(), "reactionId");
                require(command.getExpectedVersion() == null, "expectedVersion must be absent for a new reaction");
                row = new CommunityReactionStateDO().setReactionId(command.getReactionId())
                        .setTenantId(context.tenantId).setActorPrincipalId(command.getActorPrincipalId())
                        .setReactionType(command.getReactionType()).setTargetType(command.getTargetType())
                        .setTargetId(command.getTargetId()).setStatus("ACTIVE").setVersion(1L)
                        .setCreatedAt(context.now).setUpdatedAt(context.now);
                require(communityMapper.insertReaction(row) == 1, "failed to create community reaction");
            } else {
                require(command.getReactionId() == null || Objects.equals(row.getReactionId(), command.getReactionId()),
                        "reactionId does not match current reaction");
                require(command.getExpectedVersion() != null
                                && Objects.equals(row.getVersion(), command.getExpectedVersion()),
                        "community reaction version conflict");
                require(!Objects.equals(row.getStatus(), command.getDesiredStatus()),
                        "community reaction is already in desired status");
                previous = row.getStatus();
                require(communityMapper.updateReaction(context.tenantId, row.getReactionId(), row.getVersion(),
                        command.getDesiredStatus(), context.now) == 1, "community reaction transition conflict");
                row.setStatus(command.getDesiredStatus()).setVersion(row.getVersion() + 1).setUpdatedAt(context.now);
            }
            Map<String, Object> value = payload("run_id", command.getRunId(), "reaction_id", row.getReactionId(),
                    "actor_principal_id", row.getActorPrincipalId(), "reaction_type", row.getReactionType(),
                    "target_type", row.getTargetType(), "target_id", row.getTargetId(),
                    "previous_status", previous, "current_status", row.getStatus());
            append(context.tenantId, "engagement.community.reaction_status_changed", "community_reaction",
                    row.getReactionId(), row.getVersion(), (short) 1, command.getOccurredAt(),
                    command.getCorrelationId(), command.getCausationId(), command.getIdempotencyKey(), value,
                    "CHANGE_COMMUNITY_REACTION");
            return result(context.operationId, row.getReactionId(), "community_reaction",
                    row.getStatus(), row.getVersion());
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EngagementCommandResult openModerationCase(OpenModerationCaseCommand command) {
        validateCommon(command, command == null ? null : command.getIdempotencyKey(), command == null ? null : command.getRunId(),
                command == null ? null : command.getOccurredAt(), command == null ? null : command.getCorrelationId(),
                command == null ? null : command.getCausationId());
        requireUuid(command.getModerationCaseId(), "moderationCaseId"); requireUuid(command.getContentId(), "contentId");
        requireUuid(command.getReporterPrincipalId(), "reporterPrincipalId");
        requireText(command.getReportReasonCode(), "reportReasonCode", 64);
        return execute("OPEN_MODERATION_CASE", command.getIdempotencyKey(), command, context -> {
            principalReferenceValidationPort.requireActive(context.tenantId, command.getReporterPrincipalId());
            require(communityMapper.selectContent(context.tenantId, command.getContentId()) != null,
                    "reported content does not exist");
            require(communityMapper.selectModerationCase(context.tenantId, command.getModerationCaseId()) == null,
                    "moderation case already exists");
            CommunityModerationCaseDO row = new CommunityModerationCaseDO().setModerationCaseId(command.getModerationCaseId())
                    .setTenantId(context.tenantId).setContentId(command.getContentId())
                    .setReporterPrincipalId(command.getReporterPrincipalId()).setReportReasonCode(command.getReportReasonCode())
                    .setEvidenceRef(command.getEvidenceRef()).setStatus("OPEN").setVersion(1L)
                    .setCreatedAt(context.now).setUpdatedAt(context.now);
            require(communityMapper.insertModerationCase(row) == 1, "failed to create moderation case");
            appendModeration(context, row, null, command.getRunId(), command.getOccurredAt(), command.getCorrelationId(),
                    command.getCausationId(), command.getIdempotencyKey(), (short) 1, "OPEN_MODERATION_CASE");
            return result(context.operationId, row.getModerationCaseId(), "community_moderation_case", row.getStatus(), row.getVersion());
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EngagementCommandResult decideModerationCase(DecideModerationCaseCommand command) {
        validateCommon(command, command == null ? null : command.getIdempotencyKey(), command == null ? null : command.getRunId(),
                command == null ? null : command.getOccurredAt(), command == null ? null : command.getCorrelationId(),
                command == null ? null : command.getCausationId());
        requireUuid(command.getModerationCaseId(), "moderationCaseId"); requireUuid(command.getModeratorPrincipalId(), "moderatorPrincipalId");
        require(Set.of("DISMISS", "HIDE", "REMOVE").contains(command.getDecision()), "invalid moderation decision");
        requireText(command.getDecisionReasonCode(), "decisionReasonCode", 64); requirePositive(command.getExpectedVersion(), "expectedVersion");
        return execute("DECIDE_MODERATION_CASE", command.getIdempotencyKey(), command, context -> {
            principalReferenceValidationPort.requireActive(context.tenantId, command.getModeratorPrincipalId());
            CommunityModerationCaseDO row = communityMapper.selectModerationCaseForUpdate(context.tenantId,
                    command.getModerationCaseId());
            require(row != null, "moderation case does not exist"); require("OPEN".equals(row.getStatus()), "moderation case is not open");
            require(Objects.equals(row.getVersion(), command.getExpectedVersion()), "moderation case version conflict");
            CommunityContentDO content = communityMapper.selectContentForUpdate(context.tenantId, row.getContentId());
            require(content != null, "moderated content does not exist");
            String previous = row.getStatus();
            String caseStatus = "DISMISS".equals(command.getDecision()) ? "DISMISSED" : "ACTIONED";
            require(communityMapper.decideModerationCase(context.tenantId, row.getModerationCaseId(), row.getVersion(),
                    command.getModeratorPrincipalId(), caseStatus, command.getDecision(), command.getDecisionReasonCode(),
                    context.now) == 1, "moderation decision conflict");
            row.setModeratorPrincipalId(command.getModeratorPrincipalId()).setStatus(caseStatus)
                    .setDecision(command.getDecision()).setDecisionReasonCode(command.getDecisionReasonCode())
                    .setVersion(row.getVersion() + 1).setUpdatedAt(context.now);
            appendModeration(context, row, previous, command.getRunId(), command.getOccurredAt(), command.getCorrelationId(),
                    command.getCausationId(), command.getIdempotencyKey(), (short) 1, "DECIDE_MODERATION_CASE");
            if (!"DISMISS".equals(command.getDecision())) {
                String contentPrevious = content.getStatus();
                String contentStatus = "HIDE".equals(command.getDecision()) ? "HIDDEN" : "REMOVED";
                require(communityMapper.updateContentStatus(context.tenantId, content.getContentId(), content.getVersion(),
                        contentStatus, context.now) == 1, "community content moderation transition conflict");
                content.setStatus(contentStatus).setVersion(content.getVersion() + 1).setUpdatedAt(context.now);
                Map<String, Object> value = contentStatusPayload(command.getRunId(), content, contentPrevious);
                value.put("moderation_case_id", row.getModerationCaseId());
                append(context.tenantId, "engagement.community.content_status_changed", "community_content", content.getContentId(),
                        content.getVersion(), (short) 1, command.getOccurredAt(), command.getCorrelationId(), command.getCausationId(),
                        command.getIdempotencyKey(), value, "DECIDE_MODERATION_CASE");
            }
            return result(context.operationId, row.getModerationCaseId(), "community_moderation_case", row.getStatus(), row.getVersion());
        });
    }

    private EngagementCommandResult execute(String commandType, String idempotencyKey, Object command,
                                            DomainAction action) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String requestHash = DigestUtil.sha256Hex(JsonUtils.toJsonString(Map.of(
                "tenant_id", tenantId, "command_type", commandType, "command", command)));
        String attemptToken = UUID.randomUUID().toString();
        operationMapper.insertOrResolve(tenantId, idempotencyKey, commandType, requestHash, attemptToken, now);
        Long operationId = operationMapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve engagement operation");
        EngagementOperationDO operation = operationMapper.selectForUpdate(operationId, tenantId);
        require(operation != null, "engagement operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(operation.getCommandType(), commandType) && Objects.equals(operation.getRequestHash(), requestHash),
                    "idempotency key conflicts with different engagement payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing engagement operation is not complete");
            EngagementCommandResult replay = JsonUtils.parseObject(operation.getResultJson(), EngagementCommandResult.class);
            replay.setDuplicate(true);
            return replay;
        }
        EngagementCommandResult result = action.apply(new CommandContext(tenantId, operationId, now));
        require(operationMapper.markSucceeded(operationId, tenantId, result.getAggregateId(), JsonUtils.toJsonString(result), now) == 1,
                "engagement operation completion conflict");
        return result;
    }

    private NotificationDeliveryDO requireDelivery(Long tenantId, String deliveryId) {
        NotificationDeliveryDO row = deliveryMapper.selectForUpdate(tenantId, deliveryId);
        require(row != null, "notification delivery does not exist");
        return row;
    }

    private void appendDeliveryStatus(CommandContext context, NotificationDeliveryDO row, String previous,
                                      String runId, String providerReference, Instant occurredAt, String correlationId,
                                      String causationId, String idempotencyKey, short sequence, String operation) {
        Map<String, Object> value = payload("run_id", runId, "delivery_id", row.getDeliveryId(),
                "delivery_key", row.getDeliveryKey(), "campaign_id", row.getCampaignId(), "principal_id", row.getPrincipalId(),
                "channel", row.getChannel(), "previous_status", previous, "current_status", row.getStatus(),
                "provider_reference", providerReference);
        append(context.tenantId, "engagement.notification.delivery_status_changed", "notification_delivery",
                row.getDeliveryId(), row.getVersion(), sequence, occurredAt, correlationId, causationId, idempotencyKey,
                value, operation);
    }

    private void appendModeration(CommandContext context, CommunityModerationCaseDO row, String previous, String runId,
                                  Instant occurredAt, String correlationId, String causationId, String idempotencyKey,
                                  short sequence, String operation) {
        Map<String, Object> value = payload("run_id", runId, "moderation_case_id", row.getModerationCaseId(),
                "content_id", row.getContentId(), "reporter_principal_id", row.getReporterPrincipalId(),
                "moderator_principal_id", row.getModeratorPrincipalId(), "reason_code", row.getReportReasonCode(),
                "previous_status", previous, "current_status", row.getStatus(), "decision", row.getDecision(),
                "decision_reason_code", row.getDecisionReasonCode());
        append(context.tenantId, "engagement.community.moderation_status_changed", "community_moderation_case",
                row.getModerationCaseId(), row.getVersion(), sequence, occurredAt, correlationId, causationId,
                idempotencyKey, value, operation);
    }

    private static Map<String, Object> contentStatusPayload(String runId, CommunityContentDO row, String previous) {
        return payload("run_id", runId, "content_id", row.getContentId(),
                "author_principal_id", row.getAuthorPrincipalId(), "content_type", row.getContentType(),
                "body_ref", row.getBodyRef(), "body_digest_sha256", row.getBodyDigestSha256(),
                "canonical_spu_id", row.getCanonicalSpuId(), "canonical_sku_id", row.getCanonicalSkuId(),
                "listing_id", row.getListingId(), "listing_offer_id", row.getListingOfferId(),
                "previous_status", previous, "current_status", row.getStatus(),
                "source_system", row.getSourceSystem(), "source_type", row.getSourceType(),
                "source_id", row.getSourceId());
    }

    private void append(Long tenantId, String eventType, String aggregateType, String aggregateId, Long aggregateVersion,
                        short eventSequence, Instant occurredAt, String correlationId, String causationId,
                        String idempotencyKey, Map<String, Object> payload, String operation) {
        outboxAppender.append(AppendDomainEventCommand.builder().eventType(eventType).schemaVersion(1)
                .sourceSystem(SOURCE_SYSTEM).tenantId(tenantId).aggregateType(aggregateType).aggregateId(aggregateId)
                .aggregateVersion(aggregateVersion).eventSequence(eventSequence).occurredAt(occurredAt)
                .correlationId(correlationId).causationId(causationId).idempotencyKey(idempotencyKey)
                .payload(payload).headers(Map.of("operation", operation)).destination("lakehouse").build());
    }

    private static EngagementCommandResult result(Long operationId, String aggregateId, String aggregateType,
                                                  String status, Long version) {
        return EngagementCommandResult.builder().operationId(operationId).aggregateId(aggregateId)
                .aggregateType(aggregateType).status(status).aggregateVersion(version).duplicate(false).build();
    }

    private static boolean validCampaignTransition(String previous, String current) {
        if (Objects.equals(previous, current)) return false;
        return switch (previous) {
            case "DRAFT" -> "ACTIVE".equals(current);
            case "ACTIVE" -> Set.of("PAUSED", "COMPLETED").contains(current);
            case "PAUSED" -> Set.of("ACTIVE", "COMPLETED").contains(current);
            default -> false;
        };
    }

    private static boolean validReceiptTransition(String previous, String current) {
        return switch (current) {
            case "DELIVERED" -> "SENT".equals(previous);
            case "FAILED" -> Set.of("SENT", "DELIVERED").contains(previous);
            case "OPENED" -> "DELIVERED".equals(previous);
            case "CLICKED" -> Set.of("DELIVERED", "OPENED").contains(previous);
            default -> false;
        };
    }

    private static boolean sourceEquals(NotificationCampaignDO row, SaveNotificationCampaignCommand command) {
        return Objects.equals(row.getSourceSystem(), command.getSourceSystem())
                && Objects.equals(row.getSourceType(), command.getSourceType())
                && Objects.equals(row.getSourceId(), command.getSourceId());
    }

    private static void validateCommon(Object command, String idempotencyKey, String runId, Instant occurredAt,
                                       String correlationId, String causationId) {
        require(command != null, "command is required"); requireText(idempotencyKey, "idempotencyKey", 128);
        require(idempotencyKey.length() >= 8, "idempotencyKey is too short"); requireText(runId, "runId", 64);
        require(occurredAt != null, "occurredAt is required"); requireUuid(correlationId, "correlationId");
        if (causationId != null) requireUuid(causationId, "causationId");
    }

    private static void requireSourceTriple(String system, String type, String id) {
        requireText(system, "sourceSystem", 64); requireText(type, "sourceType", 64); requireText(id, "sourceId", 128);
    }

    private static void requireOptionalSourceTriple(String system, String type, String id) {
        int present = (system == null ? 0 : 1) + (type == null ? 0 : 1) + (id == null ? 0 : 1);
        require(present == 0 || present == 3, "qualified source reference must be wholly absent or present");
        if (present == 3) requireSourceTriple(system, type, id);
    }

    private static void requirePositive(Long value, String field) {
        require(value != null && value > 0, field + " must be positive");
    }

    private static void requireUuid(String value, String field) {
        requireText(value, field, 36);
        try { UUID.fromString(value); } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(field + " must be a UUID", error);
        }
    }

    private static void requireText(String value, String field, int maxLength) {
        require(value != null && !value.isBlank() && value.length() <= maxLength, field + " is required");
    }

    private static void requireSha256(String value, String field) {
        require(value != null && value.matches("^[0-9a-f]{64}$"), field + " must be lowercase SHA-256");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private static LocalDateTime utc(Instant value) { return LocalDateTime.ofInstant(value, ZoneOffset.UTC); }

    private static Map<String, Object> payload(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) result.put((String) values[i], values[i + 1]);
        return result;
    }

    private record CommandContext(Long tenantId, Long operationId, LocalDateTime now) { }

    @FunctionalInterface
    private interface DomainAction { EngagementCommandResult apply(CommandContext context); }
}
