package cn.iocoder.yudao.module.cloudmold.promotion.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.promotion.dal.dataobject.GrowthExperimentExposureDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface GrowthExperimentExposureMapper extends BaseMapperX<GrowthExperimentExposureDO> {
    @Select("""
        SELECT * FROM cloudmold_promotion_growth_experiment_exposure
        WHERE tenant_id=#{tenantId} AND exposure_key=#{exposureKey}
        """)
    GrowthExperimentExposureDO selectByKey(@Param("tenantId") Long tenantId,
                                           @Param("exposureKey") String exposureKey);

    @Select("""
        SELECT COUNT(*) FROM cloudmold_promotion_growth_experiment_exposure
        WHERE tenant_id=#{tenantId} AND experiment_id=#{experimentId} AND variant_code=#{variantCode}
        """)
    int countByVariant(@Param("tenantId") Long tenantId, @Param("experimentId") String experimentId,
                       @Param("variantCode") String variantCode);
}
