package cn.iocoder.yudao.module.cloudmold.warehouse.api;

/**
 * 入库阶段查询：按补货 recommendationId 聚合 ASN/收货/上架状态，作为 Temporal BusinessEventWait 的 canonical 查询，
 * 替代 legacy {@code YudaoWarehouseInboundQueryApi} 反查。
 */
public interface InboundQueryApi {

    /** 要求返回指定补货单的入库阶段视图；无任何入库单据时返回全 null 阶段（尚未开始入库）。 */
    InboundStageView requireInboundStage(String recommendationId);

    /**
     * 入库阶段视图。nextWaitingEventCode 对齐切片 A 的 wait-events 事件码映射；
     * terminal=true 表示入库闭环已结束（上架完成或取消），用于唤醒等待中的 Temporal run。
     */
    record InboundStageView(String recommendationId, String asnStatus, String receiptStatus,
                            String putawayStatus, String nextWaitingEventCode, String nextWaitingEventLabel,
                            boolean terminal) {
    }
}
