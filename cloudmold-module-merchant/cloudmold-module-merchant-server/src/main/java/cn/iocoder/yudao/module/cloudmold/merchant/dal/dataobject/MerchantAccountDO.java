package cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@TableName("cloudmold_merchant_account")
@Data
@Accessors(chain = true)
public class MerchantAccountDO {
    @TableId(type = IdType.INPUT)
    private String merchantId;
    private Long tenantId;
    private String merchantCode;
    private String legalEntityId;
    private String status;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
