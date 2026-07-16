package cn.iocoder.yudao.module.cloudmold.order.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.OrderBenefitApplicationDO;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface OrderBenefitApplicationMapper extends BaseMapperX<OrderBenefitApplicationDO> {

    @Select("""
            SELECT benefit_application_id,tenant_id,order_id,application_key,benefit_type,
                   benefit_source_type,benefit_source_id,benefit_source_version,entitlement_id,
                   amount_minor,currency_code,calculation_digest,operation_id,version,occurred_at,created_at
            FROM cloudmold_order_benefit_application
            WHERE tenant_id=#{tenantId} AND order_id=#{orderId}
            ORDER BY application_key,benefit_application_id
            """)
    List<OrderBenefitApplicationDO> selectByOrder(@Param("tenantId") Long tenantId,
                                                   @Param("orderId") String orderId);
}
