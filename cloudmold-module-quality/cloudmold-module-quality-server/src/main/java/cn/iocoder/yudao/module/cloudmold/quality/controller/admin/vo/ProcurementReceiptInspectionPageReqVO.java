package cn.iocoder.yudao.module.cloudmold.quality.controller.admin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - 采购收货质检分页 Request")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ProcurementReceiptInspectionPageReqVO extends PageParam {
    @Schema(description = "质检状态")
    private String status;
    @Schema(description = "收货单 UUID")
    private String receiptId;
    @Schema(description = "采购订单 UUID")
    private String purchaseOrderId;
    @Schema(description = "供应商 UUID")
    private String supplierId;
    @Schema(description = "货权主体 UUID")
    private String ownerId;
}
