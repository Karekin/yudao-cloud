package cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("cloudmold_supplier_return_line")
public class SupplierReturnLineDO {
    private String returnLineId;
    private Long tenantId;
    private String returnId;
    private Integer lineNumber;
    private String receiptLineId;
    private String purchaseOrderItemId;
    private String purchaseOrderScheduleId;
    private String qualityDecisionId;
    private Long decisionVersion;
    private String inspectionSplitId;
    private String sourceDisposition;
    private String canonicalSkuId;
    private String warehouseId;
    private String locationId;
    private String lotId;
    private BigDecimal returnQuantity;
    private BigDecimal dispatchedQuantity;
    private BigDecimal outstandingQuantity;
    private String uomCode;
    private String valuationPolicyId;
    private String valuationPolicyVersion;
    private String valuationPolicyHash;
    private Long unitCostAmountMinor;
    private String currencyCode;
    private String qualityEvidenceRef;
    private String remark;
    private String status;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
