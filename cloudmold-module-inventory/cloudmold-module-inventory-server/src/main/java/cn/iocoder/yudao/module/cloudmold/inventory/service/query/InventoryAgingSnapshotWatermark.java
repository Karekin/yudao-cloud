package cn.iocoder.yudao.module.cloudmold.inventory.service.query;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InventoryAgingSnapshotWatermark {
    private Long ledgerEntryCount;
    private Long maxLedgerEntryId;
    private Long maxBalanceVersion;
    private LocalDateTime maxLedgerEntryAt;
}
