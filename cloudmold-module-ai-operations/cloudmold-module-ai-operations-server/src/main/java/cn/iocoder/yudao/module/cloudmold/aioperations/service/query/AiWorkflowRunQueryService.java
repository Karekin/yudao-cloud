package cn.iocoder.yudao.module.cloudmold.aioperations.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.aioperations.controller.admin.vo.AiWorkflowRunPageReqVO;
import cn.iocoder.yudao.module.cloudmold.aioperations.dal.mysql.AiWorkflowRunPageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AiWorkflowRunQueryService {

    private final AiWorkflowRunPageMapper pageMapper;

    public PageResult<AiWorkflowRunPageItem> getPage(AiWorkflowRunPageReqVO request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String runId = normalize(request.getRunId());
        String runKey = normalize(request.getRunKey());
        String applicationId = normalize(request.getApplicationId());
        String workflowId = normalize(request.getWorkflowId());
        String triggerType = normalize(request.getTriggerType());
        String status = normalizeUpper(request.getStatus());
        long total = pageMapper.countPage(tenantId, runId, runKey, applicationId, workflowId, triggerType, status,
                request.getCreatedAtFrom(), request.getCreatedAtTo());
        if (total == 0) {
            return PageResult.empty();
        }
        long offset = (long) (request.getPageNo() - 1) * request.getPageSize();
        return new PageResult<>(pageMapper.selectPage(tenantId, runId, runKey, applicationId, workflowId, triggerType, status,
                request.getCreatedAtFrom(), request.getCreatedAtTo(), offset, request.getPageSize()), total);
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String normalizeUpper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : null;
    }
}
