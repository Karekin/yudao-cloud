package cn.iocoder.yudao.module.cloudmold.catalog.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.catalog.dal.dataobject.*;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

@Mapper
public interface CatalogMasterDataMapper {
    @Insert("""
            INSERT IGNORE INTO cloudmold_catalog_style
              (style_id, tenant_id, style_code, style_name, planning_category_ref, brand_ref, planning_year,
               season_code, wave_code, status, version, created_at, updated_at)
            VALUES (#{id}, #{tenantId}, #{styleCode}, #{styleName}, #{planningCategoryRef}, #{brandRef},
                    #{planningYear}, #{seasonCode}, #{waveCode}, 0, 1, #{now}, #{now})
            """)
    int insertStyle(@Param("id") String id, @Param("tenantId") Long tenantId,
                    @Param("styleCode") String styleCode, @Param("styleName") String styleName,
                    @Param("planningCategoryRef") String planningCategoryRef, @Param("brandRef") String brandRef,
                    @Param("planningYear") Integer planningYear, @Param("seasonCode") String seasonCode,
                    @Param("waveCode") String waveCode,
                    @Param("now") LocalDateTime now);

    @Select("SELECT * FROM cloudmold_catalog_style WHERE tenant_id = #{tenantId} AND style_code = #{code} FOR UPDATE")
    CatalogStyleDO selectStyle(@Param("tenantId") Long tenantId, @Param("code") String code);

    @Insert("""
            INSERT IGNORE INTO cloudmold_catalog_spu
              (spu_id, tenant_id, style_id, spu_code, product_name, sales_category_ref, status, version, created_at, updated_at)
            VALUES (#{id}, #{tenantId}, #{styleId}, #{spuCode}, #{productName}, #{salesCategoryRef}, 0, 1, #{now}, #{now})
            """)
    int insertSpu(@Param("id") String id, @Param("tenantId") Long tenantId, @Param("styleId") String styleId,
                  @Param("spuCode") String spuCode, @Param("productName") String productName,
                  @Param("salesCategoryRef") String salesCategoryRef, @Param("now") LocalDateTime now);

    @Select("SELECT * FROM cloudmold_catalog_spu WHERE tenant_id = #{tenantId} AND spu_code = #{code} FOR UPDATE")
    CatalogSpuDO selectSpu(@Param("tenantId") Long tenantId, @Param("code") String code);

    @Insert("""
            INSERT IGNORE INTO cloudmold_catalog_color
              (color_id, tenant_id, color_code, display_name, status, version, created_at, updated_at)
            VALUES (#{id}, #{tenantId}, #{code}, #{name}, 0, 1, #{now}, #{now})
            """)
    int insertColor(@Param("id") String id, @Param("tenantId") Long tenantId, @Param("code") String code,
                    @Param("name") String name, @Param("now") LocalDateTime now);

    @Select("SELECT * FROM cloudmold_catalog_color WHERE tenant_id = #{tenantId} AND color_code = #{code} FOR UPDATE")
    CatalogColorDO selectColor(@Param("tenantId") Long tenantId, @Param("code") String code);

    @Insert("""
            INSERT IGNORE INTO cloudmold_catalog_size_group
              (size_group_id, tenant_id, size_group_code, size_group_name, status, version, created_at, updated_at)
            VALUES (#{id}, #{tenantId}, #{code}, #{name}, 0, 1, #{now}, #{now})
            """)
    int insertSizeGroup(@Param("id") String id, @Param("tenantId") Long tenantId, @Param("code") String code,
                        @Param("name") String name, @Param("now") LocalDateTime now);

    @Select("SELECT * FROM cloudmold_catalog_size_group WHERE tenant_id = #{tenantId} AND size_group_code = #{code} FOR UPDATE")
    CatalogSizeGroupDO selectSizeGroup(@Param("tenantId") Long tenantId, @Param("code") String code);

    @Insert("""
            INSERT IGNORE INTO cloudmold_catalog_size
              (size_id, tenant_id, size_group_id, size_code, size_name, sort_order, status, version, created_at, updated_at)
            VALUES (#{id}, #{tenantId}, #{groupId}, #{code}, #{name}, #{sortOrder}, 0, 1, #{now}, #{now})
            """)
    int insertSize(@Param("id") String id, @Param("tenantId") Long tenantId, @Param("groupId") String groupId,
                   @Param("code") String code, @Param("name") String name, @Param("sortOrder") Integer sortOrder,
                   @Param("now") LocalDateTime now);

    @Select("""
            SELECT * FROM cloudmold_catalog_size
            WHERE tenant_id = #{tenantId} AND size_group_id = #{groupId} AND size_code = #{code} FOR UPDATE
            """)
    CatalogSizeDO selectSize(@Param("tenantId") Long tenantId, @Param("groupId") String groupId,
                             @Param("code") String code);

    @Insert("""
            INSERT IGNORE INTO cloudmold_catalog_sku
              (sku_id, tenant_id, spu_id, sku_code, color_id, size_id, variant_key, variant_key_hash, base_uom_code,
               status, version, created_at, updated_at)
            VALUES (#{id}, #{tenantId}, #{spuId}, #{skuCode}, #{colorId}, #{sizeId}, #{variantKey}, #{variantKeyHash},
                    #{baseUomCode}, 0, 1, #{now}, #{now})
            """)
    int insertSku(@Param("id") String id, @Param("tenantId") Long tenantId, @Param("spuId") String spuId,
                  @Param("skuCode") String skuCode,
                  @Param("colorId") String colorId, @Param("sizeId") String sizeId,
                  @Param("variantKey") String variantKey, @Param("variantKeyHash") String variantKeyHash,
                  @Param("baseUomCode") String baseUomCode,
                  @Param("now") LocalDateTime now);

    @Select("SELECT * FROM cloudmold_catalog_sku WHERE tenant_id = #{tenantId} AND sku_code = #{code} FOR UPDATE")
    CatalogSkuDO selectSku(@Param("tenantId") Long tenantId, @Param("code") String code);

    @Insert("""
            INSERT IGNORE INTO cloudmold_catalog_barcode
              (barcode_id, tenant_id, sku_id, barcode, barcode_type, is_primary, status, version,
               valid_from, valid_to, created_at, updated_at)
            VALUES (#{id}, #{tenantId}, #{skuId}, #{barcode}, #{barcodeType}, 1, 10, 1,
                    #{now}, NULL, #{now}, #{now})
            """)
    int insertPrimaryBarcode(@Param("id") String id, @Param("tenantId") Long tenantId,
                             @Param("skuId") String skuId, @Param("barcode") String barcode,
                             @Param("barcodeType") String barcodeType, @Param("now") LocalDateTime now);

    @Select("SELECT * FROM cloudmold_catalog_barcode WHERE tenant_id = #{tenantId} AND barcode = #{barcode} FOR UPDATE")
    CatalogBarcodeDO selectBarcode(@Param("tenantId") Long tenantId, @Param("barcode") String barcode);
}
