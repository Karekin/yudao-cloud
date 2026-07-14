package cn.iocoder.yudao.module.cloudmold.order.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("cloudmold_order_item")
public class OrderItemDO {
    @TableId(type = IdType.INPUT)
    private String orderItemId;
    private Long tenantId;
    private String orderId;
    private String canonicalSkuId;
    private BigDecimal quantity;
    private Long unitPriceMinor;
    private Long lineAmountMinor;
    private String reservationId;
    private String listingId;
    private String listingOfferId;
    private Integer listingRevision;
    private Long listingVersion;
    private String channelCode;
    private String shopId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
