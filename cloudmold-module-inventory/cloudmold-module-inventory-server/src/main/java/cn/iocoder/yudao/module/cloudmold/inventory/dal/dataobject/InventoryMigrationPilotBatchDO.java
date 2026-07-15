package cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class InventoryMigrationPilotBatchDO {
    private String batchId;
    private Long tenantId;
    private String migrationRunId;
    private String environment;
    private String sourceSystem;
    private String sourceType;
    private String sourceClassification;
    private String policyVersion;
    private String policyHash;
    private String manifestHash;
    private Integer expectedItemCount;
    private BigDecimal expectedOnHandQuantity;
    private String warehouseId;
    private String baseUomCode;
    private String sourceWatermarkKind;
    private String sourceWatermarkValue;
    private LocalDateTime sourceWatermarkCapturedAt;
    private String targetWatermarkKind;
    private String targetWatermarkValue;
    private LocalDateTime targetWatermarkAppliedAt;
    private Integer maxLagSeconds;
    private LocalDateTime executionWindowStart;
    private LocalDateTime executionWindowEnd;
    private String changeTicket;
    private String purpose;
    private Long requesterId;
    private Long executorId;
    private Integer approvalCount;
    private String status;
    private Long version;
    private LocalDateTime frozenAt;
    private LocalDateTime approvedAt;
    private LocalDateTime admittedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
