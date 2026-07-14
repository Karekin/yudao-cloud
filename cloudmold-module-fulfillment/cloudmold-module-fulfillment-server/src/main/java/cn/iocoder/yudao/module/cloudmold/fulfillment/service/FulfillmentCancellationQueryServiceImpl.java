package cn.iocoder.yudao.module.cloudmold.fulfillment.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.fulfillment.api.FulfillmentCancellationQueryApi;
import cn.iocoder.yudao.module.cloudmold.fulfillment.api.FulfillmentCommandResult;
import cn.iocoder.yudao.module.cloudmold.fulfillment.api.FulfillmentLineView;
import cn.iocoder.yudao.module.cloudmold.fulfillment.dal.dataobject.FulfillmentOrderDO;
import cn.iocoder.yudao.module.cloudmold.fulfillment.dal.mysql.FulfillmentItemMapper;
import cn.iocoder.yudao.module.cloudmold.fulfillment.dal.mysql.FulfillmentOrderMapper;
import cn.iocoder.yudao.module.cloudmold.fulfillment.dal.mysql.ShipmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Cancellation-only read boundary. Keeping it independent from the command service avoids an
 * OrderCommandApi -> FulfillmentCommandApi -> OrderQueryApi runtime dependency cycle.
 */
@Service
@RequiredArgsConstructor
public class FulfillmentCancellationQueryServiceImpl implements FulfillmentCancellationQueryApi {

    private final FulfillmentOrderMapper fulfillmentMapper;
    private final FulfillmentItemMapper itemMapper;
    private final ShipmentMapper shipmentMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FulfillmentCommandResult requireCreatedByOrder(String orderId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        requireText(orderId, "orderId", 36);
        List<FulfillmentOrderDO> rows = fulfillmentMapper.selectByOrderForUpdate(tenantId, orderId);
        require(rows.size() == 1, "paid cancellation first slice requires exactly one Fulfillment");
        FulfillmentOrderDO fulfillment = rows.get(0);
        require("CREATED".equals(fulfillment.getStatus()), "Fulfillment is not cancellable before shipment");
        require(shipmentMapper.selectByFulfillment(tenantId, fulfillment.getFulfillmentId()) == null,
                "Fulfillment already has a shipment");
        return result(fulfillment);
    }

    @Override
    public FulfillmentCommandResult requireCancelled(String orderId, String fulfillmentId,
                                                      String cancellationSagaId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        FulfillmentOrderDO fulfillment = fulfillmentMapper.selectByIdForValidation(tenantId, fulfillmentId);
        require(fulfillment != null && Objects.equals(orderId, fulfillment.getOrderId()),
                "Fulfillment does not belong to canonical order");
        require("CANCELLED".equals(fulfillment.getStatus())
                        && Objects.equals(cancellationSagaId, fulfillment.getCancellationSagaId()),
                "Fulfillment is not cancelled by the owning Saga");
        return result(fulfillment);
    }

    private FulfillmentCommandResult result(FulfillmentOrderDO fulfillment) {
        return FulfillmentCommandResult.builder()
                .fulfillmentId(fulfillment.getFulfillmentId())
                .fulfillmentNo(fulfillment.getFulfillmentNo())
                .orderId(fulfillment.getOrderId())
                .currentStatus(fulfillment.getStatus())
                .aggregateVersion(fulfillment.getVersion())
                .items(itemMapper.selectByFulfillment(fulfillment.getTenantId(), fulfillment.getFulfillmentId())
                        .stream().map(item -> FulfillmentLineView.builder()
                                .fulfillmentItemId(item.getFulfillmentItemId())
                                .orderItemId(item.getOrderItemId())
                                .canonicalSkuId(item.getCanonicalSkuId())
                                .quantity(item.getQuantity())
                                .reservationId(item.getReservationId())
                                .build()).toList())
                .duplicate(false)
                .build();
    }

    private static void requireText(String value, String field, int maxLength) {
        require(value != null && !value.isBlank(), field + " is required");
        require(value.length() <= maxLength, field + " is too long");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
