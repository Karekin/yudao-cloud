package cn.iocoder.yudao.module.cloudmold.finance.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelStatementView implements Serializable {
    private String statementId;
    private String statementCode;
    private String periodId;
    private String channelCode;
    private LocalDate statementDate;
    private String currencyCode;
    private Long grossAmountMinor;
    private Long refundAmountMinor;
    private Long feeAmountMinor;
    private Long netSettlementAmountMinor;
    private Long expectedBusinessNetAmountMinor;
    private Long differenceAmountMinor;
    private String status;
    private String differenceId;
    private String differenceStatus;
    private String settlementBatchId;
    private String settlementStatus;
    private Long version;
    private LocalDateTime importedAt;
    private LocalDateTime reconciledAt;
}
