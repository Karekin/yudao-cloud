package cn.iocoder.yudao.module.cloudmold.tokenplatform.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.tokenplatform.service.query.QuotaAccountPageItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface QuotaAccountPageMapper {

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM cloudmold_token_platform_quota_account c
            WHERE c.tenant_id = #{tenantId}
            <if test="accountId != null">AND c.account_id = #{accountId}</if>
            <if test="principalId != null">AND c.principal_id = #{principalId}</if>
            <if test="status != null">AND c.status = #{status}</if>
            <if test="createdAtFrom != null">AND c.created_at &gt;= #{createdAtFrom}</if>
            <if test="createdAtTo != null">AND c.created_at &lt;= #{createdAtTo}</if>
            </script>
            """)
    long countPage(@Param("tenantId") Long tenantId,
                   @Param("accountId") String accountId,
                   @Param("principalId") String principalId,
                   @Param("status") String status,
                   @Param("createdAtFrom") LocalDateTime createdAtFrom,
                   @Param("createdAtTo") LocalDateTime createdAtTo);

    @Select("""
            <script>
            SELECT c.account_id,
                   c.principal_id,
                   c.balance_microunits,
                   c.status,
                   c.version AS aggregate_version,
                   c.created_at,
                   c.updated_at
            FROM cloudmold_token_platform_quota_account c
            WHERE c.tenant_id = #{tenantId}
            <if test="accountId != null">AND c.account_id = #{accountId}</if>
            <if test="principalId != null">AND c.principal_id = #{principalId}</if>
            <if test="status != null">AND c.status = #{status}</if>
            <if test="createdAtFrom != null">AND c.created_at &gt;= #{createdAtFrom}</if>
            <if test="createdAtTo != null">AND c.created_at &lt;= #{createdAtTo}</if>
            ORDER BY c.updated_at DESC, c.account_id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<QuotaAccountPageItem> selectPage(@Param("tenantId") Long tenantId,
                                          @Param("accountId") String accountId,
                                          @Param("principalId") String principalId,
                                          @Param("status") String status,
                                          @Param("createdAtFrom") LocalDateTime createdAtFrom,
                                          @Param("createdAtTo") LocalDateTime createdAtTo,
                                          @Param("offset") long offset,
                                          @Param("limit") int limit);
}
