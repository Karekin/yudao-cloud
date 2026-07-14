package cn.iocoder.yudao.module.cloudmold.catalog.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.catalog.dal.dataobject.*;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

@Mapper
public interface CatalogLifecycleMapper {

    @Select("SELECT * FROM cloudmold_catalog_style WHERE tenant_id=#{tenantId} AND style_id=#{id} FOR UPDATE")
    CatalogStyleDO selectStyleForUpdate(@Param("tenantId") Long tenantId, @Param("id") String id);

    @Select("SELECT * FROM cloudmold_catalog_spu WHERE tenant_id=#{tenantId} AND spu_id=#{id} FOR UPDATE")
    CatalogSpuDO selectSpuForUpdate(@Param("tenantId") Long tenantId, @Param("id") String id);

    @Select("SELECT * FROM cloudmold_catalog_sku WHERE tenant_id=#{tenantId} AND sku_id=#{id} FOR UPDATE")
    CatalogSkuDO selectSkuForUpdate(@Param("tenantId") Long tenantId, @Param("id") String id);

    @Select("SELECT * FROM cloudmold_catalog_color WHERE tenant_id=#{tenantId} AND color_id=#{id} FOR UPDATE")
    CatalogColorDO selectColorForUpdate(@Param("tenantId") Long tenantId, @Param("id") String id);

    @Select("SELECT * FROM cloudmold_catalog_size_group WHERE tenant_id=#{tenantId} AND size_group_id=#{id} FOR UPDATE")
    CatalogSizeGroupDO selectSizeGroupForUpdate(@Param("tenantId") Long tenantId, @Param("id") String id);

    @Select("SELECT * FROM cloudmold_catalog_size WHERE tenant_id=#{tenantId} AND size_id=#{id} FOR UPDATE")
    CatalogSizeDO selectSizeForUpdate(@Param("tenantId") Long tenantId, @Param("id") String id);

    @Select("SELECT * FROM cloudmold_catalog_style WHERE tenant_id=#{tenantId} AND style_id=#{id}")
    CatalogStyleDO selectStyle(@Param("tenantId") Long tenantId, @Param("id") String id);

    @Select("SELECT * FROM cloudmold_catalog_spu WHERE tenant_id=#{tenantId} AND spu_id=#{id}")
    CatalogSpuDO selectSpu(@Param("tenantId") Long tenantId, @Param("id") String id);

    @Select("SELECT * FROM cloudmold_catalog_sku WHERE tenant_id=#{tenantId} AND sku_id=#{id}")
    CatalogSkuDO selectSku(@Param("tenantId") Long tenantId, @Param("id") String id);

    @Select("SELECT * FROM cloudmold_catalog_color WHERE tenant_id=#{tenantId} AND color_id=#{id}")
    CatalogColorDO selectColor(@Param("tenantId") Long tenantId, @Param("id") String id);

    @Select("SELECT * FROM cloudmold_catalog_size_group WHERE tenant_id=#{tenantId} AND size_group_id=#{id}")
    CatalogSizeGroupDO selectSizeGroup(@Param("tenantId") Long tenantId, @Param("id") String id);

    @Select("SELECT * FROM cloudmold_catalog_size WHERE tenant_id=#{tenantId} AND size_id=#{id}")
    CatalogSizeDO selectSize(@Param("tenantId") Long tenantId, @Param("id") String id);

    @Update("""
            UPDATE cloudmold_catalog_style SET status=#{target}, version=version+1, updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND style_id=#{id} AND status=#{current} AND version=#{version}
            """)
    int updateStyle(@Param("tenantId") Long tenantId, @Param("id") String id,
                    @Param("current") Integer current, @Param("target") Integer target,
                    @Param("version") Long version, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_catalog_spu SET status=#{target}, version=version+1, updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND spu_id=#{id} AND status=#{current} AND version=#{version}
            """)
    int updateSpu(@Param("tenantId") Long tenantId, @Param("id") String id,
                  @Param("current") Integer current, @Param("target") Integer target,
                  @Param("version") Long version, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_catalog_sku SET status=#{target}, version=version+1, updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND sku_id=#{id} AND status=#{current} AND version=#{version}
            """)
    int updateSku(@Param("tenantId") Long tenantId, @Param("id") String id,
                  @Param("current") Integer current, @Param("target") Integer target,
                  @Param("version") Long version, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_catalog_color SET status=#{target}, version=version+1, updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND color_id=#{id} AND status=#{current} AND version=#{version}
            """)
    int updateColor(@Param("tenantId") Long tenantId, @Param("id") String id,
                    @Param("current") Integer current, @Param("target") Integer target,
                    @Param("version") Long version, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_catalog_size_group SET status=#{target}, version=version+1, updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND size_group_id=#{id} AND status=#{current} AND version=#{version}
            """)
    int updateSizeGroup(@Param("tenantId") Long tenantId, @Param("id") String id,
                        @Param("current") Integer current, @Param("target") Integer target,
                        @Param("version") Long version, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_catalog_size SET status=#{target}, version=version+1, updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND size_id=#{id} AND status=#{current} AND version=#{version}
            """)
    int updateSize(@Param("tenantId") Long tenantId, @Param("id") String id,
                   @Param("current") Integer current, @Param("target") Integer target,
                   @Param("version") Long version, @Param("now") LocalDateTime now);

    @Select("""
            SELECT COUNT(*) FROM cloudmold_catalog_barcode
            WHERE tenant_id=#{tenantId} AND sku_id=#{skuId} AND status=10 AND is_primary=b'1'
              AND valid_from <= UTC_TIMESTAMP(6) AND (valid_to IS NULL OR valid_to > UTC_TIMESTAMP(6))
            """)
    int countActivePrimaryBarcodes(@Param("tenantId") Long tenantId, @Param("skuId") String skuId);

    @Select("SELECT COUNT(*) FROM cloudmold_catalog_sku WHERE tenant_id=#{tenantId} AND spu_id=#{spuId} AND status=10")
    int countActiveSkus(@Param("tenantId") Long tenantId, @Param("spuId") String spuId);

    @Select("SELECT COUNT(*) FROM cloudmold_catalog_spu WHERE tenant_id=#{tenantId} AND style_id=#{styleId} AND status<>90")
    int countNonArchivedSpus(@Param("tenantId") Long tenantId, @Param("styleId") String styleId);

    @Select("SELECT COUNT(*) FROM cloudmold_catalog_sku WHERE tenant_id=#{tenantId} AND spu_id=#{spuId} AND status<>90")
    int countNonArchivedSkus(@Param("tenantId") Long tenantId, @Param("spuId") String spuId);

    @Select("SELECT COUNT(*) FROM cloudmold_catalog_sku WHERE tenant_id=#{tenantId} AND color_id=#{colorId} AND status<>90")
    int countNonArchivedColorSkus(@Param("tenantId") Long tenantId, @Param("colorId") String colorId);

    @Select("SELECT COUNT(*) FROM cloudmold_catalog_sku WHERE tenant_id=#{tenantId} AND size_id=#{sizeId} AND status<>90")
    int countNonArchivedSizeSkus(@Param("tenantId") Long tenantId, @Param("sizeId") String sizeId);

    @Select("SELECT COUNT(*) FROM cloudmold_catalog_size WHERE tenant_id=#{tenantId} AND size_group_id=#{groupId} AND status<>90")
    int countNonArchivedSizes(@Param("tenantId") Long tenantId, @Param("groupId") String groupId);
}
