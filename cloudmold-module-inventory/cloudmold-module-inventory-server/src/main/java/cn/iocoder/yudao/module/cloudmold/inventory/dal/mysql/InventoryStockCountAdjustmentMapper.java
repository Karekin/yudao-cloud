package cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryStockCountAdjustmentDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface InventoryStockCountAdjustmentMapper extends BaseMapperX<InventoryStockCountAdjustmentDO> {

    @Select("""
            SELECT * FROM cloudmold_inventory_stock_count_adjustment_v3
            WHERE tenant_id=#{tenantId} AND stock_count_line_id=#{stockCountLineId}
            """)
    InventoryStockCountAdjustmentDO selectByStockCountLineId(Long tenantId, String stockCountLineId);
}
