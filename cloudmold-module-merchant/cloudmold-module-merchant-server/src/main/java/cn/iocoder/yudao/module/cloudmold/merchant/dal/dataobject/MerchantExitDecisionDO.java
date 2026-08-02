package cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@TableName("cloudmold_merchant_exit_decision")
@Data
@Accessors(chain = true)
public class MerchantExitDecisionDO {
    @TableId(type = IdType.INPUT)
    private String exitDecisionId;
    private Long tenantId;
    private String merchantId;
    private String shopId;
    private String admissionId;
    private String scorecardId;
    private String reasonType;
    private String decisionStatus;
    private String evidenceRef;
    private String note;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
