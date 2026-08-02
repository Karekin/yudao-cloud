package cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.*;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface InventoryScrapStoreMapper extends BaseMapperX<InventoryScrapDocumentDO> {

    @Insert("""
            INSERT INTO cloudmold_inventory_scrap_operation
              (tenant_id,idempotency_key,source_event_id,operation_type,request_hash,attempt_token,status,created_at,updated_at)
            VALUES
              (#{tenantId},#{idempotencyKey},#{sourceEventId},#{operationType},#{requestHash},#{attemptToken},0,#{now},#{now})
            ON DUPLICATE KEY UPDATE operation_id=LAST_INSERT_ID(operation_id)
            """)
    int insertOrResolveOperation(@Param("tenantId") Long tenantId,
                                 @Param("idempotencyKey") String idempotencyKey,
                                 @Param("sourceEventId") String sourceEventId,
                                 @Param("operationType") String operationType,
                                 @Param("requestHash") String requestHash,
                                 @Param("attemptToken") String attemptToken,
                                 @Param("now") LocalDateTime now);

    @Select("SELECT LAST_INSERT_ID()")
    Long selectLastInsertId();

    @Select("""
            SELECT * FROM cloudmold_inventory_scrap_operation
            WHERE tenant_id=#{tenantId} AND operation_id=#{operationId}
            FOR UPDATE
            """)
    InventoryScrapOperationDO selectOperationForUpdate(@Param("tenantId") Long tenantId,
                                                       @Param("operationId") Long operationId);

    @Update("""
            UPDATE cloudmold_inventory_scrap_operation
            SET status=10,scrap_id=#{scrapId},scrap_code=#{scrapCode},batch_id=#{batchId},batch_no=#{batchNo},
                result_status=#{resultStatus},processed_line_count=#{processedLineCount},
                aggregate_version=#{aggregateVersion},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND operation_id=#{operationId} AND status=0
            """)
    int markOperationSucceeded(@Param("tenantId") Long tenantId,
                               @Param("operationId") Long operationId,
                               @Param("scrapId") String scrapId,
                               @Param("scrapCode") String scrapCode,
                               @Param("batchId") String batchId,
                               @Param("batchNo") String batchNo,
                               @Param("resultStatus") String resultStatus,
                               @Param("processedLineCount") Integer processedLineCount,
                               @Param("aggregateVersion") Long aggregateVersion,
                               @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_inventory_scrap_document
              (scrap_id,tenant_id,scrap_code,reason_code,remark,owner_type,owner_id,warehouse_id,status,version,
               total_requested_quantity,total_disposed_quantity,line_count,requested_by_principal_id,created_at,updated_at)
            VALUES
              (#{scrapId},#{tenantId},#{scrapCode},#{reasonCode},#{remark},#{ownerType},#{ownerId},#{warehouseId},
               #{status},#{version},#{totalRequestedQuantity},#{totalDisposedQuantity},#{lineCount},
               #{requestedByPrincipalId},#{createdAt},#{updatedAt})
            """)
    int insertDocument(InventoryScrapDocumentDO row);

    @Select("""
            SELECT * FROM cloudmold_inventory_scrap_document
            WHERE tenant_id=#{tenantId} AND scrap_id=#{scrapId}
            """)
    InventoryScrapDocumentDO selectDocument(@Param("tenantId") Long tenantId, @Param("scrapId") String scrapId);

    @Select("""
            SELECT * FROM cloudmold_inventory_scrap_document
            WHERE tenant_id=#{tenantId} AND scrap_id=#{scrapId}
            FOR UPDATE
            """)
    InventoryScrapDocumentDO selectDocumentForUpdate(@Param("tenantId") Long tenantId, @Param("scrapId") String scrapId);

    @Insert("""
            INSERT INTO cloudmold_inventory_scrap_line
              (line_id,tenant_id,scrap_id,line_number,canonical_sku_id,location_id,lot_id,stock_status,
               quality_status,base_uom_code,requested_quantity,disposed_quantity,evidence_type,evidence_ref,status,
               version,remark,created_at,updated_at)
            VALUES
              (#{lineId},#{tenantId},#{scrapId},#{lineNumber},#{canonicalSkuId},#{locationId},#{lotId},
               #{stockStatus},#{qualityStatus},#{baseUomCode},#{requestedQuantity},#{disposedQuantity},
               #{evidenceType},#{evidenceRef},#{status},#{version},#{remark},#{createdAt},#{updatedAt})
            """)
    int insertLine(InventoryScrapLineDO row);

    @Select("""
            SELECT * FROM cloudmold_inventory_scrap_line
            WHERE tenant_id=#{tenantId} AND scrap_id=#{scrapId}
            ORDER BY line_number ASC, line_id ASC
            """)
    List<InventoryScrapLineDO> selectLines(@Param("tenantId") Long tenantId, @Param("scrapId") String scrapId);

    @Select("""
            SELECT * FROM cloudmold_inventory_scrap_line
            WHERE tenant_id=#{tenantId} AND scrap_id=#{scrapId} AND line_number=#{lineNumber}
            FOR UPDATE
            """)
    InventoryScrapLineDO selectLineForUpdate(@Param("tenantId") Long tenantId,
                                             @Param("scrapId") String scrapId,
                                             @Param("lineNumber") Integer lineNumber);

    @Update("""
            UPDATE cloudmold_inventory_scrap_line
            SET status=#{status},version=#{nextVersion},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND line_id=#{lineId} AND version=#{expectedVersion}
            """)
    int updateLineStatusCas(@Param("tenantId") Long tenantId,
                            @Param("lineId") String lineId,
                            @Param("expectedVersion") Long expectedVersion,
                            @Param("nextVersion") Long nextVersion,
                            @Param("status") String status,
                            @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_inventory_scrap_line
            SET disposed_quantity=#{disposedQuantity},status=#{status},version=#{nextVersion},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND line_id=#{lineId} AND version=#{expectedVersion}
            """)
    int updateLineDispositionCas(@Param("tenantId") Long tenantId,
                                 @Param("lineId") String lineId,
                                 @Param("expectedVersion") Long expectedVersion,
                                 @Param("nextVersion") Long nextVersion,
                                 @Param("disposedQuantity") BigDecimal disposedQuantity,
                                 @Param("status") String status,
                                 @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_inventory_scrap_document
            SET status=#{status},version=#{nextVersion},submitted_by_principal_id=#{actorPrincipalId},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND scrap_id=#{scrapId} AND version=#{expectedVersion}
            """)
    int submitDocumentCas(@Param("tenantId") Long tenantId,
                          @Param("scrapId") String scrapId,
                          @Param("expectedVersion") Long expectedVersion,
                          @Param("nextVersion") Long nextVersion,
                          @Param("status") String status,
                          @Param("actorPrincipalId") String actorPrincipalId,
                          @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_inventory_scrap_document
            SET status=#{status},version=#{nextVersion},approved_by_principal_id=#{actorPrincipalId},
                approved_at=#{now},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND scrap_id=#{scrapId} AND version=#{expectedVersion}
            """)
    int approveDocumentCas(@Param("tenantId") Long tenantId,
                           @Param("scrapId") String scrapId,
                           @Param("expectedVersion") Long expectedVersion,
                           @Param("nextVersion") Long nextVersion,
                           @Param("status") String status,
                           @Param("actorPrincipalId") String actorPrincipalId,
                           @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_inventory_scrap_document
            SET status=#{status},version=#{nextVersion},cancelled_by_principal_id=#{actorPrincipalId},
                cancelled_at=#{now},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND scrap_id=#{scrapId} AND version=#{expectedVersion}
            """)
    int cancelDocumentCas(@Param("tenantId") Long tenantId,
                          @Param("scrapId") String scrapId,
                          @Param("expectedVersion") Long expectedVersion,
                          @Param("nextVersion") Long nextVersion,
                          @Param("status") String status,
                          @Param("actorPrincipalId") String actorPrincipalId,
                          @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_inventory_scrap_document
            SET total_disposed_quantity=#{totalDisposedQuantity},status=#{status},version=#{nextVersion},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND scrap_id=#{scrapId} AND version=#{expectedVersion}
            """)
    int updateDispositionCas(@Param("tenantId") Long tenantId,
                             @Param("scrapId") String scrapId,
                             @Param("expectedVersion") Long expectedVersion,
                             @Param("nextVersion") Long nextVersion,
                             @Param("totalDisposedQuantity") BigDecimal totalDisposedQuantity,
                             @Param("status") String status,
                             @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_inventory_scrap_document
            SET status=#{status},version=#{nextVersion},completed_by_principal_id=#{actorPrincipalId},
                completed_at=#{now},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND scrap_id=#{scrapId} AND version=#{expectedVersion}
            """)
    int completeDocumentCas(@Param("tenantId") Long tenantId,
                            @Param("scrapId") String scrapId,
                            @Param("expectedVersion") Long expectedVersion,
                            @Param("nextVersion") Long nextVersion,
                            @Param("status") String status,
                            @Param("actorPrincipalId") String actorPrincipalId,
                            @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_inventory_scrap_disposition_batch
              (batch_id,tenant_id,scrap_id,batch_no,disposition_type,proof_type,proof_ref,status,
               total_disposed_quantity,line_count,version,executed_by_principal_id,remark,occurred_at,created_at,updated_at)
            VALUES
              (#{batchId},#{tenantId},#{scrapId},#{batchNo},#{dispositionType},#{proofType},#{proofRef},#{status},
               #{totalDisposedQuantity},#{lineCount},#{version},#{executedByPrincipalId},#{remark},#{occurredAt},
               #{createdAt},#{updatedAt})
            """)
    int insertDispositionBatch(InventoryScrapDispositionBatchDO row);

    @Insert("""
            INSERT INTO cloudmold_inventory_scrap_disposition_line
              (disposition_line_id,tenant_id,batch_id,scrap_id,scrap_line_id,line_number,canonical_sku_id,
               location_id,lot_id,stock_status,quality_status,base_uom_code,disposed_quantity,
               cumulative_disposed_quantity,inventory_idempotency_key,inventory_operation_id,
               inventory_ledger_transaction_id,inventory_balance_id,status,version,remark,occurred_at,created_at,updated_at)
            VALUES
              (#{dispositionLineId},#{tenantId},#{batchId},#{scrapId},#{scrapLineId},#{lineNumber},#{canonicalSkuId},
               #{locationId},#{lotId},#{stockStatus},#{qualityStatus},#{baseUomCode},#{disposedQuantity},
               #{cumulativeDisposedQuantity},#{inventoryIdempotencyKey},#{inventoryOperationId},
               #{inventoryLedgerTransactionId},#{inventoryBalanceId},#{status},#{version},#{remark},#{occurredAt},
               #{createdAt},#{updatedAt})
            """)
    int insertDispositionLine(InventoryScrapDispositionLineDO row);

    @Select("""
            SELECT * FROM cloudmold_inventory_scrap_disposition_batch
            WHERE tenant_id=#{tenantId} AND scrap_id=#{scrapId}
            ORDER BY occurred_at ASC, batch_id ASC
            """)
    List<InventoryScrapDispositionBatchDO> selectDispositionBatches(@Param("tenantId") Long tenantId,
                                                                    @Param("scrapId") String scrapId);

    @Select("""
            SELECT * FROM cloudmold_inventory_scrap_disposition_line
            WHERE tenant_id=#{tenantId} AND scrap_id=#{scrapId}
            ORDER BY occurred_at ASC, disposition_line_id ASC
            """)
    List<InventoryScrapDispositionLineDO> selectDispositionLines(@Param("tenantId") Long tenantId,
                                                                 @Param("scrapId") String scrapId);

    @Insert("""
            INSERT INTO cloudmold_inventory_scrap_history
              (tenant_id,scrap_id,status,status_version,actor_principal_id,note,operation_id,changed_at)
            VALUES
              (#{tenantId},#{scrapId},#{status},#{statusVersion},#{actorPrincipalId},#{note},#{operationId},#{changedAt})
            """)
    int insertHistory(InventoryScrapHistoryDO row);

    @Select("""
            SELECT * FROM cloudmold_inventory_scrap_history
            WHERE tenant_id=#{tenantId} AND scrap_id=#{scrapId}
            ORDER BY changed_at ASC, history_id ASC
            """)
    List<InventoryScrapHistoryDO> selectHistory(@Param("tenantId") Long tenantId, @Param("scrapId") String scrapId);
}
