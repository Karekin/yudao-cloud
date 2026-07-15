package cn.iocoder.yudao.module.cloudmold.customerservice.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.customerservice.dal.dataobject.*;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface CustomerServiceStoreMapper {

    @Insert("""
            INSERT INTO cloudmold_customer_service_operation
              (tenant_id,idempotency_key,command_type,request_hash,attempt_token,status,created_at,updated_at)
            VALUES (#{tenantId},#{idempotencyKey},#{commandType},#{requestHash},#{attemptToken},0,#{now},#{now})
            ON DUPLICATE KEY UPDATE operation_id=LAST_INSERT_ID(operation_id)
            """)
    int insertOrResolveOperation(@Param("tenantId") Long tenantId, @Param("idempotencyKey") String idempotencyKey,
                                 @Param("commandType") String commandType, @Param("requestHash") String requestHash,
                                 @Param("attemptToken") String attemptToken, @Param("now") LocalDateTime now);

    @Select("SELECT LAST_INSERT_ID()")
    Long selectLastInsertId();

    @Select("""
            SELECT operation_id,tenant_id,idempotency_key,command_type,request_hash,attempt_token,status,
                   aggregate_id,result_json,created_at,updated_at
            FROM cloudmold_customer_service_operation
            WHERE operation_id=#{operationId} AND tenant_id=#{tenantId}
            FOR UPDATE
            """)
    CustomerServiceOperationDO selectOperationForUpdate(@Param("operationId") Long operationId,
                                                          @Param("tenantId") Long tenantId);

    @Update("""
            UPDATE cloudmold_customer_service_operation
            SET status=10,aggregate_id=#{aggregateId},result_json=CAST(#{resultJson} AS JSON),updated_at=#{now}
            WHERE operation_id=#{operationId} AND tenant_id=#{tenantId} AND status=0
            """)
    int markOperationSucceeded(@Param("operationId") Long operationId, @Param("tenantId") Long tenantId,
                               @Param("aggregateId") String aggregateId, @Param("resultJson") String resultJson,
                               @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_customer_service_ticket
              (ticket_id,tenant_id,ticket_no,run_id,customer_principal_id,channel_code,priority,category_code,
               assigned_agent_principal_id,status,version,created_at,updated_at)
            VALUES (#{ticketId},#{tenantId},#{ticketNo},#{runId},#{customerPrincipalId},#{channelCode},#{priority},
                    #{categoryCode},#{assignedAgentPrincipalId},#{status},#{version},#{createdAt},#{updatedAt})
            """)
    int insertTicket(CustomerServiceTicketDO value);

    @Select("""
            SELECT ticket_id,tenant_id,ticket_no,run_id,customer_principal_id,channel_code,priority,category_code,
                   assigned_agent_principal_id,status,version,created_at,updated_at
            FROM cloudmold_customer_service_ticket
            WHERE tenant_id=#{tenantId} AND ticket_id=#{ticketId}
            FOR UPDATE
            """)
    CustomerServiceTicketDO selectTicketForUpdate(@Param("tenantId") Long tenantId,
                                                    @Param("ticketId") String ticketId);

    @Select("""
            SELECT ticket_id,tenant_id,ticket_no,run_id,customer_principal_id,channel_code,priority,category_code,
                   assigned_agent_principal_id,status,version,created_at,updated_at
            FROM cloudmold_customer_service_ticket
            WHERE tenant_id=#{tenantId} AND ticket_id=#{ticketId}
            """)
    CustomerServiceTicketDO selectTicket(@Param("tenantId") Long tenantId, @Param("ticketId") String ticketId);

    @Update("""
            UPDATE cloudmold_customer_service_ticket
            SET status=#{after},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND ticket_id=#{ticketId} AND status=#{before} AND version=#{expectedVersion}
            """)
    int transitionTicket(@Param("tenantId") Long tenantId, @Param("ticketId") String ticketId,
                         @Param("expectedVersion") Long expectedVersion, @Param("before") String before,
                         @Param("after") String after, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_customer_service_ticket
            SET assigned_agent_principal_id=#{agentId},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND ticket_id=#{ticketId} AND version=#{expectedVersion}
              AND status IN ('OPEN','IN_PROGRESS')
            """)
    int assignAgent(@Param("tenantId") Long tenantId, @Param("ticketId") String ticketId,
                    @Param("expectedVersion") Long expectedVersion, @Param("agentId") String agentId,
                    @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_customer_service_ticket_order_link
              (link_id,tenant_id,ticket_id,reference_source_system,reference_type,reference_id,created_at)
            VALUES (#{linkId},#{tenantId},#{ticketId},#{referenceSourceSystem},#{referenceType},#{referenceId},#{createdAt})
            """)
    int insertLink(TicketOrderLinkDO value);

    @Select("""
            SELECT link_id,tenant_id,ticket_id,reference_source_system,reference_type,reference_id,created_at
            FROM cloudmold_customer_service_ticket_order_link
            WHERE tenant_id=#{tenantId} AND ticket_id=#{ticketId}
            ORDER BY created_at,link_id
            """)
    List<TicketOrderLinkDO> selectLinks(@Param("tenantId") Long tenantId, @Param("ticketId") String ticketId);

    @Insert("""
            INSERT INTO cloudmold_customer_service_message
              (message_id,tenant_id,ticket_id,run_id,direction,sender_type,sender_principal_id,message_type,
               content_token,attachment_count,occurred_at,created_at)
            VALUES (#{messageId},#{tenantId},#{ticketId},#{runId},#{direction},#{senderType},#{senderPrincipalId},
                    #{messageType},#{contentToken},#{attachmentCount},#{occurredAt},#{createdAt})
            """)
    int insertMessage(CustomerServiceMessageDO value);

    @Select("""
            SELECT message_id,tenant_id,ticket_id,run_id,direction,sender_type,sender_principal_id,message_type,
                   content_token,attachment_count,occurred_at,created_at
            FROM cloudmold_customer_service_message
            WHERE tenant_id=#{tenantId} AND message_id=#{messageId}
            FOR UPDATE
            """)
    CustomerServiceMessageDO selectMessageForUpdate(@Param("tenantId") Long tenantId,
                                                      @Param("messageId") String messageId);

    @Update("""
            UPDATE cloudmold_customer_service_message
            SET attachment_count=attachment_count+1
            WHERE tenant_id=#{tenantId} AND message_id=#{messageId}
            """)
    int incrementAttachmentCount(@Param("tenantId") Long tenantId, @Param("messageId") String messageId);

    @Insert("""
            INSERT INTO cloudmold_customer_service_attachment
              (attachment_id,tenant_id,ticket_id,message_id,run_id,media_type,object_token,content_sha256,size_bytes,
               malware_scan_status,created_at)
            VALUES (#{attachmentId},#{tenantId},#{ticketId},#{messageId},#{runId},#{mediaType},#{objectToken},
                    #{contentSha256},#{sizeBytes},#{malwareScanStatus},#{createdAt})
            """)
    int insertAttachment(CustomerServiceAttachmentDO value);

    @Insert("""
            INSERT INTO cloudmold_customer_service_quality_review
              (review_id,tenant_id,ticket_id,run_id,reviewer_principal_id,score_basis_points,outcome_code,reason_code,
               created_at)
            VALUES (#{reviewId},#{tenantId},#{ticketId},#{runId},#{reviewerPrincipalId},#{scoreBasisPoints},
                    #{outcomeCode},#{reasonCode},#{createdAt})
            """)
    int insertQualityReview(CustomerServiceQualityReviewDO value);

    @Insert("""
            INSERT INTO cloudmold_customer_service_claim
              (claim_id,tenant_id,claim_code,ticket_id,run_id,claim_type,order_ref,after_sale_ref,status,
               requested_amount_minor,approved_amount_minor,paid_amount_minor,currency_code,reason_code,
               compensation_entry_id,version,created_at,updated_at)
            VALUES (#{claimId},#{tenantId},#{claimCode},#{ticketId},#{runId},#{claimType},#{orderRef},#{afterSaleRef},
                    #{status},#{requestedAmountMinor},#{approvedAmountMinor},#{paidAmountMinor},#{currencyCode},
                    #{reasonCode},#{compensationEntryId},#{version},#{createdAt},#{updatedAt})
            """)
    int insertClaim(CustomerServiceClaimDO value);

    @Select("""
            SELECT claim_id,tenant_id,claim_code,ticket_id,run_id,claim_type,order_ref,after_sale_ref,status,
                   requested_amount_minor,approved_amount_minor,paid_amount_minor,currency_code,reason_code,
                   compensation_entry_id,version,created_at,updated_at
            FROM cloudmold_customer_service_claim
            WHERE tenant_id=#{tenantId} AND claim_id=#{claimId}
            FOR UPDATE
            """)
    CustomerServiceClaimDO selectClaimForUpdate(@Param("tenantId") Long tenantId,
                                                  @Param("claimId") String claimId);

    @Select("""
            SELECT claim_id,tenant_id,claim_code,ticket_id,run_id,claim_type,order_ref,after_sale_ref,status,
                   requested_amount_minor,approved_amount_minor,paid_amount_minor,currency_code,reason_code,
                   compensation_entry_id,version,created_at,updated_at
            FROM cloudmold_customer_service_claim
            WHERE tenant_id=#{tenantId} AND claim_id=#{claimId}
            """)
    CustomerServiceClaimDO selectClaim(@Param("tenantId") Long tenantId, @Param("claimId") String claimId);

    @Update("""
            UPDATE cloudmold_customer_service_claim
            SET status=#{after},approved_amount_minor=#{approvedAmountMinor},paid_amount_minor=#{paidAmountMinor},
                reason_code=#{reasonCode},compensation_entry_id=#{compensationEntryId},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND claim_id=#{claimId} AND status=#{before} AND version=#{expectedVersion}
            """)
    int transitionClaim(@Param("tenantId") Long tenantId, @Param("claimId") String claimId,
                        @Param("expectedVersion") Long expectedVersion, @Param("before") String before,
                        @Param("after") String after, @Param("approvedAmountMinor") Long approvedAmountMinor,
                        @Param("paidAmountMinor") Long paidAmountMinor, @Param("reasonCode") String reasonCode,
                        @Param("compensationEntryId") String compensationEntryId,
                        @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_customer_service_compensation_entry
              (compensation_entry_id,tenant_id,claim_id,ticket_id,entry_type,amount_minor,currency_code,
               operation_idempotency_key,occurred_at,created_at)
            VALUES (#{compensationEntryId},#{tenantId},#{claimId},#{ticketId},#{entryType},#{amountMinor},
                    #{currencyCode},#{operationIdempotencyKey},#{occurredAt},#{createdAt})
            """)
    int insertCompensationEntry(CustomerServiceCompensationEntryDO value);

    @Insert("""
            INSERT INTO cloudmold_customer_service_status_history
              (tenant_id,aggregate_type,aggregate_id,aggregate_version,previous_status,current_status,operation_id,
               reason_code,occurred_at,created_at)
            VALUES (#{tenantId},#{aggregateType},#{aggregateId},#{aggregateVersion},#{previousStatus},#{currentStatus},
                    #{operationId},#{reasonCode},#{occurredAt},#{createdAt})
            """)
    int insertHistory(CustomerServiceStatusHistoryDO value);
}
