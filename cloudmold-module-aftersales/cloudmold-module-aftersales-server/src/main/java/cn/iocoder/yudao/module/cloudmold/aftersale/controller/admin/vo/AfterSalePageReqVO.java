package cn.iocoder.yudao.module.cloudmold.aftersale.controller.admin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - CloudMold 规范售后分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AfterSalePageReqVO extends PageParam {

    @Schema(description = "售后案例 ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private String afterSaleId;

    @Schema(description = "售后单号，支持模糊匹配", example = "CMAS")
    private String afterSaleNo;

    @Schema(description = "订单 ID", example = "550e8400-e29b-41d4-a716-446655440001")
    private String orderId;

    @Schema(description = "订单单号，支持模糊匹配", example = "CMO")
    private String orderNo;

    @Schema(description = "规范 SKU ID", example = "550e8400-e29b-41d4-a716-446655440002")
    private String canonicalSkuId;

    @Schema(description = "售后案例状态", example = "COMPLETED")
    private String caseStatus;

    @Schema(description = "退款状态", example = "SUCCEEDED")
    private String refundStatus;

    @Schema(description = "售后类型编码", example = "RETURN_AND_REFUND")
    private String afterSaleType;

    @Schema(description = "售后原因编码", example = "SIZE_NOT_FIT")
    private String reasonCode;

    @Schema(description = "责任归属编码", example = "BUYER")
    private String responsibility;
}
