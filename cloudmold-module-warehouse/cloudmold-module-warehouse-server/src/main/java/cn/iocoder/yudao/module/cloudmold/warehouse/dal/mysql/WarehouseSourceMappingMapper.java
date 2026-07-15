package cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.WarehouseSourceMappingDO;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface WarehouseSourceMappingMapper extends BaseMapperX<WarehouseSourceMappingDO> {
    @Select("SELECT * FROM cloudmold_warehouse_source_mapping WHERE tenant_id=#{tenantId} AND mapping_id=#{mappingId} FOR UPDATE")
    WarehouseSourceMappingDO selectForUpdate(@Param("tenantId") Long tenantId, @Param("mappingId") String mappingId);
    @Select("""
        SELECT * FROM cloudmold_warehouse_source_mapping
        WHERE tenant_id=#{tenantId} AND source_system=#{sourceSystem} AND source_type=#{sourceType} AND source_id=#{sourceId}
          AND status='ACTIVE' AND valid_from<=#{at} AND (valid_to IS NULL OR valid_to>#{at}) FOR UPDATE
        """)
    List<WarehouseSourceMappingDO> selectEffectiveForUpdate(@Param("tenantId") Long tenantId,
            @Param("sourceSystem") String sourceSystem, @Param("sourceType") String sourceType,
            @Param("sourceId") String sourceId, @Param("at") LocalDateTime at);
    @Select("""
        SELECT * FROM cloudmold_warehouse_source_mapping
        WHERE tenant_id=#{tenantId} AND source_system=#{sourceSystem} AND source_type=#{sourceType} AND source_id=#{sourceId}
          AND status='ACTIVE' AND valid_from<=#{at} AND (valid_to IS NULL OR valid_to>#{at})
        """)
    List<WarehouseSourceMappingDO> selectEffective(@Param("tenantId") Long tenantId,
            @Param("sourceSystem") String sourceSystem, @Param("sourceType") String sourceType,
            @Param("sourceId") String sourceId, @Param("at") LocalDateTime at);
    @Update("UPDATE cloudmold_warehouse_source_mapping SET status='ENDED',valid_to=#{at},version=version+1,updated_at=#{at} WHERE tenant_id=#{tenantId} AND mapping_id=#{id} AND version=#{version} AND status='ACTIVE'")
    int endCas(@Param("tenantId") Long tenantId, @Param("id") String id, @Param("version") Long version,
               @Param("at") LocalDateTime at);
}
