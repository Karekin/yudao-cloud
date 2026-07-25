package cn.iocoder.yudao.module.cloudmold.appcommerce.service;

import cn.iocoder.yudao.module.cloudmold.aftersale.api.AfterSaleCommand;
import cn.iocoder.yudao.module.cloudmold.aftersale.api.AfterSaleCommandApi;
import cn.iocoder.yudao.module.cloudmold.aftersale.api.AfterSaleQueryApi;
import cn.iocoder.yudao.module.cloudmold.aftersale.api.AfterSaleView;
import cn.iocoder.yudao.module.cloudmold.customerservice.api.CustomerServiceCommandApi;
import cn.iocoder.yudao.module.cloudmold.customerservice.api.CustomerServiceQueryApi;
import cn.iocoder.yudao.module.cloudmold.order.api.AppOrderQueryApi;
import cn.iocoder.yudao.module.cloudmold.order.api.AppOrderView;
import cn.iocoder.yudao.module.cloudmold.order.api.OrderLineView;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AppSupportServiceTest {

    private final AppMemberPrincipalResolver principalResolver = mock(AppMemberPrincipalResolver.class);
    private final AppOrderQueryApi orderQueryApi = mock(AppOrderQueryApi.class);
    private final AfterSaleCommandApi afterSaleCommandApi = mock(AfterSaleCommandApi.class);
    private final AfterSaleQueryApi afterSaleQueryApi = mock(AfterSaleQueryApi.class);
    private final CustomerServiceCommandApi customerServiceCommandApi = mock(CustomerServiceCommandApi.class);
    private final CustomerServiceQueryApi customerServiceQueryApi = mock(CustomerServiceQueryApi.class);
    private final AppFacadeOperationService facadeOperationService = mock(AppFacadeOperationService.class);

    private final AppSupportService service = new AppSupportService(principalResolver, orderQueryApi, afterSaleCommandApi,
            afterSaleQueryApi, customerServiceCommandApi, customerServiceQueryApi, facadeOperationService);

    @Test
    void requestAfterSaleShouldEnforceOwnedOrderItemAndUseControlledReasonCodeOnly() {
        when(principalResolver.requireCurrent()).thenReturn(AppMemberPrincipalView.builder()
                .principalId("principal-member-1").build());
        when(orderQueryApi.requireOwned("principal-member-1", "order-1")).thenReturn(AppOrderView.builder()
                .orderId("order-1")
                .items(List.of(OrderLineView.builder().orderItemId("item-1").build()))
                .build());
        when(facadeOperationService.execute(eq("REQUEST_AFTER_SALE"), eq("after-sale-idem-001"),
                eq("principal-member-1"), any(), eq(AfterSaleView.class), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    AppFacadeOperationService.Operation<AfterSaleView> operation =
                            invocation.getArgument(5, AppFacadeOperationService.Operation.class);
                    return new AppFacadeOperationService.Replay<>(operation.run(Instant.parse("2026-07-25T00:00:00Z")), false);
                });
        when(afterSaleCommandApi.execute(any())).thenReturn(AfterSaleView.builder().afterSaleId("after-sale-1").build());

        AfterSaleView result = service.requestAfterSale("after-sale-idem-001", "order-1", "item-1",
                BigDecimal.ONE, "RETURN_AND_REFUND", "SIZE_NOT_FIT");

        ArgumentCaptor<AfterSaleCommand> command = ArgumentCaptor.forClass(AfterSaleCommand.class);
        verify(afterSaleCommandApi).execute(command.capture());
        assertThat(result.getAfterSaleId()).isEqualTo("after-sale-1");
        assertThat(command.getValue().getReason()).isEqualTo("SIZE_NOT_FIT");
        assertThat(command.getValue().getAfterSaleType()).isEqualTo("RETURN_AND_REFUND");
        assertThat(command.getValue().getReasonCode()).isEqualTo("SIZE_NOT_FIT");
        assertThat(command.getValue().getResponsibility()).isEqualTo("BUYER");
        assertThat(command.getValue().getOccurredAt()).isEqualTo(Instant.parse("2026-07-25T00:00:00Z"));
    }

    @Test
    void requestAfterSaleShouldRejectOrderItemOutsideOwnedOrder() {
        when(principalResolver.requireCurrent()).thenReturn(AppMemberPrincipalView.builder()
                .principalId("principal-member-1").build());
        when(orderQueryApi.requireOwned("principal-member-1", "order-1")).thenReturn(AppOrderView.builder()
                .orderId("order-1")
                .items(List.of(OrderLineView.builder().orderItemId("item-2").build()))
                .build());

        assertThatThrownBy(() -> service.requestAfterSale("after-sale-idem-002", "order-1", "item-1",
                BigDecimal.ONE, "RETURN_AND_REFUND", "SIZE_NOT_FIT"))
                .hasMessage("order item does not exist");
        verifyNoInteractions(facadeOperationService, afterSaleCommandApi);
    }

    @Test
    void createTicketShouldFailClosedWhenReferencedAfterSaleIsNotOwnedByCurrentMember() {
        when(principalResolver.requireCurrent()).thenReturn(AppMemberPrincipalView.builder()
                .principalId("principal-member-1").build());
        when(facadeOperationService.execute(eq("CREATE_TICKET"), eq("ticket-idem-001"), eq("principal-member-1"),
                any(), any(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            AppFacadeOperationService.Operation<Object> operation =
                    invocation.getArgument(5, AppFacadeOperationService.Operation.class);
            return new AppFacadeOperationService.Replay<>(operation.run(Instant.parse("2026-07-25T00:00:00Z")), false);
        });
        when(afterSaleQueryApi.get("after-sale-1")).thenReturn(AfterSaleView.builder()
                .afterSaleId("after-sale-1").orderId("order-foreign").build());
        when(orderQueryApi.requireOwned("principal-member-1", "order-foreign"))
                .thenThrow(new IllegalArgumentException("canonical order does not exist"));

        assertThatThrownBy(() -> service.createTicket("ticket-idem-001", "AFTER_SALE_SUPPORT",
                "AFTER_SALE", "after-sale-1"))
                .hasMessage("canonical order does not exist");
        verifyNoInteractions(customerServiceCommandApi, customerServiceQueryApi);
    }
}
