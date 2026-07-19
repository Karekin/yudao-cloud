package cn.iocoder.yudao.module.cloudmold.payment.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.payment.service.query.PaymentPageRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PaymentQueryMapper {

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM cloudmold_payment p
            WHERE p.tenant_id = #{tenantId}
            <if test="paymentNo != null">AND p.payment_no LIKE CONCAT('%', #{paymentNo}, '%')</if>
            <if test="orderId != null">AND p.order_id = #{orderId}</if>
            <if test="status != null">AND p.status = #{status}</if>
            <if test="providerCode != null">AND p.provider_code = #{providerCode}</if>
            <if test="testMode != null">AND p.test_mode = #{testMode}</if>
            </script>
            """)
    long countPaymentPage(@Param("tenantId") Long tenantId,
                          @Param("paymentNo") String paymentNo,
                          @Param("orderId") String orderId,
                          @Param("status") String status,
                          @Param("providerCode") String providerCode,
                          @Param("testMode") Boolean testMode);

    @Select("""
            <script>
            SELECT p.payment_id,
                   p.payment_no,
                   p.order_id,
                   p.status,
                   p.payable_amount_minor,
                   p.captured_amount_minor,
                   p.refunded_amount_minor,
                   p.currency_code,
                   p.provider_code,
                   COALESCE(tx.provider_transaction_id, p.provider_transaction_id) AS provider_transaction_reference,
                   p.test_mode,
                   p.version AS aggregate_version,
                   p.captured_at,
                   p.refunded_at,
                   p.created_at,
                   p.updated_at
            FROM cloudmold_payment p
            LEFT JOIN (
                SELECT t.tenant_id,
                       t.payment_id,
                       t.provider_transaction_id
                FROM cloudmold_payment_transaction t
                JOIN (
                    SELECT tenant_id,
                           payment_id,
                           MAX(transaction_id) AS latest_transaction_id
                    FROM cloudmold_payment_transaction
                    WHERE tenant_id = #{tenantId}
                    GROUP BY tenant_id, payment_id
                ) latest
                  ON latest.tenant_id = t.tenant_id
                 AND latest.payment_id = t.payment_id
                 AND latest.latest_transaction_id = t.transaction_id
            ) tx
              ON tx.tenant_id = p.tenant_id
             AND tx.payment_id = p.payment_id
            WHERE p.tenant_id = #{tenantId}
            <if test="paymentNo != null">AND p.payment_no LIKE CONCAT('%', #{paymentNo}, '%')</if>
            <if test="orderId != null">AND p.order_id = #{orderId}</if>
            <if test="status != null">AND p.status = #{status}</if>
            <if test="providerCode != null">AND p.provider_code = #{providerCode}</if>
            <if test="testMode != null">AND p.test_mode = #{testMode}</if>
            ORDER BY p.updated_at DESC, p.payment_id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<PaymentPageRow> selectPaymentPage(@Param("tenantId") Long tenantId,
                                           @Param("paymentNo") String paymentNo,
                                           @Param("orderId") String orderId,
                                           @Param("status") String status,
                                           @Param("providerCode") String providerCode,
                                           @Param("testMode") Boolean testMode,
                                           @Param("offset") long offset,
                                           @Param("limit") int limit);

    @Select("""
            <script>
            SELECT p.payment_id,
                   p.payment_no,
                   p.order_id,
                   p.status,
                   p.payable_amount_minor,
                   p.captured_amount_minor,
                   p.refunded_amount_minor,
                   p.currency_code,
                   p.provider_code,
                   COALESCE(tx.provider_transaction_id, p.provider_transaction_id) AS provider_transaction_reference,
                   p.test_mode,
                   p.version AS aggregate_version,
                   p.captured_at,
                   p.refunded_at,
                   p.created_at,
                   p.updated_at
            FROM cloudmold_payment p
            LEFT JOIN (
                SELECT t.tenant_id,
                       t.payment_id,
                       t.provider_transaction_id
                FROM cloudmold_payment_transaction t
                JOIN (
                    SELECT tenant_id,
                           payment_id,
                           MAX(transaction_id) AS latest_transaction_id
                    FROM cloudmold_payment_transaction
                    WHERE tenant_id = #{tenantId}
                    GROUP BY tenant_id, payment_id
                ) latest
                  ON latest.tenant_id = t.tenant_id
                 AND latest.payment_id = t.payment_id
                 AND latest.latest_transaction_id = t.transaction_id
            ) tx
              ON tx.tenant_id = p.tenant_id
             AND tx.payment_id = p.payment_id
            WHERE p.tenant_id = #{tenantId}
              AND p.payment_id = #{paymentId}
            </script>
            """)
    PaymentPageRow selectPaymentDetail(@Param("tenantId") Long tenantId,
                                       @Param("paymentId") String paymentId);
}
