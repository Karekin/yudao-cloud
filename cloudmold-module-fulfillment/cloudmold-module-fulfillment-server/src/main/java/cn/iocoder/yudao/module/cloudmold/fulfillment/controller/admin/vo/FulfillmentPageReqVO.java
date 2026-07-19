package cn.iocoder.yudao.module.cloudmold.fulfillment.controller.admin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - CloudMold 规范 Fulfillment 分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class FulfillmentPageReqVO extends PageParam {

    @Schema(description = "履约单 ID，精确匹配", example = "a7a6d534-3eb4-4422-9831-cdb4632fef38")
    private String fulfillmentId;

    @Schema(description = "履约单号，支持模糊匹配", example = "CMF")
    private String fulfillmentNo;

    @Schema(description = "订单 ID，精确匹配", example = "73e79c0e-a6f0-44b7-8d9c-c6a2db62123f")
    private String orderId;

    @Schema(description = "订单号，支持模糊匹配", example = "CMO")
    private String orderNo;

    @Schema(description = "卖家 ID，精确匹配", example = "merchant-1")
    private String sellerId;

    @Schema(description = "仓网仓库 ID，精确匹配", example = "warehouse-1")
    private String warehouseId;

    @Schema(description = "履约状态，精确匹配", example = "DELIVERED")
    private String status;

    @Schema(description = "首包裹 Shipment ID，精确匹配", example = "shipment-1")
    private String shipmentId;

    @Schema(description = "首包裹 Shipment 状态，精确匹配", example = "IN_TRANSIT")
    private String shipmentStatus;

    @Schema(description = "承运商编码，精确匹配", example = "SF")
    private String carrierCode;

    @Schema(description = "运单号，支持模糊匹配", example = "SF12345678")
    private String waybillNo;
}
