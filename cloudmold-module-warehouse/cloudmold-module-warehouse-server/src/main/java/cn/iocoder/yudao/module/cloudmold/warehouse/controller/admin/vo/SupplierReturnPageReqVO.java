package cn.iocoder.yudao.module.cloudmold.warehouse.controller.admin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - CloudMold 供应商退供分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class SupplierReturnPageReqVO extends PageParam {

    @Schema(description = "退供单号、采购单号、收货单号、供应商或规范 SKU 关键字")
    private String keyword;

    @Schema(description = "退供单状态", example = "APPROVED")
    private String status;

    @Schema(description = "采购单 ID")
    private String purchaseOrderId;

    @Schema(description = "收货单 ID")
    private String receiptId;

    @Schema(description = "供应商 ID")
    private String supplierId;

    @Schema(description = "规范仓库 ID")
    private String warehouseId;
}
