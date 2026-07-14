package cn.iocoder.yudao.module.cloudmold.fulfillment.dal.mysql.returning;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.fulfillment.dal.dataobject.returning.ReturnFulfillmentItemDO;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ReturnFulfillmentItemMapper extends BaseMapperX<ReturnFulfillmentItemDO> {
    @Select("SELECT * FROM cloudmold_return_fulfillment_item WHERE tenant_id=#{tenantId} AND return_fulfillment_id=#{id} ORDER BY return_fulfillment_item_id")
    List<ReturnFulfillmentItemDO> selectByFulfillment(@Param("tenantId") Long tenantId, @Param("id") String id);
}
