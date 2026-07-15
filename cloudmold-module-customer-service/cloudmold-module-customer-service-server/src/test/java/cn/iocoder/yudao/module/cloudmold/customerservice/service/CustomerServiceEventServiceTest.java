package cn.iocoder.yudao.module.cloudmold.customerservice.service;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.cloudmold.customerservice.api.*;
import cn.iocoder.yudao.module.cloudmold.customerservice.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.customerservice.dal.mysql.CustomerServiceStoreMapper;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.*;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerServiceEventServiceTest {

    @Test
    void shouldAppendFiveVersionedPiiSafeEventsWithoutRawMessageOrFileLocator() {
        CustomerServiceStoreMapper mapper = mock(CustomerServiceStoreMapper.class);
        OutboxAppender outboxAppender = mock(OutboxAppender.class);
        CustomerServiceEventService service = new CustomerServiceEventService(mapper, outboxAppender);
        LocalDateTime now = LocalDateTime.of(2026, 7, 16, 1, 0);
        Instant occurredAt = now.toInstant(ZoneOffset.UTC);
        CustomerServiceCommand command = CustomerServiceCommand.builder().operation(CustomerServiceOperation.CREATE_TICKET)
                .idempotencyKey("customer-service-event-001").runId("csr-001")
                .correlationId("correlation-001").causationId("causation-001").build();
        CustomerServiceTicketDO ticket = new CustomerServiceTicketDO().setTicketId("ticket-1").setTenantId(1L)
                .setTicketNo("CS-1001").setRunId("csr-001").setCustomerPrincipalId("principal-customer-1")
                .setChannelCode("APP").setPriority("NORMAL").setCategoryCode("DELIVERY")
                .setAssignedAgentPrincipalId("principal-agent-1").setStatus("IN_PROGRESS").setVersion(2L);
        when(mapper.selectLinks(1L, "ticket-1")).thenReturn(List.of(
                new TicketOrderLinkDO().setReferenceType("ORDER").setReferenceId("canonical-order-1"),
                new TicketOrderLinkDO().setReferenceType("AFTER_SALE").setReferenceId("canonical-after-sale-1")));
        CustomerServiceMessageDO message = new CustomerServiceMessageDO().setMessageId("message-1").setTenantId(1L)
                .setTicketId("ticket-1").setRunId("csr-001").setDirection("INBOUND").setSenderType("CUSTOMER")
                .setSenderPrincipalId("principal-customer-1").setMessageType("TEXT")
                .setContentToken("sha256:" + "a".repeat(64)).setAttachmentCount(0);
        CustomerServiceAttachmentDO attachment = new CustomerServiceAttachmentDO().setAttachmentId("attachment-1")
                .setTenantId(1L).setTicketId("ticket-1").setMessageId("message-1").setRunId("csr-001")
                .setMediaType("image/png").setObjectToken("restricted:objecttoken12345678")
                .setContentSha256("b".repeat(64)).setSizeBytes(1024L).setMalwareScanStatus("CLEAN");
        CustomerServiceQualityReviewDO review = new CustomerServiceQualityReviewDO().setReviewId("review-1")
                .setTenantId(1L).setTicketId("ticket-1").setRunId("csr-001")
                .setReviewerPrincipalId("principal-reviewer-1").setScoreBasisPoints(9500)
                .setOutcomeCode("PASS").setReasonCode("COMPLETE_AND_ACCURATE");
        CustomerServiceClaimDO claim = new CustomerServiceClaimDO().setClaimId("claim-1").setTenantId(1L)
                .setClaimCode("CLAIM-1001").setTicketId("ticket-1").setRunId("csr-001")
                .setClaimType("SERVICE_COMPENSATION").setOrderRef("canonical-order-1")
                .setAfterSaleRef("canonical-after-sale-1").setStatus("PAID").setRequestedAmountMinor(5000L)
                .setApprovedAmountMinor(3000L).setPaidAmountMinor(3000L).setCurrencyCode("CNY")
                .setReasonCode("LATE_DELIVERY").setCompensationEntryId("compensation-1").setVersion(3L);

        service.appendTicket(1L, ticket, "OPEN", command, occurredAt, now);
        service.appendMessage(message, command, occurredAt);
        service.appendAttachment(attachment, command, occurredAt);
        service.appendQualityReview(review, command, occurredAt);
        service.appendClaim(2L, claim, "APPROVED", command, occurredAt, now);

        ArgumentCaptor<AppendDomainEventCommand> captor = ArgumentCaptor.forClass(AppendDomainEventCommand.class);
        verify(outboxAppender, times(5)).append(captor.capture());
        assertThat(captor.getAllValues()).extracting(AppendDomainEventCommand::getEventType).containsExactly(
                "customer_service.ticket.status_changed",
                "customer_service.message.recorded",
                "customer_service.attachment.recorded",
                "customer_service.quality_review.recorded",
                "customer_service.claim.status_changed");
        assertThat(captor.getAllValues()).allSatisfy(event -> {
            assertThat(event.getSchemaVersion()).isEqualTo(1);
            assertThat(event.getSourceSystem()).isEqualTo("cloudmold-customer-service");
            assertThat(event.getTenantId()).isEqualTo(1L);
            assertThat(event.getDestination()).isEqualTo("lakehouse");
            assertThat(event.getHeaders()).containsEntry("pii_safe", true);
            assertThat(event.getIdempotencyKey()).endsWith(":" + event.getAggregateVersion());
            String json = JsonUtils.toJsonString(event.getPayload());
            assertThat(json).doesNotContain("message_body", "file_url", "13800138000",
                    "https://bucket.example/customer/id-card.png");
        });
        AppendDomainEventCommand messageEvent = captor.getAllValues().get(1);
        assertThat(messageEvent.getPayload()).containsEntry("content_token", "sha256:" + "a".repeat(64));
        assertThat(messageEvent.getPayload()).doesNotContainKeys("body", "content", "customer_phone", "customer_name");
        AppendDomainEventCommand attachmentEvent = captor.getAllValues().get(2);
        assertThat(attachmentEvent.getPayload()).containsEntry("object_token", "restricted:objecttoken12345678")
                .doesNotContainKeys("file_name", "object_url", "bucket", "customer_address");
        verify(mapper, times(2)).insertHistory(any(CustomerServiceStatusHistoryDO.class));
    }
}
