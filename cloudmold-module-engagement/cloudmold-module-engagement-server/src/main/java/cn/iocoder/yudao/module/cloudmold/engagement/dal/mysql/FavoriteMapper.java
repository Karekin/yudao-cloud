package cn.iocoder.yudao.module.cloudmold.engagement.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.engagement.dal.dataobject.FavoriteBehaviorDO;
import cn.iocoder.yudao.module.cloudmold.engagement.dal.dataobject.FavoriteDO;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

@Mapper
public interface FavoriteMapper extends BaseMapperX<FavoriteDO> {

    @Select("""
            SELECT favorite_id,tenant_id,principal_id,canonical_spu_id,status,version,
                   source_system,source_type,source_id,created_at,updated_at
            FROM cloudmold_engagement_favorite
            WHERE tenant_id=#{tenantId} AND principal_id=#{principalId} AND canonical_spu_id=#{canonicalSpuId}
            """)
    FavoriteDO selectByBusinessKey(@Param("tenantId") Long tenantId, @Param("principalId") String principalId,
                                   @Param("canonicalSpuId") String canonicalSpuId);

    @Select("""
            SELECT favorite_id,tenant_id,principal_id,canonical_spu_id,status,version,
                   source_system,source_type,source_id,created_at,updated_at
            FROM cloudmold_engagement_favorite
            WHERE tenant_id=#{tenantId} AND principal_id=#{principalId} AND canonical_spu_id=#{canonicalSpuId}
            FOR UPDATE
            """)
    FavoriteDO selectForUpdate(@Param("tenantId") Long tenantId, @Param("principalId") String principalId,
                               @Param("canonicalSpuId") String canonicalSpuId);

    @Update("""
            UPDATE cloudmold_engagement_favorite
            SET status=#{status},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND favorite_id=#{favoriteId} AND version=#{expectedVersion}
            """)
    int updateStatus(@Param("tenantId") Long tenantId, @Param("favoriteId") String favoriteId,
                     @Param("expectedVersion") Long expectedVersion, @Param("status") String status,
                     @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_engagement_favorite_behavior
              (behavior_id,tenant_id,favorite_id,principal_id,canonical_spu_id,behavior_type,favorite_version,
               source_system,source_type,source_id,occurred_at,created_at)
            VALUES (#{behaviorId},#{tenantId},#{favoriteId},#{principalId},#{canonicalSpuId},#{behaviorType},
                    #{favoriteVersion},#{sourceSystem},#{sourceType},#{sourceId},#{occurredAt},#{createdAt})
            """)
    int insertBehavior(FavoriteBehaviorDO row);
}
