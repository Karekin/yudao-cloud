package cn.iocoder.yudao.module.cloudmold.fulfillment.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.fulfillment.dal.dataobject.FulfillmentItemDO;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface FulfillmentItemMapper extends BaseMapperX<FulfillmentItemDO> {
    @Select("""
            SELECT fulfillment_item_id,tenant_id,fulfillment_id,order_item_id,canonical_sku_id,quantity,
                   reservation_id,created_at,updated_at
            FROM cloudmold_fulfillment_item
            WHERE tenant_id=#{tenantId} AND fulfillment_id=#{fulfillmentId}
            ORDER BY order_item_id
            """)
    List<FulfillmentItemDO> selectByFulfillment(@Param("tenantId") Long tenantId,
                                                @Param("fulfillmentId") String fulfillmentId);
}
