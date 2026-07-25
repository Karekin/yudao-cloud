package cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryV3BalanceDO;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Mapper
public interface InventoryV3BalanceMapper extends BaseMapperX<InventoryV3BalanceDO> {
    @Insert("""
            INSERT INTO cloudmold_inventory_balance_v3
              (balance_id,tenant_id,owner_type,owner_id,canonical_sku_id,warehouse_id,location_id,lot_id,
               stock_status,quality_status,base_uom_code,on_hand_quantity,reserved_quantity,in_transit_quantity,
               version,created_at,updated_at)
            VALUES
              (#{balanceId},#{tenantId},#{ownerType},#{ownerId},#{skuId},#{warehouseId},#{locationId},#{lotId},
               #{stockStatus},#{qualityStatus},#{uomCode},0,0,0,0,#{now},#{now})
            ON DUPLICATE KEY UPDATE balance_id=balance_id
            """)
    int insertOrResolve(@Param("balanceId") String balanceId, @Param("tenantId") Long tenantId,
                        @Param("ownerType") String ownerType, @Param("ownerId") String ownerId,
                        @Param("skuId") String skuId, @Param("warehouseId") String warehouseId,
                        @Param("locationId") String locationId, @Param("lotId") String lotId,
                        @Param("stockStatus") String stockStatus, @Param("qualityStatus") String qualityStatus,
                        @Param("uomCode") String uomCode, @Param("now") LocalDateTime now);

    @Select("""
            SELECT * FROM cloudmold_inventory_balance_v3
            WHERE tenant_id=#{tenantId} AND owner_type=#{ownerType} AND owner_id=#{ownerId}
              AND canonical_sku_id=#{skuId} AND warehouse_id=#{warehouseId} AND location_id=#{locationId}
              AND (lot_id=#{lotId} OR (lot_id IS NULL AND #{lotId} IS NULL))
              AND stock_status=#{stockStatus} AND quality_status=#{qualityStatus}
            FOR UPDATE
            """)
    InventoryV3BalanceDO selectDimensionForUpdate(@Param("tenantId") Long tenantId,
                                                   @Param("ownerType") String ownerType,
                                                   @Param("ownerId") String ownerId,
                                                   @Param("skuId") String skuId,
                                                   @Param("warehouseId") String warehouseId,
                                                   @Param("locationId") String locationId,
                                                   @Param("lotId") String lotId,
                                                   @Param("stockStatus") String stockStatus,
                                                   @Param("qualityStatus") String qualityStatus);

    @Select("SELECT * FROM cloudmold_inventory_balance_v3 WHERE tenant_id=#{tenantId} AND balance_id=#{balanceId} FOR UPDATE")
    InventoryV3BalanceDO selectByIdForUpdate(@Param("tenantId") Long tenantId, @Param("balanceId") String balanceId);

    @Update("""
            UPDATE cloudmold_inventory_balance_v3
            SET on_hand_quantity=#{onHand},reserved_quantity=#{reserved},in_transit_quantity=#{inTransit},
                version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND balance_id=#{balanceId} AND version=#{expectedVersion}
            """)
    int updateBalanceCas(@Param("tenantId") Long tenantId, @Param("balanceId") String balanceId,
                         @Param("expectedVersion") Long expectedVersion,
                         @Param("onHand") BigDecimal onHand, @Param("reserved") BigDecimal reserved,
                         @Param("inTransit") BigDecimal inTransit, @Param("now") LocalDateTime now);

    @Select("""
            SELECT #{skuId} AS canonical_sku_id,
                   COALESCE(SUM(GREATEST(b.on_hand_quantity-b.reserved_quantity,0)),0) AS allocatable_quantity,
                   MAX(b.base_uom_code) AS base_uom_code,
                   COALESCE(MAX(b.version),0) AS inventory_version,
                   COUNT(*) AS balance_count,
                   COUNT(DISTINCT b.base_uom_code) AS uom_count
            FROM cloudmold_inventory_balance_v3 b
            LEFT JOIN cloudmold_inventory_lot l
              ON l.tenant_id=b.tenant_id AND l.lot_id=b.lot_id
            WHERE b.tenant_id=#{tenantId} AND b.canonical_sku_id=#{skuId}
              AND b.stock_status='SELLABLE' AND b.quality_status='QUALIFIED'
              AND (b.lot_id IS NULL OR (
                    l.status='ACTIVE'
                    AND (l.expires_on IS NULL OR l.expires_on >= #{eligibilityDate})
              ))
            """)
    cn.iocoder.yudao.module.cloudmold.inventory.api.InventorySkuAvailabilityView selectSkuAvailability(
            @Param("tenantId") Long tenantId,
            @Param("skuId") String skuId,
            @Param("eligibilityDate") java.time.LocalDate eligibilityDate);

    @Select("""
            SELECT b.*
            FROM cloudmold_inventory_balance_v3 b
            LEFT JOIN cloudmold_inventory_lot l
              ON l.tenant_id=b.tenant_id AND l.lot_id=b.lot_id
            WHERE b.tenant_id=#{tenantId} AND b.canonical_sku_id=#{skuId}
              AND b.stock_status='SELLABLE' AND b.quality_status='QUALIFIED'
              AND b.on_hand_quantity-b.reserved_quantity >= #{quantity}
              AND (b.lot_id IS NULL OR (
                    l.status='ACTIVE'
                    AND (l.expires_on IS NULL OR l.expires_on >= #{eligibilityDate})
              ))
            ORDER BY CASE WHEN l.expires_on IS NULL THEN 1 ELSE 0 END,
                     l.expires_on ASC,b.updated_at ASC,b.balance_id ASC
            LIMIT 1
            """)
    InventoryV3BalanceDO selectReservationCandidate(@Param("tenantId") Long tenantId,
                                                     @Param("skuId") String skuId,
                                                     @Param("quantity") BigDecimal quantity,
                                                     @Param("eligibilityDate") java.time.LocalDate eligibilityDate);
}
