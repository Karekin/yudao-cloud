package cn.iocoder.yudao.module.cloudmold.tokenplatform.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.tokenplatform.controller.admin.vo.QuotaAccountPageReqVO;
import cn.iocoder.yudao.module.cloudmold.tokenplatform.dal.mysql.QuotaAccountPageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class QuotaAccountQueryService {

    private final QuotaAccountPageMapper pageMapper;

    public PageResult<QuotaAccountPageItem> getPage(QuotaAccountPageReqVO request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String accountId = normalize(request.getAccountId());
        String principalId = normalize(request.getPrincipalId());
        String status = normalizeUpper(request.getStatus());
        long total = pageMapper.countPage(tenantId, accountId, principalId, status,
                request.getCreatedAtFrom(), request.getCreatedAtTo());
        if (total == 0) {
            return PageResult.empty();
        }
        long offset = (long) (request.getPageNo() - 1) * request.getPageSize();
        return new PageResult<>(pageMapper.selectPage(tenantId, accountId, principalId, status,
                request.getCreatedAtFrom(), request.getCreatedAtTo(), offset, request.getPageSize()), total);
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String normalizeUpper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : null;
    }
}
