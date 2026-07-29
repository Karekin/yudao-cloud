package cn.iocoder.yudao.module.cloudmold.warehouse.service.query;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.InboundQueryApi;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.AsnDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.PutawayDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.ReceiptDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.AsnMapper;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.PutawayMapper;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.ReceiptMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 入库阶段聚合查询：按补货 recommendationId 汇总 ASN/收货/上架状态，作为 Temporal BusinessEventWait 的 canonical 查询，
 * 替代 legacy {@code YudaoWarehouseInboundQueryApi.getPurchaseInboundTerminal} 的反查式实现。
 */
@Service
@RequiredArgsConstructor
public class InboundQueryService implements InboundQueryApi {

    private static final String SOURCE_BUSINESS_TYPE = "REPLENISHMENT";

    private final AsnMapper asnMapper;
    private final ReceiptMapper receiptMapper;
    private final PutawayMapper putawayMapper;

    @Override
    public InboundStageView requireInboundStage(String recommendationId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        AsnDO asn = asnMapper.selectBySourceRef(tenantId, SOURCE_BUSINESS_TYPE, recommendationId);
        // 尚未开始入库：无任何 ASN，非终态（仍由上游创建 ASN 推进）
        if (asn == null) {
            return new InboundStageView(recommendationId, null, null, null, null, null, false);
        }
        if ("CANCELLED".equals(asn.getStatus())) {
            return new InboundStageView(recommendationId, asn.getStatus(), null, null,
                    null, "ASN cancelled", true);
        }
        ReceiptDO receipt = receiptMapper.selectByAsn(tenantId, asn.getAsnId());
        String receiptStatus = receipt == null ? null : receipt.getStatus();
        PutawayDO putaway = receipt == null ? null : putawayMapper.selectByReceipt(tenantId, receipt.getReceiptId());
        String putawayStatus = putaway == null ? null : putaway.getStatus();

        // 上架完成 = 补货闭环下游终点（终态，唤醒等待中的 Temporal run 成功）
        if (putaway != null && "COMPLETED".equals(putaway.getStatus())) {
            return new InboundStageView(recommendationId, asn.getStatus(), receiptStatus, putawayStatus,
                    null, "Inbound completed", true);
        }
        // 非终态：按最前进度给出下一个预期事件码（对齐切片 A 事件映射）
        if (receipt != null && "COMPLETED".equals(receipt.getStatus())) {
            return new InboundStageView(recommendationId, asn.getStatus(), receiptStatus, putawayStatus,
                    "PUTAWAY_COMPLETED", "Waiting for putaway completion", false);
        }
        return new InboundStageView(recommendationId, asn.getStatus(), receiptStatus, putawayStatus,
                "WAREHOUSE_RECEIPT_COMPLETED", "Waiting for warehouse receipt completion", false);
    }
}
