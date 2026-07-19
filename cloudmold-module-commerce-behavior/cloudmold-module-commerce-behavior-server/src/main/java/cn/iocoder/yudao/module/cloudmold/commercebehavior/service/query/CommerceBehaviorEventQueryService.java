package cn.iocoder.yudao.module.cloudmold.commercebehavior.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.commercebehavior.controller.admin.vo.CommerceBehaviorEventPageReqVO;
import cn.iocoder.yudao.module.cloudmold.commercebehavior.dal.mysql.CommerceBehaviorEventPageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class CommerceBehaviorEventQueryService {

    private final CommerceBehaviorEventPageMapper pageMapper;

    public PageResult<BehaviorEventPageItem> getPage(CommerceBehaviorEventPageReqVO request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String behaviorId = normalize(request.getBehaviorId());
        String sessionId = normalize(request.getSessionId());
        String principalId = normalize(request.getPrincipalId());
        String behaviorType = normalizeUpper(request.getBehaviorType());
        String canonicalSpuId = normalize(request.getCanonicalSpuId());
        String shopId = normalize(request.getShopId());
        String merchantId = normalize(request.getMerchantId());
        String channelCode = normalize(request.getChannelCode());
        long total = pageMapper.countPage(tenantId, behaviorId, sessionId, principalId, behaviorType, canonicalSpuId,
                shopId, merchantId, channelCode, request.getOccurredAtFrom(), request.getOccurredAtTo());
        if (total == 0) {
            return PageResult.empty();
        }
        long offset = (long) (request.getPageNo() - 1) * request.getPageSize();
        return new PageResult<>(pageMapper.selectPage(tenantId, behaviorId, sessionId, principalId, behaviorType,
                canonicalSpuId, shopId, merchantId, channelCode, request.getOccurredAtFrom(),
                request.getOccurredAtTo(), offset, request.getPageSize()), total);
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String normalizeUpper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : null;
    }
}
