package cn.iocoder.yudao.module.cloudmold.order.service.query;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - CloudMold 规范订单行")
@Data
public class OrderDetailItem {

    @Schema(description = "订单行 ID")
    private String orderItemId;
    @Schema(description = "行键")
    private String lineKey;
    @Schema(description = "规范 SKU ID")
    private String canonicalSkuId;

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "数量")
    private BigDecimal quantity;

    @Schema(description = "单价（分）")
    private Long unitPriceMinor;
    @Schema(description = "行金额（分）")
    private Long lineAmountMinor;
    @Schema(description = "行优惠（分）")
    private Long discountAmountMinor;
    @Schema(description = "行净额（分）")
    private Long netAmountMinor;
    @Schema(description = "商品成本（分）")
    private Long merchandiseCostMinor;
    @Schema(description = "库存预留 ID")
    private String reservationId;
    @Schema(description = "刊登 ID")
    private String listingId;
    @Schema(description = "刊登 Offer ID")
    private String listingOfferId;
    @Schema(description = "刊登修订号")
    private Integer listingRevision;
    @Schema(description = "刊登版本")
    private Long listingVersion;
    @Schema(description = "渠道编码")
    private String channelCode;
    @Schema(description = "店铺 ID")
    private String shopId;
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
