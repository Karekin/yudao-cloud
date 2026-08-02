package cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@TableName("cloudmold_merchant_managed_invitation")
@Data
@Accessors(chain = true)
public class MerchantManagedInvitationDO {
    @TableId(type = IdType.INPUT)
    private String invitationId;
    private Long tenantId;
    private String invitationCode;
    private String recruiterPrincipalId;
    private String attributionSourceSystem;
    private String attributionSourceType;
    private String attributionSourceId;
    private String attributionReference;
    private String evidenceRef;
    private String usedAdmissionId;
    private LocalDateTime usedAt;
    private String status;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
