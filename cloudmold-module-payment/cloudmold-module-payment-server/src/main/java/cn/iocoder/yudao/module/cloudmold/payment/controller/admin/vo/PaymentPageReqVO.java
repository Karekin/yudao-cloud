package cn.iocoder.yudao.module.cloudmold.payment.controller.admin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - CloudMold 规范 Payment 分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class PaymentPageReqVO extends PageParam {

    @Schema(description = "支付单号，支持模糊匹配", example = "CMP1234567890")
    private String paymentNo;

    @Schema(description = "规范订单 ID，支持精确匹配", example = "order-1")
    private String orderId;

    @Schema(description = "支付状态，支持精确匹配", example = "CAPTURED")
    private String status;

    @Schema(description = "支付提供方编码，支持精确匹配", example = "INTERNAL_TEST")
    private String providerCode;

    @Schema(description = "是否只查询测试支付；null 表示不过滤", example = "true")
    private Boolean testMode;
}
