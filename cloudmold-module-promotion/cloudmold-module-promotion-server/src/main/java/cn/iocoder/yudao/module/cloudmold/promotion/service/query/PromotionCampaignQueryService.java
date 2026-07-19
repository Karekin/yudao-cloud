package cn.iocoder.yudao.module.cloudmold.promotion.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.promotion.controller.admin.vo.PromotionCampaignPageReqVO;
import cn.iocoder.yudao.module.cloudmold.promotion.dal.mysql.PromotionCampaignPageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class PromotionCampaignQueryService {

    private final PromotionCampaignPageMapper pageMapper;

    public PageResult<PromotionCampaignPageItem> getPage(PromotionCampaignPageReqVO request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String campaignId = normalize(request.getCampaignId());
        String campaignCode = normalize(request.getCampaignCode());
        String name = normalize(request.getName());
        String status = normalizeUpper(request.getStatus());
        String campaignKind = normalizeUpper(request.getCampaignKind());
        long total = pageMapper.countPage(tenantId, campaignId, campaignCode, name, status, campaignKind,
                request.getCreatedAtFrom(), request.getCreatedAtTo());
        if (total == 0) {
            return PageResult.empty();
        }
        long offset = (long) (request.getPageNo() - 1) * request.getPageSize();
        return new PageResult<>(pageMapper.selectPage(tenantId, campaignId, campaignCode, name, status, campaignKind,
                request.getCreatedAtFrom(), request.getCreatedAtTo(), offset, request.getPageSize()), total);
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String normalizeUpper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : null;
    }
}
