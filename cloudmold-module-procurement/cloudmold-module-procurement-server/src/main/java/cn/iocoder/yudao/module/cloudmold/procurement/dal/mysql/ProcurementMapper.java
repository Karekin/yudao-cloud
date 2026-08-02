package cn.iocoder.yudao.module.cloudmold.procurement.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.Operation;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.OrderStatusHistory;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.ProcurementOrder;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.PurchaseOrderDeliverySchedule;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.PurchaseOrderAwardSource;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.PurchaseOrderItem;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.PurchaseRequisition;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.PurchaseRequisitionDeliverySchedule;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.PurchaseRequisitionLine;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.PurchaseRequisitionStatusHistory;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementAdminRecords.RequisitionSummary;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementAdminRecords.OrderSummary;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ProcurementMapper {
    @Insert("""
            INSERT INTO cloudmold_procurement_operation
              (tenant_id,idempotency_key,command_type,request_hash,attempt_token,status,created_at,updated_at)
            VALUES (#{tenantId},#{idempotencyKey},#{commandType},#{requestHash},#{attemptToken},0,#{now},#{now})
            ON DUPLICATE KEY UPDATE operation_id=LAST_INSERT_ID(operation_id)
            """)
    int insertOrResolveOperation(@Param("tenantId") Long tenantId,
                                 @Param("idempotencyKey") String idempotencyKey,
                                 @Param("commandType") String commandType,
                                 @Param("requestHash") String requestHash,
                                 @Param("attemptToken") String attemptToken,
                                 @Param("now") LocalDateTime now);

    @Select("SELECT LAST_INSERT_ID()")
    Long selectLastInsertId();

    @Select("""
            SELECT operation_id,tenant_id,idempotency_key,command_type,request_hash,attempt_token,status,
                   aggregate_type,aggregate_id,result_json,created_at,updated_at
            FROM cloudmold_procurement_operation
            WHERE tenant_id=#{tenantId} AND operation_id=#{operationId} FOR UPDATE
            """)
    Operation selectOperationForUpdate(@Param("operationId") Long operationId,
                                       @Param("tenantId") Long tenantId);

    @Update("""
            UPDATE cloudmold_procurement_operation
            SET status=10,aggregate_type=#{aggregateType},aggregate_id=#{aggregateId},
                result_json=#{resultJson},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND operation_id=#{operationId} AND status=0
            """)
    int markOperationSucceeded(@Param("operationId") Long operationId,
                               @Param("tenantId") Long tenantId,
                               @Param("aggregateType") String aggregateType,
                               @Param("aggregateId") String aggregateId,
                               @Param("resultJson") String resultJson,
                               @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_procurement_order
              (order_id,tenant_id,order_code,source_business_type,source_business_ref,award_id,award_version,legal_entity_id,supplier_id,
               currency_code,lead_time_days,header_net_amount_minor,header_tax_amount_minor,
               header_gross_amount_minor,tax_calculation_policy_code,rounding_policy_code,status,
               created_by_principal_id,reason_code,remark,version,created_at,updated_at)
            VALUES (#{orderId},#{tenantId},#{orderCode},#{sourceBusinessType},#{sourceBusinessRef},#{awardId},#{awardVersion},#{legalEntityId},#{supplierId},
                    #{currencyCode},#{leadTimeDays},#{headerNetAmountMinor},#{headerTaxAmountMinor},
                    #{headerGrossAmountMinor},#{taxCalculationPolicyCode},#{roundingPolicyCode},#{status},
                    #{createdByPrincipalId},#{reasonCode},#{remark},#{version},#{createdAt},#{updatedAt})
            """)
    int insertOrder(ProcurementOrder value);

    @Insert("""
            <script>
            INSERT INTO cloudmold_procurement_order_item
              (item_id,tenant_id,order_id,line_number,award_line_id,canonical_sku_id,ordered_quantity,uom_code,tax_code,
               tax_rate_bps,unit_net_price_minor,valuation_policy_id,valuation_policy_version,valuation_policy_hash,
               line_net_amount_minor,line_tax_amount_minor,
               line_gross_amount_minor,created_at,updated_at)
            VALUES
            <foreach collection="values" item="value" separator=",">
              (#{value.itemId},#{value.tenantId},#{value.orderId},#{value.lineNumber},#{value.awardLineId},#{value.canonicalSkuId},
               #{value.orderedQuantity},#{value.uomCode},#{value.taxCode},#{value.taxRateBps},
               #{value.unitNetPriceMinor},#{value.valuationPolicyId},#{value.valuationPolicyVersion},#{value.valuationPolicyHash},
               #{value.lineNetAmountMinor},#{value.lineTaxAmountMinor},
               #{value.lineGrossAmountMinor},#{value.createdAt},#{value.updatedAt})
            </foreach>
            </script>
            """)
    int insertItems(@Param("values") List<PurchaseOrderItem> values);

    @Insert("""
            <script>
            INSERT INTO cloudmold_procurement_order_award_source
              (tenant_id,order_id,award_id,award_version,award_line_id,item_id,source_snapshot_id,created_at)
            VALUES
            <foreach collection="values" item="value" separator=",">
              (#{value.tenantId},#{value.orderId},#{value.awardId},#{value.awardVersion},#{value.awardLineId},
               #{value.itemId},#{value.sourceSnapshotId},#{value.createdAt})
            </foreach>
            </script>
            """)
    int insertAwardSources(@Param("values") List<PurchaseOrderAwardSource> values);

    @Insert("""
            <script>
            INSERT INTO cloudmold_procurement_order_delivery_schedule
              (schedule_id,tenant_id,order_id,item_id,schedule_number,required_delivery_date,
               canonical_warehouse_id,scheduled_quantity,created_at,updated_at)
            VALUES
            <foreach collection="values" item="value" separator=",">
              (#{value.scheduleId},#{value.tenantId},#{value.orderId},#{value.itemId},#{value.scheduleNumber},
               #{value.requiredDeliveryDate},#{value.canonicalWarehouseId},#{value.scheduledQuantity},
               #{value.createdAt},#{value.updatedAt})
            </foreach>
            </script>
            """)
    int insertSchedules(@Param("values") List<PurchaseOrderDeliverySchedule> values);

    @Insert("""
            INSERT INTO cloudmold_procurement_order_status_history
              (tenant_id,order_id,operation_id,aggregate_version,status,actor_principal_id,reason_code,occurred_at,created_at)
            VALUES (#{tenantId},#{orderId},#{operationId},#{aggregateVersion},#{status},#{actorPrincipalId},
                    #{reasonCode},#{occurredAt},#{createdAt})
            """)
    int insertStatusHistory(OrderStatusHistory value);

    @Insert("""
            INSERT INTO cloudmold_purchase_requisition
              (requisition_id,tenant_id,requisition_code,source_business_type,source_business_ref,status,
               requested_by_principal_id,approved_by_principal_id,reason_code,remark,version,
               requested_at,approved_at,created_at,updated_at)
            VALUES (#{requisitionId},#{tenantId},#{requisitionCode},#{sourceBusinessType},#{sourceBusinessRef},#{status},
                    #{requestedByPrincipalId},#{approvedByPrincipalId},#{reasonCode},#{remark},#{version},
                    #{requestedAt},#{approvedAt},#{createdAt},#{updatedAt})
            """)
    int insertPurchaseRequisition(PurchaseRequisition value);

    @Insert("""
            <script>
            INSERT INTO cloudmold_purchase_requisition_line
              (line_id,tenant_id,requisition_id,line_number,canonical_sku_id,requested_quantity,uom_code,created_at,updated_at)
            VALUES
            <foreach collection="values" item="value" separator=",">
              (#{value.lineId},#{value.tenantId},#{value.requisitionId},#{value.lineNumber},#{value.canonicalSkuId},
               #{value.requestedQuantity},#{value.uomCode},#{value.createdAt},#{value.updatedAt})
            </foreach>
            </script>
            """)
    int insertPurchaseRequisitionLines(@Param("values") List<PurchaseRequisitionLine> values);

    @Insert("""
            <script>
            INSERT INTO cloudmold_purchase_requisition_delivery_schedule
              (schedule_id,tenant_id,requisition_id,line_id,schedule_number,canonical_warehouse_id,
               required_delivery_date,scheduled_quantity,created_at,updated_at)
            VALUES
            <foreach collection="values" item="value" separator=",">
              (#{value.scheduleId},#{value.tenantId},#{value.requisitionId},#{value.lineId},#{value.scheduleNumber},
               #{value.canonicalWarehouseId},#{value.requiredDeliveryDate},#{value.scheduledQuantity},
               #{value.createdAt},#{value.updatedAt})
            </foreach>
            </script>
            """)
    int insertPurchaseRequisitionSchedules(@Param("values") List<PurchaseRequisitionDeliverySchedule> values);

    @Insert("""
            INSERT INTO cloudmold_purchase_requisition_status_history
              (tenant_id,requisition_id,operation_id,aggregate_version,status,actor_principal_id,
               reason_code,occurred_at,created_at)
            VALUES (#{tenantId},#{requisitionId},#{operationId},#{aggregateVersion},#{status},#{actorPrincipalId},
                    #{reasonCode},#{occurredAt},#{createdAt})
            """)
    int insertPurchaseRequisitionStatusHistory(PurchaseRequisitionStatusHistory value);

    @Select("""
            SELECT requisition_id,tenant_id,requisition_code,source_business_type,source_business_ref,status,
                   requested_by_principal_id,approved_by_principal_id,reason_code,remark,version,
                   requested_at,approved_at,created_at,updated_at
            FROM cloudmold_purchase_requisition
            WHERE tenant_id=#{tenantId} AND source_business_type=#{sourceBusinessType}
              AND source_business_ref=#{sourceBusinessRef}
            """)
    PurchaseRequisition selectPurchaseRequisitionBySourceBusiness(
            @Param("tenantId") Long tenantId,
            @Param("sourceBusinessType") String sourceBusinessType,
            @Param("sourceBusinessRef") String sourceBusinessRef);

    @Select("""
            SELECT requisition_id,tenant_id,requisition_code,source_business_type,source_business_ref,status,
                   requested_by_principal_id,approved_by_principal_id,reason_code,remark,version,
                   requested_at,approved_at,created_at,updated_at
            FROM cloudmold_purchase_requisition
            WHERE tenant_id=#{tenantId} AND requisition_id=#{requisitionId}
            """)
    PurchaseRequisition selectPurchaseRequisitionById(
            @Param("tenantId") Long tenantId, @Param("requisitionId") String requisitionId);

    @Select("""
            SELECT line_id,tenant_id,requisition_id,line_number,canonical_sku_id,requested_quantity,uom_code,
                   created_at,updated_at
            FROM cloudmold_purchase_requisition_line
            WHERE tenant_id=#{tenantId} AND requisition_id=#{requisitionId}
            ORDER BY line_number ASC,line_id ASC
            """)
    List<PurchaseRequisitionLine> selectPurchaseRequisitionLines(
            @Param("tenantId") Long tenantId, @Param("requisitionId") String requisitionId);

    @Select("""
            SELECT schedule_id,tenant_id,requisition_id,line_id,schedule_number,canonical_warehouse_id,
                   required_delivery_date,scheduled_quantity,created_at,updated_at
            FROM cloudmold_purchase_requisition_delivery_schedule
            WHERE tenant_id=#{tenantId} AND requisition_id=#{requisitionId}
            ORDER BY line_id ASC,schedule_number ASC,schedule_id ASC
            """)
    List<PurchaseRequisitionDeliverySchedule> selectPurchaseRequisitionSchedules(
            @Param("tenantId") Long tenantId, @Param("requisitionId") String requisitionId);

    @Select("""
            SELECT order_id,tenant_id,order_code,source_business_type,source_business_ref,award_id,award_version,legal_entity_id,supplier_id,
                   currency_code,lead_time_days,header_net_amount_minor,header_tax_amount_minor,
                   header_gross_amount_minor,tax_calculation_policy_code,rounding_policy_code,status,
                   created_by_principal_id,submitted_by_principal_id,approved_by_principal_id,released_by_principal_id,
                   dispatched_by_principal_id,supplier_confirmed_by_principal_id,
                   cancelled_by_principal_id,closed_by_principal_id,reason_code,remark,version,created_at,
                   submitted_at,approved_at,released_at,updated_at,dispatched_at,supplier_confirmed_at,cancelled_at,closed_at
            FROM cloudmold_procurement_order
            WHERE tenant_id=#{tenantId} AND order_id=#{orderId} FOR UPDATE
            """)
    ProcurementOrder selectOrderForUpdate(@Param("tenantId") Long tenantId,
                                          @Param("orderId") String orderId);

    @Select("""
            SELECT order_id,tenant_id,order_code,source_business_type,source_business_ref,award_id,award_version,legal_entity_id,supplier_id,
                   currency_code,lead_time_days,header_net_amount_minor,header_tax_amount_minor,
                   header_gross_amount_minor,tax_calculation_policy_code,rounding_policy_code,status,
                   created_by_principal_id,submitted_by_principal_id,approved_by_principal_id,released_by_principal_id,
                   dispatched_by_principal_id,supplier_confirmed_by_principal_id,
                   cancelled_by_principal_id,closed_by_principal_id,reason_code,remark,version,created_at,
                   submitted_at,approved_at,released_at,updated_at,dispatched_at,supplier_confirmed_at,cancelled_at,closed_at
            FROM cloudmold_procurement_order
            WHERE tenant_id=#{tenantId} AND order_id=#{orderId}
            """)
    ProcurementOrder selectCurrentHeader(@Param("tenantId") Long tenantId,
                                         @Param("orderId") String orderId);

    @Select("""
            SELECT order_id,tenant_id,order_code,source_business_type,source_business_ref,award_id,award_version,legal_entity_id,supplier_id,
                   currency_code,lead_time_days,header_net_amount_minor,header_tax_amount_minor,
                   header_gross_amount_minor,tax_calculation_policy_code,rounding_policy_code,status,
                   created_by_principal_id,submitted_by_principal_id,approved_by_principal_id,released_by_principal_id,
                   dispatched_by_principal_id,supplier_confirmed_by_principal_id,
                   cancelled_by_principal_id,closed_by_principal_id,reason_code,remark,version,created_at,
                   submitted_at,approved_at,released_at,updated_at,dispatched_at,supplier_confirmed_at,cancelled_at,closed_at
            FROM cloudmold_procurement_order
            WHERE tenant_id=#{tenantId}
              AND source_business_type=#{sourceBusinessType}
              AND source_business_ref=#{sourceBusinessRef}
            """)
    ProcurementOrder selectCurrentHeaderBySourceBusiness(@Param("tenantId") Long tenantId,
                                                         @Param("sourceBusinessType") String sourceBusinessType,
                                                         @Param("sourceBusinessRef") String sourceBusinessRef);

    @Select("""
            SELECT item_id,tenant_id,order_id,line_number,award_line_id,canonical_sku_id,ordered_quantity,uom_code,tax_code,
                   tax_rate_bps,unit_net_price_minor,valuation_policy_id,valuation_policy_version,valuation_policy_hash,
                   line_net_amount_minor,line_tax_amount_minor,
                   line_gross_amount_minor,created_at,updated_at
            FROM cloudmold_procurement_order_item
            WHERE tenant_id=#{tenantId} AND order_id=#{orderId}
            ORDER BY line_number ASC,item_id ASC
            """)
    List<PurchaseOrderItem> selectItems(@Param("tenantId") Long tenantId,
                                        @Param("orderId") String orderId);

    @Select("""
            SELECT schedule_id,tenant_id,order_id,item_id,schedule_number,required_delivery_date,
                   canonical_warehouse_id,scheduled_quantity,created_at,updated_at
            FROM cloudmold_procurement_order_delivery_schedule
            WHERE tenant_id=#{tenantId} AND order_id=#{orderId}
            ORDER BY item_id ASC,schedule_number ASC,schedule_id ASC
            """)
    List<PurchaseOrderDeliverySchedule> selectSchedules(@Param("tenantId") Long tenantId,
                                                        @Param("orderId") String orderId);

    @Update("""
            UPDATE cloudmold_procurement_order SET status='SUBMITTED',submitted_by_principal_id=#{actor},
              submitted_at=#{now},reason_code=#{reason},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND order_id=#{orderId} AND status='DRAFT' AND version=#{version}
            """)
    int submitOrder(@Param("tenantId") Long tenantId,@Param("orderId") String orderId,@Param("version") Long version,
                    @Param("actor") String actor,@Param("reason") String reason,@Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_procurement_order SET status='APPROVED',approved_by_principal_id=#{actor},
              approved_at=#{now},reason_code=#{reason},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND order_id=#{orderId} AND status='SUBMITTED' AND version=#{version}
            """)
    int approveOrder(@Param("tenantId") Long tenantId,@Param("orderId") String orderId,@Param("version") Long version,
                     @Param("actor") String actor,@Param("reason") String reason,@Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_procurement_order SET status='RELEASED',released_by_principal_id=#{actor},
              released_at=#{now},reason_code=#{reason},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND order_id=#{orderId} AND status='APPROVED' AND version=#{version}
            """)
    int releaseOrder(@Param("tenantId") Long tenantId,@Param("orderId") String orderId,@Param("version") Long version,
                     @Param("actor") String actor,@Param("reason") String reason,@Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_procurement_order
            SET status='DISPATCHED',dispatched_by_principal_id=#{actorPrincipalId},reason_code=#{reasonCode},
                dispatched_at=#{now},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND order_id=#{orderId}
              AND status='RELEASED' AND award_id IS NOT NULL AND version=#{expectedVersion}
            """)
    int dispatchOrder(@Param("tenantId") Long tenantId,
                      @Param("orderId") String orderId,
                      @Param("expectedVersion") Long expectedVersion,
                      @Param("actorPrincipalId") String actorPrincipalId,
                      @Param("reasonCode") String reasonCode,
                      @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_procurement_order
            SET status='SUPPLIER_CONFIRMED',supplier_confirmed_by_principal_id=#{actorPrincipalId},
                reason_code=#{reasonCode},supplier_confirmed_at=#{now},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND order_id=#{orderId}
              AND status='DISPATCHED' AND version=#{expectedVersion}
            """)
    int confirmSupplier(@Param("tenantId") Long tenantId,
                        @Param("orderId") String orderId,
                        @Param("expectedVersion") Long expectedVersion,
                        @Param("actorPrincipalId") String actorPrincipalId,
                        @Param("reasonCode") String reasonCode,
                        @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_procurement_order
            SET status='CANCELLED',cancelled_by_principal_id=#{actorPrincipalId},reason_code=#{reasonCode},
                cancelled_at=#{now},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND order_id=#{orderId}
              AND status IN ('DRAFT','SUBMITTED','APPROVED','RELEASED','DISPATCHED','SUPPLIER_CONFIRMED') AND version=#{expectedVersion}
            """)
    int cancelOrder(@Param("tenantId") Long tenantId,
                    @Param("orderId") String orderId,
                    @Param("expectedVersion") Long expectedVersion,
                    @Param("actorPrincipalId") String actorPrincipalId,
                    @Param("reasonCode") String reasonCode,
                    @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_procurement_order
            SET status='CLOSED',closed_by_principal_id=#{actorPrincipalId},reason_code=#{reasonCode},
                closed_at=#{now},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND order_id=#{orderId}
              AND status='SUPPLIER_CONFIRMED' AND version=#{expectedVersion}
            """)
    int closeOrder(@Param("tenantId") Long tenantId,
                   @Param("orderId") String orderId,
                   @Param("expectedVersion") Long expectedVersion,
                   @Param("actorPrincipalId") String actorPrincipalId,
                   @Param("reasonCode") String reasonCode,
                   @Param("now") LocalDateTime now);

    @Select("""
      SELECT COUNT(*) FROM cloudmold_purchase_requisition pr
      WHERE pr.tenant_id=#{tenantId} AND (#{status} IS NULL OR pr.status=#{status})
        AND (#{keyword} IS NULL OR #{keyword}='' OR pr.requisition_code LIKE CONCAT('%',#{keyword},'%')
          OR pr.requisition_id LIKE CONCAT('%',#{keyword},'%')
          OR EXISTS (SELECT 1 FROM cloudmold_purchase_requisition_line l WHERE l.tenant_id=pr.tenant_id
            AND l.requisition_id=pr.requisition_id AND l.canonical_sku_id LIKE CONCAT('%',#{keyword},'%')))
      """)
    long countPurchaseRequisitionPage(@Param("tenantId") Long tenantId,@Param("status") String status,@Param("keyword") String keyword);

    @Select("SELECT * FROM cloudmold_purchase_requisition WHERE tenant_id=#{tenantId} AND (#{status} IS NULL OR status=#{status}) ORDER BY created_at DESC,requisition_id LIMIT #{limit} OFFSET #{offset}")
    List<PurchaseRequisition> selectPurchaseRequisitionPage(@Param("tenantId") Long tenantId,@Param("status") String status,@Param("offset") long offset,@Param("limit") int limit);

    @Select("""
      SELECT COUNT(*) FROM cloudmold_procurement_order po
      WHERE po.tenant_id=#{tenantId} AND (#{status} IS NULL OR po.status=#{status})
        AND (#{keyword} IS NULL OR #{keyword}='' OR po.order_code LIKE CONCAT('%',#{keyword},'%')
          OR po.order_id LIKE CONCAT('%',#{keyword},'%') OR po.supplier_id LIKE CONCAT('%',#{keyword},'%')
          OR EXISTS (SELECT 1 FROM cloudmold_procurement_award a WHERE a.tenant_id=po.tenant_id
            AND a.award_id=po.award_id AND a.award_code LIKE CONCAT('%',#{keyword},'%'))
          OR EXISTS (SELECT 1 FROM cloudmold_procurement_order_item i WHERE i.tenant_id=po.tenant_id
            AND i.order_id=po.order_id AND i.canonical_sku_id LIKE CONCAT('%',#{keyword},'%')))
      """)
    long countOrderPage(@Param("tenantId") Long tenantId,@Param("status") String status,@Param("keyword") String keyword);

    @Select("SELECT * FROM cloudmold_procurement_order WHERE tenant_id=#{tenantId} AND (#{status} IS NULL OR status=#{status}) ORDER BY created_at DESC,order_id LIMIT #{limit} OFFSET #{offset}")
    List<ProcurementOrder> selectOrderPage(@Param("tenantId") Long tenantId,@Param("status") String status,@Param("offset") long offset,@Param("limit") int limit);

    @Select("""
      SELECT pr.version aggregate_version,pr.approved_at,COUNT(l.line_id) line_count,pr.requisition_code,pr.requisition_id,
       pr.requested_by_principal_id,(SELECT MIN(s.required_delivery_date) FROM cloudmold_purchase_requisition_delivery_schedule s WHERE s.tenant_id=pr.tenant_id AND s.requisition_id=pr.requisition_id) required_delivery_date,
       pr.status,pr.requested_at submitted_at,COALESCE(SUM(l.requested_quantity),0) total_requested_quantity,MIN(l.uom_code) uom_code,pr.updated_at
      FROM cloudmold_purchase_requisition pr LEFT JOIN cloudmold_purchase_requisition_line l ON l.tenant_id=pr.tenant_id AND l.requisition_id=pr.requisition_id
      WHERE pr.tenant_id=#{tenantId} AND (#{status} IS NULL OR pr.status=#{status})
       AND (#{keyword} IS NULL OR #{keyword}='' OR pr.requisition_code LIKE CONCAT('%',#{keyword},'%')
        OR pr.requisition_id LIKE CONCAT('%',#{keyword},'%')
        OR EXISTS (SELECT 1 FROM cloudmold_purchase_requisition_line lx WHERE lx.tenant_id=pr.tenant_id
          AND lx.requisition_id=pr.requisition_id AND lx.canonical_sku_id LIKE CONCAT('%',#{keyword},'%')))
      GROUP BY pr.requisition_id ORDER BY pr.updated_at DESC,pr.requisition_id LIMIT #{limit} OFFSET #{offset}
      """) List<RequisitionSummary> selectRequisitionAdminPage(@Param("tenantId") Long tenantId,@Param("status") String status,@Param("keyword") String keyword,@Param("offset") long offset,@Param("limit") int limit);

    @Select("""
      SELECT po.version aggregate_version,a.award_code,po.award_id,po.currency_code,po.header_gross_amount_minor gross_amount_minor,
       (SELECT COUNT(*) FROM cloudmold_procurement_order_item i WHERE i.tenant_id=po.tenant_id AND i.order_id=po.order_id) line_count,
       po.order_code,po.order_id,(SELECT COALESCE(SUM(i.ordered_quantity),0) FROM cloudmold_procurement_order_item i WHERE i.tenant_id=po.tenant_id AND i.order_id=po.order_id) ordered_quantity,
       CAST(0 AS DECIMAL(24,6)) qualified_quantity,CAST(0 AS DECIMAL(24,6)) received_quantity,
       (SELECT COUNT(*) FROM cloudmold_procurement_order_delivery_schedule s WHERE s.tenant_id=po.tenant_id AND s.order_id=po.order_id) schedule_count,
       po.status,po.supplier_id,po.updated_at
      FROM cloudmold_procurement_order po LEFT JOIN cloudmold_procurement_award a ON a.tenant_id=po.tenant_id AND a.award_id=po.award_id
      WHERE po.tenant_id=#{tenantId} AND (#{status} IS NULL OR po.status=#{status})
       AND (#{keyword} IS NULL OR #{keyword}='' OR po.order_code LIKE CONCAT('%',#{keyword},'%')
        OR po.order_id LIKE CONCAT('%',#{keyword},'%') OR po.supplier_id LIKE CONCAT('%',#{keyword},'%')
        OR a.award_code LIKE CONCAT('%',#{keyword},'%')
        OR EXISTS (SELECT 1 FROM cloudmold_procurement_order_item ix WHERE ix.tenant_id=po.tenant_id
          AND ix.order_id=po.order_id AND ix.canonical_sku_id LIKE CONCAT('%',#{keyword},'%')))
      ORDER BY po.updated_at DESC,po.order_id LIMIT #{limit} OFFSET #{offset}
      """) List<OrderSummary> selectOrderAdminPage(@Param("tenantId") Long tenantId,@Param("status") String status,@Param("keyword") String keyword,@Param("offset") long offset,@Param("limit") int limit);

    @Select("SELECT * FROM cloudmold_procurement_order_status_history WHERE tenant_id=#{tenantId} AND order_id=#{orderId} ORDER BY aggregate_version")
    List<OrderStatusHistory> selectOrderStatusHistory(@Param("tenantId") Long tenantId,@Param("orderId") String orderId);
}
