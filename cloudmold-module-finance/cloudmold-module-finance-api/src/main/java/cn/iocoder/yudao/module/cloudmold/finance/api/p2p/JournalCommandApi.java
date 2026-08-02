package cn.iocoder.yudao.module.cloudmold.finance.api.p2p;

public interface JournalCommandApi {
    ProcureToPayResult reverse(JournalCommands.Reverse command, String actorPrincipalId);
}
