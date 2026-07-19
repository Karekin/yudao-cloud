package cn.iocoder.yudao.module.cloudmold.inventory.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.inventory.controller.admin.vo.InventoryV3BalancePageReqVO;
import cn.iocoder.yudao.module.cloudmold.inventory.controller.admin.vo.InventoryV3LedgerPageReqVO;
import cn.iocoder.yudao.module.cloudmold.inventory.controller.admin.vo.InventoryV3ReservationPageReqVO;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.InventoryV3BalancePageMapper;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.InventoryV3LedgerPageMapper;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.InventoryV3ReservationPageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class InventoryV3QueryService {

    private final InventoryV3BalancePageMapper balancePageMapper;
    private final InventoryV3ReservationPageMapper reservationPageMapper;
    private final InventoryV3LedgerPageMapper ledgerPageMapper;

    public PageResult<InventoryV3BalancePageItem> getBalancePage(InventoryV3BalancePageReqVO request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String skuCode = normalize(request.getSkuCode());
        String warehouseCode = normalize(request.getWarehouseCode());
        String locationCode = normalize(request.getLocationCode());
        String lotCode = normalize(request.getLotCode());
        String ownerType = normalizeUpper(request.getOwnerType());
        String ownerId = normalize(request.getOwnerId());
        String stockStatus = normalizeUpper(request.getStockStatus());
        String qualityStatus = normalizeUpper(request.getQualityStatus());
        Boolean onlyNonZero = Boolean.TRUE.equals(request.getOnlyNonZero()) ? Boolean.TRUE : null;
        long total = balancePageMapper.countBalancePage(tenantId, skuCode, warehouseCode, locationCode, lotCode,
                ownerType, ownerId, stockStatus, qualityStatus, onlyNonZero);
        if (total == 0) {
            return PageResult.empty();
        }
        long offset = offset(request.getPageNo(), request.getPageSize());
        return new PageResult<>(balancePageMapper.selectBalancePage(tenantId, skuCode, warehouseCode, locationCode,
                lotCode, ownerType, ownerId, stockStatus, qualityStatus, onlyNonZero, offset,
                request.getPageSize()), total);
    }

    public PageResult<InventoryV3ReservationPageItem> getReservationPage(InventoryV3ReservationPageReqVO request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String reservationId = normalize(request.getReservationId());
        String businessType = normalizeUpper(request.getBusinessType());
        String businessId = normalize(request.getBusinessId());
        String businessItemId = normalize(request.getBusinessItemId());
        String skuCode = normalize(request.getSkuCode());
        String warehouseCode = normalize(request.getWarehouseCode());
        String locationCode = normalize(request.getLocationCode());
        String lotCode = normalize(request.getLotCode());
        long total = reservationPageMapper.countReservationPage(tenantId, reservationId, businessType, businessId,
                businessItemId, request.getStatus(), skuCode, warehouseCode, locationCode, lotCode);
        if (total == 0) {
            return PageResult.empty();
        }
        long offset = offset(request.getPageNo(), request.getPageSize());
        return new PageResult<>(reservationPageMapper.selectReservationPage(tenantId, reservationId, businessType,
                businessId, businessItemId, request.getStatus(), skuCode, warehouseCode, locationCode, lotCode,
                offset, request.getPageSize()), total);
    }

    public PageResult<InventoryV3LedgerPageItem> getLedgerPage(InventoryV3LedgerPageReqVO request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String movementGroupId = normalize(request.getMovementGroupId());
        String commandType = normalizeUpper(request.getCommandType());
        String businessType = normalizeUpper(request.getBusinessType());
        String businessId = normalize(request.getBusinessId());
        String businessItemId = normalize(request.getBusinessItemId());
        String businessNo = normalize(request.getBusinessNo());
        String skuCode = normalize(request.getSkuCode());
        String warehouseCode = normalize(request.getWarehouseCode());
        String locationCode = normalize(request.getLocationCode());
        String lotCode = normalize(request.getLotCode());
        String entryRole = normalizeUpper(request.getEntryRole());
        long total = ledgerPageMapper.countLedgerPage(tenantId, movementGroupId, commandType, businessType,
                businessId, businessItemId, businessNo, skuCode, warehouseCode, locationCode, lotCode, entryRole,
                request.getOccurredTimeFrom(), request.getOccurredTimeTo());
        if (total == 0) {
            return PageResult.empty();
        }
        long offset = offset(request.getPageNo(), request.getPageSize());
        return new PageResult<>(ledgerPageMapper.selectLedgerPage(tenantId, movementGroupId, commandType,
                businessType, businessId, businessItemId, businessNo, skuCode, warehouseCode, locationCode, lotCode,
                entryRole, request.getOccurredTimeFrom(), request.getOccurredTimeTo(), offset,
                request.getPageSize()), total);
    }

    public InventoryV3BalanceDetailVO getBalanceDetail(String balanceId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String id = normalize(balanceId);
        InventoryV3BalanceDetailVO detail = balancePageMapper.selectBalanceDetail(tenantId, id);
        if (detail == null) {
            return null;
        }
        detail.setRecentLedgerEntries(balancePageMapper.selectRecentLedgerEntries(tenantId, id));
        detail.setActiveAllocations(balancePageMapper.selectActiveAllocations(tenantId, id));
        return detail;
    }

    private static long offset(int pageNo, int pageSize) {
        return (long) (pageNo - 1) * pageSize;
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String normalizeUpper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : null;
    }
}
