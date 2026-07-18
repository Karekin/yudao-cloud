package cn.iocoder.yudao.module.cloudmold.customerservice.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.customerservice.api.*;
import cn.iocoder.yudao.module.cloudmold.customerservice.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.customerservice.dal.mysql.CustomerServiceStoreMapper;
import org.junit.jupiter.api.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CustomerServiceCommandServiceImplTest {

    private final CustomerServiceStoreMapper mapper = mock(CustomerServiceStoreMapper.class);
    private final CustomerServiceEventService eventService = mock(CustomerServiceEventService.class);
    private final CustomerServiceCommandServiceImpl service = new CustomerServiceCommandServiceImpl(mapper, eventService);
    private final Map<String, CustomerServiceOperationDO> operations = new HashMap<>();
    private final Map<String, List<TicketOrderLinkDO>> links = new HashMap<>();
    private final Map<String, CustomerServiceMessageDO> messages = new HashMap<>();
    private final Map<String, CustomerServiceClaimDO> claims = new HashMap<>();
    private final AtomicLong operationSequence = new AtomicLong();
    private final AtomicReference<Long> lastOperationId = new AtomicReference<>();
    private CustomerServiceTicketDO ticket;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        wireOperationStore();
        wirePersistence();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldTraverseTicketStateMachineWithOptimisticVersionAndTenantIsolation() {
        CustomerServiceView created = service.execute(createTicket("ticket-create-001", "CS-1001"));
        assertThat(created.getTicketStatus()).isEqualTo("OPEN");
        assertThat(created.getTicketVersion()).isEqualTo(1L);

        CustomerServiceView assigned = service.execute(base(CustomerServiceOperation.ASSIGN_AGENT, "ticket-assign-001")
                .ticketId(created.getTicketId()).expectedVersion(1L).assignedAgentPrincipalId("principal-agent-1").build());
        assertThat(assigned.getAssignedAgentPrincipalId()).isEqualTo("principal-agent-1");
        assertThat(assigned.getTicketVersion()).isEqualTo(2L);

        CustomerServiceView processing = service.execute(base(CustomerServiceOperation.START_PROCESSING,
                "ticket-start-001").ticketId(created.getTicketId()).expectedVersion(2L).build());
        CustomerServiceView resolved = service.execute(base(CustomerServiceOperation.RESOLVE_TICKET,
                "ticket-resolve-001").ticketId(created.getTicketId()).expectedVersion(3L).build());
        CustomerServiceView closed = service.execute(base(CustomerServiceOperation.CLOSE_TICKET,
                "ticket-close-001").ticketId(created.getTicketId()).expectedVersion(4L).build());
        CustomerServiceView reopened = service.execute(base(CustomerServiceOperation.REOPEN_TICKET,
                "ticket-reopen-001").ticketId(created.getTicketId()).expectedVersion(5L).build());
        assertThat(processing.getTicketStatus()).isEqualTo("IN_PROGRESS");
        assertThat(resolved.getTicketStatus()).isEqualTo("RESOLVED");
        assertThat(closed.getTicketStatus()).isEqualTo("CLOSED");
        assertThat(reopened.getTicketStatus()).isEqualTo("OPEN");
        assertThat(reopened.getTicketVersion()).isEqualTo(6L);

        assertThatThrownBy(() -> service.execute(base(CustomerServiceOperation.START_PROCESSING,
                "ticket-stale-001").ticketId(created.getTicketId()).expectedVersion(5L).build()))
                .hasMessage("ticket version conflict");

        TenantContextHolder.setTenantId(2L);
        assertThatThrownBy(() -> service.getTicket(created.getTicketId()))
                .hasMessage("customer-service ticket does not exist");
        verify(mapper).selectTicket(2L, created.getTicketId());
    }

    @Test
    void shouldReplaySamePayloadAndRejectIdempotencyKeyPayloadConflict() {
        CustomerServiceCommand command = createTicket("ticket-replay-001", "CS-1002");
        CustomerServiceView first = service.execute(command);
        CustomerServiceView replay = service.execute(command);
        assertThat(replay.getTicketId()).isEqualTo(first.getTicketId());
        assertThat(replay.getDuplicate()).isTrue();
        verify(mapper, times(1)).insertTicket(any(CustomerServiceTicketDO.class));
        verify(eventService, times(1)).appendTicket(anyLong(), any(), isNull(), same(command), any(), any());

        CustomerServiceCommand conflict = createTicket("ticket-replay-001", "CS-DIFFERENT");
        assertThatThrownBy(() -> service.execute(conflict))
                .hasMessage("idempotency key conflicts with different customer-service payload");
    }

    @Test
    void shouldValidateExperienceSnapshotOnTicketCreation() {
        assertThatThrownBy(() -> service.execute(base(CustomerServiceOperation.CREATE_TICKET, "ticket-bad-sla")
                .ticketNo("CS-1010").customerPrincipalId("principal-customer-1")
                .channelCode("APP").priority("NORMAL").categoryCode("AFTER_SALE_CONSULTATION")
                .slaPolicyCode("SERVICE_STANDARD").slaPolicyVersion(1)
                .resolutionDeadlineAt(Instant.now().minusSeconds(3600)).fcrWindowHours(72).build()))
                .hasMessage("resolutionDeadlineAt must be present and not before occurredAt");
    }

    @Test
    void shouldRejectNonUuidRunIdBeforePersistingAnyState() {
        CustomerServiceCommand invalid = CustomerServiceCommand.builder()
                .operation(CustomerServiceOperation.CREATE_TICKET)
                .idempotencyKey("ticket-invalid-runid")
                .runId("csr-001")
                .correlationId("customer-service-correlation-001")
                .occurredAt(Instant.now().minusSeconds(60))
                .ticketNo("CS-1002")
                .customerPrincipalId("principal-customer-1")
                .channelCode("APP")
                .priority("NORMAL")
                .categoryCode("AFTER_SALE_CONSULTATION")
                .build();

        assertThatThrownBy(() -> service.execute(invalid))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("runId must be a UUID");
        verifyNoInteractions(mapper, eventService);
    }

    @Test
    void shouldPersistQualifiedOrderLinkAndAppendExactCompensationLedgerOnce() {
        CustomerServiceView created = service.execute(createTicket("ticket-claim-create", "CS-1003"));
        CustomerServiceView link = service.execute(base(CustomerServiceOperation.LINK_REFERENCE, "ticket-link-order")
                .ticketId(created.getTicketId()).expectedVersion(1L).referenceType("ORDER")
                .referenceSourceSystem("cloudmold-order").referenceId("canonical-order-1").build());
        assertThat(link.getLinkId()).isNotBlank();
        assertThat(links.get(created.getTicketId())).singleElement().satisfies(value -> {
            assertThat(value.getReferenceSourceSystem()).isEqualTo("cloudmold-order");
            assertThat(value.getReferenceId()).isEqualTo("canonical-order-1");
        });

        CustomerServiceView requested = service.execute(base(CustomerServiceOperation.REQUEST_CLAIM,
                "claim-request-001").ticketId(created.getTicketId()).expectedVersion(1L).claimCode("CLAIM-1001")
                .claimType("SERVICE_COMPENSATION").requestedAmountMinor(5000L).currencyCode("CNY")
                .reasonCode("LATE_DELIVERY").build());
        CustomerServiceView approved = service.execute(base(CustomerServiceOperation.APPROVE_CLAIM,
                "claim-approve-001").claimId(requested.getClaimId()).expectedVersion(1L)
                .approvedAmountMinor(3000L).build());
        CustomerServiceCommand pay = base(CustomerServiceOperation.PAY_COMPENSATION, "claim-pay-001")
                .claimId(requested.getClaimId()).expectedVersion(2L).build();
        CustomerServiceView paid = service.execute(pay);
        CustomerServiceView replay = service.execute(pay);

        assertThat(requested.getClaimStatus()).isEqualTo("REQUESTED");
        assertThat(approved.getClaimStatus()).isEqualTo("APPROVED");
        assertThat(paid.getClaimStatus()).isEqualTo("PAID");
        assertThat(paid.getPaidAmountMinor()).isEqualTo(3000L);
        assertThat(paid.getCompensationEntryId()).isNotBlank();
        assertThat(replay.getDuplicate()).isTrue();
        verify(mapper, times(1)).insertCompensationEntry(argThat(value -> value.getAmountMinor() == 3000L
                && "CNY".equals(value.getCurrencyCode()) && "claim-pay-001".equals(value.getOperationIdempotencyKey())));
    }

    @Test
    void shouldFailClosedForUnqualifiedReferencesAndRawContentOrFileLocators() {
        CustomerServiceView created = service.execute(createTicket("ticket-pii-create", "CS-1004"));
        assertThatThrownBy(() -> service.execute(base(CustomerServiceOperation.LINK_REFERENCE, "bad-link")
                .ticketId(created.getTicketId()).expectedVersion(1L).referenceType("ORDER")
                .referenceSourceSystem("legacy-trade").referenceId("order-1").build()))
                .hasMessage("ORDER reference must be qualified by cloudmold-order");

        assertThatThrownBy(() -> service.execute(base(CustomerServiceOperation.RECORD_MESSAGE, "raw-message")
                .ticketId(created.getTicketId()).expectedVersion(1L).direction("INBOUND").senderType("CUSTOMER")
                .senderPrincipalId("principal-customer-1").messageType("TEXT")
                .contentToken("my phone is 13800138000").build()))
                .hasMessage("contentToken must be a SHA-256 digest or opaque restricted-store token");

        messages.put("message-1", new CustomerServiceMessageDO().setMessageId("message-1").setTenantId(1L)
                .setTicketId(created.getTicketId()).setAttachmentCount(0));
        assertThatThrownBy(() -> service.execute(base(CustomerServiceOperation.RECORD_ATTACHMENT, "raw-file")
                .ticketId(created.getTicketId()).expectedVersion(1L).messageId("message-1").mediaType("image/png")
                .objectToken("https://bucket.example/customer/id-card.png")
                .contentSha256("a".repeat(64)).sizeBytes(10L).malwareScanStatus("CLEAN").build()))
                .hasMessage("objectToken must be a SHA-256 digest or opaque restricted-store token");
    }

    @Test
    void shouldRecordPiiSafeMessageAttachmentAndResolvedTicketQualityReview() {
        CustomerServiceView created = service.execute(createTicket("ticket-artifact-create", "CS-1005"));
        CustomerServiceView message = service.execute(base(CustomerServiceOperation.RECORD_MESSAGE, "message-001")
                .ticketId(created.getTicketId()).expectedVersion(1L).direction("INBOUND").senderType("CUSTOMER")
                .senderPrincipalId("principal-customer-1").messageType("TEXT")
                .contentToken("sha256:" + "a".repeat(64)).build());
        CustomerServiceView attachment = service.execute(base(CustomerServiceOperation.RECORD_ATTACHMENT,
                "attachment-001").ticketId(created.getTicketId()).expectedVersion(1L)
                .messageId(message.getMessageId()).mediaType("image/png")
                .objectToken("restricted:objecttoken12345678").contentSha256("b".repeat(64))
                .sizeBytes(1024L).malwareScanStatus("CLEAN").build());
        service.execute(base(CustomerServiceOperation.RESOLVE_TICKET, "resolve-for-review")
                .ticketId(created.getTicketId()).expectedVersion(1L).build());
        CustomerServiceView review = service.execute(base(CustomerServiceOperation.RECORD_QUALITY_REVIEW,
                "quality-001").ticketId(created.getTicketId()).expectedVersion(2L)
                .reviewerPrincipalId("principal-reviewer-1").scoreBasisPoints(9500)
                .outcomeCode("PASS").reasonCode("COMPLETE_AND_ACCURATE").build());

        assertThat(message.getMessageId()).isNotBlank();
        assertThat(attachment.getAttachmentId()).isNotBlank();
        assertThat(review.getReviewId()).isNotBlank();
        verify(eventService).appendMessage(any(CustomerServiceMessageDO.class), any(), any());
        verify(eventService).appendAttachment(any(CustomerServiceAttachmentDO.class), any(), any());
        verify(eventService).appendQualityReview(any(CustomerServiceQualityReviewDO.class), any(), any());
    }

    @Test
    void shouldRecordBuyerFeedbackSeparatelyFromInternalQualityReview() {
        CustomerServiceView created = service.execute(base(CustomerServiceOperation.CREATE_TICKET, "ticket-csat-create")
                .ticketNo("CS-1006").customerPrincipalId("principal-customer-1")
                .channelCode("APP").priority("NORMAL").categoryCode("AFTER_SALE_CONSULTATION")
                .slaPolicyCode("SERVICE_STANDARD").slaPolicyVersion(1)
                .resolutionDeadlineAt(Instant.now().plusSeconds(3600)).fcrWindowHours(72).build());
        service.execute(base(CustomerServiceOperation.RESOLVE_TICKET, "ticket-csat-resolve")
                .ticketId(created.getTicketId()).expectedVersion(1L).build());

        CustomerServiceView feedback = service.execute(base(CustomerServiceOperation.RECORD_BUYER_FEEDBACK,
                "ticket-csat-feedback").ticketId(created.getTicketId()).expectedVersion(2L)
                .customerPrincipalId("principal-customer-1").touchpointCode("TICKET_RESOLUTION")
                .sentimentCode("SATISFIED").commentToken("sha256:" + "c".repeat(64)).build());

        assertThat(feedback.getFeedbackId()).isNotBlank();
        verify(mapper).insertBuyerFeedback(argThat(value ->
                "principal-customer-1".equals(value.getCustomerPrincipalId())
                        && "TICKET_RESOLUTION".equals(value.getTouchpointCode())
                        && "SATISFIED".equals(value.getSentimentCode())
                        && value.getScoreBasisPoints() == 10000));
        verify(eventService).appendBuyerFeedback(any(CustomerServiceBuyerFeedbackDO.class), any(), any());
        verify(eventService, never()).appendQualityReview(any(CustomerServiceQualityReviewDO.class), any(), any());
    }

    @Test
    void shouldRejectBuyerFeedbackFromNonTicketCustomer() {
        CustomerServiceView created = service.execute(createTicket("ticket-csat-mismatch", "CS-1007"));
        service.execute(base(CustomerServiceOperation.RESOLVE_TICKET, "ticket-csat-mismatch-resolve")
                .ticketId(created.getTicketId()).expectedVersion(1L).build());

        assertThatThrownBy(() -> service.execute(base(CustomerServiceOperation.RECORD_BUYER_FEEDBACK,
                "ticket-csat-mismatch-feedback").ticketId(created.getTicketId()).expectedVersion(2L)
                .customerPrincipalId("principal-customer-2").touchpointCode("TICKET_RESOLUTION")
                .sentimentCode("DISSATISFIED").reasonCode("UNRESOLVED").build()))
                .hasMessage("buyer feedback must be authored by the ticket customer");
    }

    private void wirePersistence() {
        when(mapper.insertTicket(any())).thenAnswer(invocation -> {
            ticket = invocation.getArgument(0);
            return 1;
        });
        when(mapper.selectTicketForUpdate(anyLong(), anyString())).thenAnswer(invocation -> {
            Long tenantId = invocation.getArgument(0);
            return ticket != null && tenantId.equals(ticket.getTenantId()) ? ticket : null;
        });
        when(mapper.selectTicket(anyLong(), anyString())).thenAnswer(invocation -> {
            Long tenantId = invocation.getArgument(0);
            return ticket != null && tenantId.equals(ticket.getTenantId()) ? ticket : null;
        });
        when(mapper.assignAgent(anyLong(), anyString(), anyLong(), anyString(), any())).thenReturn(1);
        when(mapper.transitionTicket(anyLong(), anyString(), anyLong(), anyString(), anyString(), any())).thenReturn(1);
        when(mapper.insertLink(any())).thenAnswer(invocation -> {
            TicketOrderLinkDO value = invocation.getArgument(0);
            links.computeIfAbsent(value.getTicketId(), ignored -> new ArrayList<>()).add(value);
            return 1;
        });
        when(mapper.selectLinks(anyLong(), anyString())).thenAnswer(invocation ->
                links.getOrDefault(invocation.getArgument(1), List.of()));
        when(mapper.insertMessage(any())).thenAnswer(invocation -> {
            CustomerServiceMessageDO value = invocation.getArgument(0);
            messages.put(value.getMessageId(), value);
            return 1;
        });
        when(mapper.selectMessageForUpdate(anyLong(), anyString())).thenAnswer(invocation ->
                messages.get(invocation.getArgument(1)));
        when(mapper.insertAttachment(any())).thenReturn(1);
        when(mapper.incrementAttachmentCount(anyLong(), anyString())).thenAnswer(invocation -> {
            CustomerServiceMessageDO value = messages.get(invocation.getArgument(1));
            if (value == null) return 0;
            value.setAttachmentCount(value.getAttachmentCount() + 1);
            return 1;
        });
        when(mapper.insertBuyerFeedback(any())).thenReturn(1);
        when(mapper.insertQualityReview(any())).thenReturn(1);
        when(mapper.insertClaim(any())).thenAnswer(invocation -> {
            CustomerServiceClaimDO value = invocation.getArgument(0);
            claims.put(value.getClaimId(), value);
            return 1;
        });
        when(mapper.selectClaimForUpdate(anyLong(), anyString())).thenAnswer(invocation -> {
            CustomerServiceClaimDO value = claims.get(invocation.getArgument(1));
            return value != null && invocation.getArgument(0).equals(value.getTenantId()) ? value : null;
        });
        when(mapper.selectClaim(anyLong(), anyString())).thenAnswer(invocation -> {
            CustomerServiceClaimDO value = claims.get(invocation.getArgument(1));
            return value != null && invocation.getArgument(0).equals(value.getTenantId()) ? value : null;
        });
        when(mapper.transitionClaim(anyLong(), anyString(), anyLong(), anyString(), anyString(), any(), any(), any(),
                any(), any())).thenReturn(1);
        when(mapper.insertCompensationEntry(any())).thenReturn(1);
    }

    private void wireOperationStore() {
        when(mapper.insertOrResolveOperation(anyLong(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    Long tenantId = invocation.getArgument(0);
                    String idempotencyKey = invocation.getArgument(1);
                    String key = tenantId + "|" + idempotencyKey;
                    CustomerServiceOperationDO existing = operations.get(key);
                    if (existing == null) {
                        long operationId = operationSequence.incrementAndGet();
                        operations.put(key, new CustomerServiceOperationDO().setOperationId(operationId)
                                .setTenantId(tenantId).setIdempotencyKey(idempotencyKey)
                                .setCommandType(invocation.getArgument(2)).setRequestHash(invocation.getArgument(3))
                                .setAttemptToken(invocation.getArgument(4)).setStatus(0));
                        lastOperationId.set(operationId);
                    } else {
                        lastOperationId.set(existing.getOperationId());
                    }
                    return 1;
                });
        when(mapper.selectLastInsertId()).thenAnswer(ignored -> lastOperationId.get());
        when(mapper.selectOperationForUpdate(anyLong(), anyLong())).thenAnswer(invocation -> operations.values().stream()
                .filter(value -> value.getOperationId().equals(invocation.getArgument(0))
                        && value.getTenantId().equals(invocation.getArgument(1))).findFirst().orElse(null));
        when(mapper.markOperationSucceeded(anyLong(), anyLong(), anyString(), anyString(), any())).thenAnswer(invocation -> {
            CustomerServiceOperationDO value = operations.values().stream()
                    .filter(operation -> operation.getOperationId().equals(invocation.getArgument(0))
                            && operation.getTenantId().equals(invocation.getArgument(1))).findFirst().orElse(null);
            if (value == null || value.getStatus() != 0) return 0;
            value.setStatus(10).setAggregateId(invocation.getArgument(2)).setResultJson(invocation.getArgument(3));
            return 1;
        });
    }

    private static CustomerServiceCommand createTicket(String key, String ticketNo) {
        return base(CustomerServiceOperation.CREATE_TICKET, key).ticketNo(ticketNo)
                .customerPrincipalId("principal-customer-1").channelCode("APP").priority("NORMAL")
                .categoryCode("AFTER_SALE_CONSULTATION").build();
    }

    private static CustomerServiceCommand.CustomerServiceCommandBuilder base(CustomerServiceOperation operation,
                                                                               String key) {
        return CustomerServiceCommand.builder().operation(operation).idempotencyKey(key)
                .runId("550e8400-e29b-41d4-a716-446655440000")
                .correlationId("customer-service-correlation-001").occurredAt(Instant.now().minusSeconds(60));
    }
}
