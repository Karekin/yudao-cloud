package cn.iocoder.yudao.module.cloudmold.catalog.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.catalog.service.query.CatalogSkuBarcodeItem;
import cn.iocoder.yudao.module.cloudmold.catalog.service.query.CatalogSkuDetailVO;
import cn.iocoder.yudao.module.cloudmold.catalog.service.query.CatalogSkuPageItem;
import cn.iocoder.yudao.module.cloudmold.catalog.service.workflow.CatalogWaveAggregateRow;
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

    @Select("""
            SELECT s.style_id AS canonical_style_id,
                   s.style_code,
                   s.style_name,
                   p.spu_id AS canonical_spu_id,
                   p.spu_code,
                   p.product_name,
                   k.sku_id AS canonical_sku_id,
                   k.sku_code,
                   k.variant_key,
                   k.variant_key_hash,
                   k.color_id,
                   c.color_code,
                   c.display_name AS color_name,
                   k.size_id,
                   g.size_group_id,
                   z.size_code,
                   z.size_name,
                   g.size_group_code,
                   s.status AS style_status,
                   s.version AS style_version,
                   p.status AS spu_status,
                   p.version AS spu_version,
                   c.status AS color_status,
                   c.version AS color_version,
                   g.status AS size_group_status,
                   g.version AS size_group_version,
                   z.status AS size_status,
                   z.version AS size_version,
                   (SELECT b.barcode
                      FROM cloudmold_catalog_barcode b
                     WHERE b.tenant_id = k.tenant_id
                       AND b.sku_id = k.sku_id
                       AND b.is_primary = b'1'
                       AND b.status = 10
                       AND b.valid_from <= UTC_TIMESTAMP(6)
                       AND (b.valid_to IS NULL OR b.valid_to > UTC_TIMESTAMP(6))
                     ORDER BY b.valid_from DESC, b.barcode_id DESC
                     LIMIT 1) AS primary_barcode,
                   k.base_uom_code,
                   k.status AS catalog_status,
                   k.version AS aggregate_version,
                   k.created_at,
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
              AND k.sku_id = #{skuId}
            """)
    CatalogSkuDetailVO selectSkuDetail(@Param("tenantId") Long tenantId,
                                       @Param("skuId") String skuId);

    @Select("""
            SELECT barcode_id,
                   barcode,
                   barcode_type,
                   is_primary,
                   status,
                   valid_from,
                   valid_to
            FROM cloudmold_catalog_barcode
            WHERE tenant_id = #{tenantId}
              AND sku_id = #{skuId}
            ORDER BY is_primary DESC, valid_from DESC, barcode_id DESC
            """)
    List<CatalogSkuBarcodeItem> selectSkuBarcodes(@Param("tenantId") Long tenantId,
                                                  @Param("skuId") String skuId);

    @Select("""
            SELECT COUNT(DISTINCT s.style_id) AS style_count,
                   COUNT(DISTINCT CASE WHEN s.status = 10 THEN s.style_id END) AS active_style_count,
                   COUNT(DISTINCT p.spu_id) AS spu_count,
                   COUNT(DISTINCT CASE WHEN p.status = 30 THEN p.spu_id END) AS active_spu_count,
                   COUNT(DISTINCT k.sku_id) AS sku_count,
                   COUNT(DISTINCT CASE WHEN k.status = 10 THEN k.sku_id END) AS active_sku_count,
                   DATE_FORMAT(MAX(GREATEST(
                           COALESCE(s.updated_at, TIMESTAMP('1970-01-01 00:00:00')),
                           COALESCE(p.updated_at, TIMESTAMP('1970-01-01 00:00:00')),
                           COALESCE(k.updated_at, TIMESTAMP('1970-01-01 00:00:00'))
                   )), '%Y-%m-%dT%H:%i:%sZ') AS last_catalog_updated_at
            FROM cloudmold_catalog_style s
            LEFT JOIN cloudmold_catalog_spu p
              ON p.tenant_id = s.tenant_id AND p.style_id = s.style_id
            LEFT JOIN cloudmold_catalog_sku k
              ON k.tenant_id = p.tenant_id AND k.spu_id = p.spu_id
            WHERE s.tenant_id = #{tenantId}
              AND s.planning_year = #{planningYear}
              AND s.season_code = #{seasonCode}
              AND s.wave_code = #{waveCode}
            """)
    CatalogWaveAggregateRow selectWaveAggregate(@Param("tenantId") Long tenantId,
                                                @Param("planningYear") Integer planningYear,
                                                @Param("seasonCode") String seasonCode,
                                                @Param("waveCode") String waveCode);
}
