package cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.warehouse.service.query.WarehouseLocationPageItem;
import cn.iocoder.yudao.module.cloudmold.warehouse.service.query.WarehousePageItem;
import cn.iocoder.yudao.module.cloudmold.warehouse.service.query.WarehouseZonePageItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * CloudMold 规范仓网只读查询 Mapper（Query 侧，与 Command 侧 CanonicalWarehouseMapper /
 * WarehouseZoneMapper / WarehouseLocationMapper 分离）。
 * 走纯注解 SQL + script 动态条件 + LIMIT/OFFSET 手动分页，不继承 MyBatis-Plus BaseMapper。
 * cloudmold 规范表为不可变权威表，无逻辑删除列，故不过滤 deleted。
 */
@Mapper
public interface WarehouseQueryMapper {

    // ===== Warehouse =====
    @Select("""
            <script>
            SELECT COUNT(*)
            FROM cloudmold_warehouse w
            WHERE w.tenant_id = #{tenantId}
            <if test="warehouseCode != null">AND w.warehouse_code LIKE CONCAT('%', #{warehouseCode}, '%')</if>
            <if test="warehouseType != null">AND w.warehouse_type = #{warehouseType}</if>
            <if test="status != null">AND w.status = #{status}</if>
            </script>
            """)
    long countWarehousePage(@Param("tenantId") Long tenantId,
                            @Param("warehouseCode") String warehouseCode,
                            @Param("warehouseType") String warehouseType,
                            @Param("status") String status);

    @Select("""
            <script>
            SELECT w.warehouse_id, w.warehouse_code, w.name, w.warehouse_type,
                   w.timezone, w.status, w.version, w.updated_at
            FROM cloudmold_warehouse w
            WHERE w.tenant_id = #{tenantId}
            <if test="warehouseCode != null">AND w.warehouse_code LIKE CONCAT('%', #{warehouseCode}, '%')</if>
            <if test="warehouseType != null">AND w.warehouse_type = #{warehouseType}</if>
            <if test="status != null">AND w.status = #{status}</if>
            ORDER BY w.updated_at DESC, w.warehouse_id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<WarehousePageItem> selectWarehousePage(@Param("tenantId") Long tenantId,
                                                @Param("warehouseCode") String warehouseCode,
                                                @Param("warehouseType") String warehouseType,
                                                @Param("status") String status,
                                                @Param("offset") long offset,
                                                @Param("limit") int limit);

    // ===== Zone (JOIN warehouse 取 warehouseCode) =====
    @Select("""
            <script>
            SELECT COUNT(*)
            FROM cloudmold_warehouse_zone z
            WHERE z.tenant_id = #{tenantId}
            <if test="warehouseId != null">AND z.warehouse_id = #{warehouseId}</if>
            <if test="zoneCode != null">AND z.zone_code LIKE CONCAT('%', #{zoneCode}, '%')</if>
            <if test="status != null">AND z.status = #{status}</if>
            </script>
            """)
    long countZonePage(@Param("tenantId") Long tenantId,
                       @Param("warehouseId") String warehouseId,
                       @Param("zoneCode") String zoneCode,
                       @Param("status") String status);

    @Select("""
            <script>
            SELECT z.zone_id, z.warehouse_id, w.warehouse_code, z.zone_code, z.name,
                   z.zone_type, z.status, z.version, z.updated_at
            FROM cloudmold_warehouse_zone z
            LEFT JOIN cloudmold_warehouse w
              ON w.tenant_id = z.tenant_id AND w.warehouse_id = z.warehouse_id
            WHERE z.tenant_id = #{tenantId}
            <if test="warehouseId != null">AND z.warehouse_id = #{warehouseId}</if>
            <if test="zoneCode != null">AND z.zone_code LIKE CONCAT('%', #{zoneCode}, '%')</if>
            <if test="status != null">AND z.status = #{status}</if>
            ORDER BY z.updated_at DESC, z.zone_id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<WarehouseZonePageItem> selectZonePage(@Param("tenantId") Long tenantId,
                                               @Param("warehouseId") String warehouseId,
                                               @Param("zoneCode") String zoneCode,
                                               @Param("status") String status,
                                               @Param("offset") long offset,
                                               @Param("limit") int limit);

    // ===== Location (JOIN warehouse + zone 取 warehouseCode/zoneCode) =====
    @Select("""
            <script>
            SELECT COUNT(*)
            FROM cloudmold_warehouse_location l
            WHERE l.tenant_id = #{tenantId}
            <if test="warehouseId != null">AND l.warehouse_id = #{warehouseId}</if>
            <if test="zoneId != null">AND l.zone_id = #{zoneId}</if>
            <if test="locationCode != null">AND l.location_code LIKE CONCAT('%', #{locationCode}, '%')</if>
            <if test="status != null">AND l.status = #{status}</if>
            </script>
            """)
    long countLocationPage(@Param("tenantId") Long tenantId,
                           @Param("warehouseId") String warehouseId,
                           @Param("zoneId") String zoneId,
                           @Param("locationCode") String locationCode,
                           @Param("status") String status);

    @Select("""
            <script>
            SELECT l.location_id, l.warehouse_id, w.warehouse_code, l.zone_id, z.zone_code,
                   l.location_code, l.name, l.location_type, l.aisle_code, l.rack_code,
                   l.bay_code, l.level_code, l.allow_item_mixing, l.allow_lot_mixing,
                   l.capacity_quantity, l.capacity_uom_code, l.status, l.version, l.updated_at
            FROM cloudmold_warehouse_location l
            LEFT JOIN cloudmold_warehouse w
              ON w.tenant_id = l.tenant_id AND w.warehouse_id = l.warehouse_id
            LEFT JOIN cloudmold_warehouse_zone z
              ON z.tenant_id = l.tenant_id AND z.zone_id = l.zone_id
            WHERE l.tenant_id = #{tenantId}
            <if test="warehouseId != null">AND l.warehouse_id = #{warehouseId}</if>
            <if test="zoneId != null">AND l.zone_id = #{zoneId}</if>
            <if test="locationCode != null">AND l.location_code LIKE CONCAT('%', #{locationCode}, '%')</if>
            <if test="status != null">AND l.status = #{status}</if>
            ORDER BY l.updated_at DESC, l.location_id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<WarehouseLocationPageItem> selectLocationPage(@Param("tenantId") Long tenantId,
                                                       @Param("warehouseId") String warehouseId,
                                                       @Param("zoneId") String zoneId,
                                                       @Param("locationCode") String locationCode,
                                                       @Param("status") String status,
                                                       @Param("offset") long offset,
                                                       @Param("limit") int limit);
}
