package cn.iocoder.yudao.module.cloudmold.commercebehavior.controller.admin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - CloudMold 交易行为事件分页 Request")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CommerceBehaviorEventPageReqVO extends PageParam {

    @Schema(description = "行为事件 ID")
    private String behaviorId;

    @Schema(description = "会话 ID")
    private String sessionId;

    @Schema(description = "买家主体 ID")
    private String principalId;

    @Schema(description = "行为类型 PDP_VIEWED/CART_ADDED/CHECKOUT_STARTED 等")
    private String behaviorType;

    @Schema(description = "规范 SPU ID")
    private String canonicalSpuId;

    @Schema(description = "店铺 ID")
    private String shopId;

    @Schema(description = "商家 ID")
    private String merchantId;

    @Schema(description = "渠道编码")
    private String channelCode;

    @Schema(description = "发生时间起")
    private LocalDateTime occurredAtFrom;

    @Schema(description = "发生时间止")
    private LocalDateTime occurredAtTo;
}
