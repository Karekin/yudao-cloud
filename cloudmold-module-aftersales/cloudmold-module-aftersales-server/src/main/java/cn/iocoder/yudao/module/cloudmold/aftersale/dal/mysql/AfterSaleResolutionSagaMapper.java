package cn.iocoder.yudao.module.cloudmold.aftersale.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.aftersale.dal.dataobject.AfterSaleResolutionSagaDO;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface AfterSaleResolutionSagaMapper extends BaseMapperX<AfterSaleResolutionSagaDO> {
    @Select("SELECT * FROM cloudmold_after_sale_resolution_saga WHERE tenant_id=#{tenantId} AND saga_id=#{id}")
    AfterSaleResolutionSagaDO selectTenant(@Param("tenantId") Long tenantId, @Param("id") String id);
    @Select("SELECT * FROM cloudmold_after_sale_resolution_saga WHERE tenant_id=#{tenantId} AND saga_id=#{id} FOR UPDATE")
    AfterSaleResolutionSagaDO selectForUpdate(@Param("tenantId") Long tenantId, @Param("id") String id);
    @Select("SELECT * FROM cloudmold_after_sale_resolution_saga WHERE tenant_id=#{tenantId} AND after_sale_id=#{afterSaleId}")
    AfterSaleResolutionSagaDO selectByAfterSale(@Param("tenantId") Long tenantId,
                                                @Param("afterSaleId") String afterSaleId);
    @Select("""
            SELECT * FROM cloudmold_after_sale_resolution_saga
            WHERE status IN ('REQUESTED','RETURNING_INVENTORY','INVENTORY_RETURNED','REFUNDING_PAYMENT',
              'PAYMENT_REFUNDED','CONFIRMING_ORDER_REFUND','ORDER_REFUNDED','RETURNING_ORDER',
              'ORDER_RETURNED','RETRY_SCHEDULED')
              AND (status <> 'RETRY_SCHEDULED' OR next_retry_at <= #{now})
              AND (lease_until IS NULL OR lease_until < #{now})
            ORDER BY created_at,saga_id LIMIT #{limit}
            """)
    List<AfterSaleResolutionSagaDO> selectDue(@Param("now") LocalDateTime now, @Param("limit") int limit);
    @Update("""
            UPDATE cloudmold_after_sale_resolution_saga
            SET lease_owner=#{owner},lease_until=#{until},attempt_count=attempt_count+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND saga_id=#{id}
              AND status IN ('REQUESTED','RETURNING_INVENTORY','INVENTORY_RETURNED','REFUNDING_PAYMENT',
                'PAYMENT_REFUNDED','CONFIRMING_ORDER_REFUND','ORDER_REFUNDED','RETURNING_ORDER',
                'ORDER_RETURNED','RETRY_SCHEDULED')
              AND (status <> 'RETRY_SCHEDULED' OR next_retry_at <= #{now})
              AND (lease_until IS NULL OR lease_until < #{now}) AND attempt_count < max_attempts
            """)
    int claim(@Param("tenantId") Long tenantId, @Param("id") String id, @Param("owner") String owner,
              @Param("until") LocalDateTime until, @Param("now") LocalDateTime now);
}
