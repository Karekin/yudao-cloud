package cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("cloudmold_warehouse_procurement_quality_effect")
public class ProcurementQualityEffectDO {
    private String effectId;
    private Long tenantId;
    private Long operationId;
    private String sourceEventId;
    private String qualityDecisionId;
    private Long decisionVersion;
    private String inspectionSplitId;
    private String disposition;
    private String receiptId;
    private String receiptLineId;
    private String procurementOrderId;
    private String procurementOrderItemId;
    private String deliveryScheduleId;
    private BigDecimal dispositionQuantity;
    private BigDecimal pendingQuantityBefore;
    private BigDecimal pendingQuantityAfter;
    private BigDecimal acceptedQuantityBefore;
    private BigDecimal acceptedQuantityAfter;
    private BigDecimal rejectedQuantityBefore;
    private BigDecimal rejectedQuantityAfter;
    private BigDecimal quarantinedQuantityBefore;
    private BigDecimal quarantinedQuantityAfter;
    private Long receiptVersionBefore;
    private Long receiptVersionAfter;
    private Long receiptLineVersionBefore;
    private Long receiptLineVersionAfter;
    private Long scheduleFulfillmentVersionBefore;
    private Long scheduleFulfillmentVersionAfter;
    private String evidenceRef;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
}
