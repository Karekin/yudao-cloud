package cn.iocoder.yudao.module.cloudmold.aftersale.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.aftersale.service.query.AfterSaleDetailItem;
import cn.iocoder.yudao.module.cloudmold.aftersale.service.query.AfterSaleDetailVO;
import cn.iocoder.yudao.module.cloudmold.aftersale.service.query.AfterSalePageItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AfterSalePageMapper {

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM cloudmold_after_sale_case c
            JOIN cloudmold_after_sale_item i
              ON i.tenant_id = c.tenant_id
             AND i.after_sale_id = c.after_sale_id
            LEFT JOIN cloudmold_return_fulfillment rf
              ON rf.tenant_id = c.tenant_id
             AND rf.return_fulfillment_id = c.return_fulfillment_id
            LEFT JOIN cloudmold_after_sale_resolution_saga s
              ON s.tenant_id = c.tenant_id
             AND s.saga_id = c.resolution_saga_id
            WHERE c.tenant_id = #{tenantId}
            <if test="afterSaleId != null">AND c.after_sale_id = #{afterSaleId}</if>
            <if test="afterSaleNo != null">AND c.after_sale_no LIKE CONCAT('%', #{afterSaleNo}, '%')</if>
            <if test="orderId != null">AND c.order_id = #{orderId}</if>
            <if test="orderNo != null">AND c.order_no LIKE CONCAT('%', #{orderNo}, '%')</if>
            <if test="canonicalSkuId != null">AND i.canonical_sku_id = #{canonicalSkuId}</if>
            <if test="caseStatus != null">AND c.status = #{caseStatus}</if>
            <if test="refundStatus != null">AND c.refund_status = #{refundStatus}</if>
            <if test="afterSaleType != null">AND c.after_sale_type = #{afterSaleType}</if>
            <if test="reasonCode != null">AND c.reason_code = #{reasonCode}</if>
            <if test="responsibility != null">AND c.responsibility = #{responsibility}</if>
            </script>
            """)
    long countPage(@Param("tenantId") Long tenantId,
                   @Param("afterSaleId") String afterSaleId,
                   @Param("afterSaleNo") String afterSaleNo,
                   @Param("orderId") String orderId,
                   @Param("orderNo") String orderNo,
                   @Param("canonicalSkuId") String canonicalSkuId,
                   @Param("caseStatus") String caseStatus,
                   @Param("refundStatus") String refundStatus,
                   @Param("afterSaleType") String afterSaleType,
                   @Param("reasonCode") String reasonCode,
                   @Param("responsibility") String responsibility);

    @Select("""
            <script>
            SELECT c.after_sale_id,
                   c.after_sale_no,
                   c.order_id,
                   c.order_no,
                   c.status AS case_status,
                   c.refund_status,
                   c.after_sale_type,
                   c.reason_code,
                   c.responsibility,
                   i.after_sale_item_id,
                   i.order_item_id,
                   i.canonical_sku_id,
                   i.quantity,
                   c.approved_amount_minor,
                   c.currency_code,
                   c.return_fulfillment_id,
                   rf.status AS return_fulfillment_status,
                   c.resolution_saga_id,
                   s.status AS resolution_saga_status,
                   s.active_step AS resolution_saga_active_step,
                   s.attempt_count AS resolution_saga_attempt_count,
                   s.last_error_code AS resolution_saga_last_error_code,
                   c.version AS aggregate_version,
                   s.version AS resolution_saga_version,
                   c.created_at,
                   c.updated_at,
                   c.completed_at
            FROM cloudmold_after_sale_case c
            JOIN cloudmold_after_sale_item i
              ON i.tenant_id = c.tenant_id
             AND i.after_sale_id = c.after_sale_id
            LEFT JOIN cloudmold_return_fulfillment rf
              ON rf.tenant_id = c.tenant_id
             AND rf.return_fulfillment_id = c.return_fulfillment_id
            LEFT JOIN cloudmold_after_sale_resolution_saga s
              ON s.tenant_id = c.tenant_id
             AND s.saga_id = c.resolution_saga_id
            WHERE c.tenant_id = #{tenantId}
            <if test="afterSaleId != null">AND c.after_sale_id = #{afterSaleId}</if>
            <if test="afterSaleNo != null">AND c.after_sale_no LIKE CONCAT('%', #{afterSaleNo}, '%')</if>
            <if test="orderId != null">AND c.order_id = #{orderId}</if>
            <if test="orderNo != null">AND c.order_no LIKE CONCAT('%', #{orderNo}, '%')</if>
            <if test="canonicalSkuId != null">AND i.canonical_sku_id = #{canonicalSkuId}</if>
            <if test="caseStatus != null">AND c.status = #{caseStatus}</if>
            <if test="refundStatus != null">AND c.refund_status = #{refundStatus}</if>
            <if test="afterSaleType != null">AND c.after_sale_type = #{afterSaleType}</if>
            <if test="reasonCode != null">AND c.reason_code = #{reasonCode}</if>
            <if test="responsibility != null">AND c.responsibility = #{responsibility}</if>
            ORDER BY c.updated_at DESC, c.after_sale_id DESC, i.after_sale_item_id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<AfterSalePageItem> selectPage(@Param("tenantId") Long tenantId,
                                       @Param("afterSaleId") String afterSaleId,
                                       @Param("afterSaleNo") String afterSaleNo,
                                       @Param("orderId") String orderId,
                                       @Param("orderNo") String orderNo,
                                       @Param("canonicalSkuId") String canonicalSkuId,
                                       @Param("caseStatus") String caseStatus,
                                       @Param("refundStatus") String refundStatus,
                                       @Param("afterSaleType") String afterSaleType,
                                       @Param("reasonCode") String reasonCode,
                                       @Param("responsibility") String responsibility,
                                       @Param("offset") long offset,
                                       @Param("limit") int limit);

    @Select("""
            <script>
            SELECT c.after_sale_id,
                   c.after_sale_no,
                   c.order_id,
                   c.order_no,
                   c.status AS case_status,
                   c.refund_status,
                   c.after_sale_type,
                   c.reason_code,
                   c.reason,
                   c.responsibility,
                   c.approved_amount_minor,
                   c.currency_code,
                   c.return_fulfillment_id,
                   rf.status AS return_fulfillment_status,
                   c.resolution_saga_id,
                   s.status AS resolution_saga_status,
                   s.active_step AS resolution_saga_active_step,
                   s.attempt_count AS resolution_saga_attempt_count,
                   s.max_attempts AS resolution_saga_max_attempts,
                   s.last_error_code AS resolution_saga_last_error_code,
                   s.last_error_message AS resolution_saga_last_error_message,
                   c.buyer_id,
                   c.payment_id,
                   c.reviewer_id,
                   c.forward_fulfillment_id,
                   c.owner_id,
                   c.warehouse_id,
                   c.uom_code,
                   c.version AS aggregate_version,
                   c.created_at,
                   c.updated_at,
                   c.completed_at
            FROM cloudmold_after_sale_case c
            LEFT JOIN cloudmold_return_fulfillment rf
              ON rf.tenant_id = c.tenant_id
             AND rf.return_fulfillment_id = c.return_fulfillment_id
            LEFT JOIN cloudmold_after_sale_resolution_saga s
              ON s.tenant_id = c.tenant_id
             AND s.saga_id = c.resolution_saga_id
            WHERE c.tenant_id = #{tenantId}
              AND c.after_sale_id = #{afterSaleId}
            </script>
            """)
    AfterSaleDetailVO selectDetail(@Param("tenantId") Long tenantId,
                                   @Param("afterSaleId") String afterSaleId);

    @Select("""
            SELECT after_sale_item_id,
                   order_item_id,
                   canonical_sku_id,
                   quantity,
                   line_amount_minor,
                   discount_amount_minor,
                   net_amount_minor,
                   listing_id,
                   listing_offer_id,
                   created_at
            FROM cloudmold_after_sale_item
            WHERE tenant_id = #{tenantId}
              AND after_sale_id = #{afterSaleId}
            ORDER BY created_at ASC, after_sale_item_id ASC
            """)
    List<AfterSaleDetailItem> selectAfterSaleItems(@Param("tenantId") Long tenantId,
                                                   @Param("afterSaleId") String afterSaleId);
}
