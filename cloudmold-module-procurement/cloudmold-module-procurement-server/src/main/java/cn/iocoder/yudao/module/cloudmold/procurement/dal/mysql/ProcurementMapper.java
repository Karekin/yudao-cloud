package cn.iocoder.yudao.module.cloudmold.procurement.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.procurement.api.ProcurementOrderView;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.Operation;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.ProcurementOrder;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

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
              (order_id,tenant_id,order_code,source_business_type,source_business_ref,supplier_ref,
               canonical_sku_id,canonical_warehouse_id,ordered_quantity,uom_code,unit_cost_minor,
               total_amount_minor,currency_code,lead_time_days,required_delivery_date,status,
               created_by_principal_id,reason_code,remark,projection_source_system,
               projection_document_type,projection_external_document_id,projection_external_document_no,
               projection_document_status,projection_evidence_sha256,version,created_at,updated_at)
            VALUES (#{orderId},#{tenantId},#{orderCode},#{sourceBusinessType},#{sourceBusinessRef},
                    #{supplierRef},#{canonicalSkuId},#{canonicalWarehouseId},#{orderedQuantity},
                    #{uomCode},#{unitCostMinor},#{totalAmountMinor},#{currencyCode},#{leadTimeDays},
                    #{requiredDeliveryDate},#{status},#{createdByPrincipalId},#{reasonCode},#{remark},
                    #{projectionSourceSystem},#{projectionDocumentType},#{projectionExternalDocumentId},
                    #{projectionExternalDocumentNo},#{projectionDocumentStatus},#{projectionEvidenceSha256},
                    #{version},#{createdAt},#{updatedAt})
            """)
    int insertOrder(ProcurementOrder value);

    @Select("""
            SELECT order_id,tenant_id,order_code,source_business_type,source_business_ref,supplier_ref,
                   canonical_sku_id,canonical_warehouse_id,ordered_quantity,uom_code,unit_cost_minor,
                   total_amount_minor,currency_code,lead_time_days,required_delivery_date,status,
                   created_by_principal_id,dispatched_by_principal_id,supplier_confirmed_by_principal_id,
                   cancelled_by_principal_id,closed_by_principal_id,reason_code,remark,
                   projection_source_system,projection_document_type,projection_external_document_id,
                   projection_external_document_no,projection_document_status,projection_evidence_sha256,
                   version,created_at,updated_at,dispatched_at,supplier_confirmed_at,cancelled_at,closed_at
            FROM cloudmold_procurement_order
            WHERE tenant_id=#{tenantId} AND order_id=#{orderId} FOR UPDATE
            """)
    ProcurementOrder selectOrderForUpdate(@Param("tenantId") Long tenantId,
                                          @Param("orderId") String orderId);

    @Select("""
            SELECT order_id orderId,order_code orderCode,source_business_type sourceBusinessType,
                   source_business_ref sourceBusinessRef,supplier_ref supplierRef,
                   canonical_sku_id canonicalSkuId,canonical_warehouse_id canonicalWarehouseId,
                   ordered_quantity orderedQuantity,uom_code uomCode,unit_cost_minor unitCostMinor,
                   total_amount_minor totalAmountMinor,currency_code currencyCode,lead_time_days leadTimeDays,
                   required_delivery_date requiredDeliveryDate,status,version,
                   created_by_principal_id createdByPrincipalId,
                   dispatched_by_principal_id dispatchedByPrincipalId,
                   supplier_confirmed_by_principal_id supplierConfirmedByPrincipalId,
                   cancelled_by_principal_id cancelledByPrincipalId,
                   closed_by_principal_id closedByPrincipalId,reason_code,projection_source_system projectionSourceSystem,
                   projection_document_type projectionDocumentType,
                   projection_external_document_id projectionExternalDocumentId,
                   projection_external_document_no projectionExternalDocumentNo,
                   projection_document_status projectionDocumentStatus,
                   projection_evidence_sha256 projectionEvidenceSha256,created_at createdAt,
                   dispatched_at dispatchedAt,supplier_confirmed_at supplierConfirmedAt,
                   cancelled_at cancelledAt,closed_at closedAt
            FROM cloudmold_procurement_order
            WHERE tenant_id=#{tenantId} AND order_id=#{orderId}
            """)
    ProcurementOrderView selectCurrent(@Param("tenantId") Long tenantId,
                                       @Param("orderId") String orderId);

    @Select("""
            SELECT order_id orderId,order_code orderCode,source_business_type sourceBusinessType,
                   source_business_ref sourceBusinessRef,supplier_ref supplierRef,
                   canonical_sku_id canonicalSkuId,canonical_warehouse_id canonicalWarehouseId,
                   ordered_quantity orderedQuantity,uom_code uomCode,unit_cost_minor unitCostMinor,
                   total_amount_minor totalAmountMinor,currency_code currencyCode,lead_time_days leadTimeDays,
                   required_delivery_date requiredDeliveryDate,status,version,
                   created_by_principal_id createdByPrincipalId,
                   dispatched_by_principal_id dispatchedByPrincipalId,
                   supplier_confirmed_by_principal_id supplierConfirmedByPrincipalId,
                   cancelled_by_principal_id cancelledByPrincipalId,
                   closed_by_principal_id closedByPrincipalId,reason_code,projection_source_system projectionSourceSystem,
                   projection_document_type projectionDocumentType,
                   projection_external_document_id projectionExternalDocumentId,
                   projection_external_document_no projectionExternalDocumentNo,
                   projection_document_status projectionDocumentStatus,
                   projection_evidence_sha256 projectionEvidenceSha256,created_at createdAt,
                   dispatched_at dispatchedAt,supplier_confirmed_at supplierConfirmedAt,
                   cancelled_at cancelledAt,closed_at closedAt
            FROM cloudmold_procurement_order
            WHERE tenant_id=#{tenantId}
              AND source_business_type=#{sourceBusinessType}
              AND source_business_ref=#{sourceBusinessRef}
            """)
    ProcurementOrderView selectCurrentBySourceBusiness(@Param("tenantId") Long tenantId,
                                                       @Param("sourceBusinessType") String sourceBusinessType,
                                                       @Param("sourceBusinessRef") String sourceBusinessRef);

    @Update("""
            UPDATE cloudmold_procurement_order
            SET status='DISPATCHED',dispatched_by_principal_id=#{actorPrincipalId},reason_code=#{reasonCode},
                dispatched_at=#{now},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND order_id=#{orderId}
              AND status='CREATED' AND version=#{expectedVersion}
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
              AND status IN ('CREATED','DISPATCHED','SUPPLIER_CONFIRMED') AND version=#{expectedVersion}
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
}
