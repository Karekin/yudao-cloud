package cn.iocoder.yudao.module.cloudmold.crm.dal.mysql.contract;

import cn.iocoder.yudao.module.cloudmold.crm.dal.dataobject.contract.SalesContractRecords.Operation;
import cn.iocoder.yudao.module.cloudmold.crm.dal.dataobject.contract.SalesContractRecords.SalesContract;
import cn.iocoder.yudao.module.cloudmold.crm.dal.dataobject.contract.SalesContractRecords.SalesContractItem;
import cn.iocoder.yudao.module.cloudmold.crm.dal.dataobject.contract.SalesContractRecords.StatusHistory;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SalesContractMapper {

    @Insert("""
            INSERT INTO cloudmold_sales_contract_operation
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
            FROM cloudmold_sales_contract_operation
            WHERE tenant_id=#{tenantId} AND operation_id=#{operationId}
            FOR UPDATE
            """)
    Operation selectOperationForUpdate(@Param("tenantId") Long tenantId,
                                       @Param("operationId") Long operationId);

    @Update("""
            UPDATE cloudmold_sales_contract_operation
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
            INSERT INTO cloudmold_sales_contract
              (sales_contract_id,tenant_id,contract_code,contract_name,customer_id,seller_merchant_id,seller_shop_id,
               seller_legal_entity_id,status,currency_code,total_amount_minor,effective_date,expires_on,
               approval_process_instance_id,created_by_principal_id,updated_by_principal_id,submitted_by_principal_id,
               version,created_at,updated_at,submitted_at)
            VALUES (#{salesContractId},#{tenantId},#{contractCode},#{contractName},#{customerId},#{sellerMerchantId},
                    #{sellerShopId},#{sellerLegalEntityId},#{status},#{currencyCode},#{totalAmountMinor},
                    #{effectiveDate},#{expiresOn},#{approvalProcessInstanceId},#{createdByPrincipalId},
                    #{updatedByPrincipalId},#{submittedByPrincipalId},#{version},#{createdAt},#{updatedAt},
                    #{submittedAt})
            """)
    int insertSalesContract(SalesContract value);

    @Update("""
            UPDATE cloudmold_sales_contract
            SET contract_code=#{contractCode},
                contract_name=#{contractName},
                customer_id=#{customerId},
                seller_merchant_id=#{sellerMerchantId},
                seller_shop_id=#{sellerShopId},
                seller_legal_entity_id=#{sellerLegalEntityId},
                currency_code=#{currencyCode},
                total_amount_minor=#{totalAmountMinor},
                effective_date=#{effectiveDate},
                expires_on=#{expiresOn},
                updated_by_principal_id=#{updatedByPrincipalId},
                version=#{nextVersion},
                updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND sales_contract_id=#{salesContractId}
              AND version=#{expectedVersion} AND status='DRAFT'
            """)
    int updateDraft(@Param("tenantId") Long tenantId,
                    @Param("salesContractId") String salesContractId,
                    @Param("expectedVersion") Long expectedVersion,
                    @Param("nextVersion") Long nextVersion,
                    @Param("contractCode") String contractCode,
                    @Param("contractName") String contractName,
                    @Param("customerId") String customerId,
                    @Param("sellerMerchantId") String sellerMerchantId,
                    @Param("sellerShopId") String sellerShopId,
                    @Param("sellerLegalEntityId") String sellerLegalEntityId,
                    @Param("currencyCode") String currencyCode,
                    @Param("totalAmountMinor") Long totalAmountMinor,
                    @Param("effectiveDate") java.time.LocalDate effectiveDate,
                    @Param("expiresOn") java.time.LocalDate expiresOn,
                    @Param("updatedByPrincipalId") String updatedByPrincipalId,
                    @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_sales_contract
            SET status=#{status},
                approval_process_instance_id=#{approvalProcessInstanceId},
                submitted_by_principal_id=#{submittedByPrincipalId},
                version=#{nextVersion},
                updated_at=#{now},
                submitted_at=#{now}
            WHERE tenant_id=#{tenantId} AND sales_contract_id=#{salesContractId}
              AND version=#{expectedVersion} AND status='DRAFT'
            """)
    int submitApproval(@Param("tenantId") Long tenantId,
                       @Param("salesContractId") String salesContractId,
                       @Param("expectedVersion") Long expectedVersion,
                       @Param("nextVersion") Long nextVersion,
                       @Param("status") String status,
                       @Param("approvalProcessInstanceId") String approvalProcessInstanceId,
                       @Param("submittedByPrincipalId") String submittedByPrincipalId,
                       @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_sales_contract
            SET status=#{status},
                updated_by_principal_id=#{updatedByPrincipalId},
                version=#{nextVersion},
                updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND sales_contract_id=#{salesContractId}
              AND approval_process_instance_id=#{approvalProcessInstanceId}
              AND version=#{expectedVersion} AND status='PENDING_APPROVAL'
            """)
    int completeApproval(@Param("tenantId") Long tenantId,
                         @Param("salesContractId") String salesContractId,
                         @Param("approvalProcessInstanceId") String approvalProcessInstanceId,
                         @Param("expectedVersion") Long expectedVersion,
                         @Param("nextVersion") Long nextVersion,
                         @Param("status") String status,
                         @Param("updatedByPrincipalId") String updatedByPrincipalId,
                         @Param("now") LocalDateTime now);

    @Select("""
            SELECT sales_contract_id,tenant_id,contract_code,contract_name,customer_id,seller_merchant_id,seller_shop_id,
                   seller_legal_entity_id,status,currency_code,total_amount_minor,effective_date,expires_on,
                   approval_process_instance_id,created_by_principal_id,updated_by_principal_id,submitted_by_principal_id,
                   version,created_at,updated_at,submitted_at
            FROM cloudmold_sales_contract
            WHERE tenant_id=#{tenantId} AND sales_contract_id=#{salesContractId}
            FOR UPDATE
            """)
    SalesContract selectSalesContractForUpdate(@Param("tenantId") Long tenantId,
                                               @Param("salesContractId") String salesContractId);

    @Select("""
            SELECT sales_contract_id,tenant_id,contract_code,contract_name,customer_id,seller_merchant_id,seller_shop_id,
                   seller_legal_entity_id,status,currency_code,total_amount_minor,effective_date,expires_on,
                   approval_process_instance_id,created_by_principal_id,updated_by_principal_id,submitted_by_principal_id,
                   version,created_at,updated_at,submitted_at
            FROM cloudmold_sales_contract
            WHERE tenant_id=#{tenantId} AND sales_contract_id=#{salesContractId}
            """)
    SalesContract selectSalesContract(@Param("tenantId") Long tenantId,
                                      @Param("salesContractId") String salesContractId);

    @Select("""
            SELECT sales_contract_item_id,tenant_id,sales_contract_id,line_no,canonical_sku_id,item_name,uom_code,
                   quantity,unit_price_minor,line_amount_minor,created_at,updated_at
            FROM cloudmold_sales_contract_item
            WHERE tenant_id=#{tenantId} AND sales_contract_id=#{salesContractId}
            ORDER BY line_no ASC,sales_contract_item_id ASC
            """)
    List<SalesContractItem> selectItems(@Param("tenantId") Long tenantId,
                                        @Param("salesContractId") String salesContractId);

    @Delete("""
            DELETE FROM cloudmold_sales_contract_item
            WHERE tenant_id=#{tenantId} AND sales_contract_id=#{salesContractId}
            """)
    int deleteItems(@Param("tenantId") Long tenantId,
                    @Param("salesContractId") String salesContractId);

    @Insert("""
            <script>
            INSERT INTO cloudmold_sales_contract_item
              (sales_contract_item_id,tenant_id,sales_contract_id,line_no,canonical_sku_id,item_name,uom_code,
               quantity,unit_price_minor,line_amount_minor,created_at,updated_at)
            VALUES
            <foreach collection="values" item="value" separator=",">
              (#{value.salesContractItemId},#{value.tenantId},#{value.salesContractId},#{value.lineNo},
               #{value.canonicalSkuId},#{value.itemName},#{value.uomCode},#{value.quantity},#{value.unitPriceMinor},
               #{value.lineAmountMinor},#{value.createdAt},#{value.updatedAt})
            </foreach>
            </script>
            """)
    int insertItems(@Param("values") List<SalesContractItem> values);

    @Insert("""
            INSERT INTO cloudmold_sales_contract_status_history
              (tenant_id,sales_contract_id,operation_id,aggregate_version,status,actor_principal_id,actor_admin_user_id,
               reason_code,approval_process_instance_id,occurred_at,created_at)
            VALUES (#{tenantId},#{salesContractId},#{operationId},#{aggregateVersion},#{status},#{actorPrincipalId},
                    #{actorAdminUserId},#{reasonCode},#{approvalProcessInstanceId},#{occurredAt},#{createdAt})
            """)
    int insertStatusHistory(StatusHistory value);
}
