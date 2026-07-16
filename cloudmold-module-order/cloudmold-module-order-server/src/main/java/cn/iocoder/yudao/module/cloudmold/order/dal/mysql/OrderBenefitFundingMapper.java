package cn.iocoder.yudao.module.cloudmold.order.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.OrderBenefitFundingDO;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface OrderBenefitFundingMapper extends BaseMapperX<OrderBenefitFundingDO> {

    @Select("""
            SELECT benefit_funding_id,tenant_id,order_id,benefit_application_id,benefit_allocation_id,
                   funding_key,funder_type,funder_id,amount_minor,currency_code,created_at
            FROM cloudmold_order_benefit_funding
            WHERE tenant_id=#{tenantId} AND order_id=#{orderId}
            ORDER BY benefit_application_id,benefit_allocation_id,funding_key,benefit_funding_id
            """)
    List<OrderBenefitFundingDO> selectByOrder(@Param("tenantId") Long tenantId,
                                               @Param("orderId") String orderId);
}
