package cn.iocoder.yudao.module.cloudmold.tokenplatform.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.tokenplatform.dal.dataobject.AccessCredentialDO;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

@Mapper
public interface AccessCredentialMapper extends BaseMapperX<AccessCredentialDO> {
    @Select("SELECT * FROM cloudmold_token_platform_access_credential WHERE tenant_id=#{tenantId} AND credential_id=#{id}")
    AccessCredentialDO selectOneById(@Param("tenantId") Long tenantId, @Param("id") String id);

    @Select("SELECT * FROM cloudmold_token_platform_access_credential WHERE tenant_id=#{tenantId} AND credential_id=#{id} FOR UPDATE")
    AccessCredentialDO selectForUpdate(@Param("tenantId") Long tenantId, @Param("id") String id);

    @Update("""
        UPDATE cloudmold_token_platform_access_credential
        SET status=#{status},version=version+1,updated_at=#{now}
        WHERE tenant_id=#{tenantId} AND credential_id=#{id} AND version=#{version}
        """)
    int updateStatusCas(@Param("tenantId") Long tenantId, @Param("id") String id,
                        @Param("version") Long version, @Param("status") String status,
                        @Param("now") LocalDateTime now);
}
