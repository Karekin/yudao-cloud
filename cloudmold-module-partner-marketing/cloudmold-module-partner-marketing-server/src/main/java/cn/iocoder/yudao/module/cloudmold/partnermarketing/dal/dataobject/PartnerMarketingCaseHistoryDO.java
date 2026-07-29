package cn.iocoder.yudao.module.cloudmold.partnermarketing.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@TableName("cloudmold_partner_marketing_case_history")
@Data
@Accessors(chain = true)
public class PartnerMarketingCaseHistoryDO {
    @TableId(type = IdType.AUTO)
    private Long historyId;
    private String caseId;
    private Long tenantId;
    private Long aggregateVersion;
    private String commandType;
    private String fromStatus;
    private String toStatus;
    private String actorPrincipalId;
    private String reasonCode;
    private String evidenceSha256;
    private LocalDateTime createdAt;
}
