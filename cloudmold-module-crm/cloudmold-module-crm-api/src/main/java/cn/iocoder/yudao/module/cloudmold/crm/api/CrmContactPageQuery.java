package cn.iocoder.yudao.module.cloudmold.crm.api;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CrmContactPageQuery extends PageParam {
    private String contactId;
    private String customerId;
    private String keyword;
    private String status;
    private Boolean isPrimary;
    private LocalDateTime createdAtFrom;
    private LocalDateTime createdAtTo;
}
