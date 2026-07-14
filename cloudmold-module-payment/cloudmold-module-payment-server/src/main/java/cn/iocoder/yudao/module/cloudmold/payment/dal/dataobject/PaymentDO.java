package cn.iocoder.yudao.module.cloudmold.payment.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_payment")
public class PaymentDO {
    @TableId(type = IdType.INPUT)
    private String paymentId;
    private Long tenantId;
    private String paymentNo;
    private String runId;
    private String orderId;
    private String status;
    private Long payableAmountMinor;
    private Long capturedAmountMinor;
    private Long refundedAmountMinor;
    private String currencyCode;
    private String providerCode;
    private String providerTransactionId;
    private Boolean testMode;
    private Long version;
    private LocalDateTime capturedAt;
    private LocalDateTime refundedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
