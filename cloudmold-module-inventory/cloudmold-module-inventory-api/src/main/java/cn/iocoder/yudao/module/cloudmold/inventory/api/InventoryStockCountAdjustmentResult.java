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
public class InventoryStockCountAdjustmentResult {
    private Long operationId;
    private String adjustmentId;
    private Long ledgerTransactionId;
    private String balanceId;
    private Long aggregateVersion;
    private BigDecimal onHandQuantity;
    private BigDecimal adjustmentQuantity;
    private boolean duplicate;
}
