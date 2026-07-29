package cn.iocoder.yudao.module.cloudmold.finance.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinanceCloseResult implements Serializable {
    private Long operationId;
    private Boolean duplicate;
    private String aggregateType;
    private String aggregateId;
    private Long aggregateVersion;
    private String status;
    private String periodId;
    private String statementId;
    private String differenceId;
    private String settlementBatchId;
    private String journalEntryId;
}
