package cn.iocoder.yudao.module.cloudmold.order.service.query;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - CloudMold 规范订单详情")
@Data
public class OrderDetailVO {

    @Schema(description = "规范订单 ID")
    private String orderId;
    @Schema(description = "订单号")
    private String orderNo;
    @Schema(description = "迁移运行 ID")
    private String runId;
    @Schema(description = "规范买家 ID")
    private String buyerId;
    @Schema(description = "令牌化地址快照引用")
    private String addressRef;
    @Schema(description = "地址快照版本")
    private Long addressSnapshotVersion;
    @Schema(description = "非敏感目的地区域编码")
    private String destinationRegionCode;
    @Schema(description = "订单状态")
    private String status;

    @Schema(description = "订单行数")
    private Long itemCount;

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "总数量")
    private BigDecimal totalQuantity;

    @Schema(description = "商品金额（分）")
    private Long productAmountMinor;
    @Schema(description = "运费（分）")
    private Long shippingAmountMinor;
    @Schema(description = "优惠（分）")
    private Long discountAmountMinor;
    @Schema(description = "应付（分）")
    private Long payableAmountMinor;
    @Schema(description = "币种")
    private String currencyCode;

    @Schema(description = "支付 ID")
    private String paymentId;
    @Schema(description = "支付状态")
    private String paymentStatus;
    @Schema(description = "履约 ID")
    private String fulfillmentId;
    @Schema(description = "履约状态")
    private String fulfillmentStatus;
    @Schema(description = "发运 ID")
    private String shipmentId;
    @Schema(description = "退款 ID")
    private String refundId;
    @Schema(description = "取消 Saga ID")
    private String cancellationSagaId;
    @Schema(description = "取消前状态")
    private String preCancellationStatus;
    @Schema(description = "取消责任方")
    private String cancellationResponsibilityParty;
    @Schema(description = "取消责任码")
    private String cancellationResponsibilityCode;

    @Schema(description = "聚合版本")
    private Long aggregateVersion;
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;

    @Schema(description = "订单行列表")
    private List<OrderDetailItem> items;
}
