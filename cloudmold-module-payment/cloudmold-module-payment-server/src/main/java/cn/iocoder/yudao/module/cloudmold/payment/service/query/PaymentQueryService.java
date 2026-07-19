package cn.iocoder.yudao.module.cloudmold.payment.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.payment.controller.admin.vo.PaymentPageReqVO;
import cn.iocoder.yudao.module.cloudmold.payment.dal.mysql.PaymentQueryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class PaymentQueryService {

    private static final String INTERNAL_TEST = "INTERNAL_TEST";

    private final PaymentQueryMapper paymentQueryMapper;

    public PageResult<PaymentPageItem> getPage(PaymentPageReqVO request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String paymentNo = normalize(request.getPaymentNo());
        String orderId = normalize(request.getOrderId());
        String status = normalizeUpper(request.getStatus());
        String providerCode = normalizeUpper(request.getProviderCode());
        long total = paymentQueryMapper.countPaymentPage(tenantId, paymentNo, orderId, status, providerCode,
                request.getTestMode());
        if (total == 0) {
            return PageResult.empty();
        }
        long offset = offset(request.getPageNo(), request.getPageSize());
        return new PageResult<>(paymentQueryMapper.selectPaymentPage(tenantId, paymentNo, orderId, status,
                providerCode, request.getTestMode(), offset, request.getPageSize()).stream()
                .map(PaymentQueryService::toPageItem)
                .toList(), total);
    }

    private static PaymentPageItem toPageItem(PaymentPageRow row) {
        PaymentPageItem item = new PaymentPageItem();
        item.setPaymentId(row.getPaymentId());
        item.setPaymentNo(row.getPaymentNo());
        item.setOrderId(row.getOrderId());
        item.setStatus(row.getStatus());
        item.setPayableAmountMinor(row.getPayableAmountMinor());
        item.setCapturedAmountMinor(row.getCapturedAmountMinor());
        item.setRefundedAmountMinor(row.getRefundedAmountMinor());
        item.setRemainingAmountMinor(Math.subtractExact(defaultZero(row.getCapturedAmountMinor()),
                defaultZero(row.getRefundedAmountMinor())));
        item.setCurrencyCode(row.getCurrencyCode());
        item.setProviderCode(row.getProviderCode());
        item.setProviderTransactionReferenceMasked(maskProviderReference(row.getProviderTransactionReference()));
        item.setTestMode(row.getTestMode());
        item.setExecutionMode(isInternalTest(row) ? INTERNAL_TEST : "LIVE");
        item.setAggregateVersion(row.getAggregateVersion());
        item.setCapturedAt(row.getCapturedAt());
        item.setRefundedAt(row.getRefundedAt());
        item.setCreatedAt(row.getCreatedAt());
        item.setUpdatedAt(row.getUpdatedAt());
        return item;
    }

    private static boolean isInternalTest(PaymentPageRow row) {
        return Boolean.TRUE.equals(row.getTestMode()) || INTERNAL_TEST.equals(row.getProviderCode());
    }

    private static String maskProviderReference(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 8) {
            return "*".repeat(trimmed.length());
        }
        return trimmed.substring(0, 4) + "*".repeat(trimmed.length() - 8)
                + trimmed.substring(trimmed.length() - 4);
    }

    private static long defaultZero(Long value) {
        return value == null ? 0L : value;
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
