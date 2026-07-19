package cn.iocoder.yudao.module.cloudmold.operationsintelligence.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.operationsintelligence.controller.admin.vo.OperationsAlertPageReqVO;
import cn.iocoder.yudao.module.cloudmold.operationsintelligence.dal.mysql.OperationsAlertPageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class OperationsAlertQueryService {

    private final OperationsAlertPageMapper pageMapper;

    public PageResult<OperationsAlertPageItem> getPage(OperationsAlertPageReqVO request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String alertId = normalize(request.getAlertId());
        String alertCode = normalize(request.getAlertCode());
        String sourceType = normalizeUpper(request.getSourceType());
        String severity = normalizeUpper(request.getSeverity());
        String category = normalize(request.getCategory());
        String status = normalizeUpper(request.getStatus());
        String currentActorPrincipalId = normalize(request.getCurrentActorPrincipalId());
        long total = pageMapper.countPage(tenantId, alertId, alertCode, sourceType, severity, category, status,
                currentActorPrincipalId, request.getCreatedAtFrom(), request.getCreatedAtTo());
        if (total == 0) {
            return PageResult.empty();
        }
        long offset = (long) (request.getPageNo() - 1) * request.getPageSize();
        return new PageResult<>(pageMapper.selectPage(tenantId, alertId, alertCode, sourceType, severity, category,
                status, currentActorPrincipalId, request.getCreatedAtFrom(), request.getCreatedAtTo(), offset,
                request.getPageSize()), total);
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String normalizeUpper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : null;
    }
}
