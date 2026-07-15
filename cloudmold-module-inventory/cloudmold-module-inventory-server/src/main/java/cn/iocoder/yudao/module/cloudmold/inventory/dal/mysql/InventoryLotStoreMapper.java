package cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.*;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface InventoryLotStoreMapper {

    @Insert("""
            INSERT INTO cloudmold_inventory_lot_operation
              (tenant_id,idempotency_key,source_event_id,command_type,request_hash,attempt_token,status,created_at,updated_at)
            VALUES (#{tenantId},#{idempotencyKey},#{sourceEventId},#{commandType},#{requestHash},#{attemptToken},0,#{now},#{now})
            ON DUPLICATE KEY UPDATE operation_id=LAST_INSERT_ID(operation_id)
            """)
    int insertOrResolveOperation(@Param("tenantId") Long tenantId,
                                 @Param("idempotencyKey") String idempotencyKey,
                                 @Param("sourceEventId") String sourceEventId,
                                 @Param("commandType") String commandType,
                                 @Param("requestHash") String requestHash,
                                 @Param("attemptToken") String attemptToken,
                                 @Param("now") LocalDateTime now);

    @Select("SELECT LAST_INSERT_ID()")
    Long selectLastInsertId();

    @Select("""
            SELECT operation_id,tenant_id,idempotency_key,source_event_id,command_type,request_hash,attempt_token,
                   status,lot_id,mapping_id,result_json,created_at,updated_at
            FROM cloudmold_inventory_lot_operation
            WHERE operation_id=#{operationId} AND tenant_id=#{tenantId}
            FOR UPDATE
            """)
    InventoryLotOperationDO selectOperationForUpdate(@Param("operationId") Long operationId,
                                                       @Param("tenantId") Long tenantId);

    @Update("""
            UPDATE cloudmold_inventory_lot_operation
            SET status=10,lot_id=#{lotId},mapping_id=#{mappingId},result_json=CAST(#{resultJson} AS JSON),updated_at=#{now}
            WHERE operation_id=#{operationId} AND tenant_id=#{tenantId} AND status=0
            """)
    int markOperationSucceeded(@Param("operationId") Long operationId,
                               @Param("tenantId") Long tenantId,
                               @Param("lotId") String lotId,
                               @Param("mappingId") String mappingId,
                               @Param("resultJson") String resultJson,
                               @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_inventory_lot
              (lot_id,tenant_id,owner_type,owner_id,canonical_sku_id,lot_code,manufactured_on,expires_on,
               received_at,status,version,created_at,updated_at)
            VALUES (#{lotId},#{tenantId},#{ownerType},#{ownerId},#{canonicalSkuId},#{lotCode},#{manufacturedOn},
                    #{expiresOn},#{receivedAt},#{status},#{version},#{createdAt},#{updatedAt})
            """)
    int insertLot(InventoryLotDO value);

    @Select("""
            SELECT lot_id,tenant_id,owner_type,owner_id,canonical_sku_id,lot_code,manufactured_on,expires_on,
                   received_at,status,version,created_at,updated_at
            FROM cloudmold_inventory_lot
            WHERE tenant_id=#{tenantId} AND lot_id=#{lotId}
            FOR UPDATE
            """)
    InventoryLotDO selectLotForUpdate(@Param("tenantId") Long tenantId, @Param("lotId") String lotId);

    @Select("""
            SELECT lot_id,tenant_id,owner_type,owner_id,canonical_sku_id,lot_code,manufactured_on,expires_on,
                   received_at,status,version,created_at,updated_at
            FROM cloudmold_inventory_lot
            WHERE tenant_id=#{tenantId} AND lot_id=#{lotId}
            """)
    InventoryLotDO selectLot(@Param("tenantId") Long tenantId, @Param("lotId") String lotId);

    @Update("""
            UPDATE cloudmold_inventory_lot
            SET status=#{currentStatus},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND lot_id=#{lotId} AND version=#{expectedVersion} AND status=#{previousStatus}
            """)
    int updateLotStatus(@Param("tenantId") Long tenantId,
                        @Param("lotId") String lotId,
                        @Param("expectedVersion") Long expectedVersion,
                        @Param("previousStatus") String previousStatus,
                        @Param("currentStatus") String currentStatus,
                        @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_inventory_lot_source_mapping
              (mapping_id,tenant_id,source_system,source_type,source_id,lot_id,valid_from,valid_to,
               verification_ref,status,version,created_at,updated_at)
            VALUES (#{mappingId},#{tenantId},#{sourceSystem},#{sourceType},#{sourceId},#{lotId},#{validFrom},
                    #{validTo},#{verificationRef},#{status},#{version},#{createdAt},#{updatedAt})
            """)
    int insertMapping(InventoryLotSourceMappingDO value);

    @Select("""
            SELECT mapping_id,tenant_id,source_system,source_type,source_id,lot_id,valid_from,valid_to,
                   verification_ref,status,version,created_at,updated_at
            FROM cloudmold_inventory_lot_source_mapping
            WHERE tenant_id=#{tenantId} AND mapping_id=#{mappingId}
            FOR UPDATE
            """)
    InventoryLotSourceMappingDO selectMappingForUpdate(@Param("tenantId") Long tenantId,
                                                        @Param("mappingId") String mappingId);

    @Select("""
            SELECT mapping_id,tenant_id,source_system,source_type,source_id,lot_id,valid_from,valid_to,
                   verification_ref,status,version,created_at,updated_at
            FROM cloudmold_inventory_lot_source_mapping
            WHERE tenant_id=#{tenantId} AND source_system=#{sourceSystem} AND source_type=#{sourceType}
              AND source_id=#{sourceId} AND valid_from <= #{effectiveAt}
              AND (valid_to IS NULL OR valid_to > #{effectiveAt})
            ORDER BY valid_from DESC
            LIMIT 2
            """)
    List<InventoryLotSourceMappingDO> selectEffectiveMappings(@Param("tenantId") Long tenantId,
                                                               @Param("sourceSystem") String sourceSystem,
                                                               @Param("sourceType") String sourceType,
                                                               @Param("sourceId") String sourceId,
                                                               @Param("effectiveAt") LocalDateTime effectiveAt);

    @Select("""
            SELECT mapping_id,tenant_id,source_system,source_type,source_id,lot_id,valid_from,valid_to,
                   verification_ref,status,version,created_at,updated_at
            FROM cloudmold_inventory_lot_source_mapping
            WHERE tenant_id=#{tenantId} AND source_system=#{sourceSystem} AND source_type=#{sourceType}
              AND source_id=#{sourceId} AND (valid_to IS NULL OR valid_to > #{validFrom})
            ORDER BY valid_from,mapping_id
            FOR UPDATE
            """)
    List<InventoryLotSourceMappingDO> selectOverlappingMappingsForUpdate(
            @Param("tenantId") Long tenantId,
            @Param("sourceSystem") String sourceSystem,
            @Param("sourceType") String sourceType,
            @Param("sourceId") String sourceId,
            @Param("validFrom") LocalDateTime validFrom);

    @Update("""
            UPDATE cloudmold_inventory_lot_source_mapping
            SET status='ENDED',valid_to=#{validTo},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND mapping_id=#{mappingId} AND version=#{expectedVersion}
              AND status='ACTIVE' AND valid_from < #{validTo}
            """)
    int endMapping(@Param("tenantId") Long tenantId,
                   @Param("mappingId") String mappingId,
                   @Param("expectedVersion") Long expectedVersion,
                   @Param("validTo") LocalDateTime validTo,
                   @Param("now") LocalDateTime now);

    @Select("""
            SELECT COUNT(*)
            FROM cloudmold_inventory_balance_v3
            WHERE tenant_id=#{tenantId} AND lot_id=#{lotId}
              AND (on_hand_quantity <> 0 OR reserved_quantity <> 0 OR in_transit_quantity <> 0)
            """)
    long countNonZeroBalances(@Param("tenantId") Long tenantId, @Param("lotId") String lotId);

    @Select("""
            SELECT COUNT(*)
            FROM cloudmold_inventory_reservation_allocation_v3 a
            JOIN cloudmold_inventory_balance_v3 b
              ON b.tenant_id=a.tenant_id AND b.balance_id=a.balance_id
            WHERE a.tenant_id=#{tenantId} AND b.lot_id=#{lotId} AND a.status=10
            """)
    long countActiveAllocations(@Param("tenantId") Long tenantId, @Param("lotId") String lotId);

    @Select("""
            SELECT balance_id
            FROM cloudmold_inventory_balance_v3
            WHERE tenant_id=#{tenantId} AND lot_id=#{lotId}
            ORDER BY balance_id
            FOR UPDATE
            """)
    List<String> lockBalanceIds(@Param("tenantId") Long tenantId, @Param("lotId") String lotId);

    @Select("""
            SELECT a.allocation_id
            FROM cloudmold_inventory_reservation_allocation_v3 a
            JOIN cloudmold_inventory_balance_v3 b
              ON b.tenant_id=a.tenant_id AND b.balance_id=a.balance_id
            WHERE a.tenant_id=#{tenantId} AND b.lot_id=#{lotId} AND a.status=10
            ORDER BY a.allocation_id
            FOR UPDATE
            """)
    List<String> lockActiveAllocationIds(@Param("tenantId") Long tenantId, @Param("lotId") String lotId);

    @Select("""
            SELECT balance_id,owner_type,owner_id,canonical_sku_id,warehouse_id,location_id,lot_id,
                   stock_status,quality_status,base_uom_code,on_hand_quantity,reserved_quantity,
                   in_transit_quantity,version AS aggregate_version
            FROM cloudmold_inventory_balance_v3
            WHERE tenant_id=#{tenantId} AND lot_id=#{lotId}
            ORDER BY warehouse_id,location_id,stock_status,quality_status,base_uom_code,balance_id
            """)
    List<InventoryV3AvailabilityDO> selectAvailabilityByLot(@Param("tenantId") Long tenantId,
                                                             @Param("lotId") String lotId);
}
