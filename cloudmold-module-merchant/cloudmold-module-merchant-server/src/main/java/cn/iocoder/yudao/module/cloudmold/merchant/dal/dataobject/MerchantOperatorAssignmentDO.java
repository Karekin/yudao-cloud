package cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@TableName("cloudmold_merchant_operator_assignment")
@Data
@Accessors(chain = true)
public class MerchantOperatorAssignmentDO {
    @TableId(type = IdType.INPUT)
    private String assignmentId;
    private Long tenantId;
    private String merchantId;
    private String shopId;
    private String principalId;
    private String roleCode;
    private String status;
    private Long version;
    private LocalDateTime validFrom;
    private LocalDateTime validTo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
