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
public class FinanceCloseView implements Serializable {
    private String periodId;
    private String periodCode;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private String currencyCode;
    private String status;
    private Integer statementCount;
    private Integer reconciledStatementCount;
    private Integer openDifferenceCount;
    private Integer settlementBatchCount;
    private Integer settledBatchCount;
    private Integer journalEntryCount;
    private Integer postedJournalCount;
    private Long statementNetAmountMinor;
    private Long settledAmountMinor;
    private Long version;
    private String openedByPrincipalId;
    private String closedByPrincipalId;
    private LocalDateTime openedAt;
    private LocalDateTime closedAt;
}
