package cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("cloudmold_stock_count_line")
public class StockCountLineDO {
    private String lineId;
    private Long tenantId;
    private String stockCountId;
    private Integer lineNumber;
    private String ownerType;
    private String ownerId;
    private String canonicalSkuId;
    private String warehouseId;
    private String locationId;
    private String lotId;
    private String stockStatus;
    private String qualityStatus;
    private String baseUomCode;
    private String balanceId;
    private BigDecimal bookOnHandQuantity;
    private BigDecimal bookReservedQuantity;
    private BigDecimal bookInTransitQuantity;
    private BigDecimal bookAvailableQuantity;
    private Long bookAggregateVersion;
    private BigDecimal countedOnHandQuantity;
    private BigDecimal differenceQuantity;
    private String countStatus;
    private String countedByPrincipalId;
    private LocalDateTime countedAt;
    private String adjustmentId;
    private Long adjustmentLedgerTransactionId;
    private Long adjustedAggregateVersion;
    private String remark;
    private Long version;
    private LocalDateTime freezeCapturedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
