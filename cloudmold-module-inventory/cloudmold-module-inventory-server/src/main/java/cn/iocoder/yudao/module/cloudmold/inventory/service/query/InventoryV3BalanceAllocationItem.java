package cn.iocoder.yudao.module.cloudmold.inventory.service.query;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "管理后台 - CloudMold 规范库存余额详情活跃预占")
@Data
public class InventoryV3BalanceAllocationItem {

    private String allocationId;
    private String reservationId;
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal quantity;
    private Integer status;
    private Long version;
}
