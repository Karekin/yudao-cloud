package cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("cloudmold_inventory_reservation")
@Data
public class InventoryReservationDO {

    @TableId(type = IdType.INPUT)
    private String reservationId;
    private Long tenantId;
    private String balanceId;
    private String businessType;
    private String businessId;
    private String businessItemId;
    private BigDecimal quantity;
    private Integer status;
    private Long version;
    private Long createdOperationId;
    private Long closedOperationId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
