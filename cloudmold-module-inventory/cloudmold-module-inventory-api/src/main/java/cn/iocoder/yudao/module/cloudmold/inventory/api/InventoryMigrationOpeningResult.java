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
public class InventoryMigrationOpeningResult {
    private Long operationId;
    private Long openingOperationId;
    private String qualificationId;
    private String migrationRunId;
    private String candidateId;
    private String targetBalanceId;
    private Long ledgerTransactionId;
    private String movementGroupId;
    private String bridgeId;
    private Long aggregateVersion;
    private BigDecimal onHandQuantity;
    private BigDecimal reservedQuantity;
    private BigDecimal inTransitQuantity;
    private BigDecimal availableQuantity;
    private boolean duplicate;
}
