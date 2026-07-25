package cn.iocoder.yudao.module.cloudmold.appcommerce.service;

import cn.iocoder.yudao.module.cloudmold.aftersale.api.*;
import cn.iocoder.yudao.module.cloudmold.customerservice.api.*;
import cn.iocoder.yudao.module.cloudmold.order.api.AppOrderQueryApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AppSupportService {

    private final AppMemberPrincipalResolver principalResolver;
    private final AppOrderQueryApi orderQueryApi;
    private final AfterSaleCommandApi afterSaleCommandApi;
    private final AfterSaleQueryApi afterSaleQueryApi;
    private final CustomerServiceCommandApi customerServiceCommandApi;
    private final CustomerServiceQueryApi customerServiceQueryApi;
    private final AppFacadeOperationService facadeOperationService;

    @Transactional(rollbackFor = Exception.class)
    public AfterSaleView requestAfterSale(String idempotencyKey, String orderId, String orderItemId,
                                          BigDecimal quantity, String afterSaleType, String reasonCode) {
        AppMemberPrincipalView principal = principalResolver.requireCurrent();
        var ownedOrder = orderQueryApi.requireOwned(principal.getPrincipalId(), orderId);
        require(ownedOrder.getItems() != null && ownedOrder.getItems().stream()
                        .anyMatch(item -> orderItemId.equals(item.getOrderItemId())),
                "order item does not exist");
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("orderId", orderId);
        payload.put("orderItemId", orderItemId);
        payload.put("quantity", quantity);
        payload.put("afterSaleType", afterSaleType);
        payload.put("reasonCode", reasonCode);
        AppFacadeOperationService.Replay<AfterSaleView> replay = facadeOperationService.execute(
                "REQUEST_AFTER_SALE", idempotencyKey, principal.getPrincipalId(), payload, AfterSaleView.class,
                occurredAt -> requestAfterSaleOnce(idempotencyKey, orderId, orderItemId, quantity,
                        afterSaleType, reasonCode, occurredAt));
        replay.value().setDuplicate(replay.duplicate() || Boolean.TRUE.equals(replay.value().getDuplicate()));
        return replay.value();
    }

    private AfterSaleView requestAfterSaleOnce(String idempotencyKey, String orderId, String orderItemId,
                                               BigDecimal quantity, String afterSaleType, String reasonCode,
                                               Instant occurredAt) {
        String correlationId = UUID.nameUUIDFromBytes(("after-sale:" + idempotencyKey).getBytes()).toString();
        return afterSaleCommandApi.execute(AfterSaleCommand.builder().operation(AfterSaleOperation.REQUEST)
                .idempotencyKey(idempotencyKey).runId(correlationId).orderId(orderId).orderItemId(orderItemId)
                .requestedQuantity(quantity).afterSaleType(afterSaleType).reasonCode(reasonCode)
                .responsibility("BUYER").reason(reasonCode).correlationId(correlationId)
                .causationId(correlationId).occurredAt(occurredAt).build());
    }

    public AfterSaleView getAfterSale(String afterSaleId) {
        AppMemberPrincipalView principal = principalResolver.requireCurrent();
        AfterSaleView view = afterSaleQueryApi.get(afterSaleId);
        orderQueryApi.requireOwned(principal.getPrincipalId(), view.getOrderId());
        return view;
    }

    public AppAfterSalePageView listAfterSales(int pageNo, int pageSize) {
        AppMemberPrincipalView principal = principalResolver.requireCurrent();
        return afterSaleQueryApi.listOwned(principal.getPrincipalId(), pageNo, pageSize);
    }

    @Transactional(rollbackFor = Exception.class)
    public CustomerServiceView createTicket(String idempotencyKey, String categoryCode,
                                            String referenceType, String referenceId) {
        AppMemberPrincipalView principal = principalResolver.requireCurrent();
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("categoryCode", categoryCode);
        payload.put("referenceType", referenceType);
        payload.put("referenceId", referenceId);
        AppFacadeOperationService.Replay<CustomerServiceView> replay = facadeOperationService.execute(
                "CREATE_TICKET", idempotencyKey, principal.getPrincipalId(), payload, CustomerServiceView.class,
                occurredAt -> createTicketOnce(idempotencyKey, categoryCode, referenceType, referenceId,
                        principal, occurredAt));
        replay.value().setDuplicate(replay.duplicate() || Boolean.TRUE.equals(replay.value().getDuplicate()));
        return replay.value();
    }

    private CustomerServiceView createTicketOnce(String idempotencyKey, String categoryCode,
                                                 String referenceType, String referenceId,
                                                 AppMemberPrincipalView principal, Instant now) {
        String sourceSystem;
        if ("ORDER".equals(referenceType)) {
            orderQueryApi.requireOwned(principal.getPrincipalId(), referenceId);
            sourceSystem = "cloudmold-order";
        } else if ("AFTER_SALE".equals(referenceType)) {
            AfterSaleView sale = afterSaleQueryApi.get(referenceId);
            orderQueryApi.requireOwned(principal.getPrincipalId(), sale.getOrderId());
            sourceSystem = "cloudmold-aftersales";
        } else if ("GENERAL".equals(referenceType)) {
            require(referenceId == null || referenceId.isBlank(),
                    "GENERAL ticket must not fabricate a business reference");
            sourceSystem = null;
        } else {
            throw new IllegalArgumentException("referenceType must be ORDER, AFTER_SALE, or GENERAL");
        }
        String runId = UUID.nameUUIDFromBytes(("ticket:" + idempotencyKey).getBytes()).toString();
        String ticketNo = "CS" + runId.replace("-", "").substring(0, 20).toUpperCase(Locale.ROOT);
        CustomerServiceView created = customerServiceCommandApi.execute(CustomerServiceCommand.builder()
                .operation(CustomerServiceOperation.CREATE_TICKET).idempotencyKey(idempotencyKey + ":create")
                .runId(runId).ticketNo(ticketNo).customerPrincipalId(principal.getPrincipalId())
                .channelCode("APP").priority("NORMAL").categoryCode(categoryCode)
                .correlationId(runId).causationId(runId).occurredAt(now).build());
        CustomerServiceView linked = created;
        if (sourceSystem != null) {
            linked = customerServiceCommandApi.execute(CustomerServiceCommand.builder()
                    .operation(CustomerServiceOperation.LINK_REFERENCE).idempotencyKey(idempotencyKey + ":reference")
                    .runId(runId).ticketId(created.getTicketId()).expectedVersion(created.getTicketVersion())
                    .referenceSourceSystem(sourceSystem).referenceType(referenceType).referenceId(referenceId)
                    .correlationId(runId).causationId(runId).occurredAt(now).build());
        }
        return customerServiceQueryApi.requireOwnedTicket(created.getTicketId(), principal.getPrincipalId());
    }

    public CustomerServiceView getTicket(String ticketId) {
        AppMemberPrincipalView principal = principalResolver.requireCurrent();
        return customerServiceQueryApi.requireOwnedTicket(ticketId, principal.getPrincipalId());
    }

    public AppCustomerServicePageView listTickets(int pageNo, int pageSize) {
        AppMemberPrincipalView principal = principalResolver.requireCurrent();
        return customerServiceQueryApi.listOwnedTickets(principal.getPrincipalId(), pageNo, pageSize);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
