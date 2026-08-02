package cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("cloudmold_inventory_scrap_line")
public class InventoryScrapLineDO {
    private String lineId;
    private Long tenantId;
    private String scrapId;
    private Integer lineNumber;
    private String canonicalSkuId;
    private String locationId;
    private String lotId;
    private String stockStatus;
    private String qualityStatus;
    private String baseUomCode;
    private BigDecimal requestedQuantity;
    private BigDecimal disposedQuantity;
    private String evidenceType;
    private String evidenceRef;
    private String status;
    private Long version;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
