package cn.iocoder.yudao.module.cloudmold.identity.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.identity.dal.dataobject.SourceIdentityDO;
import org.apache.ibatis.annotations.*;

@Mapper
public interface SourceIdentityMapper extends BaseMapperX<SourceIdentityDO> {

    @Select("""
            SELECT source_identity_id,tenant_id,principal_id,source_system,source_type,source_id,status,version,
                   valid_from,valid_to,created_at,updated_at
            FROM cloudmold_identity_source_identity
            WHERE tenant_id=#{tenantId} AND source_system=#{sourceSystem}
              AND source_type=#{sourceType} AND source_id=#{sourceId} AND status='ACTIVE'
            """)
    SourceIdentityDO selectActiveBySource(@Param("tenantId") Long tenantId,
                                          @Param("sourceSystem") String sourceSystem,
                                          @Param("sourceType") String sourceType,
                                          @Param("sourceId") String sourceId);

    @Select("""
            SELECT COUNT(*) FROM cloudmold_identity_source_identity
            WHERE tenant_id=#{tenantId} AND source_system=#{sourceSystem}
              AND source_type=#{sourceType} AND source_id=#{sourceId}
            """)
    long countBySource(@Param("tenantId") Long tenantId,
                       @Param("sourceSystem") String sourceSystem,
                       @Param("sourceType") String sourceType,
                       @Param("sourceId") String sourceId);
}
