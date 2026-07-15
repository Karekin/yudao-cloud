package cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@TableName("cloudmold_merchant_legal_entity")
@Data
@Accessors(chain = true)
public class MerchantLegalEntityDO {
    @TableId(type = IdType.INPUT)
    private String legalEntityId;
    private Long tenantId;
    private String legalName;
    private String registrationHashToken;
    private String businessLicenseToken;
    private String status;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
