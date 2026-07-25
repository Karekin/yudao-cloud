package cn.iocoder.yudao.module.cloudmold.customerservice.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.module.cloudmold.customerservice.api.CustomerServiceCommand;
import cn.iocoder.yudao.module.cloudmold.customerservice.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.customerservice.dal.mysql.CustomerServiceStoreMapper;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CustomerServiceEventService {

    static final String SOURCE_SYSTEM = "cloudmold-customer-service";
    private final CustomerServiceStoreMapper mapper;
    private final OutboxAppender outboxAppender;

    public void appendTicket(Long operationId, CustomerServiceTicketDO ticket, String previousStatus,
                             CustomerServiceCommand command, Instant occurredAt, LocalDateTime now) {
        mapper.insertHistory(new CustomerServiceStatusHistoryDO().setTenantId(ticket.getTenantId())
                .setAggregateType("TICKET").setAggregateId(ticket.getTicketId())
                .setAggregateVersion(ticket.getVersion()).setPreviousStatus(previousStatus)
                .setCurrentStatus(ticket.getStatus()).setOperationId(operationId).setReasonCode(command.getReasonCode())
                .setOccurredAt(LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC)).setCreatedAt(now));
        List<TicketOrderLinkDO> links = mapper.selectLinks(ticket.getTenantId(), ticket.getTicketId());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("run_id", ticket.getRunId());
        payload.put("ticket_id", ticket.getTicketId());
        payload.put("ticket_no", ticket.getTicketNo());
        payload.put("customer_principal_id", ticket.getCustomerPrincipalId());
        payload.put("channel_code", ticket.getChannelCode());
        payload.put("priority", ticket.getPriority());
        payload.put("category_code", ticket.getCategoryCode());
        payload.put("sla_policy_code", ticket.getSlaPolicyCode());
        payload.put("sla_policy_version", ticket.getSlaPolicyVersion());
        payload.put("resolution_deadline_at",
                ticket.getResolutionDeadlineAt() == null ? null : ticket.getResolutionDeadlineAt().toInstant(ZoneOffset.UTC).toString());
        payload.put("fcr_window_hours", ticket.getFcrWindowHours());
        payload.put("previous_status", previousStatus);
        payload.put("current_status", ticket.getStatus());
        payload.put("assigned_agent_principal_id", ticket.getAssignedAgentPrincipalId());
        payload.put("primary_order_ref", firstReference(links, "ORDER"));
        payload.put("primary_after_sale_ref", firstReference(links, "AFTER_SALE"));
        payload.put("operation", command.getOperation().name());
        append("customer_service.ticket.status_changed", "customer_service_ticket", ticket.getTicketId(),
                ticket.getVersion(), ticket.getTenantId(), ticket.getRunId(), command, occurredAt, payload);
    }

    public void appendMessage(CustomerServiceMessageDO message, CustomerServiceCommand command, Instant occurredAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("run_id", message.getRunId());
        payload.put("message_id", message.getMessageId());
        payload.put("ticket_id", message.getTicketId());
        payload.put("direction", message.getDirection());
        payload.put("sender_type", message.getSenderType());
        payload.put("sender_principal_id", message.getSenderPrincipalId());
        payload.put("message_type", message.getMessageType());
        payload.put("has_content", hasText(message.getContentToken()));
        payload.put("content_digest_sha256", safeDigest(message.getContentToken()));
        payload.put("attachment_count", message.getAttachmentCount());
        payload.put("occurred_at", occurredAt.toString());
        append("customer_service.message.recorded", "customer_service_message", message.getMessageId(), 1L,
                message.getTenantId(), message.getRunId(), command, occurredAt, payload);
    }

    public void appendAttachment(CustomerServiceAttachmentDO attachment, CustomerServiceCommand command,
                                 Instant occurredAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("run_id", attachment.getRunId());
        payload.put("attachment_id", attachment.getAttachmentId());
        payload.put("ticket_id", attachment.getTicketId());
        payload.put("message_id", attachment.getMessageId());
        payload.put("media_type", attachment.getMediaType());
        payload.put("has_object_locator", hasText(attachment.getObjectToken()));
        payload.put("object_locator_digest_sha256", safeDigest(attachment.getObjectToken()));
        payload.put("content_sha256", attachment.getContentSha256());
        payload.put("size_bytes", attachment.getSizeBytes());
        payload.put("malware_scan_status", attachment.getMalwareScanStatus());
        append("customer_service.attachment.recorded", "customer_service_attachment", attachment.getAttachmentId(),
                1L, attachment.getTenantId(), attachment.getRunId(), command, occurredAt, payload);
    }

    public void appendBuyerFeedback(CustomerServiceBuyerFeedbackDO feedback, CustomerServiceCommand command,
                                    Instant occurredAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("run_id", feedback.getRunId());
        payload.put("feedback_id", feedback.getFeedbackId());
        payload.put("ticket_id", feedback.getTicketId());
        payload.put("customer_principal_id", feedback.getCustomerPrincipalId());
        payload.put("touchpoint_code", feedback.getTouchpointCode());
        payload.put("sentiment_code", feedback.getSentimentCode());
        payload.put("score_basis_points", feedback.getScoreBasisPoints());
        payload.put("reason_code", feedback.getReasonCode());
        payload.put("comment_token", hasText(feedback.getCommentToken())
                ? "sha256:" + safeDigest(feedback.getCommentToken()) : null);
        payload.put("occurred_at", occurredAt.toString());
        append("customer_service.buyer_feedback.recorded", "customer_service_buyer_feedback",
                feedback.getFeedbackId(), 1L, feedback.getTenantId(), feedback.getRunId(), command, occurredAt,
                payload);
    }

    public void appendQualityReview(CustomerServiceQualityReviewDO review, CustomerServiceCommand command,
                                    Instant occurredAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("run_id", review.getRunId());
        payload.put("review_id", review.getReviewId());
        payload.put("ticket_id", review.getTicketId());
        payload.put("reviewer_principal_id", review.getReviewerPrincipalId());
        payload.put("score_basis_points", review.getScoreBasisPoints());
        payload.put("outcome_code", review.getOutcomeCode());
        payload.put("reason_code", review.getReasonCode());
        append("customer_service.quality_review.recorded", "customer_service_quality_review", review.getReviewId(),
                1L, review.getTenantId(), review.getRunId(), command, occurredAt, payload);
    }

    public void appendClaim(Long operationId, CustomerServiceClaimDO claim, String previousStatus,
                            CustomerServiceCommand command, Instant occurredAt, LocalDateTime now) {
        mapper.insertHistory(new CustomerServiceStatusHistoryDO().setTenantId(claim.getTenantId())
                .setAggregateType("CLAIM").setAggregateId(claim.getClaimId()).setAggregateVersion(claim.getVersion())
                .setPreviousStatus(previousStatus).setCurrentStatus(claim.getStatus()).setOperationId(operationId)
                .setReasonCode(claim.getReasonCode()).setOccurredAt(LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC))
                .setCreatedAt(now));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("run_id", claim.getRunId());
        payload.put("claim_id", claim.getClaimId());
        payload.put("claim_code", claim.getClaimCode());
        payload.put("ticket_id", claim.getTicketId());
        payload.put("claim_type", claim.getClaimType());
        payload.put("order_ref", claim.getOrderRef());
        payload.put("after_sale_ref", claim.getAfterSaleRef());
        payload.put("previous_status", previousStatus);
        payload.put("current_status", claim.getStatus());
        payload.put("requested_amount_minor", claim.getRequestedAmountMinor());
        payload.put("approved_amount_minor", claim.getApprovedAmountMinor());
        payload.put("paid_amount_minor", claim.getPaidAmountMinor());
        payload.put("currency_code", claim.getCurrencyCode());
        payload.put("reason_code", claim.getReasonCode());
        payload.put("compensation_entry_id", claim.getCompensationEntryId());
        payload.put("operation", command.getOperation().name());
        append("customer_service.claim.status_changed", "customer_service_claim", claim.getClaimId(),
                claim.getVersion(), claim.getTenantId(), claim.getRunId(), command, occurredAt, payload);
    }

    private void append(String eventType, String aggregateType, String aggregateId, Long version, Long tenantId,
                        String runId, CustomerServiceCommand command, Instant occurredAt, Map<String, Object> payload) {
        outboxAppender.append(AppendDomainEventCommand.builder().eventType(eventType).schemaVersion(1)
                .sourceSystem(SOURCE_SYSTEM).tenantId(tenantId).aggregateType(aggregateType)
                .aggregateId(aggregateId).aggregateVersion(version).eventSequence((short) 1)
                .occurredAt(occurredAt).traceId(runId).correlationId(command.getCorrelationId())
                .causationId(command.getCausationId())
                .idempotencyKey(aggregateType + ":" + aggregateId + ":event:" + version)
                .payload(payload).headers(Map.of("pii_safe", true)).destination("lakehouse").build());
    }

    private static String firstReference(List<TicketOrderLinkDO> links, String type) {
        if (links == null) return null;
        return links.stream().filter(link -> type.equals(link.getReferenceType()))
                .map(TicketOrderLinkDO::getReferenceId).findFirst().orElse(null);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String safeDigest(String tokenOrDigest) {
        if (!hasText(tokenOrDigest)) {
            return null;
        }
        String trimmed = tokenOrDigest.trim();
        if (trimmed.startsWith("sha256:") && trimmed.length() == 71) {
            return trimmed.substring("sha256:".length());
        }
        return DigestUtil.sha256Hex(trimmed);
    }
}
