package cn.iocoder.yudao.module.cloudmold.engagement.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.engagement.dal.dataobject.NotificationDeliveryAttemptDO;
import cn.iocoder.yudao.module.cloudmold.engagement.dal.dataobject.NotificationDeliveryDO;
import cn.iocoder.yudao.module.cloudmold.engagement.dal.dataobject.NotificationDeliveryReceiptDO;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

@Mapper
public interface NotificationDeliveryMapper extends BaseMapperX<NotificationDeliveryDO> {

    @Select("""
            SELECT delivery_id,tenant_id,delivery_key,campaign_id,principal_id,channel,destination_token,
                   status,attempt_count,receipt_count,version,occurred_at,created_at,updated_at
            FROM cloudmold_engagement_notification_delivery
            WHERE tenant_id=#{tenantId} AND delivery_id=#{deliveryId}
            """)
    NotificationDeliveryDO selectByTenantAndId(@Param("tenantId") Long tenantId,
                                               @Param("deliveryId") String deliveryId);

    @Select("""
            SELECT delivery_id,tenant_id,delivery_key,campaign_id,principal_id,channel,destination_token,
                   status,attempt_count,receipt_count,version,occurred_at,created_at,updated_at
            FROM cloudmold_engagement_notification_delivery
            WHERE tenant_id=#{tenantId} AND delivery_id=#{deliveryId}
            FOR UPDATE
            """)
    NotificationDeliveryDO selectForUpdate(@Param("tenantId") Long tenantId,
                                           @Param("deliveryId") String deliveryId);

    @Select("""
            SELECT delivery_id,tenant_id,delivery_key,campaign_id,principal_id,channel,destination_token,
                   status,attempt_count,receipt_count,version,occurred_at,created_at,updated_at
            FROM cloudmold_engagement_notification_delivery
            WHERE tenant_id=#{tenantId} AND delivery_key=#{deliveryKey}
            """)
    NotificationDeliveryDO selectByBusinessKey(@Param("tenantId") Long tenantId,
                                               @Param("deliveryKey") String deliveryKey);

    @Insert("""
            INSERT INTO cloudmold_engagement_notification_attempt
              (attempt_id,tenant_id,delivery_id,attempt_no,provider_code,provider_reference,outcome,
               occurred_at,created_at)
            VALUES (#{attemptId},#{tenantId},#{deliveryId},#{attemptNo},#{providerCode},#{providerReference},
                    #{outcome},#{occurredAt},#{createdAt})
            """)
    int insertAttempt(NotificationDeliveryAttemptDO row);

    @Insert("""
            INSERT INTO cloudmold_engagement_notification_receipt
              (receipt_id,tenant_id,delivery_id,external_receipt_id,receipt_status,occurred_at,created_at)
            VALUES (#{receiptId},#{tenantId},#{deliveryId},#{externalReceiptId},#{receiptStatus},
                    #{occurredAt},#{createdAt})
            """)
    int insertReceipt(NotificationDeliveryReceiptDO row);

    @Update("""
            UPDATE cloudmold_engagement_notification_delivery
            SET status=#{status},attempt_count=attempt_count+1,version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND delivery_id=#{deliveryId} AND version=#{expectedVersion}
            """)
    int advanceAttempt(@Param("tenantId") Long tenantId, @Param("deliveryId") String deliveryId,
                       @Param("expectedVersion") Long expectedVersion, @Param("status") String status,
                       @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_engagement_notification_delivery
            SET status=#{status},receipt_count=receipt_count+1,version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND delivery_id=#{deliveryId} AND version=#{expectedVersion}
            """)
    int advanceReceipt(@Param("tenantId") Long tenantId, @Param("deliveryId") String deliveryId,
                       @Param("expectedVersion") Long expectedVersion, @Param("status") String status,
                       @Param("now") LocalDateTime now);
}
