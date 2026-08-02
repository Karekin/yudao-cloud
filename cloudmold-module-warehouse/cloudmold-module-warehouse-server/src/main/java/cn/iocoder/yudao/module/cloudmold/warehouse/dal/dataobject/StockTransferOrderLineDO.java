package cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("cloudmold_stock_transfer_order_line")
public class StockTransferOrderLineDO {
    private String lineId;
    private Long tenantId;
    private String orderId;
    private Integer lineNumber;
    private String canonicalSkuId;
    private BigDecimal requestedQuantity;
    private String uomCode;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
