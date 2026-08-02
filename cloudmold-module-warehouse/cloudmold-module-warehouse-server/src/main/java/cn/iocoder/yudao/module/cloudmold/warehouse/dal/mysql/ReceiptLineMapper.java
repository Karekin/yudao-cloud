package cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.ReceiptLineDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ReceiptLineMapper extends BaseMapperX<ReceiptLineDO> {
    @Select("SELECT * FROM cloudmold_warehouse_procurement_receipt_line WHERE tenant_id=#{tenantId} AND receipt_id=#{receiptId} ORDER BY line_no")
    List<ReceiptLineDO> selectByReceipt(@Param("tenantId") Long tenantId, @Param("receiptId") String receiptId);

    @Select("SELECT * FROM cloudmold_warehouse_procurement_receipt_line WHERE tenant_id=#{tenantId} AND receipt_line_id=#{receiptLineId} FOR UPDATE")
    ReceiptLineDO selectForUpdate(@Param("tenantId") Long tenantId, @Param("receiptLineId") String receiptLineId);

    @Update("""
            UPDATE cloudmold_warehouse_procurement_receipt_line
            SET pending_quality_quantity=#{pendingQuantity},accepted_quantity=#{acceptedQuantity},
                rejected_quantity=#{rejectedQuantity},quarantined_quantity=#{quarantinedQuantity},
                quality_status=#{qualityStatus},quality_inspection_id=#{qualityInspectionId},
                version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND receipt_line_id=#{receiptLineId} AND version=#{version}
            """)
    int updateQualityCas(@Param("tenantId") Long tenantId,
                         @Param("receiptLineId") String receiptLineId,
                         @Param("version") Long version,
                         @Param("pendingQuantity") BigDecimal pendingQuantity,
                         @Param("acceptedQuantity") BigDecimal acceptedQuantity,
                         @Param("rejectedQuantity") BigDecimal rejectedQuantity,
                         @Param("quarantinedQuantity") BigDecimal quarantinedQuantity,
                         @Param("qualityStatus") String qualityStatus,
                         @Param("qualityInspectionId") String qualityInspectionId,
                         @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_warehouse_procurement_receipt_line
            SET cumulative_putaway_quantity=#{cumulativePutawayQuantity},quality_status=#{qualityStatus},
                version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND receipt_line_id=#{receiptLineId} AND version=#{version}
              AND cumulative_putaway_quantity=#{currentCumulativePutawayQuantity}
            """)
    int updatePutawayCas(@Param("tenantId") Long tenantId,
                         @Param("receiptLineId") String receiptLineId,
                         @Param("version") Long version,
                         @Param("currentCumulativePutawayQuantity") BigDecimal currentCumulativePutawayQuantity,
                         @Param("cumulativePutawayQuantity") BigDecimal cumulativePutawayQuantity,
                         @Param("qualityStatus") String qualityStatus,
                         @Param("now") LocalDateTime now);

    @Update("UPDATE cloudmold_warehouse_procurement_receipt_line "
            + "SET finance_receipt_evidence_operation_id=#{financeOperationId},"
            + "finance_receipt_evidence_id=#{financeEvidenceId},"
            + "finance_receipt_evidence_version=#{financeEvidenceVersion},updated_at=#{now} "
            + "WHERE tenant_id=#{tenantId} AND receipt_line_id=#{receiptLineId} AND version=1 "
            + "AND finance_receipt_evidence_operation_id IS NULL AND finance_receipt_evidence_id IS NULL "
            + "AND finance_receipt_evidence_version IS NULL")
    int bindFinanceEvidence(@Param("tenantId") Long tenantId,
                            @Param("receiptLineId") String receiptLineId,
                            @Param("financeOperationId") Long financeOperationId,
                            @Param("financeEvidenceId") String financeEvidenceId,
                            @Param("financeEvidenceVersion") Long financeEvidenceVersion,
                            @Param("now") LocalDateTime now);
}
