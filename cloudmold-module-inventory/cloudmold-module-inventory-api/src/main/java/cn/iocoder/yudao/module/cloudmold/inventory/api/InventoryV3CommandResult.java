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
public class InventoryV3CommandResult {
    private Long operationId;
    private Long ledgerTransactionId;
    private String movementGroupId;
    private String balanceId;
    private String reservationId;
    private String allocationId;
    private Long aggregateVersion;
    private BigDecimal onHandQuantity;
    private BigDecimal reservedQuantity;
    private BigDecimal inTransitQuantity;
    private BigDecimal availableQuantity;
    private boolean duplicate;
}
