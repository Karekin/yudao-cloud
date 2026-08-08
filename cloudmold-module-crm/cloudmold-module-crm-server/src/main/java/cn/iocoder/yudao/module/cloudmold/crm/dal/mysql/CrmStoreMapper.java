package cn.iocoder.yudao.module.cloudmold.crm.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.crm.dal.dataobject.*;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

@Mapper
public interface CrmStoreMapper {

    @Insert("""
            INSERT INTO cloudmold_crm_operation
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
            FROM cloudmold_crm_operation
            WHERE operation_id=#{operationId} AND tenant_id=#{tenantId}
            FOR UPDATE
            """)
    CrmOperationDO selectOperationForUpdate(@Param("operationId") Long operationId, @Param("tenantId") Long tenantId);

    @Update("""
            UPDATE cloudmold_crm_operation
            SET status=10,aggregate_id=#{aggregateId},result_json=CAST(#{resultJson} AS JSON),updated_at=#{now}
            WHERE operation_id=#{operationId} AND tenant_id=#{tenantId} AND status=0
            """)
    int markOperationSucceeded(@Param("operationId") Long operationId, @Param("tenantId") Long tenantId,
                               @Param("aggregateId") String aggregateId, @Param("resultJson") String resultJson,
                               @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_crm_customer
              (customer_id,tenant_id,customer_code,customer_name,level_code,lifecycle_status,pool_status,
               owner_principal_id,source_code,industry_code,region_code,next_follow_up_at,version,created_at,updated_at)
            VALUES (#{customerId},#{tenantId},#{customerCode},#{customerName},#{levelCode},#{lifecycleStatus},
                    #{poolStatus},#{ownerPrincipalId},#{sourceCode},#{industryCode},#{regionCode},
                    #{nextFollowUpAt},#{version},#{createdAt},#{updatedAt})
            """)
    int insertCustomer(CrmCustomerDO value);

    @Select("""
            SELECT customer_id,tenant_id,customer_code,customer_name,level_code,lifecycle_status,pool_status,
                   owner_principal_id,source_code,industry_code,region_code,next_follow_up_at,version,created_at,updated_at
            FROM cloudmold_crm_customer
            WHERE tenant_id=#{tenantId} AND customer_id=#{customerId}
            FOR UPDATE
            """)
    CrmCustomerDO selectCustomerForUpdate(@Param("tenantId") Long tenantId, @Param("customerId") String customerId);

    @Update("""
            UPDATE cloudmold_crm_customer
            SET customer_code=#{customerCode},customer_name=#{customerName},level_code=#{levelCode},
                lifecycle_status=#{lifecycleStatus},source_code=#{sourceCode},industry_code=#{industryCode},
                region_code=#{regionCode},next_follow_up_at=#{nextFollowUpAt},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND customer_id=#{customerId} AND version=#{expectedVersion}
            """)
    int updateCustomer(@Param("tenantId") Long tenantId, @Param("customerId") String customerId,
                       @Param("customerCode") String customerCode, @Param("customerName") String customerName,
                       @Param("levelCode") String levelCode, @Param("lifecycleStatus") String lifecycleStatus,
                       @Param("sourceCode") String sourceCode, @Param("industryCode") String industryCode,
                       @Param("regionCode") String regionCode, @Param("nextFollowUpAt") LocalDateTime nextFollowUpAt,
                       @Param("expectedVersion") Long expectedVersion, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_crm_customer
            SET pool_status=#{poolStatus},owner_principal_id=#{ownerPrincipalId},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND customer_id=#{customerId} AND version=#{expectedVersion}
            """)
    int transitionCustomerPool(@Param("tenantId") Long tenantId, @Param("customerId") String customerId,
                               @Param("poolStatus") String poolStatus, @Param("ownerPrincipalId") String ownerPrincipalId,
                               @Param("expectedVersion") Long expectedVersion, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_crm_customer
            SET next_follow_up_at=#{nextFollowUpAt},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND customer_id=#{customerId}
            """)
    int bumpCustomerNextFollowUp(@Param("tenantId") Long tenantId, @Param("customerId") String customerId,
                                 @Param("nextFollowUpAt") LocalDateTime nextFollowUpAt, @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_crm_lead
              (lead_id,tenant_id,lead_code,lead_name,source_code,status,owner_principal_id,contact_channel_ref,
               masked_contact,next_follow_up_at,version,created_at,updated_at)
            VALUES (#{leadId},#{tenantId},#{leadCode},#{leadName},#{sourceCode},#{status},#{ownerPrincipalId},
                    #{contactChannelRef},#{maskedContact},#{nextFollowUpAt},#{version},#{createdAt},#{updatedAt})
            """)
    int insertLead(CrmLeadDO value);

    @Select("""
            SELECT lead_id,tenant_id,lead_code,lead_name,source_code,status,owner_principal_id,contact_channel_ref,
                   masked_contact,next_follow_up_at,version,created_at,updated_at
            FROM cloudmold_crm_lead
            WHERE tenant_id=#{tenantId} AND lead_id=#{leadId}
            FOR UPDATE
            """)
    CrmLeadDO selectLeadForUpdate(@Param("tenantId") Long tenantId, @Param("leadId") String leadId);

    @Update("""
            UPDATE cloudmold_crm_lead
            SET lead_code=#{leadCode},lead_name=#{leadName},source_code=#{sourceCode},status=#{status},
                contact_channel_ref=#{contactChannelRef},masked_contact=#{maskedContact},
                next_follow_up_at=#{nextFollowUpAt},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND lead_id=#{leadId} AND version=#{expectedVersion}
            """)
    int updateLead(@Param("tenantId") Long tenantId, @Param("leadId") String leadId,
                   @Param("leadCode") String leadCode, @Param("leadName") String leadName,
                   @Param("sourceCode") String sourceCode, @Param("status") String status,
                   @Param("contactChannelRef") String contactChannelRef, @Param("maskedContact") String maskedContact,
                   @Param("nextFollowUpAt") LocalDateTime nextFollowUpAt, @Param("expectedVersion") Long expectedVersion,
                   @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_crm_lead
            SET owner_principal_id=#{ownerPrincipalId},status=#{status},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND lead_id=#{leadId} AND version=#{expectedVersion}
            """)
    int assignLead(@Param("tenantId") Long tenantId, @Param("leadId") String leadId,
                   @Param("ownerPrincipalId") String ownerPrincipalId, @Param("status") String status,
                   @Param("expectedVersion") Long expectedVersion, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_crm_lead
            SET next_follow_up_at=#{nextFollowUpAt},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND lead_id=#{leadId}
            """)
    int bumpLeadNextFollowUp(@Param("tenantId") Long tenantId, @Param("leadId") String leadId,
                             @Param("nextFollowUpAt") LocalDateTime nextFollowUpAt, @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_crm_contact
              (contact_id,tenant_id,customer_id,contact_name,role_title,contact_channel_ref,masked_contact,is_primary,
               status,version,created_at,updated_at)
            VALUES (#{contactId},#{tenantId},#{customerId},#{contactName},#{roleTitle},#{contactChannelRef},
                    #{maskedContact},#{isPrimary},#{status},#{version},#{createdAt},#{updatedAt})
            """)
    int insertContact(CrmContactDO value);

    @Select("""
            SELECT contact_id,tenant_id,customer_id,contact_name,role_title,contact_channel_ref,masked_contact,
                   is_primary,status,version,created_at,updated_at
            FROM cloudmold_crm_contact
            WHERE tenant_id=#{tenantId} AND contact_id=#{contactId}
            FOR UPDATE
            """)
    CrmContactDO selectContactForUpdate(@Param("tenantId") Long tenantId, @Param("contactId") String contactId);

    @Update("""
            UPDATE cloudmold_crm_contact
            SET contact_name=#{contactName},role_title=#{roleTitle},contact_channel_ref=#{contactChannelRef},
                masked_contact=#{maskedContact},is_primary=#{isPrimary},status=#{status},version=version+1,
                updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND contact_id=#{contactId} AND version=#{expectedVersion}
            """)
    int updateContact(@Param("tenantId") Long tenantId, @Param("contactId") String contactId,
                      @Param("contactName") String contactName, @Param("roleTitle") String roleTitle,
                      @Param("contactChannelRef") String contactChannelRef, @Param("maskedContact") String maskedContact,
                      @Param("isPrimary") Boolean isPrimary, @Param("status") String status,
                      @Param("expectedVersion") Long expectedVersion, @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_crm_opportunity
              (opportunity_id,tenant_id,opportunity_code,customer_id,opportunity_name,stage,expected_amount_minor,
               currency_code,expected_close_date,owner_principal_id,version,created_at,updated_at)
            VALUES (#{opportunityId},#{tenantId},#{opportunityCode},#{customerId},#{opportunityName},#{stage},
                    #{expectedAmountMinor},#{currencyCode},#{expectedCloseDate},#{ownerPrincipalId},#{version},
                    #{createdAt},#{updatedAt})
            """)
    int insertOpportunity(CrmOpportunityDO value);

    @Select("""
            SELECT opportunity_id,tenant_id,opportunity_code,customer_id,opportunity_name,stage,expected_amount_minor,
                   currency_code,expected_close_date,owner_principal_id,version,created_at,updated_at
            FROM cloudmold_crm_opportunity
            WHERE tenant_id=#{tenantId} AND opportunity_id=#{opportunityId}
            FOR UPDATE
            """)
    CrmOpportunityDO selectOpportunityForUpdate(@Param("tenantId") Long tenantId,
                                                @Param("opportunityId") String opportunityId);

    @Update("""
            UPDATE cloudmold_crm_opportunity
            SET opportunity_code=#{opportunityCode},opportunity_name=#{opportunityName},stage=#{stage},
                expected_amount_minor=#{expectedAmountMinor},currency_code=#{currencyCode},
                expected_close_date=#{expectedCloseDate},owner_principal_id=#{ownerPrincipalId},
                version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND opportunity_id=#{opportunityId} AND version=#{expectedVersion}
            """)
    int updateOpportunity(@Param("tenantId") Long tenantId, @Param("opportunityId") String opportunityId,
                          @Param("opportunityCode") String opportunityCode, @Param("opportunityName") String opportunityName,
                          @Param("stage") String stage, @Param("expectedAmountMinor") Long expectedAmountMinor,
                          @Param("currencyCode") String currencyCode, @Param("expectedCloseDate") java.time.LocalDate expectedCloseDate,
                          @Param("ownerPrincipalId") String ownerPrincipalId, @Param("expectedVersion") Long expectedVersion,
                          @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_crm_follow_up
              (follow_up_id,tenant_id,subject_type,subject_id,method_code,summary,next_follow_up_at,actor_principal_id,
               occurred_at,created_at)
            VALUES (#{followUpId},#{tenantId},#{subjectType},#{subjectId},#{methodCode},#{summary},#{nextFollowUpAt},
                    #{actorPrincipalId},#{occurredAt},#{createdAt})
            """)
    int insertFollowUp(CrmFollowUpDO value);

    @Insert("""
            INSERT INTO cloudmold_crm_status_history
              (tenant_id,aggregate_type,aggregate_id,aggregate_version,from_status,to_status,reason_code,
               actor_principal_id,occurred_at,created_at)
            VALUES (#{tenantId},#{aggregateType},#{aggregateId},#{aggregateVersion},#{fromStatus},#{toStatus},
                    #{reasonCode},#{actorPrincipalId},#{occurredAt},#{createdAt})
            """)
    int insertStatusHistory(CrmStatusHistoryDO value);

    @Insert("""
            INSERT INTO cloudmold_crm_customer_owner_history
              (tenant_id,customer_id,from_owner_principal_id,to_owner_principal_id,from_pool_status,to_pool_status,
               operation_id,reason_code,actor_principal_id,occurred_at,created_at)
            VALUES (#{tenantId},#{customerId},#{fromOwnerPrincipalId},#{toOwnerPrincipalId},#{fromPoolStatus},
                    #{toPoolStatus},#{operationId},#{reasonCode},#{actorPrincipalId},#{occurredAt},#{createdAt})
            """)
    int insertCustomerOwnerHistory(CrmCustomerOwnerHistoryDO value);
}
