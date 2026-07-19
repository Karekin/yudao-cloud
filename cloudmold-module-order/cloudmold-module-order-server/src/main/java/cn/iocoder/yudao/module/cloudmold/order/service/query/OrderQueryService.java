package cn.iocoder.yudao.module.cloudmold.order.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.order.controller.admin.vo.OrderPageReqVO;
import cn.iocoder.yudao.module.cloudmold.order.dal.mysql.OrderQueryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class OrderQueryService {

    private final OrderQueryMapper orderQueryMapper;

    public PageResult<OrderPageItem> getOrderPage(OrderPageReqVO request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String orderId = normalize(request.getOrderId());
        String orderNo = normalize(request.getOrderNo());
        String buyerId = normalize(request.getBuyerId());
        String status = normalize(request.getStatus());
        long total = orderQueryMapper.countOrderPage(tenantId, orderId, orderNo, buyerId, status);
        if (total == 0) {
            return PageResult.empty();
        }
        long offset = (long) (request.getPageNo() - 1) * request.getPageSize();
        return new PageResult<>(orderQueryMapper.selectOrderPage(tenantId, orderId, orderNo, buyerId, status,
                offset, request.getPageSize()), total);
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
