package cn.iocoder.yudao.module.cloudmold.payment.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_payment_transaction")
public class PaymentTransactionDO {
    @TableId(type = IdType.AUTO)
    private Long transactionId;
    private Long tenantId;
    private String paymentId;
    private Long operationId;
    private String transactionType;
    private Long amountMinor;
    private String currencyCode;
    private String providerCode;
    private String providerTransactionId;
    private Long aggregateVersion;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
}
