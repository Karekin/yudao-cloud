package cn.iocoder.yudao.module.cloudmold.order.controller.admin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - CloudMold 规范订单分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class OrderPageReqVO extends PageParam {

    @Schema(description = "规范订单 ID，精确匹配", example = "7f628e8e-7307-4708-bd5c-91f0db9bc209")
    private String orderId;

    @Schema(description = "规范订单号，支持模糊匹配", example = "CMO1234567890")
    private String orderNo;

    @Schema(description = "规范买家 ID，精确匹配", example = "buyer-001")
    private String buyerId;

    @Schema(description = "订单状态", example = "PAYMENT_CONFIRMED")
    private String status;
}
