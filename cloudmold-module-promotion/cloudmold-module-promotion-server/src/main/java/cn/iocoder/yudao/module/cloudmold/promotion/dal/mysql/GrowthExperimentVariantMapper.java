package cn.iocoder.yudao.module.cloudmold.promotion.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.promotion.dal.dataobject.GrowthExperimentVariantDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface GrowthExperimentVariantMapper extends BaseMapperX<GrowthExperimentVariantDO> {
    @Select("""
        SELECT * FROM cloudmold_promotion_growth_experiment_variant
        WHERE tenant_id=#{tenantId} AND experiment_id=#{experimentId} ORDER BY variant_code
        """)
    List<GrowthExperimentVariantDO> selectByExperiment(@Param("tenantId") Long tenantId,
                                                       @Param("experimentId") String experimentId);

    @Select("""
        SELECT * FROM cloudmold_promotion_growth_experiment_variant
        WHERE tenant_id=#{tenantId} AND experiment_id=#{experimentId} AND variant_code=#{variantCode}
        """)
    GrowthExperimentVariantDO selectByCode(@Param("tenantId") Long tenantId,
                                           @Param("experimentId") String experimentId,
                                           @Param("variantCode") String variantCode);
}
