package cn.iocoder.yudao.module.cloudmold.engagement.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.engagement.controller.admin.vo.NotificationCampaignPageReqVO;
import cn.iocoder.yudao.module.cloudmold.engagement.dal.mysql.NotificationCampaignPageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class NotificationCampaignQueryService {

    private final NotificationCampaignPageMapper pageMapper;

    public PageResult<NotificationCampaignPageItem> getPage(NotificationCampaignPageReqVO request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String campaignId = normalize(request.getCampaignId());
        String campaignCode = normalize(request.getCampaignCode());
        String campaignName = normalize(request.getCampaignName());
        String channel = normalizeUpper(request.getChannel());
        String status = normalizeUpper(request.getStatus());
        long total = pageMapper.countPage(tenantId, campaignId, campaignCode, campaignName, channel, status,
                request.getCreatedAtFrom(), request.getCreatedAtTo());
        if (total == 0) {
            return PageResult.empty();
        }
        long offset = (long) (request.getPageNo() - 1) * request.getPageSize();
        return new PageResult<>(pageMapper.selectPage(tenantId, campaignId, campaignCode, campaignName, channel, status,
                request.getCreatedAtFrom(), request.getCreatedAtTo(), offset, request.getPageSize()), total);
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String normalizeUpper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : null;
    }
}
