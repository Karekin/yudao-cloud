package cn.iocoder.yudao.module.cloudmold.warehouse.api;

public interface InboundQueryApi {

    InboundStageView requireInboundStage(String procurementOrderId);

    InboundReceiptProgressView requireReceiptProgress(String procurementOrderId);

    record InboundStageView(String procurementOrderId, String asnId, String asnStatus,
                            String latestReceiptId, String latestReceiptStatus,
                            String nextWaitingEventCode, String nextWaitingEventLabel,
                            boolean terminal) {
    }
}
