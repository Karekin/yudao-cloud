package cn.iocoder.yudao.module.cloudmold.risk.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.risk.controller.admin.vo.RiskReviewPageReqVO;
import cn.iocoder.yudao.module.cloudmold.risk.dal.mysql.RiskReviewPageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class RiskReviewQueryService {

    private final RiskReviewPageMapper pageMapper;

    public PageResult<RiskReviewPageItem> getPage(RiskReviewPageReqVO request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String caseId = normalize(request.getCaseId());
        String clusterId = normalize(request.getClusterId());
        String status = normalizeUpper(request.getStatus());
        String reviewerPrincipalId = normalize(request.getReviewerPrincipalId());
        long total = pageMapper.countPage(tenantId, caseId, clusterId, status, reviewerPrincipalId,
                request.getCreatedAtFrom(), request.getCreatedAtTo());
        if (total == 0) {
            return PageResult.empty();
        }
        long offset = (long) (request.getPageNo() - 1) * request.getPageSize();
        return new PageResult<>(pageMapper.selectPage(tenantId, caseId, clusterId, status, reviewerPrincipalId,
                request.getCreatedAtFrom(), request.getCreatedAtTo(), offset, request.getPageSize()), total);
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String normalizeUpper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : null;
    }
}
