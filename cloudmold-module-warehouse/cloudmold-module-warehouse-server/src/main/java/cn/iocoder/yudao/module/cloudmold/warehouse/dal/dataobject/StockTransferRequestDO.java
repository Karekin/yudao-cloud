package cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_stock_transfer_request")
public class StockTransferRequestDO {
    private String requestId;
    private Long tenantId;
    private String requestCode;
    private String sourceBusinessType;
    private String sourceBusinessRef;
    private String ownerType;
    private String ownerId;
    private String sourceWarehouseId;
    private String targetWarehouseId;
    private String reasonCode;
    private String remark;
    private String status;
    private Long version;
    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
