package cn.iocoder.yudao.module.cloudmold.integration.yudao.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.integration.yudao.dal.dataobject.LegacyCatalogProjectionDO;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

@Mapper
public interface LegacyCatalogProjectionMapper {

    @Insert("""
            INSERT INTO cloudmold_catalog_legacy_projection
              (tenant_id,target_system,target_entity,canonical_type,canonical_id,aggregate_version,
               payload,payload_hash,status,created_at,updated_at)
            VALUES
              (#{tenantId},#{targetSystem},#{targetEntity},'SKU',#{canonicalId},#{aggregateVersion},
               CAST(#{payload} AS JSON),#{payloadHash},0,#{now},#{now})
            ON DUPLICATE KEY UPDATE projection_id=LAST_INSERT_ID(projection_id)
            """)
    int insertOrResolve(@Param("tenantId") Long tenantId,
                        @Param("targetSystem") String targetSystem,
                        @Param("targetEntity") String targetEntity,
                        @Param("canonicalId") String canonicalId,
                        @Param("aggregateVersion") Long aggregateVersion,
                        @Param("payload") String payload,
                        @Param("payloadHash") String payloadHash,
                        @Param("now") LocalDateTime now);

    @Select("SELECT LAST_INSERT_ID()")
    Long selectLastInsertId();

    @Select("""
            SELECT projection_id,tenant_id,target_system,target_entity,canonical_type,canonical_id,
                   aggregate_version,payload,payload_hash,status,last_error_summary
            FROM cloudmold_catalog_legacy_projection
            WHERE tenant_id=#{tenantId} AND target_system=#{targetSystem} AND target_entity=#{targetEntity}
              AND canonical_type='SKU' AND canonical_id=#{canonicalId}
            FOR UPDATE
            """)
    LegacyCatalogProjectionDO selectByBusinessKeyForUpdate(@Param("tenantId") Long tenantId,
                                                            @Param("targetSystem") String targetSystem,
                                                            @Param("targetEntity") String targetEntity,
                                                            @Param("canonicalId") String canonicalId);

    @Select("""
            SELECT projection_id,tenant_id,target_system,target_entity,canonical_type,canonical_id,
                   aggregate_version,payload,payload_hash,status,last_error_summary
            FROM cloudmold_catalog_legacy_projection
            WHERE projection_id=#{projectionId} AND tenant_id=#{tenantId}
            FOR UPDATE
            """)
    LegacyCatalogProjectionDO selectForUpdate(@Param("projectionId") Long projectionId,
                                               @Param("tenantId") Long tenantId);

    @Update("""
            UPDATE cloudmold_catalog_legacy_projection
            SET aggregate_version=#{aggregateVersion}, payload=CAST(#{payload} AS JSON),
                payload_hash=#{payloadHash}, status=0, last_error_summary=NULL, updated_at=#{now}
            WHERE projection_id=#{projectionId} AND tenant_id=#{tenantId}
              AND aggregate_version <= #{aggregateVersion}
            """)
    int refresh(@Param("projectionId") Long projectionId,
                @Param("tenantId") Long tenantId,
                @Param("aggregateVersion") Long aggregateVersion,
                @Param("payload") String payload,
                @Param("payloadHash") String payloadHash,
                @Param("now") LocalDateTime now);

}
