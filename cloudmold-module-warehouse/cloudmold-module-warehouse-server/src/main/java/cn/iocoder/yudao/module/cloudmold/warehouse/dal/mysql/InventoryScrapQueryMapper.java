package cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.InventoryScrapDocumentDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.service.inventoryscrap.InventoryScrapQueryService.InventoryScrapPageItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface InventoryScrapQueryMapper extends BaseMapperX<InventoryScrapDocumentDO> {

    @Select("""
            SELECT COUNT(1)
            FROM cloudmold_inventory_scrap_document
            WHERE tenant_id=#{tenantId}
              AND (#{keyword} IS NULL OR scrap_code LIKE CONCAT('%',#{keyword},'%')
                   OR reason_code LIKE CONCAT('%',#{keyword},'%')
                   OR owner_id LIKE CONCAT('%',#{keyword},'%'))
              AND (#{status} IS NULL OR status=#{status})
              AND (#{warehouseId} IS NULL OR warehouse_id=#{warehouseId})
            """)
    long countPage(@Param("tenantId") Long tenantId,
                   @Param("keyword") String keyword,
                   @Param("status") String status,
                   @Param("warehouseId") String warehouseId);

    @Select("""
            SELECT scrap_id AS scrapId,
                   scrap_code AS scrapCode,
                   reason_code AS reasonCode,
                   owner_type AS ownerType,
                   owner_id AS ownerId,
                   warehouse_id AS warehouseId,
                   status AS scrapStatus,
                   total_requested_quantity AS totalRequestedQuantity,
                   total_disposed_quantity AS totalDisposedQuantity,
                   line_count AS lineCount,
                   version AS aggregateVersion,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM cloudmold_inventory_scrap_document
            WHERE tenant_id=#{tenantId}
              AND (#{keyword} IS NULL OR scrap_code LIKE CONCAT('%',#{keyword},'%')
                   OR reason_code LIKE CONCAT('%',#{keyword},'%')
                   OR owner_id LIKE CONCAT('%',#{keyword},'%'))
              AND (#{status} IS NULL OR status=#{status})
              AND (#{warehouseId} IS NULL OR warehouse_id=#{warehouseId})
            ORDER BY updated_at DESC, scrap_id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<InventoryScrapPageItem> selectPage(@Param("tenantId") Long tenantId,
                                            @Param("keyword") String keyword,
                                            @Param("status") String status,
                                            @Param("warehouseId") String warehouseId,
                                            @Param("offset") long offset,
                                            @Param("limit") int limit);
}
