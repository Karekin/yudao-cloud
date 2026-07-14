package cn.iocoder.yudao.module.cloudmold.aftersale.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.aftersale.dal.dataobject.AfterSaleItemDO;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.time.LocalDateTime;

@Mapper
public interface AfterSaleItemMapper extends BaseMapperX<AfterSaleItemDO> {
    @Select("SELECT * FROM cloudmold_after_sale_item WHERE tenant_id=#{tenantId} AND after_sale_id=#{id} ORDER BY after_sale_item_id")
    List<AfterSaleItemDO> selectByAfterSale(@Param("tenantId") Long tenantId, @Param("id") String id);

    @Update("UPDATE cloudmold_after_sale_item SET active_guard=NULL WHERE tenant_id=#{tenantId} AND after_sale_id=#{id} AND active_guard=1")
    int releaseActiveGuard(@Param("tenantId") Long tenantId, @Param("id") String id);
}
