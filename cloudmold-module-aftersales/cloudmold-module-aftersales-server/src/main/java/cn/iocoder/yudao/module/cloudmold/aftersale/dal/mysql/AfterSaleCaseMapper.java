package cn.iocoder.yudao.module.cloudmold.aftersale.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.aftersale.dal.dataobject.AfterSaleCaseDO;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AfterSaleCaseMapper extends BaseMapperX<AfterSaleCaseDO> {
    @Select("SELECT * FROM cloudmold_after_sale_case WHERE tenant_id=#{tenantId} AND after_sale_id=#{id} FOR UPDATE")
    AfterSaleCaseDO selectForUpdate(@Param("tenantId") Long tenantId, @Param("id") String id);

    @Select("SELECT * FROM cloudmold_after_sale_case WHERE tenant_id=#{tenantId} AND after_sale_id=#{id}")
    AfterSaleCaseDO selectTenant(@Param("tenantId") Long tenantId, @Param("id") String id);

    @Select("SELECT c.* FROM cloudmold_after_sale_case c JOIN cloudmold_after_sale_item i ON i.tenant_id=c.tenant_id AND i.after_sale_id=c.after_sale_id WHERE c.tenant_id=#{tenantId} AND c.order_id=#{orderId} AND i.order_item_id=#{orderItemId} ORDER BY c.created_at DESC,c.after_sale_id DESC LIMIT 1")
    AfterSaleCaseDO selectByOrderItem(@Param("tenantId") Long tenantId, @Param("orderId") String orderId,
                                      @Param("orderItemId") String orderItemId);

    @Select("SELECT c.* FROM cloudmold_after_sale_case c JOIN cloudmold_after_sale_item i ON i.tenant_id=c.tenant_id AND i.after_sale_id=c.after_sale_id WHERE c.tenant_id=#{tenantId} AND c.order_id=#{orderId} AND i.order_item_id=#{orderItemId} AND c.status IN ('REQUESTED','APPROVED','RESOLUTION_PENDING','MANUAL_REVIEW') LIMIT 1")
    AfterSaleCaseDO selectActiveByOrderItem(@Param("tenantId") Long tenantId, @Param("orderId") String orderId,
                                            @Param("orderItemId") String orderItemId);

    @Select("SELECT COUNT(*) FROM cloudmold_after_sale_case WHERE tenant_id=#{tenantId} AND buyer_id=#{buyerId}")
    long countByBuyer(@Param("tenantId") Long tenantId, @Param("buyerId") String buyerId);

    @Select("""
            SELECT * FROM cloudmold_after_sale_case
            WHERE tenant_id=#{tenantId} AND buyer_id=#{buyerId}
            ORDER BY updated_at DESC,after_sale_id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<AfterSaleCaseDO> selectByBuyer(@Param("tenantId") Long tenantId, @Param("buyerId") String buyerId,
                                        @Param("offset") long offset, @Param("limit") int limit);

    @Update("""
            UPDATE cloudmold_after_sale_case
            SET status='APPROVED',refund_status='REQUESTED',approved_amount_minor=#{amount},
                reviewer_id=#{reviewerId},return_fulfillment_id=#{returnFulfillmentId},
                version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND after_sale_id=#{id} AND status='REQUESTED' AND version=#{version}
            """)
    int approve(@Param("tenantId") Long tenantId, @Param("id") String id, @Param("version") Long version,
                @Param("amount") Long amount, @Param("reviewerId") String reviewerId,
                @Param("returnFulfillmentId") String returnFulfillmentId, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_after_sale_case
            SET status='RESOLUTION_PENDING',return_shipment_id=#{returnShipmentId},inspection_id=#{inspectionId},
                resolution_saga_id=#{sagaId},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND after_sale_id=#{id} AND status='APPROVED' AND version=#{version}
            """)
    int startResolution(@Param("tenantId") Long tenantId, @Param("id") String id,
                        @Param("version") Long version, @Param("returnShipmentId") String returnShipmentId,
                        @Param("inspectionId") String inspectionId, @Param("sagaId") String sagaId,
                        @Param("now") LocalDateTime now);

    @Update("UPDATE cloudmold_after_sale_case SET refund_status='SUCCEEDED',updated_at=#{now} WHERE tenant_id=#{tenantId} AND after_sale_id=#{id} AND refund_status='REQUESTED'")
    int markRefundSucceeded(@Param("tenantId") Long tenantId, @Param("id") String id,
                            @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_after_sale_case SET status='COMPLETED',version=version+1,
                completed_at=#{now},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND after_sale_id=#{id}
              AND status='RESOLUTION_PENDING' AND refund_status='SUCCEEDED' AND version=#{version}
            """)
    int complete(@Param("tenantId") Long tenantId, @Param("id") String id,
                 @Param("version") Long version, @Param("now") LocalDateTime now);
}
