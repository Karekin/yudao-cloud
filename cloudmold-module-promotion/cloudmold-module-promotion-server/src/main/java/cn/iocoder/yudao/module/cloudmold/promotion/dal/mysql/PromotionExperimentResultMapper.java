package cn.iocoder.yudao.module.cloudmold.promotion.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.promotion.dal.dataobject.PromotionExperimentResultDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface PromotionExperimentResultMapper extends BaseMapperX<PromotionExperimentResultDO> {
    @Select("SELECT * FROM cloudmold_promotion_experiment_result WHERE tenant_id=#{tenantId} AND experiment_id=#{id}")
    PromotionExperimentResultDO selectById(@Param("tenantId") Long tenantId, @Param("id") String id);

    @Select("SELECT * FROM cloudmold_promotion_experiment_result WHERE tenant_id=#{tenantId} AND experiment_id=#{id} FOR UPDATE")
    PromotionExperimentResultDO selectForUpdate(@Param("tenantId") Long tenantId, @Param("id") String id);

    @Select("SELECT * FROM cloudmold_promotion_experiment_result WHERE tenant_id=#{tenantId} AND experiment_code=#{code}")
    PromotionExperimentResultDO selectByCode(@Param("tenantId") Long tenantId, @Param("code") String code);

    @Update("""
        UPDATE cloudmold_promotion_experiment_result
        SET merchant_id=#{row.merchantId},
            measured_from=#{row.measuredFrom},
            measured_to=#{row.measuredTo},
            baseline_contribution_profit_minor=#{row.baselineContributionProfitMinor},
            treatment_contribution_profit_minor=#{row.treatmentContributionProfitMinor},
            incremental_contribution_profit_minor=#{row.incrementalContributionProfitMinor},
            promotion_cost_minor=#{row.promotionCostMinor},
            eligible_population_count=#{row.eligiblePopulationCount},
            treatment_population_count=#{row.treatmentPopulationCount},
            control_population_count=#{row.controlPopulationCount},
            currency_code=#{row.currencyCode},
            methodology_ref=#{row.methodologyRef},
            version=version+1,
            updated_at=#{now}
        WHERE tenant_id=#{tenantId} AND experiment_id=#{experimentId} AND version=#{version}
        """)
    int updateCas(@Param("tenantId") Long tenantId,
                  @Param("experimentId") String experimentId,
                  @Param("version") Long version,
                  @Param("row") PromotionExperimentResultDO row,
                  @Param("now") LocalDateTime now);
}
