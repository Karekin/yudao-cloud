package cn.iocoder.yudao.module.cloudmold.finance.service.receivables;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.crm.api.contract.SalesContractSourceView;
import cn.iocoder.yudao.module.cloudmold.crm.api.contract.SalesContractValidationApi;
import cn.iocoder.yudao.module.cloudmold.finance.api.receivables.ReceivableSourceValidationPort;
import cn.iocoder.yudao.module.cloudmold.finance.api.receivables.ReceivableSourceView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@RequiredArgsConstructor
public class CrmReceivableSourceValidationAdapter implements ReceivableSourceValidationPort {

    private final SalesContractValidationApi salesContractValidationApi;

    @Override
    public ReceivableSourceView requireActiveSalesReceivableSource(Long tenantId, String customerId,
                                                                   String salesContractId) {
        require(Objects.equals(TenantContextHolder.getRequiredTenantId(), tenantId), "tenant context mismatch");
        requireText(customerId, "customerId");
        requireText(salesContractId, "salesContractId");
        SalesContractSourceView source = salesContractValidationApi.requireReceivableSource(
                customerId.trim(), salesContractId.trim());
        require(source != null, "sales contract validation returned no source");
        require(Objects.equals(customerId.trim(), source.getCustomerId()), "sales contract customer mismatch");
        require(Objects.equals(salesContractId.trim(), source.getSalesContractId()), "sales contract id mismatch");
        require("ACTIVE".equals(source.getStatus()), "sales contract is not active");
        require(source.getTotalAmountMinor() != null && source.getTotalAmountMinor() > 0,
                "sales contract amount must be positive");
        requireText(source.getCurrencyCode(), "sales contract currencyCode");
        return ReceivableSourceView.builder().customerId(source.getCustomerId())
                .salesContractId(source.getSalesContractId()).currencyCode(source.getCurrencyCode())
                .contractAmountMinor(source.getTotalAmountMinor()).build();
    }

    private static void requireText(String value, String field) {
        require(value != null && !value.isBlank(), field + " is required");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
