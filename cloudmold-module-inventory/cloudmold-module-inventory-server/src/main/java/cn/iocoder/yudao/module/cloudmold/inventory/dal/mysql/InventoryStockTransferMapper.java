package cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryStockTransferDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Mapper
public interface InventoryStockTransferMapper extends BaseMapperX<InventoryStockTransferDO> {

    @Insert("""
            INSERT INTO cloudmold_inventory_stock_transfer_v3
              (movement_group_id,tenant_id,owner_type,owner_id,canonical_sku_id,
               source_warehouse_id,source_location_id,target_warehouse_id,target_location_id,lot_id,
               stock_status,quality_status,base_uom_code,dispatched_quantity,received_quantity,
               version,created_operation_id,last_operation_id,created_at,updated_at)
            VALUES
              (#{movementGroupId},#{tenantId},#{ownerType},#{ownerId},#{skuId},
               #{sourceWarehouseId},#{sourceLocationId},#{targetWarehouseId},#{targetLocationId},#{lotId},
               #{stockStatus},#{qualityStatus},#{uomCode},0,0,0,#{operationId},#{operationId},#{now},#{now})
            ON DUPLICATE KEY UPDATE movement_group_id=movement_group_id
            """)
    int insertOrResolve(@Param("movementGroupId") String movementGroupId, @Param("tenantId") Long tenantId,
                        @Param("ownerType") String ownerType, @Param("ownerId") String ownerId,
                        @Param("skuId") String skuId,
                        @Param("sourceWarehouseId") String sourceWarehouseId,
                        @Param("sourceLocationId") String sourceLocationId,
                        @Param("targetWarehouseId") String targetWarehouseId,
                        @Param("targetLocationId") String targetLocationId,
                        @Param("lotId") String lotId, @Param("stockStatus") String stockStatus,
                        @Param("qualityStatus") String qualityStatus, @Param("uomCode") String uomCode,
                        @Param("operationId") Long operationId, @Param("now") LocalDateTime now);

    @Select("""
            SELECT * FROM cloudmold_inventory_stock_transfer_v3
            WHERE tenant_id=#{tenantId} AND movement_group_id=#{movementGroupId}
            FOR UPDATE
            """)
    InventoryStockTransferDO selectForUpdate(@Param("tenantId") Long tenantId,
                                              @Param("movementGroupId") String movementGroupId);

    @Update("""
            UPDATE cloudmold_inventory_stock_transfer_v3
            SET dispatched_quantity=dispatched_quantity+#{quantity},version=version+1,
                last_operation_id=#{operationId},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND movement_group_id=#{movementGroupId} AND version=#{expectedVersion}
            """)
    int addDispatched(@Param("tenantId") Long tenantId, @Param("movementGroupId") String movementGroupId,
                      @Param("expectedVersion") Long expectedVersion, @Param("quantity") BigDecimal quantity,
                      @Param("operationId") Long operationId, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_inventory_stock_transfer_v3
            SET received_quantity=received_quantity+#{quantity},version=version+1,
                last_operation_id=#{operationId},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND movement_group_id=#{movementGroupId} AND version=#{expectedVersion}
              AND received_quantity+#{quantity}<=dispatched_quantity
            """)
    int addReceived(@Param("tenantId") Long tenantId, @Param("movementGroupId") String movementGroupId,
                    @Param("expectedVersion") Long expectedVersion, @Param("quantity") BigDecimal quantity,
                    @Param("operationId") Long operationId, @Param("now") LocalDateTime now);
}
