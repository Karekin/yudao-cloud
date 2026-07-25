package cn.iocoder.yudao.module.cloudmold.supplyplanning.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.controller.admin.vo.SupplyPlanningPageReqVO;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.dal.mysql.SupplyPlanningMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class SupplyPlanningQueryService {
    private final SupplyPlanningMapper mapper;

    public PageResult<SupplyPlanningWorkItem> getPage(SupplyPlanningPageReqVO request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String itemType = upper(request.getItemType());
        String status = upper(request.getStatus());
        long total = mapper.countWorkItems(tenantId, itemType, status);
        if (total == 0) {
            return PageResult.empty();
        }
        long offset = (long) (request.getPageNo() - 1) * request.getPageSize();
        return new PageResult<>(
                mapper.selectWorkItems(tenantId, itemType, status, offset, request.getPageSize()),
                total);
    }

    private static String upper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : null;
    }
}
