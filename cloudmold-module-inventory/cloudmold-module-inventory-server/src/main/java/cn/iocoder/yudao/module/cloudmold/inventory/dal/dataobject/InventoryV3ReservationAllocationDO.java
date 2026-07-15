package cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("cloudmold_inventory_reservation_allocation_v3")
public class InventoryV3ReservationAllocationDO {
    @TableId(type = IdType.INPUT)
    private String allocationId;
    private Long tenantId;
    private String reservationId;
    private String balanceId;
    private BigDecimal quantity;
    private Integer status;
    private Long version;
    private Long createdOperationId;
    private Long closedOperationId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
