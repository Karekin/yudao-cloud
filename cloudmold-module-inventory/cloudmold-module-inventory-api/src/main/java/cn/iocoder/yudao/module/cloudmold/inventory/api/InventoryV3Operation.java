package cn.iocoder.yudao.module.cloudmold.inventory.api;

public enum InventoryV3Operation {
    RECEIVE,
    RESERVE,
    SHIP,
    RETURN,
    RELEASE,
    /** 采购确认后增加在途库存（只改 in_transit，不动 on_hand） */
    INTRANSIT_ADD,
    /** 收货时清减在途库存（与 RECEIVE 配对，在途转为实收） */
    INTRANSIT_SETTLE,
    /** 质检放行翻转：把源维度（如 NON_SELLABLE/PENDING_QC）的 Q 量翻转到目标维度（PASS→SELLABLE/QUALIFIED；FAIL→NON_SELLABLE/DAMAGED），双账 OUT/IN counterparty 互指 */
    QUALITY_RELEASE,
    /** 库位移动：库存状态和质检状态不变，只把 Q 量从源库位双账转移到目标库位 */
    RELOCATE
}
