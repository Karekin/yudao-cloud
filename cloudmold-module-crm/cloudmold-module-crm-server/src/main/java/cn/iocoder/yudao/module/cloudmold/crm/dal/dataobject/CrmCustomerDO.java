package cn.iocoder.yudao.module.cloudmold.crm.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@TableName("cloudmold_crm_customer")
@Data
@Accessors(chain = true)
public class CrmCustomerDO {
    @TableId(type = IdType.INPUT)
    private String customerId;
    private Long tenantId;
    private String customerCode;
    private String customerName;
    private String levelCode;
    private String lifecycleStatus;
    private String poolStatus;
    private String ownerPrincipalId;
    private String sourceCode;
    private String industryCode;
    private String regionCode;
    private LocalDateTime nextFollowUpAt;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
