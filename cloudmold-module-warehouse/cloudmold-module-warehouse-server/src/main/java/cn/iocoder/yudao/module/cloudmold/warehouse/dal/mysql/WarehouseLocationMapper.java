package cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.WarehouseLocationDO;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;

@Mapper
public interface WarehouseLocationMapper extends BaseMapperX<WarehouseLocationDO> {
    @Select("SELECT * FROM cloudmold_warehouse_location WHERE tenant_id=#{tenantId} AND location_id=#{locationId} FOR UPDATE")
    WarehouseLocationDO selectForUpdate(@Param("tenantId") Long tenantId, @Param("locationId") String locationId);
    @Select("SELECT * FROM cloudmold_warehouse_location WHERE tenant_id=#{tenantId} AND location_id=#{locationId}")
    WarehouseLocationDO selectCurrent(@Param("tenantId") Long tenantId, @Param("locationId") String locationId);
    @Select("SELECT * FROM cloudmold_warehouse_location WHERE tenant_id=#{tenantId} AND warehouse_id=#{warehouseId} AND location_code=#{code}")
    WarehouseLocationDO selectByCode(@Param("tenantId") Long tenantId, @Param("warehouseId") String warehouseId,
                                     @Param("code") String code);
    @Update("UPDATE cloudmold_warehouse_location SET status=#{status},version=version+1,updated_at=#{now} WHERE tenant_id=#{tenantId} AND location_id=#{id} AND version=#{version}")
    int updateStatusCas(@Param("tenantId") Long tenantId, @Param("id") String id, @Param("version") Long version,
                        @Param("status") String status, @Param("now") LocalDateTime now);
}
