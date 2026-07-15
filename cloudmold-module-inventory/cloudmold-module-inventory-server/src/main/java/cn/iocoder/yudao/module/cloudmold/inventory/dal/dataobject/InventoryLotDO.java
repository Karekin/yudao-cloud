package cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("cloudmold_inventory_lot")
public class InventoryLotDO {
    @TableId(type = IdType.INPUT)
    private String lotId;
    private Long tenantId;
    private String ownerType;
    private String ownerId;
    private String canonicalSkuId;
    private String lotCode;
    private LocalDate manufacturedOn;
    private LocalDate expiresOn;
    private LocalDateTime receivedAt;
    private String status;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
