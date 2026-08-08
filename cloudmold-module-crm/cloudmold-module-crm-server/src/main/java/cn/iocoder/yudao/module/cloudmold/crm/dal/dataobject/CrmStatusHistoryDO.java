package cn.iocoder.yudao.module.cloudmold.crm.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@TableName("cloudmold_crm_status_history")
@Data
@Accessors(chain = true)
public class CrmStatusHistoryDO {
    @TableId(type = IdType.AUTO)
    private Long historyId;
    private Long tenantId;
    private String aggregateType;
    private String aggregateId;
    private Long aggregateVersion;
    private String fromStatus;
    private String toStatus;
    private String reasonCode;
    private String actorPrincipalId;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
}
