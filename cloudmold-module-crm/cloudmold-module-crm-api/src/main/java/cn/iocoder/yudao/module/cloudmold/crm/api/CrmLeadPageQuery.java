package cn.iocoder.yudao.module.cloudmold.crm.api;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CrmLeadPageQuery extends PageParam {
    private String leadId;
    private String leadCode;
    private String keyword;
    private String status;
    private String ownerPrincipalId;
    private String sourceCode;
    private LocalDateTime createdAtFrom;
    private LocalDateTime createdAtTo;
}
