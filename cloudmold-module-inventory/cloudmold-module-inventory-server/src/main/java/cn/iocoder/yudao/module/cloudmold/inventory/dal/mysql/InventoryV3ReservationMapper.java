package cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryV3ReservationDO;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryV3ReservationAllocationDO;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

@Mapper
public interface InventoryV3ReservationMapper extends BaseMapperX<InventoryV3ReservationDO> {
    @Select("SELECT * FROM cloudmold_inventory_reservation_v3 WHERE tenant_id=#{tenantId} AND reservation_id=#{reservationId} FOR UPDATE")
    InventoryV3ReservationDO selectForUpdate(@Param("tenantId") Long tenantId,
                                             @Param("reservationId") String reservationId);

    @Select("SELECT * FROM cloudmold_inventory_reservation_allocation_v3 WHERE tenant_id=#{tenantId} AND reservation_id=#{reservationId} LIMIT 2")
    java.util.List<InventoryV3ReservationAllocationDO> selectAllocationHints(@Param("tenantId") Long tenantId,
                                                                              @Param("reservationId") String reservationId);

    @Select("SELECT * FROM cloudmold_inventory_reservation_allocation_v3 WHERE tenant_id=#{tenantId} AND reservation_id=#{reservationId} AND allocation_id=#{allocationId} FOR UPDATE")
    InventoryV3ReservationAllocationDO selectAllocationForUpdate(@Param("tenantId") Long tenantId,
                                                                  @Param("reservationId") String reservationId,
                                                                  @Param("allocationId") String allocationId);

    @Insert("""
            INSERT INTO cloudmold_inventory_reservation_allocation_v3
              (allocation_id,tenant_id,reservation_id,balance_id,quantity,status,version,created_operation_id,created_at,updated_at)
            VALUES
              (#{row.allocationId},#{row.tenantId},#{row.reservationId},#{row.balanceId},#{row.quantity},#{row.status},
               #{row.version},#{row.createdOperationId},#{row.createdAt},#{row.updatedAt})
            """)
    int insertAllocation(@Param("row") InventoryV3ReservationAllocationDO row);

    @Update("""
            UPDATE cloudmold_inventory_reservation_v3
            SET status=#{status},version=version+1,closed_operation_id=#{operationId},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND reservation_id=#{reservationId} AND status=10
            """)
    int closeReservation(@Param("tenantId") Long tenantId, @Param("reservationId") String reservationId,
                         @Param("status") int status, @Param("operationId") Long operationId,
                         @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_inventory_reservation_allocation_v3
            SET status=#{status},version=version+1,closed_operation_id=#{operationId},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND allocation_id=#{allocationId} AND status=10
            """)
    int closeAllocation(@Param("tenantId") Long tenantId, @Param("allocationId") String allocationId,
                        @Param("status") int status, @Param("operationId") Long operationId,
                        @Param("now") LocalDateTime now);
}
