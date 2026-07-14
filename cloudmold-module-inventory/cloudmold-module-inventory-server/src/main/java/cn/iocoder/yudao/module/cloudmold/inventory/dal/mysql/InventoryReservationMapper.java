package cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryReservationDO;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryCancellationReservationDO;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

@Mapper
public interface InventoryReservationMapper extends BaseMapperX<InventoryReservationDO> {
    @Select("""
            SELECT r.reservation_id,r.business_type,r.business_id,r.business_item_id,r.quantity,r.status,r.version,
                   b.owner_id,b.canonical_sku_id,b.warehouse_id,b.stock_status,b.quality_status,
                   b.base_uom_code AS uom_code
            FROM cloudmold_inventory_reservation r
            JOIN cloudmold_inventory_balance b
              ON b.tenant_id=r.tenant_id AND b.balance_id=r.balance_id
            WHERE r.tenant_id=#{tenantId} AND r.reservation_id=#{reservationId}
            """)
    InventoryCancellationReservationDO selectCancellationView(@Param("tenantId") Long tenantId,
                                                                @Param("reservationId") String reservationId);

    @Select("SELECT * FROM cloudmold_inventory_reservation WHERE reservation_id = #{reservationId} AND tenant_id = #{tenantId}")
    InventoryReservationDO selectHint(@Param("tenantId") Long tenantId,
                                      @Param("reservationId") String reservationId);

    @Select("SELECT * FROM cloudmold_inventory_reservation WHERE reservation_id = #{reservationId} AND tenant_id = #{tenantId} FOR UPDATE")
    InventoryReservationDO selectForUpdate(@Param("tenantId") Long tenantId,
                                           @Param("reservationId") String reservationId);

    @Update("""
            UPDATE cloudmold_inventory_reservation
            SET status = #{status}, version = version + 1, closed_operation_id = #{operationId}, updated_at = #{now}
            WHERE reservation_id = #{reservationId} AND tenant_id = #{tenantId} AND status = 10
            """)
    int close(@Param("tenantId") Long tenantId,
              @Param("reservationId") String reservationId,
              @Param("status") int status,
              @Param("operationId") Long operationId,
              @Param("now") LocalDateTime now);

}
