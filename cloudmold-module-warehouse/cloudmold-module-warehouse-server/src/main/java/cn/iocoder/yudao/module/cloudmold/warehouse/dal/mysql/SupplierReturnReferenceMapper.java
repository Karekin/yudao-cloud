package cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql;

import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
public interface SupplierReturnReferenceMapper {

    @Select("""
            SELECT r.receipt_id,r.tenant_id,r.procurement_order_id,r.supplier_id,r.warehouse_id,r.status,r.version,
                   l.receipt_line_id,l.procurement_order_item_id,l.delivery_schedule_id,l.canonical_sku_id,
                   l.owner_type,l.owner_id,l.base_uom_code,l.valuation_policy_id,l.valuation_policy_version,
                   l.valuation_policy_hash,l.unit_cost_amount_minor,l.currency_code,l.receipt_location_id,l.lot_id
            FROM cloudmold_warehouse_procurement_receipt r
            JOIN cloudmold_warehouse_procurement_receipt_line l
              ON l.tenant_id=r.tenant_id AND l.receipt_id=r.receipt_id
            WHERE r.tenant_id=#{tenantId} AND l.receipt_line_id=#{receiptLineId}
            """)
    ReceiptLineReference selectReceiptLineReference(@Param("tenantId") Long tenantId,
                                                    @Param("receiptLineId") String receiptLineId);

    @Select("""
            SELECT rs.quality_decision_id,rs.decision_version,rs.inspection_split_id,rs.evidence_ref,
                   il.receipt_line_id,il.purchase_order_id,il.item_id purchase_order_item_id,
                   il.schedule_id purchase_order_schedule_id,il.canonical_sku_id,il.uom_code,il.supplier_id,
                   il.owner_type,il.owner_id,il.valuation_policy valuation_policy_id,
                   il.valuation_policy_version,il.valuation_policy_hash,il.unit_cost_amount_minor,il.currency_code,
                   split.warehouse_id,split.location_id,split.lot_id,
                   rs.accepted_quantity,rs.rejected_quantity,rs.quarantined_quantity
            FROM cloudmold_procurement_receipt_inspection_result_split rs
            JOIN cloudmold_procurement_receipt_inspection_split split
              ON split.tenant_id=rs.tenant_id AND split.inspection_split_id=rs.inspection_split_id
             AND split.inspection_id=rs.inspection_id AND split.inspection_line_id=rs.inspection_line_id
            JOIN cloudmold_procurement_receipt_inspection_line il
              ON il.tenant_id=rs.tenant_id AND il.inspection_id=rs.inspection_id
             AND il.inspection_line_id=rs.inspection_line_id
            WHERE rs.tenant_id=#{tenantId}
              AND rs.quality_decision_id=#{qualityDecisionId}
              AND rs.decision_version=#{decisionVersion}
            """)
    QualityDecisionReference selectQualityDecisionReference(@Param("tenantId") Long tenantId,
                                                            @Param("qualityDecisionId") String qualityDecisionId,
                                                            @Param("decisionVersion") Long decisionVersion);

    @Data
    @Accessors(chain = true)
    class ReceiptLineReference {
        private String receiptId;
        private Long tenantId;
        private String procurementOrderId;
        private String supplierId;
        private String warehouseId;
        private String status;
        private Long version;
        private String receiptLineId;
        private String procurementOrderItemId;
        private String deliveryScheduleId;
        private String canonicalSkuId;
        private String ownerType;
        private String ownerId;
        private String baseUomCode;
        private String valuationPolicyId;
        private String valuationPolicyVersion;
        private String valuationPolicyHash;
        private Long unitCostAmountMinor;
        private String currencyCode;
        private String receiptLocationId;
        private String lotId;
    }

    @Data
    @Accessors(chain = true)
    class QualityDecisionReference {
        private String qualityDecisionId;
        private Long decisionVersion;
        private String inspectionSplitId;
        private String evidenceRef;
        private String receiptLineId;
        private String purchaseOrderId;
        private String purchaseOrderItemId;
        private String purchaseOrderScheduleId;
        private String canonicalSkuId;
        private String uomCode;
        private String supplierId;
        private String ownerType;
        private String ownerId;
        private String valuationPolicyId;
        private String valuationPolicyVersion;
        private String valuationPolicyHash;
        private Long unitCostAmountMinor;
        private String currencyCode;
        private String warehouseId;
        private String locationId;
        private String lotId;
        private BigDecimal acceptedQuantity;
        private BigDecimal rejectedQuantity;
        private BigDecimal quarantinedQuantity;
    }
}
