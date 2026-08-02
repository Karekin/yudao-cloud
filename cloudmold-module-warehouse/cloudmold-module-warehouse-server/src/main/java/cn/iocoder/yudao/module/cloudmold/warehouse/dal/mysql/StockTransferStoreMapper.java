package cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.*;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface StockTransferStoreMapper extends BaseMapperX<StockTransferRequestDO> {

    @Insert("""
            INSERT INTO cloudmold_stock_transfer_operation
              (tenant_id,idempotency_key,source_event_id,operation_type,request_hash,attempt_token,status,created_at,updated_at)
            VALUES (#{tenantId},#{idempotencyKey},#{sourceEventId},#{operationType},#{requestHash},#{attemptToken},0,#{now},#{now})
            ON DUPLICATE KEY UPDATE operation_id = LAST_INSERT_ID(operation_id)
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
            SELECT * FROM cloudmold_stock_transfer_operation
            WHERE operation_id=#{operationId} AND tenant_id=#{tenantId}
            FOR UPDATE
            """)
    StockTransferOperationDO selectOperationForUpdate(@Param("operationId") Long operationId,
                                                      @Param("tenantId") Long tenantId);

    @Update("""
            UPDATE cloudmold_stock_transfer_operation
            SET status=10,aggregate_id=#{aggregateId},result_json=#{resultJson},updated_at=#{now}
            WHERE operation_id=#{operationId} AND tenant_id=#{tenantId} AND status=0
            """)
    int markOperationSucceeded(@Param("operationId") Long operationId,
                               @Param("tenantId") Long tenantId,
                               @Param("aggregateId") String aggregateId,
                               @Param("resultJson") String resultJson,
                               @Param("now") LocalDateTime now);

    @Select("""
            SELECT * FROM cloudmold_stock_transfer_request
            WHERE tenant_id=#{tenantId}
              AND source_business_type=#{sourceBusinessType}
              AND source_business_ref=#{sourceBusinessRef}
            """)
    StockTransferRequestDO selectRequestBySourceBusiness(@Param("tenantId") Long tenantId,
                                                         @Param("sourceBusinessType") String sourceBusinessType,
                                                         @Param("sourceBusinessRef") String sourceBusinessRef);

    @Select("""
            SELECT * FROM cloudmold_stock_transfer_request
            WHERE tenant_id=#{tenantId} AND request_id=#{requestId}
            """)
    StockTransferRequestDO selectRequestByRequestId(@Param("tenantId") Long tenantId,
                                                    @Param("requestId") String requestId);

    @Insert("""
            INSERT INTO cloudmold_stock_transfer_request
              (request_id,tenant_id,request_code,source_business_type,source_business_ref,owner_type,owner_id,
               source_warehouse_id,target_warehouse_id,reason_code,remark,status,version,approved_at,created_at,updated_at)
            VALUES
              (#{requestId},#{tenantId},#{requestCode},#{sourceBusinessType},#{sourceBusinessRef},#{ownerType},#{ownerId},
               #{sourceWarehouseId},#{targetWarehouseId},#{reasonCode},#{remark},#{status},#{version},#{approvedAt},
               #{createdAt},#{updatedAt})
            """)
    int insertRequest(StockTransferRequestDO row);

    @Insert("""
            INSERT INTO cloudmold_stock_transfer_request_line
              (line_id,tenant_id,request_id,line_number,canonical_sku_id,requested_quantity,uom_code,remark,created_at,updated_at)
            VALUES
              (#{lineId},#{tenantId},#{requestId},#{lineNumber},#{canonicalSkuId},#{requestedQuantity},#{uomCode},
               #{remark},#{createdAt},#{updatedAt})
            """)
    int insertRequestLine(StockTransferRequestLineDO row);

    @Insert("""
            INSERT INTO cloudmold_stock_transfer_order
              (order_id,tenant_id,request_id,order_code,owner_type,owner_id,source_warehouse_id,target_warehouse_id,
               status,version,prepared_at,created_at,updated_at)
            VALUES
              (#{orderId},#{tenantId},#{requestId},#{orderCode},#{ownerType},#{ownerId},#{sourceWarehouseId},
               #{targetWarehouseId},#{status},#{version},#{preparedAt},#{createdAt},#{updatedAt})
            """)
    int insertOrder(StockTransferOrderDO row);

    @Insert("""
            INSERT INTO cloudmold_stock_transfer_order_line
              (line_id,tenant_id,order_id,line_number,canonical_sku_id,requested_quantity,uom_code,remark,created_at,updated_at)
            VALUES
              (#{lineId},#{tenantId},#{orderId},#{lineNumber},#{canonicalSkuId},#{requestedQuantity},#{uomCode},
               #{remark},#{createdAt},#{updatedAt})
            """)
    int insertOrderLine(StockTransferOrderLineDO row);

    @Insert("""
            INSERT INTO cloudmold_stock_transfer_status_history
              (history_id,tenant_id,operation_id,business_object_type,business_object_id,status,status_version,
               stage_code,stage_label,changed_at,created_at)
            VALUES
              (#{historyId},#{tenantId},#{operationId},#{businessObjectType},#{businessObjectId},#{status},
               #{statusVersion},#{stageCode},#{stageLabel},#{changedAt},#{createdAt})
            """)
    int insertStatusHistory(StockTransferStatusHistoryDO row);

    @Select("""
            SELECT * FROM cloudmold_stock_transfer_order
            WHERE tenant_id=#{tenantId} AND request_id=#{requestId}
            """)
    StockTransferOrderDO selectOrderByRequestId(@Param("tenantId") Long tenantId,
                                                @Param("requestId") String requestId);

    @Select("""
            SELECT * FROM cloudmold_stock_transfer_request_line
            WHERE tenant_id=#{tenantId} AND request_id=#{requestId}
            ORDER BY line_number ASC, line_id ASC
            """)
    List<StockTransferRequestLineDO> selectRequestLines(@Param("tenantId") Long tenantId,
                                                        @Param("requestId") String requestId);

    @Select("""
            SELECT * FROM cloudmold_stock_transfer_status_history
            WHERE tenant_id=#{tenantId}
              AND (business_object_id=#{requestId} OR business_object_id=#{orderId})
            ORDER BY changed_at ASC, history_id ASC
            """)
    List<StockTransferStatusHistoryDO> selectStatusHistory(@Param("tenantId") Long tenantId,
                                                           @Param("requestId") String requestId,
                                                           @Param("orderId") String orderId);
}
