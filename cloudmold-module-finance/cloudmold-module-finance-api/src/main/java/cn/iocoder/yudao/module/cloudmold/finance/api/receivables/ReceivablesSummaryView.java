package cn.iocoder.yudao.module.cloudmold.finance.api.receivables;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceivablesSummaryView implements Serializable {
    private String customerId;
    private String salesContractId;
    private String currencyCode;
    private Long receivablePlanCount;
    private Long receivablePlanAmountMinor;
    private Long receivablePlanAllocatedMinor;
    private Long receivablePlanOutstandingMinor;
    private Long receiptCount;
    private Long receiptAmountMinor;
    private Long receiptAllocatedMinor;
    private Long receiptUnallocatedMinor;
    private Long allocationCount;
    private Long allocationAmountMinor;
}
