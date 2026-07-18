package cn.iocoder.yudao.module.cloudmold.commercebehavior.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.commercebehavior.dal.dataobject.CommerceBehaviorEventDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CommerceBehaviorEventMapper extends BaseMapperX<CommerceBehaviorEventDO> {

    @Select("""
            SELECT behavior_id,tenant_id,session_id,session_version,behavior_type,principal_id,canonical_spu_id,sku_id,
                   listing_id,listing_offer_id,merchant_id,shop_id,channel_code,
                   search_token,result_set_token,result_position,quantity,checkout_token,source_system,source_type,
                   source_id,occurred_at,created_at
            FROM cloudmold_commerce_behavior_event
            WHERE tenant_id=#{tenantId} AND session_id=#{sessionId}
              AND behavior_type='CHECKOUT_STARTED' AND checkout_token=#{checkoutToken}
            ORDER BY occurred_at DESC, created_at DESC, behavior_id DESC
            LIMIT 1
            """)
    CommerceBehaviorEventDO selectLatestCheckoutStarted(@Param("tenantId") Long tenantId,
                                                        @Param("sessionId") String sessionId,
                                                        @Param("checkoutToken") String checkoutToken);

    @Select("""
            SELECT COUNT(*)
            FROM cloudmold_commerce_behavior_event
            WHERE tenant_id=#{tenantId} AND session_id=#{sessionId}
              AND behavior_type='CHECKOUT_ABANDONED' AND checkout_token=#{checkoutToken}
            """)
    int countCheckoutAbandoned(@Param("tenantId") Long tenantId,
                               @Param("sessionId") String sessionId,
                               @Param("checkoutToken") String checkoutToken);
}
