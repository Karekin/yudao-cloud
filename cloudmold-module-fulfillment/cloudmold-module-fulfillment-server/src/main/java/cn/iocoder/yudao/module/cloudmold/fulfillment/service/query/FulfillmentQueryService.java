package cn.iocoder.yudao.module.cloudmold.fulfillment.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.fulfillment.controller.admin.vo.FulfillmentPageReqVO;
import cn.iocoder.yudao.module.cloudmold.fulfillment.dal.mysql.FulfillmentQueryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class FulfillmentQueryService {

    private final FulfillmentQueryMapper queryMapper;

    public PageResult<FulfillmentPageItem> getPage(FulfillmentPageReqVO request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String fulfillmentId = normalize(request.getFulfillmentId());
        String fulfillmentNo = normalize(request.getFulfillmentNo());
        String orderId = normalize(request.getOrderId());
        String orderNo = normalize(request.getOrderNo());
        String sellerId = normalize(request.getSellerId());
        String warehouseId = normalize(request.getWarehouseId());
        String status = normalizeUpper(request.getStatus());
        String shipmentId = normalize(request.getShipmentId());
        String shipmentStatus = normalizeUpper(request.getShipmentStatus());
        String carrierCode = normalizeUpper(request.getCarrierCode());
        String waybillNo = normalize(request.getWaybillNo());
        long total = queryMapper.countPage(tenantId, fulfillmentId, fulfillmentNo, orderId, orderNo, sellerId,
                warehouseId, status, shipmentId, shipmentStatus, carrierCode, waybillNo);
        if (total == 0) {
            return PageResult.empty();
        }
        long offset = (long) (request.getPageNo() - 1) * request.getPageSize();
        return new PageResult<>(queryMapper.selectPage(tenantId, fulfillmentId, fulfillmentNo, orderId, orderNo,
                sellerId, warehouseId, status, shipmentId, shipmentStatus, carrierCode, waybillNo, offset,
                request.getPageSize()), total);
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String normalizeUpper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : null;
    }
}
