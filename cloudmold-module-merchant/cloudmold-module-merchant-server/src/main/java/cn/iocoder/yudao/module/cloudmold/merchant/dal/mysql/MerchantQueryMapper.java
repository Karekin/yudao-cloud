package cn.iocoder.yudao.module.cloudmold.merchant.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.merchant.service.query.MerchantPageItem;
import cn.iocoder.yudao.module.cloudmold.merchant.service.query.MerchantShopPageItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * CloudMold 规范商家只读查询 Mapper（Query 侧，与 Command 侧 {@code MerchantStoreMapper} 分离）。
 * 走纯注解 SQL + script 动态条件 + LIMIT/OFFSET 手动分页，不继承 MyBatis-Plus BaseMapper。
 * cloudmold 规范表为不可变权威表，无逻辑删除列，故不过滤 deleted。
 */
@Mapper
public interface MerchantQueryMapper {

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM cloudmold_merchant_account a
            LEFT JOIN cloudmold_merchant_legal_entity l
              ON l.tenant_id = a.tenant_id AND l.legal_entity_id = a.legal_entity_id
            WHERE a.tenant_id = #{tenantId}
            <if test="merchantCode != null">AND a.merchant_code LIKE CONCAT('%', #{merchantCode}, '%')</if>
            <if test="legalName != null">AND l.legal_name LIKE CONCAT('%', #{legalName}, '%')</if>
            <if test="status != null">AND a.status = #{status}</if>
            </script>
            """)
    long countMerchantPage(@Param("tenantId") Long tenantId,
                           @Param("merchantCode") String merchantCode,
                           @Param("legalName") String legalName,
                           @Param("status") String status);

    @Select("""
            <script>
            SELECT a.merchant_id, a.merchant_code, a.legal_entity_id, l.legal_name,
                   a.status, a.version, a.updated_at
            FROM cloudmold_merchant_account a
            LEFT JOIN cloudmold_merchant_legal_entity l
              ON l.tenant_id = a.tenant_id AND l.legal_entity_id = a.legal_entity_id
            WHERE a.tenant_id = #{tenantId}
            <if test="merchantCode != null">AND a.merchant_code LIKE CONCAT('%', #{merchantCode}, '%')</if>
            <if test="legalName != null">AND l.legal_name LIKE CONCAT('%', #{legalName}, '%')</if>
            <if test="status != null">AND a.status = #{status}</if>
            ORDER BY a.updated_at DESC, a.merchant_id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<MerchantPageItem> selectMerchantPage(@Param("tenantId") Long tenantId,
                                              @Param("merchantCode") String merchantCode,
                                              @Param("legalName") String legalName,
                                              @Param("status") String status,
                                              @Param("offset") long offset,
                                              @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM cloudmold_merchant_shop s
            WHERE s.tenant_id = #{tenantId}
            <if test="merchantId != null">AND s.merchant_id = #{merchantId}</if>
            <if test="channelCode != null">AND s.channel_code = #{channelCode}</if>
            <if test="status != null">AND s.status = #{status}</if>
            </script>
            """)
    long countShopPage(@Param("tenantId") Long tenantId,
                       @Param("merchantId") String merchantId,
                       @Param("channelCode") String channelCode,
                       @Param("status") String status);

    @Select("""
            <script>
            SELECT s.shop_id, s.merchant_id, a.merchant_code, s.channel_code,
                   s.external_shop_id, s.status, s.version, s.updated_at
            FROM cloudmold_merchant_shop s
            LEFT JOIN cloudmold_merchant_account a
              ON a.tenant_id = s.tenant_id AND a.merchant_id = s.merchant_id
            WHERE s.tenant_id = #{tenantId}
            <if test="merchantId != null">AND s.merchant_id = #{merchantId}</if>
            <if test="channelCode != null">AND s.channel_code = #{channelCode}</if>
            <if test="status != null">AND s.status = #{status}</if>
            ORDER BY s.updated_at DESC, s.shop_id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<MerchantShopPageItem> selectShopPage(@Param("tenantId") Long tenantId,
                                              @Param("merchantId") String merchantId,
                                              @Param("channelCode") String channelCode,
                                              @Param("status") String status,
                                              @Param("offset") long offset,
                                              @Param("limit") int limit);
}
