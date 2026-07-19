package cn.iocoder.yudao.module.cloudmold.gamification.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.gamification.service.query.GamificationAccountPageItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface GamificationAccountPageMapper {

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM cloudmold_gamification_currency_account c
            WHERE c.tenant_id = #{tenantId}
            <if test="accountId != null">AND c.account_id = #{accountId}</if>
            <if test="gameId != null">AND c.game_id = #{gameId}</if>
            <if test="ownerType != null">AND c.owner_type = #{ownerType}</if>
            <if test="ownerRef != null">AND c.owner_ref LIKE CONCAT('%', #{ownerRef}, '%')</if>
            <if test="currencyCode != null">AND c.currency_code = #{currencyCode}</if>
            <if test="status != null">AND c.status = #{status}</if>
            <if test="createdAtFrom != null">AND c.created_at &gt;= #{createdAtFrom}</if>
            <if test="createdAtTo != null">AND c.created_at &lt;= #{createdAtTo}</if>
            </script>
            """)
    long countPage(@Param("tenantId") Long tenantId,
                   @Param("accountId") String accountId,
                   @Param("gameId") String gameId,
                   @Param("ownerType") String ownerType,
                   @Param("ownerRef") String ownerRef,
                   @Param("currencyCode") String currencyCode,
                   @Param("status") String status,
                   @Param("createdAtFrom") LocalDateTime createdAtFrom,
                   @Param("createdAtTo") LocalDateTime createdAtTo);

    @Select("""
            <script>
            SELECT c.account_id,
                   c.game_id,
                   c.owner_type,
                   c.owner_ref,
                   c.asset_class,
                   c.currency_code,
                   c.balance_microunits,
                   c.status,
                   c.version AS aggregate_version,
                   c.created_at,
                   c.updated_at
            FROM cloudmold_gamification_currency_account c
            WHERE c.tenant_id = #{tenantId}
            <if test="accountId != null">AND c.account_id = #{accountId}</if>
            <if test="gameId != null">AND c.game_id = #{gameId}</if>
            <if test="ownerType != null">AND c.owner_type = #{ownerType}</if>
            <if test="ownerRef != null">AND c.owner_ref LIKE CONCAT('%', #{ownerRef}, '%')</if>
            <if test="currencyCode != null">AND c.currency_code = #{currencyCode}</if>
            <if test="status != null">AND c.status = #{status}</if>
            <if test="createdAtFrom != null">AND c.created_at &gt;= #{createdAtFrom}</if>
            <if test="createdAtTo != null">AND c.created_at &lt;= #{createdAtTo}</if>
            ORDER BY c.updated_at DESC, c.account_id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<GamificationAccountPageItem> selectPage(@Param("tenantId") Long tenantId,
                                                 @Param("accountId") String accountId,
                                                 @Param("gameId") String gameId,
                                                 @Param("ownerType") String ownerType,
                                                 @Param("ownerRef") String ownerRef,
                                                 @Param("currencyCode") String currencyCode,
                                                 @Param("status") String status,
                                                 @Param("createdAtFrom") LocalDateTime createdAtFrom,
                                                 @Param("createdAtTo") LocalDateTime createdAtTo,
                                                 @Param("offset") long offset,
                                                 @Param("limit") int limit);
}
