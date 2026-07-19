package cn.iocoder.yudao.module.cloudmold.metadata.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.metadata.controller.admin.vo.MetadataDefinitionPageReqVO;
import cn.iocoder.yudao.module.cloudmold.metadata.dal.mysql.MetadataDefinitionPageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MetadataDefinitionQueryService {

    private final MetadataDefinitionPageMapper pageMapper;

    public PageResult<MetadataDefinitionPageItem> getPage(MetadataDefinitionPageReqVO request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String definitionId = normalize(request.getDefinitionId());
        String definitionKind = normalizeUpper(request.getDefinitionKind());
        String definitionCode = normalize(request.getDefinitionCode());
        String displayName = normalize(request.getDisplayName());
        String status = normalizeUpper(request.getStatus());
        String ownerPrincipalId = normalize(request.getOwnerPrincipalId());
        long total = pageMapper.countPage(tenantId, definitionId, definitionKind, definitionCode, displayName, status,
                ownerPrincipalId, request.getCreatedAtFrom(), request.getCreatedAtTo());
        if (total == 0) {
            return PageResult.empty();
        }
        long offset = (long) (request.getPageNo() - 1) * request.getPageSize();
        return new PageResult<>(pageMapper.selectPage(tenantId, definitionId, definitionKind, definitionCode, displayName,
                status, ownerPrincipalId, request.getCreatedAtFrom(), request.getCreatedAtTo(), offset,
                request.getPageSize()), total);
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String normalizeUpper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : null;
    }
}
