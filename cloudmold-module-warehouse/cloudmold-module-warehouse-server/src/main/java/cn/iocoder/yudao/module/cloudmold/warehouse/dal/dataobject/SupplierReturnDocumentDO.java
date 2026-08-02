package cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_supplier_return")
public class SupplierReturnDocumentDO {
    private String returnId;
    private Long tenantId;
    private String returnCode;
    private String purchaseOrderId;
    private String receiptId;
    private String supplierId;
    private String ownerType;
    private String ownerId;
    private String warehouseId;
    private String reasonCode;
    private String remark;
    private String status;
    private String createdByPrincipalId;
    private String submittedByPrincipalId;
    private String approvedByPrincipalId;
    private String completedByPrincipalId;
    private String cancelledByPrincipalId;
    private LocalDateTime submittedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
