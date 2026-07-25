package cn.iocoder.yudao.module.cloudmold.fulfillment.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.fulfillment.dal.dataobject.TrackingEventDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface TrackingEventMapper extends BaseMapperX<TrackingEventDO> {
    @Select("""
            SELECT tracking_event_id,tenant_id,shipment_id,provider_event_key,tracking_status,content,
                   occurred_at,received_at,created_at
            FROM cloudmold_tracking_event
            WHERE tenant_id=#{tenantId} AND shipment_id=#{shipmentId}
            ORDER BY occurred_at ASC,tracking_event_id ASC
            """)
    List<TrackingEventDO> selectByShipment(@Param("tenantId") Long tenantId,
                                           @Param("shipmentId") String shipmentId);
}
