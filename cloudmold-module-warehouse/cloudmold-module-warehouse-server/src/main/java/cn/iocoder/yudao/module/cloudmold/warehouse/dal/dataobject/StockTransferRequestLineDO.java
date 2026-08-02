package cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("cloudmold_stock_transfer_request_line")
public class StockTransferRequestLineDO {
    private String lineId;
    private Long tenantId;
    private String requestId;
    private Integer lineNumber;
    private String canonicalSkuId;
    private BigDecimal requestedQuantity;
    private String uomCode;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
