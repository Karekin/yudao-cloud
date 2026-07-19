package cn.iocoder.yudao.module.cloudmold.customerservice.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.customerservice.service.query.CustomerServiceTicketPageItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface CustomerServiceTicketPageMapper {

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM cloudmold_customer_service_ticket t
            WHERE t.tenant_id = #{tenantId}
            <if test="ticketId != null">AND t.ticket_id = #{ticketId}</if>
            <if test="ticketNo != null">AND t.ticket_no LIKE CONCAT('%', #{ticketNo}, '%')</if>
            <if test="customerPrincipalId != null">AND t.customer_principal_id = #{customerPrincipalId}</if>
            <if test="assignedAgentPrincipalId != null">AND t.assigned_agent_principal_id = #{assignedAgentPrincipalId}</if>
            <if test="channelCode != null">AND t.channel_code = #{channelCode}</if>
            <if test="priority != null">AND t.priority = #{priority}</if>
            <if test="status != null">AND t.status = #{status}</if>
            <if test="categoryCode != null">AND t.category_code = #{categoryCode}</if>
            <if test="createdAtFrom != null">AND t.created_at &gt;= #{createdAtFrom}</if>
            <if test="createdAtTo != null">AND t.created_at &lt;= #{createdAtTo}</if>
            </script>
            """)
    long countPage(@Param("tenantId") Long tenantId,
                   @Param("ticketId") String ticketId,
                   @Param("ticketNo") String ticketNo,
                   @Param("customerPrincipalId") String customerPrincipalId,
                   @Param("assignedAgentPrincipalId") String assignedAgentPrincipalId,
                   @Param("channelCode") String channelCode,
                   @Param("priority") String priority,
                   @Param("status") String status,
                   @Param("categoryCode") String categoryCode,
                   @Param("createdAtFrom") LocalDateTime createdAtFrom,
                   @Param("createdAtTo") LocalDateTime createdAtTo);

    @Select("""
            <script>
            SELECT t.ticket_id,
                   t.ticket_no,
                   t.customer_principal_id,
                   t.channel_code,
                   t.priority,
                   t.category_code,
                   t.assigned_agent_principal_id,
                   t.status,
                   t.resolution_deadline_at,
                   t.version AS aggregate_version,
                   t.created_at,
                   t.updated_at
            FROM cloudmold_customer_service_ticket t
            WHERE t.tenant_id = #{tenantId}
            <if test="ticketId != null">AND t.ticket_id = #{ticketId}</if>
            <if test="ticketNo != null">AND t.ticket_no LIKE CONCAT('%', #{ticketNo}, '%')</if>
            <if test="customerPrincipalId != null">AND t.customer_principal_id = #{customerPrincipalId}</if>
            <if test="assignedAgentPrincipalId != null">AND t.assigned_agent_principal_id = #{assignedAgentPrincipalId}</if>
            <if test="channelCode != null">AND t.channel_code = #{channelCode}</if>
            <if test="priority != null">AND t.priority = #{priority}</if>
            <if test="status != null">AND t.status = #{status}</if>
            <if test="categoryCode != null">AND t.category_code = #{categoryCode}</if>
            <if test="createdAtFrom != null">AND t.created_at &gt;= #{createdAtFrom}</if>
            <if test="createdAtTo != null">AND t.created_at &lt;= #{createdAtTo}</if>
            ORDER BY t.updated_at DESC, t.ticket_id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<CustomerServiceTicketPageItem> selectPage(@Param("tenantId") Long tenantId,
                                                   @Param("ticketId") String ticketId,
                                                   @Param("ticketNo") String ticketNo,
                                                   @Param("customerPrincipalId") String customerPrincipalId,
                                                   @Param("assignedAgentPrincipalId") String assignedAgentPrincipalId,
                                                   @Param("channelCode") String channelCode,
                                                   @Param("priority") String priority,
                                                   @Param("status") String status,
                                                   @Param("categoryCode") String categoryCode,
                                                   @Param("createdAtFrom") LocalDateTime createdAtFrom,
                                                   @Param("createdAtTo") LocalDateTime createdAtTo,
                                                   @Param("offset") long offset,
                                                   @Param("limit") int limit);
}
