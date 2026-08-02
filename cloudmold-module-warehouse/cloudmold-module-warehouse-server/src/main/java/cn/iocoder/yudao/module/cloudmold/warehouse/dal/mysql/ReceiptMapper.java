package cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.ReceiptDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.service.query.InboundQueryService;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ReceiptMapper extends BaseMapperX<ReceiptDO> {
    @Select("SELECT * FROM cloudmold_warehouse_procurement_receipt WHERE tenant_id=#{tenantId} AND receipt_id=#{receiptId} FOR UPDATE")
    ReceiptDO selectForUpdate(@Param("tenantId") Long tenantId, @Param("receiptId") String receiptId);

    @Select("SELECT * FROM cloudmold_warehouse_procurement_receipt WHERE tenant_id=#{tenantId} AND receipt_id=#{receiptId}")
    ReceiptDO selectCurrent(@Param("tenantId") Long tenantId, @Param("receiptId") String receiptId);

    @Select("SELECT * FROM cloudmold_warehouse_procurement_receipt WHERE tenant_id=#{tenantId} AND asn_id=#{asnId} ORDER BY created_at ASC, receipt_id ASC")
    List<ReceiptDO> selectByAsn(@Param("tenantId") Long tenantId, @Param("asnId") String asnId);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM cloudmold_warehouse_procurement_receipt r
            WHERE r.tenant_id=#{tenantId}
            <if test="keyword != null">
              AND (r.receipt_id LIKE CONCAT('%',#{keyword},'%')
                OR r.receipt_no LIKE CONCAT('%',#{keyword},'%')
                OR r.procurement_order_id LIKE CONCAT('%',#{keyword},'%'))
            </if>
            <if test="receiptId != null">AND r.receipt_id=#{receiptId}</if>
            <if test="receiptNo != null">AND r.receipt_no=#{receiptNo}</if>
            <if test="procurementOrderId != null">AND r.procurement_order_id=#{procurementOrderId}</if>
            <if test="supplierId != null">AND r.supplier_id=#{supplierId}</if>
            <if test="warehouseId != null">AND r.warehouse_id=#{warehouseId}</if>
            <if test="status != null">AND r.status=#{status}</if>
            </script>
            """)
    long countPage(@Param("tenantId") Long tenantId,
                   @Param("keyword") String keyword,
                   @Param("receiptId") String receiptId,
                   @Param("receiptNo") String receiptNo,
                   @Param("procurementOrderId") String procurementOrderId,
                   @Param("supplierId") String supplierId,
                   @Param("warehouseId") String warehouseId,
                   @Param("status") String status);

    @Select("""
            <script>
            SELECT r.receipt_id,r.receipt_no,r.procurement_order_id,r.asn_id,r.supplier_id,r.warehouse_id,
                   r.status,r.version,
                   SUM(l.received_quantity) AS total_received_quantity,
                   SUM(l.pending_quality_quantity) AS total_pending_quality_quantity,
                   SUM(l.accepted_quantity) AS total_accepted_quantity,
                   SUM(l.rejected_quantity) AS total_rejected_quantity,
                   SUM(l.quarantined_quantity) AS total_quarantined_quantity,
                   SUM(l.cumulative_putaway_quantity) AS total_putaway_quantity,
                   COUNT(l.receipt_line_id) AS line_count,r.created_at,r.updated_at
            FROM cloudmold_warehouse_procurement_receipt r
            JOIN cloudmold_warehouse_procurement_receipt_line l
              ON l.tenant_id=r.tenant_id AND l.receipt_id=r.receipt_id
            WHERE r.tenant_id=#{tenantId}
            <if test="keyword != null">
              AND (r.receipt_id LIKE CONCAT('%',#{keyword},'%')
                OR r.receipt_no LIKE CONCAT('%',#{keyword},'%')
                OR r.procurement_order_id LIKE CONCAT('%',#{keyword},'%'))
            </if>
            <if test="receiptId != null">AND r.receipt_id=#{receiptId}</if>
            <if test="receiptNo != null">AND r.receipt_no=#{receiptNo}</if>
            <if test="procurementOrderId != null">AND r.procurement_order_id=#{procurementOrderId}</if>
            <if test="supplierId != null">AND r.supplier_id=#{supplierId}</if>
            <if test="warehouseId != null">AND r.warehouse_id=#{warehouseId}</if>
            <if test="status != null">AND r.status=#{status}</if>
            GROUP BY r.receipt_id,r.receipt_no,r.procurement_order_id,r.asn_id,r.supplier_id,r.warehouse_id,
                     r.status,r.version,r.created_at,r.updated_at
            ORDER BY r.created_at DESC,r.receipt_id DESC
            LIMIT #{offset},#{pageSize}
            </script>
            """)
    List<InboundQueryService.InboundReceiptPageItem> selectPage(
            @Param("tenantId") Long tenantId,
            @Param("keyword") String keyword,
            @Param("receiptId") String receiptId,
            @Param("receiptNo") String receiptNo,
            @Param("procurementOrderId") String procurementOrderId,
            @Param("supplierId") String supplierId,
            @Param("warehouseId") String warehouseId,
            @Param("status") String status,
            @Param("offset") long offset,
            @Param("pageSize") int pageSize);

    @Update("UPDATE cloudmold_warehouse_procurement_receipt SET status=#{status},version=version+1,updated_at=#{now} "
            + "WHERE tenant_id=#{tenantId} AND receipt_id=#{receiptId} AND version=#{version}")
    int updateStatusCas(@Param("tenantId") Long tenantId, @Param("receiptId") String receiptId,
                        @Param("version") Long version, @Param("status") String status,
                        @Param("now") LocalDateTime now);
}
