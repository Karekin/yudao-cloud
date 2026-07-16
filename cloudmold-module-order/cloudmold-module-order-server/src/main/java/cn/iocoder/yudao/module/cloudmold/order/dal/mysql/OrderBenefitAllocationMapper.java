package cn.iocoder.yudao.module.cloudmold.order.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.OrderBenefitAllocationDO;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface OrderBenefitAllocationMapper extends BaseMapperX<OrderBenefitAllocationDO> {

    @Select("""
            SELECT benefit_allocation_id,tenant_id,order_id,benefit_application_id,allocation_key,
                   order_item_id,line_key,amount_minor,currency_code,created_at
            FROM cloudmold_order_benefit_allocation
            WHERE tenant_id=#{tenantId} AND order_id=#{orderId}
            ORDER BY benefit_application_id,allocation_key,benefit_allocation_id
            """)
    List<OrderBenefitAllocationDO> selectByOrder(@Param("tenantId") Long tenantId,
                                                  @Param("orderId") String orderId);
}
