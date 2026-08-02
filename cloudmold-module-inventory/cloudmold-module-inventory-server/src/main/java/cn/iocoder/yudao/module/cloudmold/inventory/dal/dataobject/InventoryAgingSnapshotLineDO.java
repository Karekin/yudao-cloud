package cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("cloudmold_inventory_aging_snapshot_line")
public class InventoryAgingSnapshotLineDO {
    @TableId(type = IdType.AUTO)
    private Long lineId;
    private Long tenantId;
    private String snapshotId;
    private String balanceId;
    private String ownerType;
    private String ownerId;
    private String canonicalSkuId;
    private String warehouseId;
    private String locationId;
    private String lotId;
    private String lotCode;
    private String stockStatus;
    private String qualityStatus;
    private String baseUomCode;
    private BigDecimal onHandQuantity;
    private BigDecimal reservedQuantity;
    private BigDecimal inTransitQuantity;
    private BigDecimal availableQuantity;
    private Long balanceVersion;
    private LocalDate manufacturedOn;
    private LocalDate expiresOn;
    private String ageBasisType;
    private LocalDateTime ageBasisAt;
    private Integer ageDays;
    private String ageBucket;
    private Integer expiryDaysRemaining;
    private String expiryStatus;
    private String expiryBucket;
    private String riskClassification;
    private LocalDateTime createdAt;
}
