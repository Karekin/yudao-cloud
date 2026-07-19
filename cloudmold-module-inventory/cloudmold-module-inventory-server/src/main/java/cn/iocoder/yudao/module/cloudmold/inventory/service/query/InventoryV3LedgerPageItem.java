package cn.iocoder.yudao.module.cloudmold.inventory.service.query;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - CloudMold 规范 Inventory v3 账本分页项")
@Data
public class InventoryV3LedgerPageItem {

    private Long ledgerEntryId;
    private Long ledgerTransactionId;
    private String movementGroupId;
    private String entryRole;
    private String commandType;
    private String businessType;
    private String businessId;
    private String businessItemId;
    private String businessNo;
    private String balanceId;
    private String counterpartyBalanceId;
    private String canonicalSkuId;
    private String skuCode;
    private String warehouseId;
    private String warehouseCode;
    private String locationId;
    private String locationCode;
    private String lotId;
    private String lotCode;
    private String ownerType;
    private String ownerId;
    private String stockStatus;
    private String qualityStatus;
    private String baseUomCode;
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal beforeOnHandQuantity;
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal deltaOnHandQuantity;
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal afterOnHandQuantity;
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal beforeReservedQuantity;
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal deltaReservedQuantity;
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal afterReservedQuantity;
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal beforeInTransitQuantity;
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal deltaInTransitQuantity;
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal afterInTransitQuantity;
    private Long aggregateVersion;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
}
