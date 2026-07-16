package cn.iocoder.yudao.module.cloudmold.aftersale.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.aftersale.dal.dataobject.AfterSaleBenefitReversalDO;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AfterSaleBenefitReversalMapper extends BaseMapperX<AfterSaleBenefitReversalDO> {

    @Select("""
            SELECT benefit_reversal_id,tenant_id,reversal_batch_id,after_sale_id,after_sale_item_id,
                   order_id,order_item_id,benefit_application_id,benefit_allocation_id,benefit_type,
                   benefit_source_type,benefit_source_id,benefit_source_version,entitlement_id,
                   amount_minor,currency_code,entitlement_effect_status,occurred_at,created_at
            FROM cloudmold_after_sale_benefit_reversal
            WHERE tenant_id=#{tenantId} AND after_sale_id=#{afterSaleId}
            ORDER BY benefit_application_id,benefit_allocation_id
            """)
    List<AfterSaleBenefitReversalDO> selectByAfterSale(@Param("tenantId") Long tenantId,
                                                        @Param("afterSaleId") String afterSaleId);

    @Select("""
            SELECT COALESCE(SUM(amount_minor),0)
            FROM cloudmold_after_sale_benefit_reversal
            WHERE tenant_id=#{tenantId} AND order_id=#{orderId}
              AND benefit_application_id=#{benefitApplicationId}
            """)
    Long sumByApplication(@Param("tenantId") Long tenantId,
                          @Param("orderId") String orderId,
                          @Param("benefitApplicationId") String benefitApplicationId);
}
