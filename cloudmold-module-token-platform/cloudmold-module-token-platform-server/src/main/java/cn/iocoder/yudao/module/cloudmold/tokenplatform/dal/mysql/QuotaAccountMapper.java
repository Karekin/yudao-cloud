package cn.iocoder.yudao.module.cloudmold.tokenplatform.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.tokenplatform.dal.dataobject.QuotaAccountDO;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

@Mapper
public interface QuotaAccountMapper extends BaseMapperX<QuotaAccountDO> {
    @Select("SELECT * FROM cloudmold_token_platform_quota_account WHERE tenant_id=#{tenantId} AND account_id=#{id}")
    QuotaAccountDO selectOneById(@Param("tenantId") Long tenantId, @Param("id") String id);

    @Select("SELECT * FROM cloudmold_token_platform_quota_account WHERE tenant_id=#{tenantId} AND account_id=#{id} FOR UPDATE")
    QuotaAccountDO selectForUpdate(@Param("tenantId") Long tenantId, @Param("id") String id);

    @Select("SELECT * FROM cloudmold_token_platform_quota_account WHERE tenant_id=#{tenantId} AND principal_id=#{principalId}")
    QuotaAccountDO selectByPrincipal(@Param("tenantId") Long tenantId,
                                     @Param("principalId") String principalId);

    @Update("""
        UPDATE cloudmold_token_platform_quota_account
        SET balance_microunits=#{balance},version=version+1,updated_at=#{now}
        WHERE tenant_id=#{tenantId} AND account_id=#{id} AND version=#{version}
        """)
    int updateBalanceCas(@Param("tenantId") Long tenantId, @Param("id") String id,
                         @Param("version") Long version, @Param("balance") Long balance,
                         @Param("now") LocalDateTime now);
}
