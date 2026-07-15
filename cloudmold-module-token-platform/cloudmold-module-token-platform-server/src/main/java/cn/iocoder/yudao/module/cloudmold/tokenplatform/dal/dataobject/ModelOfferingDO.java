package cn.iocoder.yudao.module.cloudmold.tokenplatform.dal.dataobject;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@TableName("cloudmold_token_platform_model_offering")
@Data
@Accessors(chain = true)
public class ModelOfferingDO {
    @TableId(type = IdType.INPUT)
    private String offeringId;
    private Long tenantId;
    private String offeringCode;
    private String providerCode;
    private String modelCode;
    private String currentPricingVersionId;
    private String status;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
