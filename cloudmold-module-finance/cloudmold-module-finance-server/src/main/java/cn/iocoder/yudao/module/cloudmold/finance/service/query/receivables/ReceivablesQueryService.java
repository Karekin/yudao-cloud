package cn.iocoder.yudao.module.cloudmold.finance.service.query.receivables;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.finance.api.receivables.ReceivablesQueryApi;
import cn.iocoder.yudao.module.cloudmold.finance.api.receivables.ReceivablesSummaryView;
import cn.iocoder.yudao.module.cloudmold.finance.dal.mysql.receivables.ReceivablesMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReceivablesQueryService implements ReceivablesQueryApi {

    private final ReceivablesMapper receivablesMapper;

    @Override
    public List<ReceivablesSummaryView> summarizeByCustomer(String customerId) {
        requireRef(customerId, "customerId");
        return receivablesMapper.selectCustomerSummaries(TenantContextHolder.getRequiredTenantId(), customerId.trim());
    }

    @Override
    public List<ReceivablesSummaryView> summarizeBySalesContract(String salesContractId) {
        requireRef(salesContractId, "salesContractId");
        return receivablesMapper.selectSalesContractSummaries(TenantContextHolder.getRequiredTenantId(),
                salesContractId.trim());
    }

    private static void requireRef(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
