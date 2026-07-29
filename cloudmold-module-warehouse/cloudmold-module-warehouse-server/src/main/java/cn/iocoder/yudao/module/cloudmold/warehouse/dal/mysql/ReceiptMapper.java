package cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.ReceiptDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface ReceiptMapper extends BaseMapperX<ReceiptDO> {
    @Select("SELECT * FROM cloudmold_receipt WHERE tenant_id=#{tenantId} AND receipt_id=#{receiptId} FOR UPDATE")
    ReceiptDO selectForUpdate(@Param("tenantId") Long tenantId, @Param("receiptId") String receiptId);

    @Select("SELECT * FROM cloudmold_receipt WHERE tenant_id=#{tenantId} AND asn_id=#{asnId}")
    ReceiptDO selectByAsn(@Param("tenantId") Long tenantId, @Param("asnId") String asnId);

    @Update("UPDATE cloudmold_receipt SET status=#{status},version=version+1,updated_at=#{now} "
            + "WHERE tenant_id=#{tenantId} AND receipt_id=#{receiptId} AND version=#{version}")
    int updateStatusCas(@Param("tenantId") Long tenantId, @Param("receiptId") String receiptId,
                        @Param("version") Long version, @Param("status") String status,
                        @Param("now") LocalDateTime now);
}
