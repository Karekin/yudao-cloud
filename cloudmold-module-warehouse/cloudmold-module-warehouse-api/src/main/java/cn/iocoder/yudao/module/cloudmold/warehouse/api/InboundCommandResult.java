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
    private String procurementOrderId;
    private String asnId;
    private String asnNo;
    private String asnStatus;
    private String receiptId;
    private String receiptNo;
    private String receiptStatus;
    private String putawayId;
    private Long aggregateVersion;
    private Integer processedLineCount;
    private String status;
    private boolean duplicate;
}
