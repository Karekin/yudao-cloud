package cn.iocoder.yudao.module.cloudmold.commercebehavior.service.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - CloudMold 交易行为事件分页项")
@Data
public class BehaviorEventPageItem {

    private String behaviorId;
    private String sessionId;
    private String behaviorType;
    private String principalId;
    private String canonicalSpuId;
    private String listingId;
    private String merchantId;
    private String shopId;
    private String channelCode;
    private Integer quantity;
    private String checkoutToken;
    private String sourceSystem;
    private String sourceType;
    private String sourceId;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
}
