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
public class InventoryStockTransferResult {
    private Long operationId;
    private Long ledgerTransactionId;
    private String movementGroupId;
    private String sourceBalanceId;
    private Long sourceAggregateVersion;
    private BigDecimal sourceOnHandQuantity;
    private BigDecimal sourceInTransitQuantity;
    private String targetBalanceId;
    private Long targetAggregateVersion;
    private BigDecimal targetOnHandQuantity;
    private BigDecimal targetInTransitQuantity;
    private BigDecimal cumulativeDispatchedQuantity;
    private BigDecimal cumulativeReceivedQuantity;
    private BigDecimal outstandingQuantity;
    private boolean duplicate;
}
