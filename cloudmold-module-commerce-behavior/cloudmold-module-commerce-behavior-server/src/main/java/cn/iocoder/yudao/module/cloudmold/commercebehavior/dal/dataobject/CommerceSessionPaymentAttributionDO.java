package cn.iocoder.yudao.module.cloudmold.commercebehavior.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("cloudmold_commerce_session_payment_attribution")
public class CommerceSessionPaymentAttributionDO {
    @TableId
    private String attributionId;
    private Long tenantId;
    private String sessionId;
    private Long sessionVersion;
    private String principalId;
    private String checkoutToken;
    private String orderId;
    private Long orderVersion;
    private String paymentId;
    private String merchantId;
    private String shopId;
    private String channelCode;
    private String sourceSystem;
    private String sourceType;
    private String sourceId;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
}
