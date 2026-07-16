package cn.iocoder.yudao.module.cloudmold.order.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.OrderItemReturnSettlementDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OrderItemReturnSettlementMapper extends BaseMapperX<OrderItemReturnSettlementDO> {
    @Select("SELECT * FROM cloudmold_order_item_return_settlement WHERE tenant_id=#{tenantId} AND order_item_id=#{orderItemId} FOR UPDATE")
    OrderItemReturnSettlementDO selectForUpdate(@Param("tenantId") Long tenantId,
                                                @Param("orderItemId") String orderItemId);

    @Select("SELECT * FROM cloudmold_order_item_return_settlement WHERE tenant_id=#{tenantId} AND order_item_id=#{orderItemId}")
    OrderItemReturnSettlementDO selectTenant(@Param("tenantId") Long tenantId,
                                             @Param("orderItemId") String orderItemId);

    @Select("SELECT * FROM cloudmold_order_item_return_settlement WHERE tenant_id=#{tenantId} AND order_id=#{orderId} ORDER BY order_item_id")
    List<OrderItemReturnSettlementDO> selectByOrder(@Param("tenantId") Long tenantId,
                                                    @Param("orderId") String orderId);
}
