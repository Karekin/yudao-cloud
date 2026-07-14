package cn.iocoder.yudao.module.cloudmold.order.dal.mysql.cancellation;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.cancellation.OrderCancellationSagaFulfillmentDO;
import org.apache.ibatis.annotations.*;

@Mapper
public interface OrderCancellationSagaFulfillmentMapper extends BaseMapperX<OrderCancellationSagaFulfillmentDO> {
    @Select("SELECT * FROM cloudmold_order_cancellation_saga_fulfillment WHERE tenant_id=#{tenantId} AND saga_id=#{sagaId}")
    OrderCancellationSagaFulfillmentDO selectBySaga(@Param("tenantId") Long tenantId,
                                                     @Param("sagaId") String sagaId);

    @Select("SELECT * FROM cloudmold_order_cancellation_saga_fulfillment WHERE tenant_id=#{tenantId} AND saga_fulfillment_id=#{id} FOR UPDATE")
    OrderCancellationSagaFulfillmentDO selectForUpdate(@Param("tenantId") Long tenantId,
                                                        @Param("id") String id);
}
