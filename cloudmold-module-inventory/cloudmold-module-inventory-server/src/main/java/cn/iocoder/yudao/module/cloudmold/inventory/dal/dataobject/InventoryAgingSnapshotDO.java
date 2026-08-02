package cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("cloudmold_inventory_aging_snapshot")
public class InventoryAgingSnapshotDO {
    @TableId(type = IdType.INPUT)
    private String snapshotId;
    private Long tenantId;
    private String snapshotCode;
    private String ownerType;
    private String ownerId;
    private String warehouseId;
    private String bucketPolicyCode;
    private String bucketPolicyVersion;
    private String bucketPolicyHash;
    private Integer ageFreshMaxDays;
    private Integer ageAgingMaxDays;
    private Integer ageStaleMaxDays;
    private Integer expiryWarningMaxDays;
    private Integer expiryCriticalMaxDays;
    private String ledgerWatermarkRef;
    private LocalDateTime ledgerWatermarkOccurredAt;
    private LocalDate snapshotDate;
    private Integer lineCount;
    private Integer unknownAgeCount;
    private Integer unknownExpiryCount;
    private String status;
    private Long version;
    private LocalDateTime createdAt;
}
