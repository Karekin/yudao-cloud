package cn.iocoder.yudao.module.cloudmold.fulfillment.dal.mysql.returning;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.fulfillment.dal.dataobject.returning.ReturnShipmentDO;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

@Mapper
public interface ReturnShipmentMapper extends BaseMapperX<ReturnShipmentDO> {
    @Select("SELECT * FROM cloudmold_return_shipment WHERE tenant_id=#{tenantId} AND return_fulfillment_id=#{id}")
    ReturnShipmentDO selectByFulfillment(@Param("tenantId") Long tenantId, @Param("id") String id);

    @Select("SELECT * FROM cloudmold_return_shipment WHERE tenant_id=#{tenantId} AND carrier_code=#{carrierCode} AND waybill_no=#{waybillNo}")
    ReturnShipmentDO selectByWaybill(@Param("tenantId") Long tenantId,
                                     @Param("carrierCode") String carrierCode,
                                     @Param("waybillNo") String waybillNo);

    @Update("""
            UPDATE cloudmold_return_shipment SET status=#{nextStatus},
              in_transit_at=COALESCE(#{inTransitAt},in_transit_at),
              received_at=COALESCE(#{receivedAt},received_at),
              receiver_id=COALESCE(#{receiverId},receiver_id),updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND return_shipment_id=#{id} AND status=#{expectedStatus}
            """)
    int transition(@Param("tenantId") Long tenantId, @Param("id") String id,
                   @Param("expectedStatus") String expectedStatus, @Param("nextStatus") String nextStatus,
                   @Param("inTransitAt") LocalDateTime inTransitAt,
                   @Param("receivedAt") LocalDateTime receivedAt, @Param("receiverId") String receiverId,
                   @Param("now") LocalDateTime now);
}
