package cn.iocoder.yudao.module.cloudmold.fulfillment.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.fulfillment.service.query.FulfillmentPageItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FulfillmentQueryMapper {

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM cloudmold_fulfillment_order f
            LEFT JOIN cloudmold_shipment s
              ON s.tenant_id = f.tenant_id
             AND s.fulfillment_id = f.fulfillment_id
            WHERE f.tenant_id = #{tenantId}
            <if test="fulfillmentId != null">AND f.fulfillment_id = #{fulfillmentId}</if>
            <if test="fulfillmentNo != null">AND f.fulfillment_no LIKE CONCAT('%', #{fulfillmentNo}, '%')</if>
            <if test="orderId != null">AND f.order_id = #{orderId}</if>
            <if test="orderNo != null">AND f.order_no LIKE CONCAT('%', #{orderNo}, '%')</if>
            <if test="sellerId != null">AND f.seller_id = #{sellerId}</if>
            <if test="warehouseId != null">AND f.warehouse_id = #{warehouseId}</if>
            <if test="status != null">AND f.status = #{status}</if>
            <if test="shipmentId != null">AND s.shipment_id = #{shipmentId}</if>
            <if test="shipmentStatus != null">AND s.status = #{shipmentStatus}</if>
            <if test="carrierCode != null">AND s.carrier_code = #{carrierCode}</if>
            <if test="waybillNo != null">AND s.waybill_no LIKE CONCAT('%', #{waybillNo}, '%')</if>
            </script>
            """)
    long countPage(@Param("tenantId") Long tenantId,
                   @Param("fulfillmentId") String fulfillmentId,
                   @Param("fulfillmentNo") String fulfillmentNo,
                   @Param("orderId") String orderId,
                   @Param("orderNo") String orderNo,
                   @Param("sellerId") String sellerId,
                   @Param("warehouseId") String warehouseId,
                   @Param("status") String status,
                   @Param("shipmentId") String shipmentId,
                   @Param("shipmentStatus") String shipmentStatus,
                   @Param("carrierCode") String carrierCode,
                   @Param("waybillNo") String waybillNo);

    @Select("""
            <script>
            SELECT f.fulfillment_id,
                   f.fulfillment_no,
                   f.order_id,
                   f.order_no,
                   f.seller_id,
                   f.warehouse_id,
                   f.status,
                   COALESCE(i.item_count, 0) AS item_count,
                   COALESCE(i.total_quantity, 0) AS total_quantity,
                   s.shipment_id AS first_slice_shipment_id,
                   s.status AS first_slice_shipment_status,
                   s.carrier_code,
                   s.waybill_no,
                   f.delivery_promise_version_ref,
                   f.promised_delivery_at,
                   f.cancellation_saga_id AS cancellation_ref,
                   f.version AS aggregate_version,
                   f.updated_at
            FROM cloudmold_fulfillment_order f
            LEFT JOIN (
                SELECT tenant_id,
                       fulfillment_id,
                       COUNT(*) AS item_count,
                       SUM(quantity) AS total_quantity
                FROM cloudmold_fulfillment_item
                WHERE tenant_id = #{tenantId}
                GROUP BY tenant_id, fulfillment_id
            ) i
              ON i.tenant_id = f.tenant_id
             AND i.fulfillment_id = f.fulfillment_id
            LEFT JOIN cloudmold_shipment s
              ON s.tenant_id = f.tenant_id
             AND s.fulfillment_id = f.fulfillment_id
            WHERE f.tenant_id = #{tenantId}
            <if test="fulfillmentId != null">AND f.fulfillment_id = #{fulfillmentId}</if>
            <if test="fulfillmentNo != null">AND f.fulfillment_no LIKE CONCAT('%', #{fulfillmentNo}, '%')</if>
            <if test="orderId != null">AND f.order_id = #{orderId}</if>
            <if test="orderNo != null">AND f.order_no LIKE CONCAT('%', #{orderNo}, '%')</if>
            <if test="sellerId != null">AND f.seller_id = #{sellerId}</if>
            <if test="warehouseId != null">AND f.warehouse_id = #{warehouseId}</if>
            <if test="status != null">AND f.status = #{status}</if>
            <if test="shipmentId != null">AND s.shipment_id = #{shipmentId}</if>
            <if test="shipmentStatus != null">AND s.status = #{shipmentStatus}</if>
            <if test="carrierCode != null">AND s.carrier_code = #{carrierCode}</if>
            <if test="waybillNo != null">AND s.waybill_no LIKE CONCAT('%', #{waybillNo}, '%')</if>
            ORDER BY f.updated_at DESC, f.fulfillment_id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<FulfillmentPageItem> selectPage(@Param("tenantId") Long tenantId,
                                         @Param("fulfillmentId") String fulfillmentId,
                                         @Param("fulfillmentNo") String fulfillmentNo,
                                         @Param("orderId") String orderId,
                                         @Param("orderNo") String orderNo,
                                         @Param("sellerId") String sellerId,
                                         @Param("warehouseId") String warehouseId,
                                         @Param("status") String status,
                                         @Param("shipmentId") String shipmentId,
                                         @Param("shipmentStatus") String shipmentStatus,
                                         @Param("carrierCode") String carrierCode,
                                         @Param("waybillNo") String waybillNo,
                                         @Param("offset") long offset,
                                         @Param("limit") int limit);
}
