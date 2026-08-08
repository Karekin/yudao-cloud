package cn.iocoder.yudao.module.cloudmold.crm.api;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CrmPoolPageQuery extends PageParam {
    private String keyword;
    private String sourceCode;
    private String industryCode;
    private String regionCode;
    private LocalDateTime createdAtFrom;
    private LocalDateTime createdAtTo;
}
