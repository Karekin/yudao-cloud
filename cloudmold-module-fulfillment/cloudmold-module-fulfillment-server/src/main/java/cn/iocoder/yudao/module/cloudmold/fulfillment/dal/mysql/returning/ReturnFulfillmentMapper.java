package cn.iocoder.yudao.module.cloudmold.fulfillment.dal.mysql.returning;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.fulfillment.dal.dataobject.returning.ReturnFulfillmentDO;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

@Mapper
public interface ReturnFulfillmentMapper extends BaseMapperX<ReturnFulfillmentDO> {
    @Select("SELECT * FROM cloudmold_return_fulfillment WHERE tenant_id=#{tenantId} AND return_fulfillment_id=#{id} FOR UPDATE")
    ReturnFulfillmentDO selectForUpdate(@Param("tenantId") Long tenantId, @Param("id") String id);

    @Select("SELECT * FROM cloudmold_return_fulfillment WHERE tenant_id=#{tenantId} AND return_fulfillment_id=#{id}")
    ReturnFulfillmentDO selectTenant(@Param("tenantId") Long tenantId, @Param("id") String id);

    @Select("SELECT * FROM cloudmold_return_fulfillment WHERE tenant_id=#{tenantId} AND after_sale_id=#{afterSaleId}")
    ReturnFulfillmentDO selectByAfterSale(@Param("tenantId") Long tenantId,
                                          @Param("afterSaleId") String afterSaleId);

    @Update("""
            UPDATE cloudmold_return_fulfillment SET status=#{nextStatus},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND return_fulfillment_id=#{id}
              AND version=#{version} AND status=#{expectedStatus}
            """)
    int transition(@Param("tenantId") Long tenantId, @Param("id") String id,
                   @Param("version") Long version, @Param("expectedStatus") String expectedStatus,
                   @Param("nextStatus") String nextStatus, @Param("now") LocalDateTime now);
}
