package cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.WarehouseDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface CanonicalWarehouseMapper extends BaseMapperX<WarehouseDO> {

    @Select("SELECT * FROM cloudmold_warehouse WHERE tenant_id=#{tenantId} AND warehouse_id=#{warehouseId} FOR UPDATE")
    WarehouseDO selectForUpdate(@Param("tenantId") Long tenantId, @Param("warehouseId") String warehouseId);

    @Select("SELECT * FROM cloudmold_warehouse WHERE tenant_id=#{tenantId} AND warehouse_id=#{warehouseId}")
    WarehouseDO selectCurrent(@Param("tenantId") Long tenantId, @Param("warehouseId") String warehouseId);

    @Select("SELECT * FROM cloudmold_warehouse WHERE tenant_id=#{tenantId} AND warehouse_code=#{code}")
    WarehouseDO selectByCode(@Param("tenantId") Long tenantId, @Param("code") String code);

    @Update("UPDATE cloudmold_warehouse SET status=#{status},version=version+1,updated_at=#{now} " +
            "WHERE tenant_id=#{tenantId} AND warehouse_id=#{id} AND version=#{version}")
    int updateStatusCas(@Param("tenantId") Long tenantId, @Param("id") String id,
                        @Param("version") Long version, @Param("status") String status,
                        @Param("now") LocalDateTime now);
}
