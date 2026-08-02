package cn.iocoder.yudao.module.cloudmold.inventory.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryProcurementReceiptResult {
    private Long operationId;
    private Long ledgerTransactionId;
    private String receiptId;
    private String receiptLineId;
    private String sourceBalanceId;
    private Long sourceAggregateVersion;
    private String targetBalanceId;
    private Long targetAggregateVersion;
    private Long unitCostAmountMinor;
    private Long movementCostAmountMinor;
    private String currencyCode;
    private String valuationPolicyId;
    private String valuationPolicyVersion;
    private String valuationPolicyHash;
    private BigDecimal receivedQuantity;
    private BigDecimal pendingQuantity;
    private BigDecimal acceptedQuantity;
    private BigDecimal rejectedQuantity;
    private BigDecimal quarantinedQuantity;
    private BigDecimal returnedQuantity;
    private boolean duplicate;
}
