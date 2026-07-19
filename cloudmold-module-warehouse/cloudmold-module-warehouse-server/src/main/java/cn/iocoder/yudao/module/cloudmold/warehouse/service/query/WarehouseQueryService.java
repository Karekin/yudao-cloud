package cn.iocoder.yudao.module.cloudmold.warehouse.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.warehouse.controller.admin.vo.WarehouseLocationPageReqVO;
import cn.iocoder.yudao.module.cloudmold.warehouse.controller.admin.vo.WarehousePageReqVO;
import cn.iocoder.yudao.module.cloudmold.warehouse.controller.admin.vo.WarehouseZonePageReqVO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.WarehouseQueryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * CloudMold 规范仓网只读查询服务。
 * 显式按当前租户隔离；走纯 SQL 分页，不依赖 MyBatis-Plus tenant 插件。
 */
@Service
@RequiredArgsConstructor
public class WarehouseQueryService {

    private final WarehouseQueryMapper queryMapper;

    public PageResult<WarehousePageItem> getWarehousePage(WarehousePageReqVO request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String warehouseCode = normalize(request.getWarehouseCode());
        String warehouseType = normalize(request.getWarehouseType());
        String status = normalize(request.getStatus());
        long total = queryMapper.countWarehousePage(tenantId, warehouseCode, warehouseType, status);
        if (total == 0) {
            return PageResult.empty();
        }
        long offset = (long) (request.getPageNo() - 1) * request.getPageSize();
        return new PageResult<>(queryMapper.selectWarehousePage(tenantId, warehouseCode,
                warehouseType, status, offset, request.getPageSize()), total);
    }

    public PageResult<WarehouseZonePageItem> getZonePage(WarehouseZonePageReqVO request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String warehouseId = normalize(request.getWarehouseId());
        String zoneCode = normalize(request.getZoneCode());
        String status = normalize(request.getStatus());
        long total = queryMapper.countZonePage(tenantId, warehouseId, zoneCode, status);
        if (total == 0) {
            return PageResult.empty();
        }
        long offset = (long) (request.getPageNo() - 1) * request.getPageSize();
        return new PageResult<>(queryMapper.selectZonePage(tenantId, warehouseId, zoneCode,
                status, offset, request.getPageSize()), total);
    }

    public PageResult<WarehouseLocationPageItem> getLocationPage(WarehouseLocationPageReqVO request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String warehouseId = normalize(request.getWarehouseId());
        String zoneId = normalize(request.getZoneId());
        String locationCode = normalize(request.getLocationCode());
        String status = normalize(request.getStatus());
        long total = queryMapper.countLocationPage(tenantId, warehouseId, zoneId, locationCode, status);
        if (total == 0) {
            return PageResult.empty();
        }
        long offset = (long) (request.getPageNo() - 1) * request.getPageSize();
        return new PageResult<>(queryMapper.selectLocationPage(tenantId, warehouseId, zoneId,
                locationCode, status, offset, request.getPageSize()), total);
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
