package cn.iocoder.yudao.module.cloudmold.crm.api;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CrmOpportunityPageQuery extends PageParam {
    private String opportunityId;
    private String opportunityCode;
    private String customerId;
    private String keyword;
    private String stage;
    private String ownerPrincipalId;
    private LocalDate expectedCloseDateFrom;
    private LocalDate expectedCloseDateTo;
    private LocalDateTime createdAtFrom;
    private LocalDateTime createdAtTo;
}
