package cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_stock_transfer_order")
public class StockTransferOrderDO {
    private String orderId;
    private Long tenantId;
    private String requestId;
    private String orderCode;
    private String ownerType;
    private String ownerId;
    private String sourceWarehouseId;
    private String targetWarehouseId;
    private String status;
    private Long version;
    private LocalDateTime preparedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
