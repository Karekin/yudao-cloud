package cn.iocoder.yudao.module.cloudmold.warehouse.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InboundCommandResult {
    private Long operationId;
    private String asnId;
    private String receiptId;
    private String putawayId;
    private Long aggregateVersion;
    private String status;
    private boolean duplicate;
}
