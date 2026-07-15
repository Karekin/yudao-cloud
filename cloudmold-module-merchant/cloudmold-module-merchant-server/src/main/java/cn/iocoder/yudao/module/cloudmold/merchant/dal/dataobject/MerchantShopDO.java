package cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@TableName("cloudmold_merchant_shop")
@Data
@Accessors(chain = true)
public class MerchantShopDO {
    @TableId(type = IdType.INPUT)
    private String shopId;
    private Long tenantId;
    private String merchantId;
    private String channelCode;
    private String externalShopId;
    private String status;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
