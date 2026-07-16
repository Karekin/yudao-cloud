package cn.iocoder.yudao.module.cloudmold.aftersale.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("cloudmold_after_sale_item")
public class AfterSaleItemDO {
    @TableId(type = IdType.INPUT)
    private String afterSaleItemId;
    private Long tenantId;
    private String afterSaleId;
    private String orderItemId;
    private String canonicalSkuId;
    private BigDecimal quantity;
    private Long lineAmountMinor;
    private Long discountAmountMinor;
    private Long netAmountMinor;
    private String listingId;
    private String listingOfferId;
    private Integer activeGuard;
    private LocalDateTime createdAt;
}
