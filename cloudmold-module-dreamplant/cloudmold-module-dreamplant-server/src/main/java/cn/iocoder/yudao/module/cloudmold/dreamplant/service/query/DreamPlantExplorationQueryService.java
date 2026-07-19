package cn.iocoder.yudao.module.cloudmold.dreamplant.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.dreamplant.controller.admin.vo.DreamPlantExplorationPageReqVO;
import cn.iocoder.yudao.module.cloudmold.dreamplant.dal.mysql.DreamPlantExplorationPageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class DreamPlantExplorationQueryService {

    private final DreamPlantExplorationPageMapper pageMapper;

    public PageResult<DreamPlantExplorationPageItem> getPage(DreamPlantExplorationPageReqVO request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String explorationRunId = normalize(request.getExplorationRunId());
        String mapKey = normalize(request.getMapKey());
        String status = normalizeUpper(request.getStatus());
        String requestedByPrincipalId = normalize(request.getRequestedByPrincipalId());
        long total = pageMapper.countPage(tenantId, explorationRunId, mapKey, status, requestedByPrincipalId,
                request.getCreatedAtFrom(), request.getCreatedAtTo());
        if (total == 0) {
            return PageResult.empty();
        }
        long offset = (long) (request.getPageNo() - 1) * request.getPageSize();
        return new PageResult<>(pageMapper.selectPage(tenantId, explorationRunId, mapKey, status, requestedByPrincipalId,
                request.getCreatedAtFrom(), request.getCreatedAtTo(), offset, request.getPageSize()), total);
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String normalizeUpper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : null;
    }
}
