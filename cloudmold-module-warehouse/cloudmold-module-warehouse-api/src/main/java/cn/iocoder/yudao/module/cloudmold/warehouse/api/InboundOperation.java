package cn.iocoder.yudao.module.cloudmold.warehouse.api;

/**
 * 入库（ASN/收货/上架）权威聚合的操作枚举。
 * 每个值对应补货闭环下游的一段物理作业状态变迁，全程事件驱动、可重放。
 */
public enum InboundOperation {
    /** 建立发货通知单 ASN（头+行，含预期量与单价） */
    CREATE_ASN,
    /** 发送 ASN（标记供应商已发货，进入在途） */
    SEND_ASN,
    /** 取消 ASN（未收货前失败关闭） */
    CANCEL_ASN,
    /** 完成收货：每行落库存 RECEIVE（NON_SELLABLE/PENDING_QC 暂存余额），回写实收/短溢量 */
    COMPLETE_RECEIPT,
    /** 完成上架：记录最终库位，标记物理作业结束 */
    COMPLETE_PUTAWAY
}
