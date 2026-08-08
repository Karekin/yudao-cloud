package cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.receivables;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_finance_receipt_allocation")
public class ReceiptAllocationDO {
    @TableId(type = IdType.INPUT)
    private String receiptAllocationId;
    private Long tenantId;
    private String receiptId;
    private String receivablePlanId;
    private String customerId;
    private String salesContractId;
    private String currencyCode;
    private Long amountMinor;
    private String status;
    private String allocatedByPrincipalId;
    private String reasonCode;
    private Long receiptVersion;
    private Long receivablePlanVersion;
    private LocalDateTime createdAt;
}
