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
        return CommunityContentView.builder().contentId(row.getContentId()).authorPrincipalId(row.getAuthorPrincipalId())
                .contentType(row.getContentType()).bodyRef(row.getBodyRef()).status(row.getStatus())
                .version(row.getVersion()).updatedAt(instant(row.getUpdatedAt())).build();
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

    private static Instant instant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
