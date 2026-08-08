package cn.iocoder.yudao.module.cloudmold.crm.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@TableName("cloudmold_crm_contact")
@Data
@Accessors(chain = true)
public class CrmContactDO {
    @TableId(type = IdType.INPUT)
    private String contactId;
    private Long tenantId;
    private String customerId;
    private String contactName;
    private String roleTitle;
    private String contactChannelRef;
    private String maskedContact;
    private Boolean isPrimary;
    private String status;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
