package cn.iocoder.yudao.module.cloudmold.finance.service.query;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.finance.api.ChannelStatementView;
import cn.iocoder.yudao.module.cloudmold.finance.api.FinanceCloseQueryApi;
import cn.iocoder.yudao.module.cloudmold.finance.api.FinanceCloseView;
import cn.iocoder.yudao.module.cloudmold.finance.dal.mysql.FinanceCloseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FinanceCloseQueryService implements FinanceCloseQueryApi {
    private final FinanceCloseMapper mapper;

    @Override
    public FinanceCloseView requirePeriod(String periodId) {
        FinanceCloseView result = mapper.selectPeriodView(
                TenantContextHolder.getRequiredTenantId(), periodId);
        if (result == null) {
            throw new IllegalArgumentException("accounting period not found");
        }
        return result;
    }

    @Override
    public ChannelStatementView requireStatement(String statementId) {
        ChannelStatementView result = mapper.selectStatementView(
                TenantContextHolder.getRequiredTenantId(), statementId);
        if (result == null) {
            throw new IllegalArgumentException("channel statement not found");
        }
        return result;
    }
}
