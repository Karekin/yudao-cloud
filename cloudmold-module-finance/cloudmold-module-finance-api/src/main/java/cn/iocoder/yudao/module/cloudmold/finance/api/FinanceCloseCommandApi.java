package cn.iocoder.yudao.module.cloudmold.finance.api;

public interface FinanceCloseCommandApi {
    FinanceCloseResult execute(FinanceCloseCommand command, String actorPrincipalId);
}
