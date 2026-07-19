package cn.iocoder.yudao.module.cloudmold.catalog.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.catalog.controller.admin.vo.CatalogSkuPageReqVO;
import cn.iocoder.yudao.module.cloudmold.catalog.dal.mysql.CatalogQueryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class CatalogQueryService {

    private final CatalogQueryMapper queryMapper;

    public PageResult<CatalogSkuPageItem> getSkuPage(CatalogSkuPageReqVO request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String skuCode = normalize(request.getSkuCode());
        String spuCode = normalize(request.getSpuCode());
        long total = queryMapper.countSkuPage(tenantId, skuCode, spuCode, request.getStatus());
        if (total == 0) {
            return PageResult.empty();
        }
        long offset = (long) (request.getPageNo() - 1) * request.getPageSize();
        return new PageResult<>(queryMapper.selectSkuPage(tenantId, skuCode, spuCode, request.getStatus(),
                offset, request.getPageSize()), total);
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
