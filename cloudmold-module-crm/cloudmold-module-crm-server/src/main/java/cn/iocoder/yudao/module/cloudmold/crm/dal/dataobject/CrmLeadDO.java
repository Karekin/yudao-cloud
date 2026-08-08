package cn.iocoder.yudao.module.cloudmold.crm.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@TableName("cloudmold_crm_lead")
@Data
@Accessors(chain = true)
public class CrmLeadDO {
    @TableId(type = IdType.INPUT)
    private String leadId;
    private Long tenantId;
    private String leadCode;
    private String leadName;
    private String sourceCode;
    private String status;
    private String ownerPrincipalId;
    private String contactChannelRef;
    private String maskedContact;
    private LocalDateTime nextFollowUpAt;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
