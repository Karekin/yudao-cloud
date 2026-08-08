package cn.iocoder.yudao.module.cloudmold.finance.dal.mysql.receivables;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.finance.api.receivables.ReceivablesSummaryView;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.receivables.ReceiptAllocationDO;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.receivables.ReceiptDO;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.receivables.ReceivablePlanDO;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.receivables.ReceivablesOperationDO;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ReceivablesMapper extends BaseMapperX<ReceivablePlanDO> {

    @Insert("""
            INSERT INTO cloudmold_finance_receivables_operation
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
            FROM cloudmold_finance_receivables_operation
            WHERE tenant_id=#{tenantId} AND operation_id=#{operationId} FOR UPDATE
            """)
    ReceivablesOperationDO selectOperationForUpdate(@Param("tenantId") Long tenantId,
                                                    @Param("operationId") Long operationId);

    @Update("""
            UPDATE cloudmold_finance_receivables_operation
            SET status=10,aggregate_type=#{aggregateType},aggregate_id=#{aggregateId},
                result_json=#{resultJson},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND operation_id=#{operationId} AND status=0
            """)
    int markOperationSucceeded(@Param("tenantId") Long tenantId,
                               @Param("operationId") Long operationId,
                               @Param("aggregateType") String aggregateType,
                               @Param("aggregateId") String aggregateId,
                               @Param("resultJson") String resultJson,
                               @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_finance_receivable_plan
              (receivable_plan_id,tenant_id,plan_code,customer_id,sales_contract_id,currency_code,
               planned_amount_minor,allocated_amount_minor,status,created_by_principal_id,
               last_modified_by_principal_id,latest_reason_code,due_date,version,created_at,updated_at)
            VALUES (#{receivablePlanId},#{tenantId},#{planCode},#{customerId},#{salesContractId},#{currencyCode},
                    #{plannedAmountMinor},#{allocatedAmountMinor},#{status},#{createdByPrincipalId},
                    #{lastModifiedByPrincipalId},#{latestReasonCode},#{dueDate},#{version},#{createdAt},#{updatedAt})
            """)
    int insertPlan(ReceivablePlanDO value);

    @Select("""
            SELECT receivable_plan_id,tenant_id,plan_code,customer_id,sales_contract_id,currency_code,
                   planned_amount_minor,allocated_amount_minor,status,created_by_principal_id,
                   last_modified_by_principal_id,latest_reason_code,due_date,version,created_at,updated_at
            FROM cloudmold_finance_receivable_plan
            WHERE tenant_id=#{tenantId} AND receivable_plan_id=#{receivablePlanId} FOR UPDATE
            """)
    ReceivablePlanDO selectPlanForUpdate(@Param("tenantId") Long tenantId,
                                         @Param("receivablePlanId") String receivablePlanId);

    @Update("""
            UPDATE cloudmold_finance_receivable_plan
            SET allocated_amount_minor=allocated_amount_minor+#{amountMinor},status=#{status},
                last_modified_by_principal_id=#{actorPrincipalId},latest_reason_code=#{reasonCode},
                version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND receivable_plan_id=#{receivablePlanId}
              AND version=#{expectedVersion} AND currency_code=#{currencyCode}
              AND planned_amount_minor-allocated_amount_minor >= #{amountMinor}
            """)
    int allocatePlan(@Param("tenantId") Long tenantId,
                     @Param("receivablePlanId") String receivablePlanId,
                     @Param("expectedVersion") Long expectedVersion,
                     @Param("amountMinor") Long amountMinor,
                     @Param("currencyCode") String currencyCode,
                     @Param("status") String status,
                     @Param("actorPrincipalId") String actorPrincipalId,
                     @Param("reasonCode") String reasonCode,
                     @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_finance_receipt
              (receipt_id,tenant_id,receipt_code,customer_id,sales_contract_id,currency_code,receipt_amount_minor,
               allocated_amount_minor,status,external_reference,recorded_by_principal_id,last_modified_by_principal_id,
               latest_reason_code,receipt_date,version,created_at,updated_at)
            VALUES (#{receiptId},#{tenantId},#{receiptCode},#{customerId},#{salesContractId},#{currencyCode},
                    #{receiptAmountMinor},#{allocatedAmountMinor},#{status},#{externalReference},
                    #{recordedByPrincipalId},#{lastModifiedByPrincipalId},#{latestReasonCode},#{receiptDate},
                    #{version},#{createdAt},#{updatedAt})
            """)
    int insertReceipt(ReceiptDO value);

    @Select("""
            SELECT receipt_id,tenant_id,receipt_code,customer_id,sales_contract_id,currency_code,
                   receipt_amount_minor,allocated_amount_minor,status,external_reference,recorded_by_principal_id,
                   last_modified_by_principal_id,latest_reason_code,receipt_date,version,created_at,updated_at
            FROM cloudmold_finance_receipt
            WHERE tenant_id=#{tenantId} AND receipt_id=#{receiptId} FOR UPDATE
            """)
    ReceiptDO selectReceiptForUpdate(@Param("tenantId") Long tenantId,
                                     @Param("receiptId") String receiptId);

    @Update("""
            UPDATE cloudmold_finance_receipt
            SET allocated_amount_minor=allocated_amount_minor+#{amountMinor},status=#{status},
                last_modified_by_principal_id=#{actorPrincipalId},latest_reason_code=#{reasonCode},
                version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND receipt_id=#{receiptId}
              AND version=#{expectedVersion} AND currency_code=#{currencyCode}
              AND receipt_amount_minor-allocated_amount_minor >= #{amountMinor}
            """)
    int allocateReceipt(@Param("tenantId") Long tenantId,
                        @Param("receiptId") String receiptId,
                        @Param("expectedVersion") Long expectedVersion,
                        @Param("amountMinor") Long amountMinor,
                        @Param("currencyCode") String currencyCode,
                        @Param("status") String status,
                        @Param("actorPrincipalId") String actorPrincipalId,
                        @Param("reasonCode") String reasonCode,
                        @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_finance_receipt_allocation
              (receipt_allocation_id,tenant_id,receipt_id,receivable_plan_id,customer_id,sales_contract_id,
               currency_code,amount_minor,status,allocated_by_principal_id,reason_code,receipt_version,
               receivable_plan_version,created_at)
            VALUES (#{receiptAllocationId},#{tenantId},#{receiptId},#{receivablePlanId},#{customerId},
                    #{salesContractId},#{currencyCode},#{amountMinor},#{status},#{allocatedByPrincipalId},
                    #{reasonCode},#{receiptVersion},#{receivablePlanVersion},#{createdAt})
            """)
    int insertAllocation(ReceiptAllocationDO value);

    @Select("""
            SELECT receipt_allocation_id,tenant_id,receipt_id,receivable_plan_id,customer_id,sales_contract_id,
                   currency_code,amount_minor,status,allocated_by_principal_id,reason_code,receipt_version,
                   receivable_plan_version,created_at
            FROM cloudmold_finance_receipt_allocation
            WHERE tenant_id=#{tenantId} AND receipt_id=#{receiptId}
            ORDER BY created_at ASC, receipt_allocation_id ASC
            """)
    List<ReceiptAllocationDO> selectAllocationsByReceipt(@Param("tenantId") Long tenantId,
                                                         @Param("receiptId") String receiptId);

    @Select("""
            SELECT customer_id,sales_contract_id,currency_code,
                   receivable_plan_count,receivable_plan_amount_minor,receivable_plan_allocated_minor,
                   receivable_plan_outstanding_minor,receipt_count,receipt_amount_minor,receipt_allocated_minor,
                   receipt_unallocated_minor,allocation_count,allocation_amount_minor
            FROM (
                SELECT #{customerId} AS customer_id,
                       NULL AS sales_contract_id,
                       x.currency_code,
                       COALESCE(plan_stats.receivable_plan_count, 0) AS receivable_plan_count,
                       COALESCE(plan_stats.receivable_plan_amount_minor, 0) AS receivable_plan_amount_minor,
                       COALESCE(plan_stats.receivable_plan_allocated_minor, 0) AS receivable_plan_allocated_minor,
                       COALESCE(plan_stats.receivable_plan_outstanding_minor, 0) AS receivable_plan_outstanding_minor,
                       COALESCE(receipt_stats.receipt_count, 0) AS receipt_count,
                       COALESCE(receipt_stats.receipt_amount_minor, 0) AS receipt_amount_minor,
                       COALESCE(receipt_stats.receipt_allocated_minor, 0) AS receipt_allocated_minor,
                       COALESCE(receipt_stats.receipt_unallocated_minor, 0) AS receipt_unallocated_minor,
                       COALESCE(allocation_stats.allocation_count, 0) AS allocation_count,
                       COALESCE(allocation_stats.allocation_amount_minor, 0) AS allocation_amount_minor
                FROM (
                    SELECT currency_code
                    FROM cloudmold_finance_receivable_plan
                    WHERE tenant_id=#{tenantId} AND customer_id=#{customerId}
                    UNION
                    SELECT currency_code
                    FROM cloudmold_finance_receipt
                    WHERE tenant_id=#{tenantId} AND customer_id=#{customerId}
                ) x
                LEFT JOIN (
                    SELECT currency_code,
                           COUNT(*) AS receivable_plan_count,
                           SUM(planned_amount_minor) AS receivable_plan_amount_minor,
                           SUM(allocated_amount_minor) AS receivable_plan_allocated_minor,
                           SUM(planned_amount_minor - allocated_amount_minor) AS receivable_plan_outstanding_minor
                    FROM cloudmold_finance_receivable_plan
                    WHERE tenant_id=#{tenantId} AND customer_id=#{customerId}
                    GROUP BY currency_code
                ) plan_stats ON plan_stats.currency_code = x.currency_code
                LEFT JOIN (
                    SELECT currency_code,
                           COUNT(*) AS receipt_count,
                           SUM(receipt_amount_minor) AS receipt_amount_minor,
                           SUM(allocated_amount_minor) AS receipt_allocated_minor,
                           SUM(receipt_amount_minor - allocated_amount_minor) AS receipt_unallocated_minor
                    FROM cloudmold_finance_receipt
                    WHERE tenant_id=#{tenantId} AND customer_id=#{customerId}
                    GROUP BY currency_code
                ) receipt_stats ON receipt_stats.currency_code = x.currency_code
                LEFT JOIN (
                    SELECT currency_code,
                           COUNT(*) AS allocation_count,
                           SUM(amount_minor) AS allocation_amount_minor
                    FROM cloudmold_finance_receipt_allocation
                    WHERE tenant_id=#{tenantId} AND customer_id=#{customerId}
                    GROUP BY currency_code
                ) allocation_stats ON allocation_stats.currency_code = x.currency_code
            ) summary
            ORDER BY currency_code ASC
            """)
    List<ReceivablesSummaryView> selectCustomerSummaries(@Param("tenantId") Long tenantId,
                                                         @Param("customerId") String customerId);

    @Select("""
            SELECT customer_id,sales_contract_id,currency_code,
                   receivable_plan_count,receivable_plan_amount_minor,receivable_plan_allocated_minor,
                   receivable_plan_outstanding_minor,receipt_count,receipt_amount_minor,receipt_allocated_minor,
                   receipt_unallocated_minor,allocation_count,allocation_amount_minor
            FROM (
                SELECT COALESCE(plan_stats.customer_id, receipt_stats.customer_id, allocation_stats.customer_id) AS customer_id,
                       #{salesContractId} AS sales_contract_id,
                       x.currency_code,
                       COALESCE(plan_stats.receivable_plan_count, 0) AS receivable_plan_count,
                       COALESCE(plan_stats.receivable_plan_amount_minor, 0) AS receivable_plan_amount_minor,
                       COALESCE(plan_stats.receivable_plan_allocated_minor, 0) AS receivable_plan_allocated_minor,
                       COALESCE(plan_stats.receivable_plan_outstanding_minor, 0) AS receivable_plan_outstanding_minor,
                       COALESCE(receipt_stats.receipt_count, 0) AS receipt_count,
                       COALESCE(receipt_stats.receipt_amount_minor, 0) AS receipt_amount_minor,
                       COALESCE(receipt_stats.receipt_allocated_minor, 0) AS receipt_allocated_minor,
                       COALESCE(receipt_stats.receipt_unallocated_minor, 0) AS receipt_unallocated_minor,
                       COALESCE(allocation_stats.allocation_count, 0) AS allocation_count,
                       COALESCE(allocation_stats.allocation_amount_minor, 0) AS allocation_amount_minor
                FROM (
                    SELECT currency_code
                    FROM cloudmold_finance_receivable_plan
                    WHERE tenant_id=#{tenantId} AND sales_contract_id=#{salesContractId}
                    UNION
                    SELECT currency_code
                    FROM cloudmold_finance_receipt
                    WHERE tenant_id=#{tenantId} AND sales_contract_id=#{salesContractId}
                ) x
                LEFT JOIN (
                    SELECT customer_id,currency_code,
                           COUNT(*) AS receivable_plan_count,
                           SUM(planned_amount_minor) AS receivable_plan_amount_minor,
                           SUM(allocated_amount_minor) AS receivable_plan_allocated_minor,
                           SUM(planned_amount_minor - allocated_amount_minor) AS receivable_plan_outstanding_minor
                    FROM cloudmold_finance_receivable_plan
                    WHERE tenant_id=#{tenantId} AND sales_contract_id=#{salesContractId}
                    GROUP BY customer_id, currency_code
                ) plan_stats ON plan_stats.currency_code = x.currency_code
                LEFT JOIN (
                    SELECT customer_id,currency_code,
                           COUNT(*) AS receipt_count,
                           SUM(receipt_amount_minor) AS receipt_amount_minor,
                           SUM(allocated_amount_minor) AS receipt_allocated_minor,
                           SUM(receipt_amount_minor - allocated_amount_minor) AS receipt_unallocated_minor
                    FROM cloudmold_finance_receipt
                    WHERE tenant_id=#{tenantId} AND sales_contract_id=#{salesContractId}
                    GROUP BY customer_id, currency_code
                ) receipt_stats ON receipt_stats.currency_code = x.currency_code
                LEFT JOIN (
                    SELECT customer_id,currency_code,
                           COUNT(*) AS allocation_count,
                           SUM(amount_minor) AS allocation_amount_minor
                    FROM cloudmold_finance_receipt_allocation
                    WHERE tenant_id=#{tenantId} AND sales_contract_id=#{salesContractId}
                    GROUP BY customer_id, currency_code
                ) allocation_stats ON allocation_stats.currency_code = x.currency_code
            ) summary
            ORDER BY currency_code ASC
            """)
    List<ReceivablesSummaryView> selectSalesContractSummaries(@Param("tenantId") Long tenantId,
                                                              @Param("salesContractId") String salesContractId);
}
