package cn.iocoder.yudao.module.cloudmold.finance.api;

public interface FinanceCloseQueryApi {
    FinanceCloseView requirePeriod(String periodId);

    ChannelStatementView requireStatement(String statementId);
}
