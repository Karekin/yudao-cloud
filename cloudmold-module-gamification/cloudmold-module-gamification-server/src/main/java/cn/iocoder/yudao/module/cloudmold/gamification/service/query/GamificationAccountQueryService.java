package cn.iocoder.yudao.module.cloudmold.gamification.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.gamification.controller.admin.vo.GamificationAccountPageReqVO;
import cn.iocoder.yudao.module.cloudmold.gamification.dal.mysql.GamificationAccountPageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class GamificationAccountQueryService {

    private final GamificationAccountPageMapper pageMapper;

    public PageResult<GamificationAccountPageItem> getPage(GamificationAccountPageReqVO request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String accountId = normalize(request.getAccountId());
        String gameId = normalize(request.getGameId());
        String ownerType = normalizeUpper(request.getOwnerType());
        String ownerRef = normalize(request.getOwnerRef());
        String currencyCode = normalizeUpper(request.getCurrencyCode());
        String status = normalizeUpper(request.getStatus());
        long total = pageMapper.countPage(tenantId, accountId, gameId, ownerType, ownerRef, currencyCode, status,
                request.getCreatedAtFrom(), request.getCreatedAtTo());
        if (total == 0) {
            return PageResult.empty();
        }
        long offset = (long) (request.getPageNo() - 1) * request.getPageSize();
        return new PageResult<>(pageMapper.selectPage(tenantId, accountId, gameId, ownerType, ownerRef, currencyCode,
                status, request.getCreatedAtFrom(), request.getCreatedAtTo(), offset, request.getPageSize()), total);
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String normalizeUpper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : null;
    }
}
