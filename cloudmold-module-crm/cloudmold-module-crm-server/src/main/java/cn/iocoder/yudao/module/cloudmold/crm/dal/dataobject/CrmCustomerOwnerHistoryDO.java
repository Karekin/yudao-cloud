package cn.iocoder.yudao.module.cloudmold.crm.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@TableName("cloudmold_crm_customer_owner_history")
@Data
@Accessors(chain = true)
public class CrmCustomerOwnerHistoryDO {
    @TableId(type = IdType.AUTO)
    private Long historyId;
    private Long tenantId;
    private String customerId;
    private String fromOwnerPrincipalId;
    private String toOwnerPrincipalId;
    private String fromPoolStatus;
    private String toPoolStatus;
    private Long operationId;
    private String reasonCode;
    private String actorPrincipalId;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
}
