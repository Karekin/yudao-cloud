package cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryScrapDispositionResult;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryScrapDispositionOperationDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface InventoryScrapDispositionOperationMapper extends BaseMapperX<InventoryScrapDispositionOperationDO> {

    @Insert("""
            INSERT INTO cloudmold_inventory_scrap_disposition_operation_v1
              (tenant_id,idempotency_key,source_event_id,disposition_line_id,scrap_document_id,scrap_line_id,
               disposition_batch_id,request_hash,attempt_token,status,created_at,updated_at)
            VALUES
              (#{tenantId},#{idempotencyKey},#{sourceEventId},#{dispositionLineId},#{scrapDocumentId},#{scrapLineId},
               #{dispositionBatchId},#{requestHash},#{attemptToken},0,#{now},#{now})
            ON DUPLICATE KEY UPDATE operation_id=LAST_INSERT_ID(operation_id)
            """)
    int insertOrResolve(@Param("tenantId") Long tenantId,
                        @Param("idempotencyKey") String idempotencyKey,
                        @Param("sourceEventId") String sourceEventId,
                        @Param("dispositionLineId") String dispositionLineId,
                        @Param("scrapDocumentId") String scrapDocumentId,
                        @Param("scrapLineId") String scrapLineId,
                        @Param("dispositionBatchId") String dispositionBatchId,
                        @Param("requestHash") String requestHash,
                        @Param("attemptToken") String attemptToken,
                        @Param("now") LocalDateTime now);

    @Select("SELECT LAST_INSERT_ID()")
    Long selectLastInsertId();

    @Select("""
            SELECT * FROM cloudmold_inventory_scrap_disposition_operation_v1
            WHERE tenant_id=#{tenantId} AND operation_id=#{operationId}
            FOR UPDATE
            """)
    InventoryScrapDispositionOperationDO selectForUpdate(@Param("tenantId") Long tenantId,
                                                         @Param("operationId") Long operationId);

    @Update("""
            UPDATE cloudmold_inventory_scrap_disposition_operation_v1
            SET status=10,ledger_transaction_id=#{result.ledgerTransactionId},balance_id=#{result.balanceId},
                aggregate_version=#{result.aggregateVersion},on_hand_quantity=#{result.onHandQuantity},
                disposed_quantity=#{result.disposedQuantity},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND operation_id=#{operationId} AND status=0
            """)
    int markSucceeded(@Param("tenantId") Long tenantId,
                      @Param("operationId") Long operationId,
                      @Param("result") InventoryScrapDispositionResult result,
                      @Param("now") LocalDateTime now);
}
