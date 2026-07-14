package cn.iocoder.yudao.module.cloudmold.fulfillment.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.fulfillment.dal.dataobject.ShipmentDO;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

@Mapper
public interface ShipmentMapper extends BaseMapperX<ShipmentDO> {
    String COLUMNS = "shipment_id,tenant_id,fulfillment_id,carrier_code,waybill_no,status,shipped_at,in_transit_at,delivered_at,created_at,updated_at";

    @Select("SELECT " + COLUMNS + " FROM cloudmold_shipment WHERE tenant_id=#{tenantId} AND fulfillment_id=#{fulfillmentId}")
    ShipmentDO selectByFulfillment(@Param("tenantId") Long tenantId,
                                   @Param("fulfillmentId") String fulfillmentId);

    @Update("""
            UPDATE cloudmold_shipment
            SET status=#{nextStatus},in_transit_at=COALESCE(#{inTransitAt},in_transit_at),
                delivered_at=COALESCE(#{deliveredAt},delivered_at),updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND shipment_id=#{shipmentId} AND status=#{expectedStatus}
            """)
    int transition(@Param("tenantId") Long tenantId, @Param("shipmentId") String shipmentId,
                   @Param("expectedStatus") String expectedStatus, @Param("nextStatus") String nextStatus,
                   @Param("inTransitAt") LocalDateTime inTransitAt, @Param("deliveredAt") LocalDateTime deliveredAt,
                   @Param("now") LocalDateTime now);
}
