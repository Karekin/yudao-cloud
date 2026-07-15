package cn.iocoder.yudao.module.cloudmold.promotion.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.promotion.dal.dataobject.AdvertisingPlacementDO;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;

@Mapper
public interface AdvertisingPlacementMapper extends BaseMapperX<AdvertisingPlacementDO> {
    @Select("SELECT * FROM cloudmold_promotion_advertising_placement WHERE tenant_id=#{tenantId} AND placement_id=#{id}")
    AdvertisingPlacementDO selectOneById(@Param("tenantId") Long tenantId, @Param("id") String id);
    @Select("SELECT * FROM cloudmold_promotion_advertising_placement WHERE tenant_id=#{tenantId} AND placement_id=#{id} FOR UPDATE")
    AdvertisingPlacementDO selectForUpdate(@Param("tenantId") Long tenantId, @Param("id") String id);
    @Select("SELECT * FROM cloudmold_promotion_advertising_placement WHERE tenant_id=#{tenantId} AND placement_code=#{code}")
    AdvertisingPlacementDO selectByCode(@Param("tenantId") Long tenantId, @Param("code") String code);
    @Update("UPDATE cloudmold_promotion_advertising_placement SET status=#{status},version=version+1,updated_at=#{now} WHERE tenant_id=#{tenantId} AND placement_id=#{id} AND version=#{version}")
    int updateStatusCas(@Param("tenantId") Long tenantId, @Param("id") String id, @Param("version") Long version,
                        @Param("status") String status, @Param("now") LocalDateTime now);
}
