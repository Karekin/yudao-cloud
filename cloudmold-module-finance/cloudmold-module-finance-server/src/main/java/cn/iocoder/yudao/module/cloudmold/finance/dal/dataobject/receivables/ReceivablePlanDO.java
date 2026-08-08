package cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.receivables;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("cloudmold_finance_receivable_plan")
public class ReceivablePlanDO {
    @TableId(type = IdType.INPUT)
    private String receivablePlanId;
    private Long tenantId;
    private String planCode;
    private String customerId;
    private String salesContractId;
    private String currencyCode;
    private Long plannedAmountMinor;
    private Long allocatedAmountMinor;
    private String status;
    private String createdByPrincipalId;
    private String lastModifiedByPrincipalId;
    private String latestReasonCode;
    private LocalDate dueDate;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
