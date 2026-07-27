package cn.iocoder.yudao.module.cloudmold.fulfillment.dal.mysql.exception;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.fulfillment.dal.dataobject.exception.FulfillmentExceptionDO;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

@Mapper
public interface FulfillmentExceptionMapper extends BaseMapperX<FulfillmentExceptionDO> {

    String COLUMNS = "exception_id,tenant_id,exception_no,run_id,fulfillment_id,order_id,exception_type,"
            + "status,action_code,action_description,plan_evidence_ref,approval_ref,resolution_evidence_ref,"
            + "reason,resolution_summary,version,occurred_at,resolved_at,closed_at,created_at,updated_at";

    @Select("SELECT " + COLUMNS + " FROM cloudmold_fulfillment_exception "
            + "WHERE tenant_id=#{tenantId} AND exception_id=#{exceptionId} FOR UPDATE")
    FulfillmentExceptionDO selectForUpdate(@Param("tenantId") Long tenantId,
                                           @Param("exceptionId") String exceptionId);

    @Select("SELECT " + COLUMNS + " FROM cloudmold_fulfillment_exception "
            + "WHERE tenant_id=#{tenantId} AND exception_id=#{exceptionId}")
    FulfillmentExceptionDO selectTenant(@Param("tenantId") Long tenantId,
                                        @Param("exceptionId") String exceptionId);

    @Select("SELECT " + COLUMNS + " FROM cloudmold_fulfillment_exception "
            + "WHERE tenant_id=#{tenantId} AND order_id=#{orderId} "
            + "ORDER BY created_at DESC, exception_id DESC LIMIT 1")
    FulfillmentExceptionDO selectLatestByOrder(@Param("tenantId") Long tenantId,
                                               @Param("orderId") String orderId);

    @Select("SELECT COUNT(*) FROM cloudmold_fulfillment_exception "
            + "WHERE tenant_id=#{tenantId} AND fulfillment_id=#{fulfillmentId} "
            + "AND status NOT IN ('RESOLVED','CLOSED')")
    int countActive(@Param("tenantId") Long tenantId,
                    @Param("fulfillmentId") String fulfillmentId);

    @Update("""
            UPDATE cloudmold_fulfillment_exception
            SET status=#{nextStatus},
                action_code=COALESCE(#{actionCode},action_code),
                action_description=COALESCE(#{actionDescription},action_description),
                plan_evidence_ref=COALESCE(#{planEvidenceRef},plan_evidence_ref),
                approval_ref=COALESCE(#{approvalRef},approval_ref),
                resolution_evidence_ref=COALESCE(#{resolutionEvidenceRef},resolution_evidence_ref),
                resolution_summary=COALESCE(#{resolutionSummary},resolution_summary),
                resolved_at=COALESCE(#{resolvedAt},resolved_at),
                closed_at=COALESCE(#{closedAt},closed_at),
                version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND exception_id=#{exceptionId}
              AND version=#{expectedVersion} AND status=#{expectedStatus}
            """)
    int transition(@Param("tenantId") Long tenantId,
                   @Param("exceptionId") String exceptionId,
                   @Param("expectedVersion") Long expectedVersion,
                   @Param("expectedStatus") String expectedStatus,
                   @Param("nextStatus") String nextStatus,
                   @Param("actionCode") String actionCode,
                   @Param("actionDescription") String actionDescription,
                   @Param("planEvidenceRef") String planEvidenceRef,
                   @Param("approvalRef") String approvalRef,
                   @Param("resolutionEvidenceRef") String resolutionEvidenceRef,
                   @Param("resolutionSummary") String resolutionSummary,
                   @Param("resolvedAt") LocalDateTime resolvedAt,
                   @Param("closedAt") LocalDateTime closedAt,
                   @Param("now") LocalDateTime now);
}
