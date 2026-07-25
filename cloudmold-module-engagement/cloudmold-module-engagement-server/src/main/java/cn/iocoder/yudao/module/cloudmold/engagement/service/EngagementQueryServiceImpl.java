package cn.iocoder.yudao.module.cloudmold.engagement.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.engagement.api.EngagementQueryApi;
import cn.iocoder.yudao.module.cloudmold.engagement.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.engagement.dal.mysql.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EngagementQueryServiceImpl implements EngagementQueryApi {

    private final FavoriteMapper favoriteMapper;
    private final NotificationCampaignMapper campaignMapper;
    private final NotificationDeliveryMapper deliveryMapper;
    private final CommunityMapper communityMapper;

    @Override
    public FavoriteView getFavorite(String principalId, String canonicalSpuId) {
        FavoriteDO row = favoriteMapper.selectByBusinessKey(tenant(), principalId, canonicalSpuId);
        require(row != null, "favorite does not exist");
        return FavoriteView.builder().favoriteId(row.getFavoriteId()).principalId(row.getPrincipalId())
                .canonicalSpuId(row.getCanonicalSpuId()).status(row.getStatus()).version(row.getVersion())
                .sourceSystem(row.getSourceSystem()).sourceType(row.getSourceType()).sourceId(row.getSourceId())
                .updatedAt(instant(row.getUpdatedAt())).build();
    }

    @Override
    public NotificationCampaignView getNotificationCampaign(String campaignId) {
        NotificationCampaignDO row = campaignMapper.selectByTenantAndId(tenant(), campaignId);
        require(row != null, "notification campaign does not exist");
        return NotificationCampaignView.builder().campaignId(row.getCampaignId()).campaignCode(row.getCampaignCode())
                .campaignName(row.getCampaignName()).channel(row.getChannel()).status(row.getStatus())
                .version(row.getVersion()).updatedAt(instant(row.getUpdatedAt())).build();
    }

    @Override
    public NotificationDeliveryView getNotificationDelivery(String deliveryId) {
        NotificationDeliveryDO row = deliveryMapper.selectByTenantAndId(tenant(), deliveryId);
        require(row != null, "notification delivery does not exist");
        return NotificationDeliveryView.builder().deliveryId(row.getDeliveryId()).deliveryKey(row.getDeliveryKey())
                .campaignId(row.getCampaignId()).principalId(row.getPrincipalId()).channel(row.getChannel())
                .status(row.getStatus()).attemptCount(row.getAttemptCount()).receiptCount(row.getReceiptCount())
                .version(row.getVersion()).updatedAt(instant(row.getUpdatedAt())).build();
    }

    @Override
    public CommunityContentView getCommunityContent(String contentId) {
        CommunityContentDO row = communityMapper.selectContent(tenant(), contentId);
        require(row != null, "community content does not exist");
        return contentView(row, null);
    }

    @Override
    public CommunityContentPageView pagePublishedCommunityContent(int pageNo, int pageSize,
                                                                   String currentPrincipalId) {
        require(pageNo > 0, "pageNo must be positive");
        require(pageSize > 0 && pageSize <= 100, "pageSize must be between 1 and 100");
        Long tenantId = tenant();
        List<CommunityContentView> list = communityMapper
                .selectPublishedPage(tenantId, (pageNo - 1) * pageSize, pageSize)
                .stream().map(row -> contentView(row, currentPrincipalId)).toList();
        return CommunityContentPageView.builder().list(list).total(communityMapper.countPublished(tenantId))
                .pageNo(pageNo).pageSize(pageSize).build();
    }

    @Override
    public CommunityContentView getPublishedCommunityContent(String contentId, String currentPrincipalId) {
        CommunityContentDO row = communityMapper.selectContent(tenant(), contentId);
        require(row != null && "PUBLISHED".equals(row.getStatus()) && "POST".equals(row.getContentType()),
                "published community content does not exist");
        return contentView(row, currentPrincipalId);
    }

    @Override
    public CommunityCommentPageView pageCommunityComments(String contentId, int pageNo, int pageSize) {
        require(pageNo > 0, "pageNo must be positive");
        require(pageSize > 0 && pageSize <= 100, "pageSize must be between 1 and 100");
        Long tenantId = tenant();
        CommunityContentDO content = communityMapper.selectContent(tenantId, contentId);
        require(content != null && "PUBLISHED".equals(content.getStatus()),
                "published community content does not exist");
        List<CommunityCommentView> list = communityMapper
                .selectComments(tenantId, contentId, (pageNo - 1) * pageSize, pageSize)
                .stream().map(row -> CommunityCommentView.builder()
                        .interactionId(row.getInteractionId()).actorPrincipalId(row.getActorPrincipalId())
                        .targetContentId(row.getTargetId()).payloadRef(row.getPayloadRef())
                        .payloadKeyId(row.getPayloadKeyId()).payloadIv(row.getPayloadIv())
                        .payloadCiphertext(row.getPayloadCiphertext())
                        .payloadDigestSha256(row.getPayloadDigestSha256())
                        .occurredAt(instant(row.getOccurredAt())).build()).toList();
        return CommunityCommentPageView.builder().list(list)
                .total(communityMapper.countComments(tenantId, contentId))
                .pageNo(pageNo).pageSize(pageSize).build();
    }

    @Override
    public ModerationCaseView getModerationCase(String moderationCaseId) {
        CommunityModerationCaseDO row = communityMapper.selectModerationCase(tenant(), moderationCaseId);
        require(row != null, "moderation case does not exist");
        return ModerationCaseView.builder().moderationCaseId(row.getModerationCaseId()).contentId(row.getContentId())
                .reporterPrincipalId(row.getReporterPrincipalId()).moderatorPrincipalId(row.getModeratorPrincipalId())
                .status(row.getStatus()).decision(row.getDecision()).decisionReasonCode(row.getDecisionReasonCode())
                .version(row.getVersion()).updatedAt(instant(row.getUpdatedAt())).build();
    }

    private static Long tenant() { return TenantContextHolder.getRequiredTenantId(); }

    private CommunityContentView contentView(CommunityContentDO row, String currentPrincipalId) {
        Long tenantId = tenant();
        CommunityReactionStateDO reaction = currentPrincipalId == null ? null
                : communityMapper.selectReaction(tenantId, currentPrincipalId, "LIKE", "CONTENT", row.getContentId());
        return CommunityContentView.builder().contentId(row.getContentId())
                .authorPrincipalId(row.getAuthorPrincipalId()).contentType(row.getContentType())
                .bodyRef(row.getBodyRef()).bodyKeyId(row.getBodyKeyId()).bodyIv(row.getBodyIv())
                .bodyCiphertext(row.getBodyCiphertext()).bodyDigestSha256(row.getBodyDigestSha256())
                .canonicalSpuId(row.getCanonicalSpuId()).canonicalSkuId(row.getCanonicalSkuId())
                .listingId(row.getListingId()).listingOfferId(row.getListingOfferId())
                .status(row.getStatus()).version(row.getVersion())
                .likeCount(communityMapper.countActiveLikes(tenantId, row.getContentId()))
                .commentCount(communityMapper.countComments(tenantId, row.getContentId()))
                .likedByCurrentUser(reaction != null && "ACTIVE".equals(reaction.getStatus()))
                .currentUserReactionId(reaction == null ? null : reaction.getReactionId())
                .currentUserReactionVersion(reaction == null ? null : reaction.getVersion())
                .createdAt(instant(row.getCreatedAt())).updatedAt(instant(row.getUpdatedAt())).build();
    }

    private static Instant instant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
