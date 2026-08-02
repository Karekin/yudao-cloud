package cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.warehouse.service.query.StockTransferPageItem;
import cn.iocoder.yudao.module.cloudmold.warehouse.service.query.StockTransferWarehouseIdentity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface StockTransferQueryMapper {

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM cloudmold_stock_transfer_request r
            LEFT JOIN cloudmold_stock_transfer_order o
              ON o.tenant_id=r.tenant_id AND o.request_id=r.request_id
            JOIN cloudmold_warehouse sw
              ON sw.tenant_id=r.tenant_id AND sw.warehouse_id=r.source_warehouse_id
            JOIN cloudmold_warehouse tw
              ON tw.tenant_id=r.tenant_id AND tw.warehouse_id=r.target_warehouse_id
            WHERE r.tenant_id=#{tenantId}
            <if test="keyword != null">
              AND (r.request_code LIKE CONCAT('%',#{keyword},'%')
                OR o.order_code LIKE CONCAT('%',#{keyword},'%')
                OR r.source_business_ref LIKE CONCAT('%',#{keyword},'%')
                OR sw.warehouse_code LIKE CONCAT('%',#{keyword},'%')
                OR sw.name LIKE CONCAT('%',#{keyword},'%')
                OR tw.warehouse_code LIKE CONCAT('%',#{keyword},'%')
                OR tw.name LIKE CONCAT('%',#{keyword},'%')
                OR EXISTS (SELECT 1 FROM cloudmold_stock_transfer_request_line rl
                           WHERE rl.tenant_id=r.tenant_id AND rl.request_id=r.request_id
                             AND rl.canonical_sku_id LIKE CONCAT('%',#{keyword},'%')))
            </if>
            <if test="orderStatus != null">AND o.status=#{orderStatus}</if>
            <if test="sourceWarehouseId != null">AND r.source_warehouse_id=#{sourceWarehouseId}</if>
            <if test="targetWarehouseId != null">AND r.target_warehouse_id=#{targetWarehouseId}</if>
            </script>
            """)
    long countPage(@Param("tenantId") Long tenantId,
                   @Param("keyword") String keyword,
                   @Param("orderStatus") String orderStatus,
                   @Param("sourceWarehouseId") String sourceWarehouseId,
                   @Param("targetWarehouseId") String targetWarehouseId);

    @Select("""
            <script>
            SELECT r.request_id,r.request_code,r.status AS request_status,r.version AS request_version,
                   o.order_id,o.order_code,o.status AS order_status,o.version AS order_version,
                   r.source_business_type,r.source_business_ref,r.owner_type,r.owner_id,
                   r.source_warehouse_id,sw.warehouse_code AS source_warehouse_code,sw.name AS source_warehouse_name,
                   r.target_warehouse_id,tw.warehouse_code AS target_warehouse_code,tw.name AS target_warehouse_name,
                   COUNT(rl.line_id) AS line_count,SUM(rl.requested_quantity) AS total_requested_quantity,
                   SUM(ol.outbound_quantity) AS total_outbound_quantity,
                   SUM(ol.received_quantity) AS total_received_quantity,
                   CASE WHEN COUNT(DISTINCT rl.uom_code)=1 THEN MAX(rl.uom_code) ELSE 'MIXED' END AS uom_code,
                   r.approved_at,o.prepared_at,GREATEST(r.updated_at,COALESCE(o.updated_at,r.updated_at)) AS updated_at
            FROM cloudmold_stock_transfer_request r
            LEFT JOIN cloudmold_stock_transfer_order o
              ON o.tenant_id=r.tenant_id AND o.request_id=r.request_id
            JOIN cloudmold_warehouse sw
              ON sw.tenant_id=r.tenant_id AND sw.warehouse_id=r.source_warehouse_id
            JOIN cloudmold_warehouse tw
              ON tw.tenant_id=r.tenant_id AND tw.warehouse_id=r.target_warehouse_id
            JOIN cloudmold_stock_transfer_request_line rl
              ON rl.tenant_id=r.tenant_id AND rl.request_id=r.request_id
            JOIN cloudmold_stock_transfer_order_line ol
              ON ol.tenant_id=o.tenant_id AND ol.order_id=o.order_id AND ol.line_number=rl.line_number
            WHERE r.tenant_id=#{tenantId}
            <if test="keyword != null">
              AND (r.request_code LIKE CONCAT('%',#{keyword},'%')
                OR o.order_code LIKE CONCAT('%',#{keyword},'%')
                OR r.source_business_ref LIKE CONCAT('%',#{keyword},'%')
                OR sw.warehouse_code LIKE CONCAT('%',#{keyword},'%')
                OR sw.name LIKE CONCAT('%',#{keyword},'%')
                OR tw.warehouse_code LIKE CONCAT('%',#{keyword},'%')
                OR tw.name LIKE CONCAT('%',#{keyword},'%')
                OR rl.canonical_sku_id LIKE CONCAT('%',#{keyword},'%'))
            </if>
            <if test="orderStatus != null">AND o.status=#{orderStatus}</if>
            <if test="sourceWarehouseId != null">AND r.source_warehouse_id=#{sourceWarehouseId}</if>
            <if test="targetWarehouseId != null">AND r.target_warehouse_id=#{targetWarehouseId}</if>
            GROUP BY r.request_id,r.request_code,r.status,r.version,o.order_id,o.order_code,o.status,o.version,
                     r.source_business_type,r.source_business_ref,r.owner_type,r.owner_id,
                     r.source_warehouse_id,sw.warehouse_code,sw.name,r.target_warehouse_id,tw.warehouse_code,tw.name,
                     r.approved_at,o.prepared_at,r.updated_at,o.updated_at
            ORDER BY updated_at DESC,r.request_id DESC
            LIMIT #{offset},#{pageSize}
            </script>
            """)
    List<StockTransferPageItem> selectPage(@Param("tenantId") Long tenantId,
                                           @Param("keyword") String keyword,
                                           @Param("orderStatus") String orderStatus,
                                           @Param("sourceWarehouseId") String sourceWarehouseId,
                                           @Param("targetWarehouseId") String targetWarehouseId,
                                           @Param("offset") long offset,
                                           @Param("pageSize") int pageSize);

    @Select("""
            SELECT warehouse_id,warehouse_code,name AS warehouse_name
            FROM cloudmold_warehouse
            WHERE tenant_id=#{tenantId} AND warehouse_id=#{warehouseId}
            """)
    StockTransferWarehouseIdentity selectWarehouseIdentity(@Param("tenantId") Long tenantId,
                                                            @Param("warehouseId") String warehouseId);
}
