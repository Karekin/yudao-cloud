package cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("cloudmold_inventory_ledger_entry_v3")
public class InventoryV3LedgerEntryDO {
    @TableId(type = IdType.AUTO)
    private Long ledgerEntryId;
    private Long tenantId;
    private Long ledgerTransactionId;
    private String movementGroupId;
    private String entryRole;
    private String counterpartyBalanceId;
    private String balanceId;
    private Long aggregateVersion;
    private String baseUomCode;
    private BigDecimal beforeOnHandQuantity;
    private BigDecimal deltaOnHandQuantity;
    private BigDecimal afterOnHandQuantity;
    private BigDecimal beforeReservedQuantity;
    private BigDecimal deltaReservedQuantity;
    private BigDecimal afterReservedQuantity;
    private BigDecimal beforeInTransitQuantity;
    private BigDecimal deltaInTransitQuantity;
    private BigDecimal afterInTransitQuantity;
    private LocalDateTime createdAt;
}
