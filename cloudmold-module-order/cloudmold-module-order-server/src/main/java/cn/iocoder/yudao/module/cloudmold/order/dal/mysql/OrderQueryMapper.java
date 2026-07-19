package cn.iocoder.yudao.module.cloudmold.order.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.order.service.query.OrderDetailItem;
import cn.iocoder.yudao.module.cloudmold.order.service.query.OrderDetailVO;
import cn.iocoder.yudao.module.cloudmold.order.service.query.OrderPageItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OrderQueryMapper {

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM cloudmold_order_header h
            WHERE h.tenant_id = #{tenantId}
            <if test="orderId != null">AND h.order_id = #{orderId}</if>
            <if test="orderNo != null">AND h.order_no LIKE CONCAT('%', #{orderNo}, '%')</if>
            <if test="buyerId != null">AND h.buyer_id = #{buyerId}</if>
            <if test="status != null">AND h.status = #{status}</if>
            </script>
            """)
    long countOrderPage(@Param("tenantId") Long tenantId,
                        @Param("orderId") String orderId,
                        @Param("orderNo") String orderNo,
                        @Param("buyerId") String buyerId,
                        @Param("status") String status);

    @Select("""
            <script>
            SELECT h.order_id,
                   h.order_no,
                   h.buyer_id,
                   h.status,
                   COALESCE(i.item_count, 0) AS item_count,
                   h.total_quantity,
                   h.product_amount_minor,
                   h.shipping_amount_minor,
                   h.discount_amount_minor,
                   h.payable_amount_minor,
                   h.currency_code,
                   h.payment_id,
                   p.status AS payment_status,
                   h.fulfillment_id,
                   f.status AS fulfillment_status,
                   h.shipment_id,
                   h.refund_id,
                   h.cancellation_saga_id,
                   h.version AS aggregate_version,
                   h.created_at,
                   h.updated_at
            FROM cloudmold_order_header h
            LEFT JOIN (
                SELECT tenant_id, order_id, COUNT(*) AS item_count
                FROM cloudmold_order_item
                WHERE tenant_id = #{tenantId}
                GROUP BY tenant_id, order_id
            ) i
              ON i.tenant_id = h.tenant_id
             AND i.order_id = h.order_id
            LEFT JOIN cloudmold_payment p
              ON p.tenant_id = h.tenant_id
             AND p.payment_id = h.payment_id
            LEFT JOIN cloudmold_fulfillment_order f
              ON f.tenant_id = h.tenant_id
             AND f.fulfillment_id = h.fulfillment_id
            WHERE h.tenant_id = #{tenantId}
            <if test="orderId != null">AND h.order_id = #{orderId}</if>
            <if test="orderNo != null">AND h.order_no LIKE CONCAT('%', #{orderNo}, '%')</if>
            <if test="buyerId != null">AND h.buyer_id = #{buyerId}</if>
            <if test="status != null">AND h.status = #{status}</if>
            ORDER BY h.updated_at DESC, h.order_id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<OrderPageItem> selectOrderPage(@Param("tenantId") Long tenantId,
                                        @Param("orderId") String orderId,
                                        @Param("orderNo") String orderNo,
                                        @Param("buyerId") String buyerId,
                                        @Param("status") String status,
                                        @Param("offset") long offset,
                                        @Param("limit") int limit);

    @Select("""
            SELECT h.order_id,
                   h.order_no,
                   h.run_id,
                   h.buyer_id,
                   h.status,
                   COALESCE(i.item_count, 0) AS item_count,
                   h.total_quantity,
                   h.product_amount_minor,
                   h.shipping_amount_minor,
                   h.discount_amount_minor,
                   h.payable_amount_minor,
                   h.currency_code,
                   h.payment_id,
                   p.status AS payment_status,
                   h.fulfillment_id,
                   f.status AS fulfillment_status,
                   h.shipment_id,
                   h.refund_id,
                   h.cancellation_saga_id,
                   h.pre_cancellation_status,
                   h.cancellation_responsibility_party,
                   h.cancellation_responsibility_code,
                   h.version AS aggregate_version,
                   h.created_at,
                   h.updated_at
            FROM cloudmold_order_header h
            LEFT JOIN (
                SELECT tenant_id, order_id, COUNT(*) AS item_count
                FROM cloudmold_order_item
                WHERE tenant_id = #{tenantId}
                GROUP BY tenant_id, order_id
            ) i
              ON i.tenant_id = h.tenant_id
             AND i.order_id = h.order_id
            LEFT JOIN cloudmold_payment p
              ON p.tenant_id = h.tenant_id
             AND p.payment_id = h.payment_id
            LEFT JOIN cloudmold_fulfillment_order f
              ON f.tenant_id = h.tenant_id
             AND f.fulfillment_id = h.fulfillment_id
            WHERE h.tenant_id = #{tenantId}
              AND h.order_id = #{orderId}
            """)
    OrderDetailVO selectOrderDetail(@Param("tenantId") Long tenantId,
                                    @Param("orderId") String orderId);

    @Select("""
            SELECT order_item_id,
                   line_key,
                   canonical_sku_id,
                   quantity,
                   unit_price_minor,
                   line_amount_minor,
                   discount_amount_minor,
                   net_amount_minor,
                   merchandise_cost_minor,
                   reservation_id,
                   listing_id,
                   listing_offer_id,
                   listing_revision,
                   listing_version,
                   channel_code,
                   shop_id,
                   created_at,
                   updated_at
            FROM cloudmold_order_item
            WHERE tenant_id = #{tenantId}
              AND order_id = #{orderId}
            ORDER BY created_at ASC, order_item_id ASC
            """)
    List<OrderDetailItem> selectOrderItems(@Param("tenantId") Long tenantId,
                                           @Param("orderId") String orderId);
}
