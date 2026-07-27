package cn.iocoder.yudao.module.cloudmold.order.service.workflow;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.order.api.workflow.OrderWorkflowFactsApi;
import cn.iocoder.yudao.module.cloudmold.order.api.workflow.OrderWorkflowFactsView;
import cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.OrderHeaderDO;
import cn.iocoder.yudao.module.cloudmold.order.dal.mysql.OrderHeaderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class OrderWorkflowFactsServiceImpl implements OrderWorkflowFactsApi {

    private final OrderHeaderMapper orderMapper;

    @Override
    public OrderWorkflowFactsView get(String orderId) {
        if (!StringUtils.hasText(orderId)) {
            throw new IllegalArgumentException("orderId is required");
        }
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        OrderHeaderDO order = orderMapper.selectForPayment(tenantId, orderId.trim());
        if (order == null) {
            throw new IllegalArgumentException("canonical order does not exist");
        }
        return OrderWorkflowFactsView.builder()
                .orderId(order.getOrderId())
                .orderNo(order.getOrderNo())
                .runId(order.getRunId())
                .status(order.getStatus())
                .aggregateVersion(order.getVersion())
                .payableAmountMinor(order.getPayableAmountMinor())
                .currencyCode(order.getCurrencyCode())
                .paymentId(order.getPaymentId())
                .fulfillmentId(order.getFulfillmentId())
                .shipmentId(order.getShipmentId())
                .refundId(order.getRefundId())
                .cancellationSagaId(order.getCancellationSagaId())
                .build();
    }
}
