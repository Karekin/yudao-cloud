package cn.iocoder.yudao.module.cloudmold.order.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.OrderReturnSettlementDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OrderReturnSettlementMapper extends BaseMapperX<OrderReturnSettlementDO> {
    @Select("SELECT * FROM cloudmold_order_return_settlement WHERE tenant_id=#{tenantId} AND order_id=#{orderId} FOR UPDATE")
    OrderReturnSettlementDO selectForUpdate(@Param("tenantId") Long tenantId,
                                            @Param("orderId") String orderId);
}
