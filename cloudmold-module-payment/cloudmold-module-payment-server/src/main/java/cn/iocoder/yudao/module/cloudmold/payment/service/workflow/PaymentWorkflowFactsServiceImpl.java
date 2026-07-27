package cn.iocoder.yudao.module.cloudmold.payment.service.workflow;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.payment.api.PaymentWorkflowFactsApi;
import cn.iocoder.yudao.module.cloudmold.payment.api.PaymentWorkflowFactsView;
import cn.iocoder.yudao.module.cloudmold.payment.dal.dataobject.PaymentDO;
import cn.iocoder.yudao.module.cloudmold.payment.dal.mysql.PaymentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PaymentWorkflowFactsServiceImpl implements PaymentWorkflowFactsApi {

    private final PaymentMapper paymentMapper;

    @Override
    public PaymentWorkflowFactsView get(String orderId, String paymentId) {
        if (!StringUtils.hasText(orderId) || !StringUtils.hasText(paymentId)) {
            throw new IllegalArgumentException("orderId and paymentId are required");
        }
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        PaymentDO payment = paymentMapper.selectById(paymentId.trim());
        if (payment == null || !Objects.equals(payment.getTenantId(), tenantId)) {
            throw new IllegalArgumentException("canonical payment does not exist");
        }
        if (!Objects.equals(payment.getOrderId(), orderId.trim())) {
            throw new IllegalArgumentException("payment does not belong to canonical order");
        }
        return PaymentWorkflowFactsView.builder()
                .paymentId(payment.getPaymentId())
                .orderId(payment.getOrderId())
                .status(payment.getStatus())
                .aggregateVersion(payment.getVersion())
                .capturedAmountMinor(payment.getCapturedAmountMinor())
                .refundedAmountMinor(payment.getRefundedAmountMinor())
                .currencyCode(payment.getCurrencyCode())
                .providerCode(payment.getProviderCode())
                .testMode(payment.getTestMode())
                .build();
    }
}
