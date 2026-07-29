package cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_receipt")
public class ReceiptDO {
    private String receiptId;
    private Long tenantId;
    private String receiptNo;
    private String asnId;
    private String warehouseId;
    private String status;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
