package cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("cloudmold_inventory_scrap_document")
public class InventoryScrapDocumentDO {
    private String scrapId;
    private Long tenantId;
    private String scrapCode;
    private String reasonCode;
    private String remark;
    private String ownerType;
    private String ownerId;
    private String warehouseId;
    private String status;
    private Long version;
    private BigDecimal totalRequestedQuantity;
    private BigDecimal totalDisposedQuantity;
    private Integer lineCount;
    private String requestedByPrincipalId;
    private String submittedByPrincipalId;
    private String approvedByPrincipalId;
    private String completedByPrincipalId;
    private String cancelledByPrincipalId;
    private LocalDateTime approvedAt;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
