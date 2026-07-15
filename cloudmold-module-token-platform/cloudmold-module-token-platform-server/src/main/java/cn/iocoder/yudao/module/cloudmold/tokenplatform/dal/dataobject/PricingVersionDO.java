package cn.iocoder.yudao.module.cloudmold.tokenplatform.dal.dataobject;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@TableName("cloudmold_token_platform_pricing_version")
@Data
@Accessors(chain = true)
public class PricingVersionDO {
    @TableId(type = IdType.INPUT)
    private String pricingVersionId;
    private Long tenantId;
    private String offeringId;
    private Long pricingVersionNo;
    private Long inputPriceMicrounitsPerMillionTokens;
    private Long cachedInputPriceMicrounitsPerMillionTokens;
    private Long outputPriceMicrounitsPerMillionTokens;
    private LocalDateTime effectiveAt;
    private LocalDateTime createdAt;
}
