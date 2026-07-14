package cn.iocoder.yudao.module.cloudmold.fulfillment.dal.mysql.returning;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.fulfillment.dal.dataobject.returning.ReturnShipmentItemDO;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ReturnShipmentItemMapper extends BaseMapperX<ReturnShipmentItemDO> {
    @Select("SELECT * FROM cloudmold_return_shipment_item WHERE tenant_id=#{tenantId} AND return_shipment_id=#{id} ORDER BY return_shipment_item_id")
    List<ReturnShipmentItemDO> selectByShipment(@Param("tenantId") Long tenantId, @Param("id") String id);
}
