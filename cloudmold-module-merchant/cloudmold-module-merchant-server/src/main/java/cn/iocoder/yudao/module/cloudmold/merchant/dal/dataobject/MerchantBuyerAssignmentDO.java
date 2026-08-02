package cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@TableName("cloudmold_merchant_buyer_assignment")
@Data
@Accessors(chain = true)
public class MerchantBuyerAssignmentDO {
    @TableId(type = IdType.INPUT)
    private String buyerAssignmentId;
    private Long tenantId;
    private String admissionId;
    private String inspectionTaskId;
    private String merchantId;
    private String shopId;
    private String buyerTlPrincipalId;
    private String buyerPrincipalId;
    private String evidenceRef;
    private String status;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
