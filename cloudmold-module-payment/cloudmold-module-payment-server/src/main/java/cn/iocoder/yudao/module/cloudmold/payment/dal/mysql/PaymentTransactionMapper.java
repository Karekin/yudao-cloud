package cn.iocoder.yudao.module.cloudmold.payment.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.payment.dal.dataobject.PaymentTransactionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PaymentTransactionMapper extends BaseMapperX<PaymentTransactionDO> {
    @Select("""
            SELECT * FROM cloudmold_payment_transaction
            WHERE tenant_id=#{tenantId} AND payment_id=#{paymentId} AND transaction_type='REFUND'
            ORDER BY transaction_id DESC LIMIT 1
            """)
    PaymentTransactionDO selectLatestRefund(@Param("tenantId") Long tenantId,
                                            @Param("paymentId") String paymentId);
}
