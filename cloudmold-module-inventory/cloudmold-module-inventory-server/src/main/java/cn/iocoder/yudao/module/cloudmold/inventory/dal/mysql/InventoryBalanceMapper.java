package cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryBalanceDO;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Mapper
public interface InventoryBalanceMapper extends BaseMapperX<InventoryBalanceDO> {

    @Insert("""
            INSERT INTO cloudmold_inventory_balance
              (balance_id, tenant_id, owner_id, canonical_sku_id, warehouse_id, stock_status,
               quality_status, base_uom_code, on_hand_quantity, reserved_quantity,
               in_transit_quantity, version, created_at, updated_at)
            VALUES
              (#{balanceId}, #{tenantId}, #{ownerId}, #{canonicalSkuId}, #{warehouseId}, #{stockStatus},
               #{qualityStatus}, #{uomCode}, 0, 0, 0, 0, #{now}, #{now})
            ON DUPLICATE KEY UPDATE balance_id = balance_id
            """)
    int insertOrResolve(@Param("balanceId") String balanceId,
                        @Param("tenantId") Long tenantId,
                        @Param("ownerId") String ownerId,
                        @Param("canonicalSkuId") String canonicalSkuId,
                        @Param("warehouseId") String warehouseId,
                        @Param("stockStatus") String stockStatus,
                        @Param("qualityStatus") String qualityStatus,
                        @Param("uomCode") String uomCode,
                        @Param("now") LocalDateTime now);

    @Select("""
            SELECT * FROM cloudmold_inventory_balance
            WHERE tenant_id = #{tenantId} AND owner_id = #{ownerId}
              AND canonical_sku_id = #{canonicalSkuId} AND warehouse_id = #{warehouseId}
              AND stock_status = #{stockStatus} AND quality_status = #{qualityStatus}
            FOR UPDATE
            """)
    InventoryBalanceDO selectDimensionForUpdate(@Param("tenantId") Long tenantId,
                                                 @Param("ownerId") String ownerId,
                                                 @Param("canonicalSkuId") String canonicalSkuId,
                                                 @Param("warehouseId") String warehouseId,
                                                 @Param("stockStatus") String stockStatus,
                                                 @Param("qualityStatus") String qualityStatus);

    @Select("SELECT * FROM cloudmold_inventory_balance WHERE balance_id = #{balanceId} AND tenant_id = #{tenantId} FOR UPDATE")
    InventoryBalanceDO selectByIdForUpdate(@Param("tenantId") Long tenantId,
                                           @Param("balanceId") String balanceId);

    @Update("""
            UPDATE cloudmold_inventory_balance
            SET on_hand_quantity = #{onHand}, reserved_quantity = #{reserved},
                version = version + 1, updated_at = #{now}
            WHERE balance_id = #{balanceId} AND tenant_id = #{tenantId} AND version = #{expectedVersion}
            """)
    int updateBalanceCas(@Param("tenantId") Long tenantId,
                         @Param("balanceId") String balanceId,
                         @Param("expectedVersion") Long expectedVersion,
                         @Param("onHand") BigDecimal onHand,
                         @Param("reserved") BigDecimal reserved,
                         @Param("now") LocalDateTime now);

}
