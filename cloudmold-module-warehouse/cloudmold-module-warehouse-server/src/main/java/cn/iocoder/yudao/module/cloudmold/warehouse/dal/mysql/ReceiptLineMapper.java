package cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.ReceiptLineDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ReceiptLineMapper extends BaseMapperX<ReceiptLineDO> {
    @Select("SELECT * FROM cloudmold_receipt_line WHERE tenant_id=#{tenantId} AND receipt_id=#{receiptId}")
    List<ReceiptLineDO> selectByReceipt(@Param("tenantId") Long tenantId, @Param("receiptId") String receiptId);

    /** 收货行落库后回写库存 operation/ledger/balance 引用，便于幂等与对账 */
    @Update("UPDATE cloudmold_receipt_line SET inventory_idempotency_key=#{invKey},"
            + "inventory_operation_id=#{invOpId},inventory_ledger_tx_id=#{invTxId},inventory_balance_id=#{invBalanceId} "
            + "WHERE tenant_id=#{tenantId} AND receipt_line_id=#{receiptLineId}")
    int updateInventoryWriteback(@Param("tenantId") Long tenantId, @Param("receiptLineId") String receiptLineId,
                                 @Param("invKey") String invKey, @Param("invOpId") Long invOpId,
                                 @Param("invTxId") Long invTxId, @Param("invBalanceId") String invBalanceId);
}
