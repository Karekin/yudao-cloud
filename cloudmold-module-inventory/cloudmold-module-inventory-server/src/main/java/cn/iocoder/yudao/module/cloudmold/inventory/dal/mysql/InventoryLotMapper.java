package cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryLotDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface InventoryLotMapper extends BaseMapperX<InventoryLotDO> {
    @Select("SELECT * FROM cloudmold_inventory_lot WHERE tenant_id=#{tenantId} AND lot_id=#{lotId} FOR UPDATE")
    InventoryLotDO selectCurrent(@Param("tenantId") Long tenantId, @Param("lotId") String lotId);
}
