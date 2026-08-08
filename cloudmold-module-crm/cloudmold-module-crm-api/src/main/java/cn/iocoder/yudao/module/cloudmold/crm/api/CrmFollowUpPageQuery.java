package cn.iocoder.yudao.module.cloudmold.crm.api;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CrmFollowUpPageQuery extends PageParam {
    private String subjectType;
    private String subjectId;
    private String methodCode;
    private String actorPrincipalId;
    private LocalDateTime occurredAtFrom;
    private LocalDateTime occurredAtTo;
}
