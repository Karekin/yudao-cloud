package cn.iocoder.yudao.module.cloudmold.aftersale.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.aftersale.dal.dataobject.AfterSaleOperationDO;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

@Mapper
public interface AfterSaleOperationMapper extends BaseMapperX<AfterSaleOperationDO> {
    @Insert("""
            INSERT INTO cloudmold_after_sale_operation
              (tenant_id,idempotency_key,command_type,request_hash,attempt_token,status,created_at,updated_at)
            VALUES (#{tenantId},#{key},#{type},#{hash},#{token},0,#{now},#{now})
            ON DUPLICATE KEY UPDATE operation_id=LAST_INSERT_ID(operation_id)
            """)
    int insertOrResolve(@Param("tenantId") Long tenantId, @Param("key") String key,
                        @Param("type") String type, @Param("hash") String hash,
                        @Param("token") String token, @Param("now") LocalDateTime now);
    @Select("SELECT LAST_INSERT_ID()") Long lastInsertId();
    @Select("SELECT * FROM cloudmold_after_sale_operation WHERE tenant_id=#{tenantId} AND operation_id=#{id} FOR UPDATE")
    AfterSaleOperationDO selectForUpdate(@Param("tenantId") Long tenantId, @Param("id") Long id);
    @Update("UPDATE cloudmold_after_sale_operation SET status=10,after_sale_id=#{afterSaleId},result_json=CAST(#{json} AS JSON),updated_at=#{now} WHERE tenant_id=#{tenantId} AND operation_id=#{id} AND status=0")
    int markSucceeded(@Param("tenantId") Long tenantId, @Param("id") Long id,
                      @Param("afterSaleId") String afterSaleId, @Param("json") String json,
                      @Param("now") LocalDateTime now);
}
