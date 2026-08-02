package cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.PutawayDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.service.query.InboundPutawayPageItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PutawayMapper extends BaseMapperX<PutawayDO> {
    @Select("SELECT * FROM cloudmold_warehouse_procurement_putaway WHERE tenant_id=#{tenantId} AND putaway_id=#{putawayId} FOR UPDATE")
    PutawayDO selectForUpdate(@Param("tenantId") Long tenantId, @Param("putawayId") String putawayId);

    @Select("SELECT * FROM cloudmold_warehouse_procurement_putaway WHERE tenant_id=#{tenantId} AND putaway_id=#{putawayId}")
    PutawayDO selectCurrent(@Param("tenantId") Long tenantId, @Param("putawayId") String putawayId);

    @Select("SELECT * FROM cloudmold_warehouse_procurement_putaway WHERE tenant_id=#{tenantId} AND receipt_id=#{receiptId} ORDER BY created_at,putaway_id")
    java.util.List<PutawayDO> selectByReceipt(@Param("tenantId") Long tenantId, @Param("receiptId") String receiptId);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM cloudmold_warehouse_procurement_putaway p
            JOIN cloudmold_warehouse_procurement_receipt r
              ON r.tenant_id=p.tenant_id AND r.receipt_id=p.receipt_id
            WHERE p.tenant_id=#{tenantId}
            <if test="keyword != null">
              AND (p.putaway_id LIKE CONCAT('%',#{keyword},'%')
                OR r.receipt_no LIKE CONCAT('%',#{keyword},'%')
                OR r.procurement_order_id LIKE CONCAT('%',#{keyword},'%'))
            </if>
            <if test="receiptId != null">AND p.receipt_id=#{receiptId}</if>
            <if test="procurementOrderId != null">AND r.procurement_order_id=#{procurementOrderId}</if>
            <if test="warehouseId != null">AND p.warehouse_id=#{warehouseId}</if>
            <if test="status != null">AND p.status=#{status}</if>
            </script>
            """)
    long countPage(@Param("tenantId") Long tenantId,
                   @Param("keyword") String keyword,
                   @Param("receiptId") String receiptId,
                   @Param("procurementOrderId") String procurementOrderId,
                   @Param("warehouseId") String warehouseId,
                   @Param("status") String status);

    @Select("""
            <script>
            SELECT p.putaway_id,p.receipt_id,r.receipt_no,r.procurement_order_id,r.supplier_id,
                   p.warehouse_id,p.status,p.version,COUNT(l.putaway_line_id) AS line_count,
                   COALESCE(SUM(l.putaway_quantity),0) AS total_putaway_quantity,p.created_at,p.updated_at
            FROM cloudmold_warehouse_procurement_putaway p
            JOIN cloudmold_warehouse_procurement_receipt r
              ON r.tenant_id=p.tenant_id AND r.receipt_id=p.receipt_id
            JOIN cloudmold_warehouse_procurement_putaway_line l
              ON l.tenant_id=p.tenant_id AND l.putaway_id=p.putaway_id
            WHERE p.tenant_id=#{tenantId}
            <if test="keyword != null">
              AND (p.putaway_id LIKE CONCAT('%',#{keyword},'%')
                OR r.receipt_no LIKE CONCAT('%',#{keyword},'%')
                OR r.procurement_order_id LIKE CONCAT('%',#{keyword},'%'))
            </if>
            <if test="receiptId != null">AND p.receipt_id=#{receiptId}</if>
            <if test="procurementOrderId != null">AND r.procurement_order_id=#{procurementOrderId}</if>
            <if test="warehouseId != null">AND p.warehouse_id=#{warehouseId}</if>
            <if test="status != null">AND p.status=#{status}</if>
            GROUP BY p.putaway_id,p.receipt_id,r.receipt_no,r.procurement_order_id,r.supplier_id,
                     p.warehouse_id,p.status,p.version,p.created_at,p.updated_at
            ORDER BY p.created_at DESC,p.putaway_id DESC
            LIMIT #{offset},#{pageSize}
            </script>
            """)
    List<InboundPutawayPageItem> selectPage(@Param("tenantId") Long tenantId,
                                            @Param("keyword") String keyword,
                                            @Param("receiptId") String receiptId,
                                            @Param("procurementOrderId") String procurementOrderId,
                                            @Param("warehouseId") String warehouseId,
                                            @Param("status") String status,
                                            @Param("offset") long offset,
                                            @Param("pageSize") int pageSize);

    @Update("UPDATE cloudmold_warehouse_procurement_putaway SET status=#{status},version=version+1,updated_at=#{now} "
            + "WHERE tenant_id=#{tenantId} AND putaway_id=#{putawayId} AND version=#{version}")
    int updateStatusCas(@Param("tenantId") Long tenantId, @Param("putawayId") String putawayId,
                        @Param("version") Long version, @Param("status") String status,
                        @Param("now") LocalDateTime now);
}
