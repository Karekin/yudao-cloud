package cn.iocoder.yudao.module.cloudmold.warehouse.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InboundCommand {
    /** 幂等信封：操作 + 幂等键 + 事件溯源键 + 关联链 + 发生时间（与 WarehouseNetworkCommand 同形） */
    private InboundOperation operation;
    private String idempotencyKey;
    private String sourceEventId;
    private String correlationId;
    private String causationId;
    private Instant occurredAt;
    /** CREATE_ASN / SEND_ASN / CANCEL_ASN 使用 */
    private AsnDefinition asn;
    /** COMPLETE_RECEIPT 使用：每个收货行驱动一次库存 RECEIVE */
    private List<ReceiptLineDefinition> receiptLines;
    /** COMPLETE_PUTAWAY 使用 */
    private PutawayDefinition putaway;
}
