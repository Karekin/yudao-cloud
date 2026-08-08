package cn.iocoder.yudao.module.cloudmold.crm.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@TableName("cloudmold_crm_follow_up")
@Data
@Accessors(chain = true)
public class CrmFollowUpDO {
    @TableId(type = IdType.INPUT)
    private String followUpId;
    private Long tenantId;
    private String subjectType;
    private String subjectId;
    private String methodCode;
    private String summary;
    private LocalDateTime nextFollowUpAt;
    private String actorPrincipalId;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
}
