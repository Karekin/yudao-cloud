package cn.iocoder.yudao.module.cloudmold.order.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.OrderItemDO;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OrderItemMapper extends BaseMapperX<OrderItemDO> {
    @Select("""
            SELECT order_item_id,tenant_id,order_id,line_key,canonical_sku_id,quantity,unit_price_minor,
                   line_amount_minor,discount_amount_minor,net_amount_minor,
                   reservation_id,listing_id,listing_offer_id,listing_revision,listing_version,
                   channel_code,shop_id,created_at,updated_at
            FROM cloudmold_order_item
            WHERE tenant_id=#{tenantId} AND order_id=#{orderId}
            ORDER BY order_item_id
            """)
    List<OrderItemDO> selectByOrder(@Param("tenantId") Long tenantId, @Param("orderId") String orderId);

    @Update("""
            UPDATE cloudmold_order_item SET reservation_id=#{reservationId},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND order_id=#{orderId} AND order_item_id=#{orderItemId}
              AND reservation_id IS NULL
            """)
    int bindReservation(@Param("tenantId") Long tenantId, @Param("orderId") String orderId,
                        @Param("orderItemId") String orderItemId, @Param("reservationId") String reservationId,
                        @Param("now") LocalDateTime now);
}
