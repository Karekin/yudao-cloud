package cn.iocoder.yudao.module.cloudmold.catalog.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.catalog.service.query.CatalogSkuPageItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CatalogQueryMapper {

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM cloudmold_catalog_sku k
            JOIN cloudmold_catalog_spu p
              ON p.tenant_id = k.tenant_id AND p.spu_id = k.spu_id
            WHERE k.tenant_id = #{tenantId}
            <if test="skuCode != null">AND k.sku_code LIKE CONCAT('%', #{skuCode}, '%')</if>
            <if test="spuCode != null">AND p.spu_code LIKE CONCAT('%', #{spuCode}, '%')</if>
            <if test="status != null">AND k.status = #{status}</if>
            </script>
            """)
    long countSkuPage(@Param("tenantId") Long tenantId,
                      @Param("skuCode") String skuCode,
                      @Param("spuCode") String spuCode,
                      @Param("status") Integer status);

    @Select("""
            <script>
            SELECT s.style_id AS canonical_style_id,
                   s.style_code,
                   s.style_name,
                   p.spu_id AS canonical_spu_id,
                   p.spu_code,
                   p.product_name,
                   k.sku_id AS canonical_sku_id,
                   k.sku_code,
                   c.color_code,
                   c.display_name AS color_name,
                   g.size_group_code,
                   z.size_code,
                   z.size_name,
                   (SELECT b.barcode
                      FROM cloudmold_catalog_barcode b
                     WHERE b.tenant_id = k.tenant_id
                       AND b.sku_id = k.sku_id
                       AND b.is_primary = b'1'
                       AND b.status = 10
                       AND b.valid_from &lt;= UTC_TIMESTAMP(6)
                       AND (b.valid_to IS NULL OR b.valid_to &gt; UTC_TIMESTAMP(6))
                     ORDER BY b.valid_from DESC, b.barcode_id DESC
                     LIMIT 1) AS primary_barcode,
                   k.base_uom_code,
                   k.status AS catalog_status,
                   k.version AS aggregate_version,
                   k.updated_at
            FROM cloudmold_catalog_sku k
            JOIN cloudmold_catalog_spu p
              ON p.tenant_id = k.tenant_id AND p.spu_id = k.spu_id
            JOIN cloudmold_catalog_style s
              ON s.tenant_id = p.tenant_id AND s.style_id = p.style_id
            JOIN cloudmold_catalog_color c
              ON c.tenant_id = k.tenant_id AND c.color_id = k.color_id
            JOIN cloudmold_catalog_size z
              ON z.tenant_id = k.tenant_id AND z.size_id = k.size_id
            JOIN cloudmold_catalog_size_group g
              ON g.tenant_id = z.tenant_id AND g.size_group_id = z.size_group_id
            WHERE k.tenant_id = #{tenantId}
            <if test="skuCode != null">AND k.sku_code LIKE CONCAT('%', #{skuCode}, '%')</if>
            <if test="spuCode != null">AND p.spu_code LIKE CONCAT('%', #{spuCode}, '%')</if>
            <if test="status != null">AND k.status = #{status}</if>
            ORDER BY k.updated_at DESC, k.sku_id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<CatalogSkuPageItem> selectSkuPage(@Param("tenantId") Long tenantId,
                                           @Param("skuCode") String skuCode,
                                           @Param("spuCode") String spuCode,
                                           @Param("status") Integer status,
                                           @Param("offset") long offset,
                                           @Param("limit") int limit);
}
