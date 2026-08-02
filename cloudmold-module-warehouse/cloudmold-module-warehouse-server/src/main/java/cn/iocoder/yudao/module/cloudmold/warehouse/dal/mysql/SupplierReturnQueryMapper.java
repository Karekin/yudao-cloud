package cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.warehouse.service.supplierreturn.SupplierReturnPageItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SupplierReturnQueryMapper {

    @Select("""
            <script>
            SELECT COUNT(DISTINCT sr.return_id)
            FROM cloudmold_supplier_return sr
            LEFT JOIN cloudmold_supplier_return_line line
              ON line.tenant_id=sr.tenant_id AND line.return_id=sr.return_id
            WHERE sr.tenant_id=#{tenantId}
            <if test="keyword != null">
              AND (
                   sr.return_id LIKE CONCAT('%',#{keyword},'%')
                OR sr.return_code LIKE CONCAT('%',#{keyword},'%')
                OR sr.purchase_order_id LIKE CONCAT('%',#{keyword},'%')
                OR sr.receipt_id LIKE CONCAT('%',#{keyword},'%')
                OR sr.supplier_id LIKE CONCAT('%',#{keyword},'%')
                OR line.canonical_sku_id LIKE CONCAT('%',#{keyword},'%')
              )
            </if>
            <if test="status != null">AND sr.status=#{status}</if>
            <if test="purchaseOrderId != null">AND sr.purchase_order_id=#{purchaseOrderId}</if>
            <if test="receiptId != null">AND sr.receipt_id=#{receiptId}</if>
            <if test="supplierId != null">AND sr.supplier_id=#{supplierId}</if>
            <if test="warehouseId != null">AND sr.warehouse_id=#{warehouseId}</if>
            </script>
            """)
    long countPage(@Param("tenantId") Long tenantId,
                   @Param("keyword") String keyword,
                   @Param("status") String status,
                   @Param("purchaseOrderId") String purchaseOrderId,
                   @Param("receiptId") String receiptId,
                   @Param("supplierId") String supplierId,
                   @Param("warehouseId") String warehouseId);

    @Select("""
            <script>
            SELECT sr.return_id,sr.return_code,sr.purchase_order_id,sr.receipt_id,sr.supplier_id,sr.owner_id,
                   sr.warehouse_id,sr.status,sr.version,
                   SUM(line.return_quantity) total_return_quantity,
                   SUM(line.dispatched_quantity) total_dispatched_quantity,
                   COUNT(line.return_line_id) line_count,
                   sr.created_at,sr.updated_at
            FROM cloudmold_supplier_return sr
            LEFT JOIN cloudmold_supplier_return_line line
              ON line.tenant_id=sr.tenant_id AND line.return_id=sr.return_id
            WHERE sr.tenant_id=#{tenantId}
            <if test="keyword != null">
              AND (
                   sr.return_id LIKE CONCAT('%',#{keyword},'%')
                OR sr.return_code LIKE CONCAT('%',#{keyword},'%')
                OR sr.purchase_order_id LIKE CONCAT('%',#{keyword},'%')
                OR sr.receipt_id LIKE CONCAT('%',#{keyword},'%')
                OR sr.supplier_id LIKE CONCAT('%',#{keyword},'%')
                OR line.canonical_sku_id LIKE CONCAT('%',#{keyword},'%')
              )
            </if>
            <if test="status != null">AND sr.status=#{status}</if>
            <if test="purchaseOrderId != null">AND sr.purchase_order_id=#{purchaseOrderId}</if>
            <if test="receiptId != null">AND sr.receipt_id=#{receiptId}</if>
            <if test="supplierId != null">AND sr.supplier_id=#{supplierId}</if>
            <if test="warehouseId != null">AND sr.warehouse_id=#{warehouseId}</if>
            GROUP BY sr.return_id,sr.return_code,sr.purchase_order_id,sr.receipt_id,sr.supplier_id,
                     sr.owner_id,sr.warehouse_id,sr.status,sr.version,sr.created_at,sr.updated_at
            ORDER BY sr.updated_at DESC,sr.return_id DESC
            LIMIT #{offset},#{pageSize}
            </script>
            """)
    List<SupplierReturnPageItem> selectPage(@Param("tenantId") Long tenantId,
                                            @Param("keyword") String keyword,
                                            @Param("status") String status,
                                            @Param("purchaseOrderId") String purchaseOrderId,
                                            @Param("receiptId") String receiptId,
                                            @Param("supplierId") String supplierId,
                                            @Param("warehouseId") String warehouseId,
                                            @Param("offset") long offset,
                                            @Param("pageSize") int pageSize);
}
