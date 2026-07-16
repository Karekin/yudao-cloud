package cn.iocoder.yudao.module.cloudmold.aftersale.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.aftersale.dal.dataobject.AfterSaleBenefitFundingReversalDO;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AfterSaleBenefitFundingReversalMapper extends BaseMapperX<AfterSaleBenefitFundingReversalDO> {

    @Select("""
            SELECT funding_reversal_id,tenant_id,benefit_reversal_id,reversal_batch_id,after_sale_id,
                   benefit_funding_id,funder_type,funder_id,amount_minor,currency_code,occurred_at,created_at
            FROM cloudmold_after_sale_benefit_funding_reversal
            WHERE tenant_id=#{tenantId} AND after_sale_id=#{afterSaleId}
            ORDER BY benefit_reversal_id,benefit_funding_id
            """)
    List<AfterSaleBenefitFundingReversalDO> selectByAfterSale(@Param("tenantId") Long tenantId,
                                                               @Param("afterSaleId") String afterSaleId);
}
