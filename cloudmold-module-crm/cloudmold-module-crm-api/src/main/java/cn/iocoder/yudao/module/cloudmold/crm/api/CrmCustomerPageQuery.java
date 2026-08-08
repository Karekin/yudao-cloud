package cn.iocoder.yudao.module.cloudmold.crm.api;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CrmCustomerPageQuery extends PageParam {
    private String customerId;
    private String customerCode;
    private String keyword;
    private String lifecycleStatus;
    private String poolStatus;
    private String ownerPrincipalId;
    private String sourceCode;
    private String industryCode;
    private String regionCode;
    private LocalDateTime createdAtFrom;
    private LocalDateTime createdAtTo;
}
