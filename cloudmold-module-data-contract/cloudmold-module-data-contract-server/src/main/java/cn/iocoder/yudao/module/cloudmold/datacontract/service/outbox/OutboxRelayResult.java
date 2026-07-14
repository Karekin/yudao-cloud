package cn.iocoder.yudao.module.cloudmold.datacontract.service.outbox;

public record OutboxRelayResult(int candidates, int claimed, int published, int retried, int dead, int unsupported) {
}
