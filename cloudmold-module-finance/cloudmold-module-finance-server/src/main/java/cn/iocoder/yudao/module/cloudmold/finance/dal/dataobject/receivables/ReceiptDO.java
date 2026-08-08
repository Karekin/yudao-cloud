package cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.receivables;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("cloudmold_finance_receipt")
public class ReceiptDO {
    @TableId(type = IdType.INPUT)
    private String receiptId;
    private Long tenantId;
    private String receiptCode;
    private String customerId;
    private String salesContractId;
    private String currencyCode;
    private Long receiptAmountMinor;
    private Long allocatedAmountMinor;
    private String status;
    private String externalReference;
    private String recordedByPrincipalId;
    private String lastModifiedByPrincipalId;
    private String latestReasonCode;
    private LocalDate receiptDate;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
