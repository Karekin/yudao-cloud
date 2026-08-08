package cn.iocoder.yudao.module.cloudmold.crm.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("cloudmold_crm_opportunity")
@Data
@Accessors(chain = true)
public class CrmOpportunityDO {
    @TableId(type = IdType.INPUT)
    private String opportunityId;
    private Long tenantId;
    private String opportunityCode;
    private String customerId;
    private String opportunityName;
    private String stage;
    private Long expectedAmountMinor;
    private String currencyCode;
    private LocalDate expectedCloseDate;
    private String ownerPrincipalId;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
