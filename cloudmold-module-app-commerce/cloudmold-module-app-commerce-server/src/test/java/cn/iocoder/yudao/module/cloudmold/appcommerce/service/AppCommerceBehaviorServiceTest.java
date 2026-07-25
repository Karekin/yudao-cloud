package cn.iocoder.yudao.module.cloudmold.appcommerce.service;

import cn.iocoder.yudao.module.cloudmold.commercebehavior.api.CommerceBehaviorCommandApi;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AppCommerceBehaviorServiceTest {

    private final AppMemberPrincipalResolver principalResolver = mock(AppMemberPrincipalResolver.class);
    private final CommerceBehaviorCommandApi commandApi = mock(CommerceBehaviorCommandApi.class);
    private final AppCommerceBehaviorService service =
            new AppCommerceBehaviorService(principalResolver, commandApi);

    @Test
    void linkShouldDerivePrincipalFromAuthenticatedMember() {
        when(principalResolver.requireCurrent()).thenReturn(AppMemberPrincipalView.builder()
                .principalId("principal-member-1").build());
        when(commandApi.linkSessionIdentity(any())).thenReturn(
                CommerceBehaviorCommandApi.CommerceBehaviorCommandResult.builder()
                        .aggregateId("session-1").aggregateVersion(2L).build());

        service.linkCurrentMember("link-idem-001", "f3734897-1a03-4887-bf9e-34a898d03594", 1L);

        ArgumentCaptor<CommerceBehaviorCommandApi.LinkCommerceSessionIdentityCommand> command =
                ArgumentCaptor.forClass(CommerceBehaviorCommandApi.LinkCommerceSessionIdentityCommand.class);
        verify(commandApi).linkSessionIdentity(command.capture());
        assertThat(command.getValue().getPrincipalId()).isEqualTo("principal-member-1");
        assertThat(command.getValue().getExpectedSessionVersion()).isEqualTo(1L);
    }

    @Test
    void attributionShouldUseServerAuthenticatedContextAndControlledSource() {
        when(principalResolver.requireCurrent()).thenReturn(AppMemberPrincipalView.builder()
                .principalId("principal-member-1").build());
        when(commandApi.attributePaidOrder(any())).thenReturn(
                CommerceBehaviorCommandApi.CommerceBehaviorCommandResult.builder()
                        .aggregateId("session-1").aggregateVersion(4L).build());

        service.attributePayment("payment-idem-001:attribution",
                "f3734897-1a03-4887-bf9e-34a898d03594", 3L,
                "checkout:12345678", "order-1", "payment-1");

        ArgumentCaptor<CommerceBehaviorCommandApi.AttributePaidOrderCommand> command =
                ArgumentCaptor.forClass(CommerceBehaviorCommandApi.AttributePaidOrderCommand.class);
        verify(commandApi).attributePaidOrder(command.capture());
        assertThat(command.getValue().getOrderId()).isEqualTo("order-1");
        assertThat(command.getValue().getPaymentId()).isEqualTo("payment-1");
        assertThat(command.getValue().getExpectedSessionVersion()).isEqualTo(3L);
        assertThat(command.getValue().getSourceSystem()).isEqualTo("YSHOPPING_UNIAPP");
    }
}
