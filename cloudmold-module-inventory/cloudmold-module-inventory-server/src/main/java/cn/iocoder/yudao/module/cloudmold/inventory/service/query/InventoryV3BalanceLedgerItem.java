package cn.iocoder.yudao.module.cloudmold.inventory.service.query;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - CloudMold 规范库存余额详情账本流水")
@Data
public class InventoryV3BalanceLedgerItem {

    private Long ledgerEntryId;
    private String entryRole;
    private String commandType;
    private String businessType;
    private String businessNo;
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal deltaOnHandQuantity;
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal deltaReservedQuantity;
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal deltaInTransitQuantity;
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal afterOnHandQuantity;
    private LocalDateTime occurredAt;
}
