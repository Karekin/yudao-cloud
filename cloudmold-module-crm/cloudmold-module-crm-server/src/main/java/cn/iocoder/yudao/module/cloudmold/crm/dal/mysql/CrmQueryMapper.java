package cn.iocoder.yudao.module.cloudmold.crm.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.crm.api.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface CrmQueryMapper {

    @Select("""
            SELECT COUNT(*)
            FROM cloudmold_crm_customer
            WHERE tenant_id=#{tenantId} AND owner_principal_id=#{ownerPrincipalId} AND pool_status='OWNED'
            """)
    long countOwnedCustomers(@Param("tenantId") Long tenantId, @Param("ownerPrincipalId") String ownerPrincipalId);

    @Select("""
            SELECT COUNT(*)
            FROM cloudmold_crm_customer
            WHERE tenant_id=#{tenantId} AND pool_status='IN_POOL' AND owner_principal_id IS NULL
            """)
    long countPoolCustomers(@Param("tenantId") Long tenantId);

    @Select("""
            SELECT COUNT(*)
            FROM cloudmold_crm_lead
            WHERE tenant_id=#{tenantId} AND owner_principal_id=#{ownerPrincipalId}
            """)
    long countOwnedLeads(@Param("tenantId") Long tenantId, @Param("ownerPrincipalId") String ownerPrincipalId);

    @Select("""
            SELECT COUNT(*)
            FROM cloudmold_crm_opportunity
            WHERE tenant_id=#{tenantId} AND owner_principal_id=#{ownerPrincipalId}
              AND stage NOT IN ('CLOSED_WON','CLOSED_LOST')
            """)
    long countOpenOpportunities(@Param("tenantId") Long tenantId, @Param("ownerPrincipalId") String ownerPrincipalId);

    @Select("""
            SELECT COUNT(*)
            FROM cloudmold_crm_follow_up
            WHERE tenant_id=#{tenantId} AND actor_principal_id=#{actorPrincipalId}
              AND next_follow_up_at IS NOT NULL AND next_follow_up_at >= #{now} AND next_follow_up_at <= #{end}
            """)
    long countDueFollowUps(@Param("tenantId") Long tenantId, @Param("actorPrincipalId") String actorPrincipalId,
                           @Param("now") LocalDateTime now, @Param("end") LocalDateTime end);

    @Select("""
            SELECT COUNT(*)
            FROM cloudmold_crm_follow_up
            WHERE tenant_id=#{tenantId} AND actor_principal_id=#{actorPrincipalId}
              AND next_follow_up_at IS NOT NULL AND next_follow_up_at < #{now}
            """)
    long countOverdueFollowUps(@Param("tenantId") Long tenantId, @Param("actorPrincipalId") String actorPrincipalId,
                               @Param("now") LocalDateTime now);

    @Select("""
            <script>
            SELECT f.follow_up_id AS followUpId,
                   f.subject_type AS subjectType,
                   f.subject_id AS subjectId,
                   CASE
                     WHEN f.subject_type='CUSTOMER' THEN c.customer_code
                     WHEN f.subject_type='LEAD' THEN l.lead_code
                     WHEN f.subject_type='CONTACT' THEN ct.contact_id
                     WHEN f.subject_type='OPPORTUNITY' THEN o.opportunity_code
                     ELSE NULL
                   END AS subjectCode,
                   CASE
                     WHEN f.subject_type='CUSTOMER' THEN c.customer_name
                     WHEN f.subject_type='LEAD' THEN l.lead_name
                     WHEN f.subject_type='CONTACT' THEN ct.contact_name
                     WHEN f.subject_type='OPPORTUNITY' THEN o.opportunity_name
                     ELSE NULL
                   END AS subjectName,
                   f.method_code AS methodCode,
                   f.summary,
                   f.next_follow_up_at AS nextFollowUpAt,
                   f.actor_principal_id AS actorPrincipalId,
                   f.occurred_at AS occurredAt,
                   f.created_at AS createdAt
            FROM cloudmold_crm_follow_up f
            LEFT JOIN cloudmold_crm_customer c
                   ON f.subject_type='CUSTOMER' AND c.tenant_id=f.tenant_id AND c.customer_id=f.subject_id
            LEFT JOIN cloudmold_crm_lead l
                   ON f.subject_type='LEAD' AND l.tenant_id=f.tenant_id AND l.lead_id=f.subject_id
            LEFT JOIN cloudmold_crm_contact ct
                   ON f.subject_type='CONTACT' AND ct.tenant_id=f.tenant_id AND ct.contact_id=f.subject_id
            LEFT JOIN cloudmold_crm_opportunity o
                   ON f.subject_type='OPPORTUNITY' AND o.tenant_id=f.tenant_id AND o.opportunity_id=f.subject_id
            WHERE f.tenant_id=#{tenantId} AND f.actor_principal_id=#{actorPrincipalId}
              AND f.next_follow_up_at IS NOT NULL
            <if test="now != null">AND f.next_follow_up_at &gt;= #{now}</if>
            ORDER BY f.next_follow_up_at ASC, f.follow_up_id ASC
            LIMIT #{limit}
            </script>
            """)
    List<CrmFollowUpView> selectUpcomingFollowUps(@Param("tenantId") Long tenantId,
                                                  @Param("actorPrincipalId") String actorPrincipalId,
                                                  @Param("now") LocalDateTime now, @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM cloudmold_crm_customer c
            WHERE c.tenant_id=#{tenantId}
            <if test="customerId != null">AND c.customer_id=#{customerId}</if>
            <if test="customerCode != null">AND c.customer_code LIKE CONCAT('%', #{customerCode}, '%')</if>
            <if test="keyword != null">AND (c.customer_code LIKE CONCAT('%', #{keyword}, '%') OR c.customer_name LIKE CONCAT('%', #{keyword}, '%'))</if>
            <if test="lifecycleStatus != null">AND c.lifecycle_status=#{lifecycleStatus}</if>
            <if test="poolStatus != null">AND c.pool_status=#{poolStatus}</if>
            <if test="ownerPrincipalId != null">AND c.owner_principal_id=#{ownerPrincipalId}</if>
            <if test="sourceCode != null">AND c.source_code=#{sourceCode}</if>
            <if test="industryCode != null">AND c.industry_code=#{industryCode}</if>
            <if test="regionCode != null">AND c.region_code=#{regionCode}</if>
            <if test="createdAtFrom != null">AND c.created_at &gt;= #{createdAtFrom}</if>
            <if test="createdAtTo != null">AND c.created_at &lt;= #{createdAtTo}</if>
            </script>
            """)
    long countCustomers(@Param("tenantId") Long tenantId, @Param("customerId") String customerId,
                        @Param("customerCode") String customerCode, @Param("keyword") String keyword,
                        @Param("lifecycleStatus") String lifecycleStatus, @Param("poolStatus") String poolStatus,
                        @Param("ownerPrincipalId") String ownerPrincipalId, @Param("sourceCode") String sourceCode,
                        @Param("industryCode") String industryCode, @Param("regionCode") String regionCode,
                        @Param("createdAtFrom") LocalDateTime createdAtFrom, @Param("createdAtTo") LocalDateTime createdAtTo);

    @Select("""
            <script>
            SELECT c.customer_id AS customerId,
                   c.customer_code AS customerCode,
                   c.customer_name AS customerName,
                   c.level_code AS levelCode,
                   c.lifecycle_status AS lifecycleStatus,
                   c.pool_status AS poolStatus,
                   c.owner_principal_id AS ownerPrincipalId,
                   c.source_code AS sourceCode,
                   c.industry_code AS industryCode,
                   c.region_code AS regionCode,
                   c.next_follow_up_at AS nextFollowUpAt,
                   c.version,
                   c.created_at AS createdAt,
                   c.updated_at AS updatedAt
            FROM cloudmold_crm_customer c
            WHERE c.tenant_id=#{tenantId}
            <if test="customerId != null">AND c.customer_id=#{customerId}</if>
            <if test="customerCode != null">AND c.customer_code LIKE CONCAT('%', #{customerCode}, '%')</if>
            <if test="keyword != null">AND (c.customer_code LIKE CONCAT('%', #{keyword}, '%') OR c.customer_name LIKE CONCAT('%', #{keyword}, '%'))</if>
            <if test="lifecycleStatus != null">AND c.lifecycle_status=#{lifecycleStatus}</if>
            <if test="poolStatus != null">AND c.pool_status=#{poolStatus}</if>
            <if test="ownerPrincipalId != null">AND c.owner_principal_id=#{ownerPrincipalId}</if>
            <if test="sourceCode != null">AND c.source_code=#{sourceCode}</if>
            <if test="industryCode != null">AND c.industry_code=#{industryCode}</if>
            <if test="regionCode != null">AND c.region_code=#{regionCode}</if>
            <if test="createdAtFrom != null">AND c.created_at &gt;= #{createdAtFrom}</if>
            <if test="createdAtTo != null">AND c.created_at &lt;= #{createdAtTo}</if>
            ORDER BY c.updated_at DESC, c.customer_id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<CrmCustomerView> selectCustomers(@Param("tenantId") Long tenantId, @Param("customerId") String customerId,
                                          @Param("customerCode") String customerCode, @Param("keyword") String keyword,
                                          @Param("lifecycleStatus") String lifecycleStatus, @Param("poolStatus") String poolStatus,
                                          @Param("ownerPrincipalId") String ownerPrincipalId, @Param("sourceCode") String sourceCode,
                                          @Param("industryCode") String industryCode, @Param("regionCode") String regionCode,
                                          @Param("createdAtFrom") LocalDateTime createdAtFrom, @Param("createdAtTo") LocalDateTime createdAtTo,
                                          @Param("offset") long offset, @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM cloudmold_crm_lead l
            WHERE l.tenant_id=#{tenantId}
            <if test="leadId != null">AND l.lead_id=#{leadId}</if>
            <if test="leadCode != null">AND l.lead_code LIKE CONCAT('%', #{leadCode}, '%')</if>
            <if test="keyword != null">AND (l.lead_code LIKE CONCAT('%', #{keyword}, '%') OR l.lead_name LIKE CONCAT('%', #{keyword}, '%') OR l.masked_contact LIKE CONCAT('%', #{keyword}, '%'))</if>
            <if test="status != null">AND l.status=#{status}</if>
            <if test="ownerPrincipalId != null">AND l.owner_principal_id=#{ownerPrincipalId}</if>
            <if test="sourceCode != null">AND l.source_code=#{sourceCode}</if>
            <if test="createdAtFrom != null">AND l.created_at &gt;= #{createdAtFrom}</if>
            <if test="createdAtTo != null">AND l.created_at &lt;= #{createdAtTo}</if>
            </script>
            """)
    long countLeads(@Param("tenantId") Long tenantId, @Param("leadId") String leadId, @Param("leadCode") String leadCode,
                    @Param("keyword") String keyword, @Param("status") String status, @Param("ownerPrincipalId") String ownerPrincipalId,
                    @Param("sourceCode") String sourceCode, @Param("createdAtFrom") LocalDateTime createdAtFrom,
                    @Param("createdAtTo") LocalDateTime createdAtTo);

    @Select("""
            <script>
            SELECT l.lead_id AS leadId,
                   l.lead_code AS leadCode,
                   l.lead_name AS leadName,
                   l.source_code AS sourceCode,
                   l.status,
                   l.owner_principal_id AS ownerPrincipalId,
                   l.contact_channel_ref AS contactChannelRef,
                   l.masked_contact AS maskedContact,
                   l.next_follow_up_at AS nextFollowUpAt,
                   l.version,
                   l.created_at AS createdAt,
                   l.updated_at AS updatedAt
            FROM cloudmold_crm_lead l
            WHERE l.tenant_id=#{tenantId}
            <if test="leadId != null">AND l.lead_id=#{leadId}</if>
            <if test="leadCode != null">AND l.lead_code LIKE CONCAT('%', #{leadCode}, '%')</if>
            <if test="keyword != null">AND (l.lead_code LIKE CONCAT('%', #{keyword}, '%') OR l.lead_name LIKE CONCAT('%', #{keyword}, '%') OR l.masked_contact LIKE CONCAT('%', #{keyword}, '%'))</if>
            <if test="status != null">AND l.status=#{status}</if>
            <if test="ownerPrincipalId != null">AND l.owner_principal_id=#{ownerPrincipalId}</if>
            <if test="sourceCode != null">AND l.source_code=#{sourceCode}</if>
            <if test="createdAtFrom != null">AND l.created_at &gt;= #{createdAtFrom}</if>
            <if test="createdAtTo != null">AND l.created_at &lt;= #{createdAtTo}</if>
            ORDER BY l.updated_at DESC, l.lead_id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<CrmLeadView> selectLeads(@Param("tenantId") Long tenantId, @Param("leadId") String leadId,
                                  @Param("leadCode") String leadCode, @Param("keyword") String keyword,
                                  @Param("status") String status, @Param("ownerPrincipalId") String ownerPrincipalId,
                                  @Param("sourceCode") String sourceCode, @Param("createdAtFrom") LocalDateTime createdAtFrom,
                                  @Param("createdAtTo") LocalDateTime createdAtTo, @Param("offset") long offset,
                                  @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM cloudmold_crm_contact c
            WHERE c.tenant_id=#{tenantId}
            <if test="contactId != null">AND c.contact_id=#{contactId}</if>
            <if test="customerId != null">AND c.customer_id=#{customerId}</if>
            <if test="keyword != null">AND (c.contact_name LIKE CONCAT('%', #{keyword}, '%') OR c.role_title LIKE CONCAT('%', #{keyword}, '%') OR c.masked_contact LIKE CONCAT('%', #{keyword}, '%'))</if>
            <if test="status != null">AND c.status=#{status}</if>
            <if test="isPrimary != null">AND c.is_primary=#{isPrimary}</if>
            <if test="createdAtFrom != null">AND c.created_at &gt;= #{createdAtFrom}</if>
            <if test="createdAtTo != null">AND c.created_at &lt;= #{createdAtTo}</if>
            </script>
            """)
    long countContacts(@Param("tenantId") Long tenantId, @Param("contactId") String contactId,
                       @Param("customerId") String customerId, @Param("keyword") String keyword,
                       @Param("status") String status, @Param("isPrimary") Boolean isPrimary,
                       @Param("createdAtFrom") LocalDateTime createdAtFrom, @Param("createdAtTo") LocalDateTime createdAtTo);

    @Select("""
            <script>
            SELECT c.contact_id AS contactId,
                   c.customer_id AS customerId,
                   c.contact_name AS contactName,
                   c.role_title AS roleTitle,
                   c.contact_channel_ref AS contactChannelRef,
                   c.masked_contact AS maskedContact,
                   c.is_primary AS isPrimary,
                   c.status,
                   c.version,
                   c.created_at AS createdAt,
                   c.updated_at AS updatedAt
            FROM cloudmold_crm_contact c
            WHERE c.tenant_id=#{tenantId}
            <if test="contactId != null">AND c.contact_id=#{contactId}</if>
            <if test="customerId != null">AND c.customer_id=#{customerId}</if>
            <if test="keyword != null">AND (c.contact_name LIKE CONCAT('%', #{keyword}, '%') OR c.role_title LIKE CONCAT('%', #{keyword}, '%') OR c.masked_contact LIKE CONCAT('%', #{keyword}, '%'))</if>
            <if test="status != null">AND c.status=#{status}</if>
            <if test="isPrimary != null">AND c.is_primary=#{isPrimary}</if>
            <if test="createdAtFrom != null">AND c.created_at &gt;= #{createdAtFrom}</if>
            <if test="createdAtTo != null">AND c.created_at &lt;= #{createdAtTo}</if>
            ORDER BY c.updated_at DESC, c.contact_id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<CrmContactView> selectContacts(@Param("tenantId") Long tenantId, @Param("contactId") String contactId,
                                        @Param("customerId") String customerId, @Param("keyword") String keyword,
                                        @Param("status") String status, @Param("isPrimary") Boolean isPrimary,
                                        @Param("createdAtFrom") LocalDateTime createdAtFrom, @Param("createdAtTo") LocalDateTime createdAtTo,
                                        @Param("offset") long offset, @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM cloudmold_crm_opportunity o
            WHERE o.tenant_id=#{tenantId}
            <if test="opportunityId != null">AND o.opportunity_id=#{opportunityId}</if>
            <if test="opportunityCode != null">AND o.opportunity_code LIKE CONCAT('%', #{opportunityCode}, '%')</if>
            <if test="customerId != null">AND o.customer_id=#{customerId}</if>
            <if test="keyword != null">AND (o.opportunity_code LIKE CONCAT('%', #{keyword}, '%') OR o.opportunity_name LIKE CONCAT('%', #{keyword}, '%'))</if>
            <if test="stage != null">AND o.stage=#{stage}</if>
            <if test="ownerPrincipalId != null">AND o.owner_principal_id=#{ownerPrincipalId}</if>
            <if test="expectedCloseDateFrom != null">AND o.expected_close_date &gt;= #{expectedCloseDateFrom}</if>
            <if test="expectedCloseDateTo != null">AND o.expected_close_date &lt;= #{expectedCloseDateTo}</if>
            <if test="createdAtFrom != null">AND o.created_at &gt;= #{createdAtFrom}</if>
            <if test="createdAtTo != null">AND o.created_at &lt;= #{createdAtTo}</if>
            </script>
            """)
    long countOpportunities(@Param("tenantId") Long tenantId, @Param("opportunityId") String opportunityId,
                            @Param("opportunityCode") String opportunityCode, @Param("customerId") String customerId,
                            @Param("keyword") String keyword, @Param("stage") String stage,
                            @Param("ownerPrincipalId") String ownerPrincipalId,
                            @Param("expectedCloseDateFrom") LocalDate expectedCloseDateFrom,
                            @Param("expectedCloseDateTo") LocalDate expectedCloseDateTo,
                            @Param("createdAtFrom") LocalDateTime createdAtFrom, @Param("createdAtTo") LocalDateTime createdAtTo);

    @Select("""
            <script>
            SELECT o.opportunity_id AS opportunityId,
                   o.opportunity_code AS opportunityCode,
                   o.customer_id AS customerId,
                   o.opportunity_name AS opportunityName,
                   o.stage,
                   o.expected_amount_minor AS expectedAmountMinor,
                   o.currency_code AS currencyCode,
                   o.expected_close_date AS expectedCloseDate,
                   o.owner_principal_id AS ownerPrincipalId,
                   o.version,
                   o.created_at AS createdAt,
                   o.updated_at AS updatedAt
            FROM cloudmold_crm_opportunity o
            WHERE o.tenant_id=#{tenantId}
            <if test="opportunityId != null">AND o.opportunity_id=#{opportunityId}</if>
            <if test="opportunityCode != null">AND o.opportunity_code LIKE CONCAT('%', #{opportunityCode}, '%')</if>
            <if test="customerId != null">AND o.customer_id=#{customerId}</if>
            <if test="keyword != null">AND (o.opportunity_code LIKE CONCAT('%', #{keyword}, '%') OR o.opportunity_name LIKE CONCAT('%', #{keyword}, '%'))</if>
            <if test="stage != null">AND o.stage=#{stage}</if>
            <if test="ownerPrincipalId != null">AND o.owner_principal_id=#{ownerPrincipalId}</if>
            <if test="expectedCloseDateFrom != null">AND o.expected_close_date &gt;= #{expectedCloseDateFrom}</if>
            <if test="expectedCloseDateTo != null">AND o.expected_close_date &lt;= #{expectedCloseDateTo}</if>
            <if test="createdAtFrom != null">AND o.created_at &gt;= #{createdAtFrom}</if>
            <if test="createdAtTo != null">AND o.created_at &lt;= #{createdAtTo}</if>
            ORDER BY o.updated_at DESC, o.opportunity_id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<CrmOpportunityView> selectOpportunities(@Param("tenantId") Long tenantId, @Param("opportunityId") String opportunityId,
                                                 @Param("opportunityCode") String opportunityCode, @Param("customerId") String customerId,
                                                 @Param("keyword") String keyword, @Param("stage") String stage,
                                                 @Param("ownerPrincipalId") String ownerPrincipalId,
                                                 @Param("expectedCloseDateFrom") LocalDate expectedCloseDateFrom,
                                                 @Param("expectedCloseDateTo") LocalDate expectedCloseDateTo,
                                                 @Param("createdAtFrom") LocalDateTime createdAtFrom, @Param("createdAtTo") LocalDateTime createdAtTo,
                                                 @Param("offset") long offset, @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM cloudmold_crm_follow_up f
            WHERE f.tenant_id=#{tenantId}
            <if test="subjectType != null">AND f.subject_type=#{subjectType}</if>
            <if test="subjectId != null">AND f.subject_id=#{subjectId}</if>
            <if test="methodCode != null">AND f.method_code=#{methodCode}</if>
            <if test="actorPrincipalId != null">AND f.actor_principal_id=#{actorPrincipalId}</if>
            <if test="occurredAtFrom != null">AND f.occurred_at &gt;= #{occurredAtFrom}</if>
            <if test="occurredAtTo != null">AND f.occurred_at &lt;= #{occurredAtTo}</if>
            </script>
            """)
    long countFollowUps(@Param("tenantId") Long tenantId, @Param("subjectType") String subjectType,
                        @Param("subjectId") String subjectId, @Param("methodCode") String methodCode,
                        @Param("actorPrincipalId") String actorPrincipalId,
                        @Param("occurredAtFrom") LocalDateTime occurredAtFrom, @Param("occurredAtTo") LocalDateTime occurredAtTo);

    @Select("""
            <script>
            SELECT f.follow_up_id AS followUpId,
                   f.subject_type AS subjectType,
                   f.subject_id AS subjectId,
                   CASE
                     WHEN f.subject_type='CUSTOMER' THEN c.customer_code
                     WHEN f.subject_type='LEAD' THEN l.lead_code
                     WHEN f.subject_type='CONTACT' THEN ct.contact_id
                     WHEN f.subject_type='OPPORTUNITY' THEN o.opportunity_code
                     ELSE NULL
                   END AS subjectCode,
                   CASE
                     WHEN f.subject_type='CUSTOMER' THEN c.customer_name
                     WHEN f.subject_type='LEAD' THEN l.lead_name
                     WHEN f.subject_type='CONTACT' THEN ct.contact_name
                     WHEN f.subject_type='OPPORTUNITY' THEN o.opportunity_name
                     ELSE NULL
                   END AS subjectName,
                   f.method_code AS methodCode,
                   f.summary,
                   f.next_follow_up_at AS nextFollowUpAt,
                   f.actor_principal_id AS actorPrincipalId,
                   f.occurred_at AS occurredAt,
                   f.created_at AS createdAt
            FROM cloudmold_crm_follow_up f
            LEFT JOIN cloudmold_crm_customer c
                   ON f.subject_type='CUSTOMER' AND c.tenant_id=f.tenant_id AND c.customer_id=f.subject_id
            LEFT JOIN cloudmold_crm_lead l
                   ON f.subject_type='LEAD' AND l.tenant_id=f.tenant_id AND l.lead_id=f.subject_id
            LEFT JOIN cloudmold_crm_contact ct
                   ON f.subject_type='CONTACT' AND ct.tenant_id=f.tenant_id AND ct.contact_id=f.subject_id
            LEFT JOIN cloudmold_crm_opportunity o
                   ON f.subject_type='OPPORTUNITY' AND o.tenant_id=f.tenant_id AND o.opportunity_id=f.subject_id
            WHERE f.tenant_id=#{tenantId}
            <if test="subjectType != null">AND f.subject_type=#{subjectType}</if>
            <if test="subjectId != null">AND f.subject_id=#{subjectId}</if>
            <if test="methodCode != null">AND f.method_code=#{methodCode}</if>
            <if test="actorPrincipalId != null">AND f.actor_principal_id=#{actorPrincipalId}</if>
            <if test="occurredAtFrom != null">AND f.occurred_at &gt;= #{occurredAtFrom}</if>
            <if test="occurredAtTo != null">AND f.occurred_at &lt;= #{occurredAtTo}</if>
            ORDER BY f.occurred_at DESC, f.follow_up_id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<CrmFollowUpView> selectFollowUps(@Param("tenantId") Long tenantId, @Param("subjectType") String subjectType,
                                          @Param("subjectId") String subjectId, @Param("methodCode") String methodCode,
                                          @Param("actorPrincipalId") String actorPrincipalId,
                                          @Param("occurredAtFrom") LocalDateTime occurredAtFrom, @Param("occurredAtTo") LocalDateTime occurredAtTo,
                                          @Param("offset") long offset, @Param("limit") int limit);
}
