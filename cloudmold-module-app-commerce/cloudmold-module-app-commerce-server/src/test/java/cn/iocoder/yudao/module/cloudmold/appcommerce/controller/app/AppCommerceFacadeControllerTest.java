package cn.iocoder.yudao.module.cloudmold.appcommerce.controller.app;

import cn.iocoder.yudao.module.cloudmold.commercebehavior.api.CommerceBehaviorCommandApi.CommerceBehaviorCommandResult;
import cn.iocoder.yudao.module.cloudmold.payment.api.PaymentCommandResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppCommerceFacadeControllerTest {

    @Test
    void paymentViewShouldExposeStableStatusAndAttributionContract() {
        PaymentCommandResult payment = PaymentCommandResult.builder()
                .paymentId("payment-1")
                .orderId("order-1")
                .currentStatus("CAPTURED")
                .capturedAmountMinor(39800L)
                .currencyCode("CNY")
                .aggregateVersion(2L)
                .duplicate(false)
                .build();
        CommerceBehaviorCommandResult attribution = CommerceBehaviorCommandResult.builder()
                .status("CHECKOUT_IN_PROGRESS")
                .aggregateVersion(4L)
                .duplicate(false)
                .build();

        AppCommerceFacadeController.AppPaymentCaptureView view =
                AppCommerceFacadeController.AppPaymentCaptureView.from(payment, attribution);

        assertThat(view.getCurrentStatus()).isEqualTo("CAPTURED");
        assertThat(view.getPaymentId()).isEqualTo("payment-1");
        assertThat(view.getBehaviorVersion()).isEqualTo(4L);
        assertThat(view.getAttributionDuplicate()).isFalse();
    }
}
