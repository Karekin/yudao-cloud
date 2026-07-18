package cn.iocoder.yudao.module.cloudmold.commercebehavior.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("cloudmold_commerce_behavior_event")
public class CommerceBehaviorEventDO {
    @TableId
    private String behaviorId;
    private Long tenantId;
    private String sessionId;
    private Long sessionVersion;
    private String behaviorType;
    private String principalId;
    private String canonicalSpuId;
    private String skuId;
    private String listingId;
    private String listingOfferId;
    private String merchantId;
    private String shopId;
    private String channelCode;
    private String searchToken;
    private String resultSetToken;
    private Integer resultPosition;
    private Integer quantity;
    private String checkoutToken;
    private String sourceSystem;
    private String sourceType;
    private String sourceId;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
}
