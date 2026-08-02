package cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.*;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface StockCountStoreMapper extends BaseMapperX<StockCountDO> {

    @Insert("""
            INSERT INTO cloudmold_stock_count_operation
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
            SELECT * FROM cloudmold_stock_count_operation
            WHERE tenant_id=#{tenantId} AND operation_id=#{operationId}
            FOR UPDATE
            """)
    StockCountOperationDO selectOperationForUpdate(@Param("operationId") Long operationId,
                                                   @Param("tenantId") Long tenantId);

    @Update("""
            UPDATE cloudmold_stock_count_operation
            SET status=10,aggregate_id=#{aggregateId},result_json=#{resultJson},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND operation_id=#{operationId}
            """)
    int markOperationSucceeded(@Param("operationId") Long operationId, @Param("tenantId") Long tenantId,
                               @Param("aggregateId") String aggregateId, @Param("resultJson") String resultJson,
                               @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_stock_count
              (stock_count_id,tenant_id,stock_count_code,count_mode,scope_type,scope_label,source_business_type,source_business_ref,
               reason_code,remark,status,version,freeze_ledger_transaction_id,freeze_captured_at,line_count,counted_line_count,
               difference_line_count,created_by_principal_id,created_at,updated_at)
            VALUES
              (#{stockCountId},#{tenantId},#{stockCountCode},#{countMode},#{scopeType},#{scopeLabel},#{sourceBusinessType},#{sourceBusinessRef},
               #{reasonCode},#{remark},#{status},#{version},#{freezeLedgerTransactionId},#{freezeCapturedAt},#{lineCount},#{countedLineCount},
               #{differenceLineCount},#{createdByPrincipalId},#{createdAt},#{updatedAt})
            """)
    int insertStockCount(StockCountDO stockCount);

    @Select("""
            SELECT * FROM cloudmold_stock_count
            WHERE tenant_id=#{tenantId} AND source_business_type=#{sourceBusinessType} AND source_business_ref=#{sourceBusinessRef}
            """)
    StockCountDO selectBySourceBusiness(@Param("tenantId") Long tenantId,
                                        @Param("sourceBusinessType") String sourceBusinessType,
                                        @Param("sourceBusinessRef") String sourceBusinessRef);

    @Select("""
            SELECT * FROM cloudmold_stock_count
            WHERE tenant_id=#{tenantId} AND stock_count_id=#{stockCountId}
            """)
    StockCountDO selectStockCount(@Param("tenantId") Long tenantId, @Param("stockCountId") String stockCountId);

    @Select("""
            SELECT * FROM cloudmold_stock_count
            WHERE tenant_id=#{tenantId} AND stock_count_id=#{stockCountId}
            FOR UPDATE
            """)
    StockCountDO selectStockCountForUpdate(@Param("tenantId") Long tenantId, @Param("stockCountId") String stockCountId);

    @Insert("""
            INSERT INTO cloudmold_stock_count_line
              (line_id,tenant_id,stock_count_id,line_number,owner_type,owner_id,canonical_sku_id,warehouse_id,location_id,lot_id,
               stock_status,quality_status,base_uom_code,balance_id,book_on_hand_quantity,book_reserved_quantity,book_in_transit_quantity,
               book_available_quantity,book_aggregate_version,counted_on_hand_quantity,difference_quantity,count_status,counted_by_principal_id,
               counted_at,adjustment_id,adjustment_ledger_transaction_id,adjusted_aggregate_version,remark,version,freeze_captured_at,created_at,updated_at)
            VALUES
              (#{lineId},#{tenantId},#{stockCountId},#{lineNumber},#{ownerType},#{ownerId},#{canonicalSkuId},#{warehouseId},#{locationId},#{lotId},
               #{stockStatus},#{qualityStatus},#{baseUomCode},#{balanceId},#{bookOnHandQuantity},#{bookReservedQuantity},#{bookInTransitQuantity},
               #{bookAvailableQuantity},#{bookAggregateVersion},#{countedOnHandQuantity},#{differenceQuantity},#{countStatus},#{countedByPrincipalId},
               #{countedAt},#{adjustmentId},#{adjustmentLedgerTransactionId},#{adjustedAggregateVersion},#{remark},#{version},#{freezeCapturedAt},
               #{createdAt},#{updatedAt})
            """)
    int insertLine(StockCountLineDO line);

    @Select("""
            SELECT * FROM cloudmold_stock_count_line
            WHERE tenant_id=#{tenantId} AND stock_count_id=#{stockCountId}
            ORDER BY line_number ASC
            """)
    List<StockCountLineDO> selectLines(@Param("tenantId") Long tenantId, @Param("stockCountId") String stockCountId);

    @Select("""
            SELECT * FROM cloudmold_stock_count_line
            WHERE tenant_id=#{tenantId} AND line_id=#{lineId}
            FOR UPDATE
            """)
    StockCountLineDO selectLineForUpdate(@Param("tenantId") Long tenantId, @Param("lineId") String lineId);

    @Update("""
            UPDATE cloudmold_stock_count_line
            SET balance_id=#{balanceId},book_on_hand_quantity=#{bookOnHandQuantity},book_reserved_quantity=#{bookReservedQuantity},
                book_in_transit_quantity=#{bookInTransitQuantity},book_available_quantity=#{bookAvailableQuantity},
                book_aggregate_version=#{bookAggregateVersion},count_status=#{countStatus},freeze_captured_at=#{freezeCapturedAt},
                version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND line_id=#{lineId} AND version=#{expectedVersion}
            """)
    int updateLineSnapshotCas(@Param("tenantId") Long tenantId, @Param("lineId") String lineId,
                              @Param("expectedVersion") Long expectedVersion, @Param("balanceId") String balanceId,
                              @Param("bookOnHandQuantity") BigDecimal bookOnHandQuantity,
                              @Param("bookReservedQuantity") BigDecimal bookReservedQuantity,
                              @Param("bookInTransitQuantity") BigDecimal bookInTransitQuantity,
                              @Param("bookAvailableQuantity") BigDecimal bookAvailableQuantity,
                              @Param("bookAggregateVersion") Long bookAggregateVersion,
                              @Param("countStatus") String countStatus,
                              @Param("freezeCapturedAt") LocalDateTime freezeCapturedAt,
                              @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_stock_count_line
            SET counted_on_hand_quantity=#{countedOnHandQuantity},difference_quantity=#{differenceQuantity},
                count_status=#{countStatus},counted_by_principal_id=#{countedByPrincipalId},counted_at=#{countedAt},
                remark=#{remark},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND line_id=#{lineId} AND version=#{expectedVersion}
            """)
    int updateLineCountCas(@Param("tenantId") Long tenantId, @Param("lineId") String lineId,
                           @Param("expectedVersion") Long expectedVersion,
                           @Param("countedOnHandQuantity") BigDecimal countedOnHandQuantity,
                           @Param("differenceQuantity") BigDecimal differenceQuantity,
                           @Param("countStatus") String countStatus,
                           @Param("countedByPrincipalId") String countedByPrincipalId,
                           @Param("countedAt") LocalDateTime countedAt,
                           @Param("remark") String remark,
                           @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_stock_count_line
            SET adjustment_id=#{adjustmentId},adjustment_ledger_transaction_id=#{adjustmentLedgerTransactionId},
                adjusted_aggregate_version=#{adjustedAggregateVersion},count_status='ADJUSTED',version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND line_id=#{lineId} AND version=#{expectedVersion}
            """)
    int updateLineAdjustmentCas(@Param("tenantId") Long tenantId, @Param("lineId") String lineId,
                                @Param("expectedVersion") Long expectedVersion,
                                @Param("adjustmentId") String adjustmentId,
                                @Param("adjustmentLedgerTransactionId") Long adjustmentLedgerTransactionId,
                                @Param("adjustedAggregateVersion") Long adjustedAggregateVersion,
                                @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_stock_count_execution_batch
              (batch_id,tenant_id,stock_count_id,batch_no,status,line_count,counted_by_principal_id,remark,version,occurred_at,created_at,updated_at)
            VALUES
              (#{batchId},#{tenantId},#{stockCountId},#{batchNo},#{status},#{lineCount},#{countedByPrincipalId},#{remark},#{version},#{occurredAt},#{createdAt},#{updatedAt})
            """)
    int insertExecutionBatch(StockCountExecutionBatchDO batch);

    @Insert("""
            INSERT INTO cloudmold_stock_count_execution_line
              (execution_line_id,tenant_id,batch_id,stock_count_id,stock_count_line_id,counted_on_hand_quantity,difference_quantity,remark,created_at)
            VALUES
              (#{executionLineId},#{tenantId},#{batchId},#{stockCountId},#{stockCountLineId},#{countedOnHandQuantity},#{differenceQuantity},#{remark},#{createdAt})
            """)
    int insertExecutionLine(StockCountExecutionLineDO line);

    @Select("""
            SELECT * FROM cloudmold_stock_count_execution_batch
            WHERE tenant_id=#{tenantId} AND stock_count_id=#{stockCountId}
            ORDER BY occurred_at ASC,batch_id ASC
            """)
    List<StockCountExecutionBatchDO> selectExecutionBatches(@Param("tenantId") Long tenantId,
                                                            @Param("stockCountId") String stockCountId);

    @Select("""
            SELECT * FROM cloudmold_stock_count_execution_line
            WHERE tenant_id=#{tenantId} AND stock_count_id=#{stockCountId}
            ORDER BY created_at ASC,execution_line_id ASC
            """)
    List<StockCountExecutionLineDO> selectExecutionLines(@Param("tenantId") Long tenantId,
                                                         @Param("stockCountId") String stockCountId);

    @Insert("""
            INSERT INTO cloudmold_stock_count_difference_approval
              (approval_id,tenant_id,stock_count_id,approval_type,approved_by_principal_id,total_book_on_hand_quantity,
               total_counted_on_hand_quantity,total_difference_quantity,remark,approved_at,created_at)
            VALUES
              (#{approvalId},#{tenantId},#{stockCountId},#{approvalType},#{approvedByPrincipalId},#{totalBookOnHandQuantity},
               #{totalCountedOnHandQuantity},#{totalDifferenceQuantity},#{remark},#{approvedAt},#{createdAt})
            """)
    int insertApproval(StockCountDifferenceApprovalDO approval);

    @Select("""
            SELECT * FROM cloudmold_stock_count_difference_approval
            WHERE tenant_id=#{tenantId} AND stock_count_id=#{stockCountId}
            ORDER BY approved_at ASC,approval_id ASC
            """)
    List<StockCountDifferenceApprovalDO> selectApprovals(@Param("tenantId") Long tenantId,
                                                         @Param("stockCountId") String stockCountId);

    @Insert("""
            INSERT INTO cloudmold_stock_count_status_history
              (history_id,tenant_id,operation_id,stock_count_id,status,status_version,stage_code,stage_label,changed_by_principal_id,remark,changed_at,created_at)
            VALUES
              (#{historyId},#{tenantId},#{operationId},#{stockCountId},#{status},#{statusVersion},#{stageCode},#{stageLabel},#{changedByPrincipalId},#{remark},#{changedAt},#{createdAt})
            """)
    int insertStatusHistory(StockCountStatusHistoryDO history);

    @Select("""
            SELECT * FROM cloudmold_stock_count_status_history
            WHERE tenant_id=#{tenantId} AND stock_count_id=#{stockCountId}
            ORDER BY status_version ASC,changed_at ASC,history_id ASC
            """)
    List<StockCountStatusHistoryDO> selectStatusHistory(@Param("tenantId") Long tenantId,
                                                        @Param("stockCountId") String stockCountId);

    @Update("""
            UPDATE cloudmold_stock_count
            SET status=#{status},version=#{nextVersion},freeze_ledger_transaction_id=#{freezeLedgerTransactionId},
                freeze_captured_at=#{freezeCapturedAt},line_count=#{lineCount},counted_line_count=#{countedLineCount},
                difference_line_count=#{differenceLineCount},remark=#{remark},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND stock_count_id=#{stockCountId} AND version=#{expectedVersion}
            """)
    int updateStockCountCas(@Param("tenantId") Long tenantId, @Param("stockCountId") String stockCountId,
                            @Param("expectedVersion") Long expectedVersion, @Param("nextVersion") Long nextVersion,
                            @Param("status") String status,
                            @Param("freezeLedgerTransactionId") Long freezeLedgerTransactionId,
                            @Param("freezeCapturedAt") LocalDateTime freezeCapturedAt,
                            @Param("lineCount") Integer lineCount,
                            @Param("countedLineCount") Integer countedLineCount,
                            @Param("differenceLineCount") Integer differenceLineCount,
                            @Param("remark") String remark,
                            @Param("now") LocalDateTime now);

    @Select("""
            SELECT COALESCE(MAX(ledger_transaction_id),0)
            FROM cloudmold_inventory_ledger_transaction_v3
            WHERE tenant_id=#{tenantId}
            """)
    Long selectFreezeLedgerTransactionId(@Param("tenantId") Long tenantId);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM cloudmold_stock_count sc
            WHERE sc.tenant_id=#{tenantId}
              <if test="keyword != null and keyword != ''">
                AND (
                  sc.stock_count_code LIKE CONCAT('%',#{keyword},'%')
                  OR EXISTS (
                    SELECT 1 FROM cloudmold_stock_count_line l
                    WHERE l.tenant_id=sc.tenant_id AND l.stock_count_id=sc.stock_count_id
                      AND (
                        l.canonical_sku_id LIKE CONCAT('%',#{keyword},'%')
                        OR l.warehouse_id LIKE CONCAT('%',#{keyword},'%')
                        OR l.location_id LIKE CONCAT('%',#{keyword},'%')
                      )
                  )
                )
              </if>
              <if test="status != null and status != ''">
                AND sc.status=#{status}
              </if>
              <if test="countMode != null and countMode != ''">
                AND sc.count_mode=#{countMode}
              </if>
            </script>
            """)
    long countPage(@Param("tenantId") Long tenantId, @Param("keyword") String keyword,
                   @Param("status") String status, @Param("countMode") String countMode);

    @Select("""
            <script>
            SELECT sc.stock_count_id,sc.stock_count_code,sc.count_mode,sc.scope_type,sc.scope_label,
                   sc.source_business_type,sc.source_business_ref,sc.status,sc.version,sc.line_count,
                   sc.counted_line_count,sc.difference_line_count,sc.freeze_ledger_transaction_id,
                   sc.freeze_captured_at,sc.updated_at
            FROM cloudmold_stock_count sc
            WHERE sc.tenant_id=#{tenantId}
              <if test="keyword != null and keyword != ''">
                AND (
                  sc.stock_count_code LIKE CONCAT('%',#{keyword},'%')
                  OR EXISTS (
                    SELECT 1 FROM cloudmold_stock_count_line l
                    WHERE l.tenant_id=sc.tenant_id AND l.stock_count_id=sc.stock_count_id
                      AND (
                        l.canonical_sku_id LIKE CONCAT('%',#{keyword},'%')
                        OR l.warehouse_id LIKE CONCAT('%',#{keyword},'%')
                        OR l.location_id LIKE CONCAT('%',#{keyword},'%')
                      )
                  )
                )
              </if>
              <if test="status != null and status != ''">
                AND sc.status=#{status}
              </if>
              <if test="countMode != null and countMode != ''">
                AND sc.count_mode=#{countMode}
              </if>
            ORDER BY sc.updated_at DESC,sc.stock_count_id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<cn.iocoder.yudao.module.cloudmold.warehouse.service.query.StockCountPageItem> selectPage(
            @Param("tenantId") Long tenantId, @Param("keyword") String keyword, @Param("status") String status,
            @Param("countMode") String countMode, @Param("limit") int limit, @Param("offset") int offset);
}
