package cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.WarehouseZoneDO;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface WarehouseZoneMapper extends BaseMapperX<WarehouseZoneDO> {
    @Select("SELECT * FROM cloudmold_warehouse_zone WHERE tenant_id=#{tenantId} AND zone_id=#{zoneId} FOR UPDATE")
    WarehouseZoneDO selectForUpdate(@Param("tenantId") Long tenantId, @Param("zoneId") String zoneId);
    @Select("SELECT * FROM cloudmold_warehouse_zone WHERE tenant_id=#{tenantId} AND zone_id=#{zoneId}")
    WarehouseZoneDO selectCurrent(@Param("tenantId") Long tenantId, @Param("zoneId") String zoneId);
    @Select("SELECT * FROM cloudmold_warehouse_zone WHERE tenant_id=#{tenantId} AND warehouse_id=#{warehouseId} AND zone_code=#{code}")
    WarehouseZoneDO selectByCode(@Param("tenantId") Long tenantId, @Param("warehouseId") String warehouseId,
                                 @Param("code") String code);
    @Select("SELECT * FROM cloudmold_warehouse_zone WHERE tenant_id=#{tenantId} AND warehouse_id=#{warehouseId} AND status='ACTIVE' ORDER BY zone_id")
    List<WarehouseZoneDO> selectActiveByWarehouse(@Param("tenantId") Long tenantId,
                                                   @Param("warehouseId") String warehouseId);
    @Update("UPDATE cloudmold_warehouse_zone SET status=#{status},version=version+1,updated_at=#{now} WHERE tenant_id=#{tenantId} AND zone_id=#{id} AND version=#{version}")
    int updateStatusCas(@Param("tenantId") Long tenantId, @Param("id") String id, @Param("version") Long version,
                        @Param("status") String status, @Param("now") LocalDateTime now);
}
