package cn.iocoder.yudao.module.cloudmold.finance.api.inventorycontrol;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryControlAccountingResult {
    private Long operationId;
    private boolean duplicate;
    private String inventoryControlPostingId;
    private String sourceType;
    private String sourceDocumentId;
    private String sourceLineId;
    private String sourceReferenceId;
    private Long aggregateVersion;
    private String status;
    private String journalEntryId;
    private String journalCode;
    private String reversalJournalEntryId;
    private String currencyCode;
    private Long totalAmountMinor;
}
