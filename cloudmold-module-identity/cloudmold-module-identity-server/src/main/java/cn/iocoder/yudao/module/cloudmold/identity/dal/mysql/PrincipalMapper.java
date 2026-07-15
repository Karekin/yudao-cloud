package cn.iocoder.yudao.module.cloudmold.identity.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.identity.dal.dataobject.PrincipalDO;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

@Mapper
public interface PrincipalMapper extends BaseMapperX<PrincipalDO> {

    @Select("""
            SELECT principal_id,tenant_id,principal_type,status,version,created_at,updated_at
            FROM cloudmold_identity_principal
            WHERE tenant_id=#{tenantId} AND principal_id=#{principalId}
            """)
    PrincipalDO selectByTenantAndId(@Param("tenantId") Long tenantId, @Param("principalId") String principalId);

    @Select("""
            SELECT principal_id,tenant_id,principal_type,status,version,created_at,updated_at
            FROM cloudmold_identity_principal
            WHERE tenant_id=#{tenantId} AND principal_id=#{principalId}
            FOR UPDATE
            """)
    PrincipalDO selectForUpdate(@Param("tenantId") Long tenantId, @Param("principalId") String principalId);

    @Update("""
            UPDATE cloudmold_identity_principal
            SET version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND principal_id=#{principalId}
              AND version=#{expectedVersion} AND status='ACTIVE'
            """)
    int advanceVersion(@Param("tenantId") Long tenantId, @Param("principalId") String principalId,
                       @Param("expectedVersion") Long expectedVersion, @Param("now") LocalDateTime now);
}
