package cn.iocoder.yudao.module.cloudmold.order.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.OrderReturnEffectDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OrderReturnEffectMapper extends BaseMapperX<OrderReturnEffectDO> {
    @Select("SELECT * FROM cloudmold_order_return_effect WHERE tenant_id=#{tenantId} AND after_sale_id=#{afterSaleId}")
    OrderReturnEffectDO selectByAfterSale(@Param("tenantId") Long tenantId,
                                         @Param("afterSaleId") String afterSaleId);
}
