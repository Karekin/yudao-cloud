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
    private AsnDefinition asn;
    private ReceiptDefinition receipt;
    private PutawayDefinition putaway;
}
