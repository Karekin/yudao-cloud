package cn.iocoder.yudao.module.cloudmold.warehouse.api;

/**
 * 入库权威聚合命令入口（ASN/收货/上架），与 {@link WarehouseNetworkCommandApi} 并列同模块。
 * 仓网主数据是静态生命周期，ASN/收货是时序业务单据，聚合边界不同，但共用同形幂等信封。
 */
public interface InboundCommandApi {
    InboundCommandResult execute(InboundCommand command);
}
