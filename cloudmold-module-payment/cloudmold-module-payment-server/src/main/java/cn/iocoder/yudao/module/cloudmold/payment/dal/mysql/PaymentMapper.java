package cn.iocoder.yudao.module.cloudmold.payment.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.payment.dal.dataobject.PaymentDO;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

@Mapper
public interface PaymentMapper extends BaseMapperX<PaymentDO> {
    @Select("""
            SELECT payment_id,tenant_id,payment_no,run_id,order_id,status,payable_amount_minor,captured_amount_minor,
                   refunded_amount_minor,currency_code,provider_code,provider_transaction_id,test_mode,version,
                   captured_at,refunded_at,created_at,updated_at
            FROM cloudmold_payment WHERE tenant_id=#{tenantId} AND payment_id=#{paymentId} FOR UPDATE
            """)
    PaymentDO selectForUpdate(@Param("tenantId") Long tenantId, @Param("paymentId") String paymentId);

    @Update("""
            UPDATE cloudmold_payment
            SET status=#{nextStatus},refunded_amount_minor=refunded_amount_minor+#{amountMinor},version=version+1,
                refunded_at=#{now},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND payment_id=#{paymentId}
              AND version=#{expectedVersion} AND status IN ('CAPTURED','PARTIALLY_REFUNDED')
              AND refunded_amount_minor+#{amountMinor} <= captured_amount_minor
            """)
    int refund(@Param("tenantId") Long tenantId, @Param("paymentId") String paymentId,
               @Param("expectedVersion") Long expectedVersion, @Param("amountMinor") Long amountMinor,
               @Param("nextStatus") String nextStatus,
               @Param("now") LocalDateTime now);
}
