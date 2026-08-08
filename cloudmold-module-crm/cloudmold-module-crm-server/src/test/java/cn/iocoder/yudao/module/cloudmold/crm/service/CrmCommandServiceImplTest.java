package cn.iocoder.yudao.module.cloudmold.crm.service;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.crm.api.*;
import cn.iocoder.yudao.module.cloudmold.crm.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.crm.dal.mysql.CrmStoreMapper;
import cn.iocoder.yudao.module.cloudmold.crm.service.actor.CrmActorPrincipalPort;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventResult;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CrmCommandServiceImplTest {

    private final CrmStoreMapper mapper = mock(CrmStoreMapper.class);
    private final OutboxAppender outboxAppender = mock(OutboxAppender.class);
    private final CrmActorPrincipalPort actorPrincipalPort = mock(CrmActorPrincipalPort.class);
    private final CrmCommandServiceImpl service = new CrmCommandServiceImpl(mapper, outboxAppender, actorPrincipalPort);

    private final AtomicReference<String> attemptToken = new AtomicReference<>();
    private final AtomicReference<CrmCustomerDO> customer = new AtomicReference<>();
    private final AtomicReference<CrmLeadDO> lead = new AtomicReference<>();
    private final AtomicReference<CrmContactDO> contact = new AtomicReference<>();
    private final AtomicReference<CrmOpportunityDO> opportunity = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        doNothing().when(actorPrincipalPort).requireActive(anyString());
        when(mapper.selectLastInsertId()).thenReturn(11L);
        when(mapper.insertOrResolveOperation(anyLong(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    attemptToken.set(invocation.getArgument(4));
                    return 1;
                });
        when(mapper.selectOperationForUpdate(11L, 1L)).thenAnswer(invocation -> new CrmOperationDO()
                .setOperationId(11L).setTenantId(1L).setAttemptToken(attemptToken.get()).setStatus(0));
        when(mapper.markOperationSucceeded(anyLong(), anyLong(), anyString(), anyString(), any())).thenReturn(1);
        when(outboxAppender.append(any())).thenReturn(new AppendDomainEventResult("event-1", "a".repeat(64), false));

        when(mapper.insertCustomer(any())).thenAnswer(invocation -> {
            customer.set(invocation.getArgument(0));
            return 1;
        });
        when(mapper.selectCustomerForUpdate(eq(1L), anyString())).thenAnswer(invocation -> {
            CrmCustomerDO current = customer.get();
            return current != null && Objects.equals(current.getCustomerId(), invocation.getArgument(1)) ? clone(current) : null;
        });
        when(mapper.updateCustomer(anyLong(), anyString(), anyString(), anyString(), any(), any(), any(), any(), any(), any(), anyLong(), any()))
                .thenAnswer(invocation -> {
                    CrmCustomerDO current = customer.get();
                    current.setCustomerCode(invocation.getArgument(2)).setCustomerName(invocation.getArgument(3))
                            .setLevelCode(invocation.getArgument(4)).setLifecycleStatus(invocation.getArgument(5))
                            .setSourceCode(invocation.getArgument(6)).setIndustryCode(invocation.getArgument(7))
                            .setRegionCode(invocation.getArgument(8)).setNextFollowUpAt(invocation.getArgument(9))
                            .setVersion(current.getVersion() + 1);
                    return 1;
                });
        when(mapper.transitionCustomerPool(anyLong(), anyString(), anyString(), nullable(String.class), anyLong(), any()))
                .thenAnswer(invocation -> {
                    CrmCustomerDO current = customer.get();
                    current.setPoolStatus(invocation.getArgument(2)).setOwnerPrincipalId(invocation.getArgument(3))
                            .setVersion(current.getVersion() + 1);
                    return 1;
                });

        when(mapper.insertLead(any())).thenAnswer(invocation -> {
            lead.set(invocation.getArgument(0));
            return 1;
        });
        when(mapper.selectLeadForUpdate(eq(1L), anyString())).thenAnswer(invocation -> {
            CrmLeadDO current = lead.get();
            return current != null && Objects.equals(current.getLeadId(), invocation.getArgument(1)) ? clone(current) : null;
        });
        when(mapper.updateLead(anyLong(), anyString(), anyString(), anyString(), any(), any(), any(), any(), any(), anyLong(), any()))
                .thenAnswer(invocation -> {
                    CrmLeadDO current = lead.get();
                    current.setLeadCode(invocation.getArgument(2)).setLeadName(invocation.getArgument(3))
                            .setSourceCode(invocation.getArgument(4)).setStatus(invocation.getArgument(5))
                            .setContactChannelRef(invocation.getArgument(6)).setMaskedContact(invocation.getArgument(7))
                            .setNextFollowUpAt(invocation.getArgument(8)).setVersion(current.getVersion() + 1);
                    return 1;
                });
        when(mapper.assignLead(anyLong(), anyString(), anyString(), anyString(), anyLong(), any()))
                .thenAnswer(invocation -> {
                    CrmLeadDO current = lead.get();
                    current.setOwnerPrincipalId(invocation.getArgument(2)).setStatus(invocation.getArgument(3))
                            .setVersion(current.getVersion() + 1);
                    return 1;
                });

        when(mapper.insertContact(any())).thenAnswer(invocation -> {
            contact.set(invocation.getArgument(0));
            return 1;
        });
        when(mapper.selectContactForUpdate(eq(1L), anyString())).thenAnswer(invocation -> {
            CrmContactDO current = contact.get();
            return current != null && Objects.equals(current.getContactId(), invocation.getArgument(1)) ? clone(current) : null;
        });
        when(mapper.updateContact(anyLong(), anyString(), anyString(), any(), any(), any(), anyBoolean(), anyString(), anyLong(), any()))
                .thenAnswer(invocation -> {
                    CrmContactDO current = contact.get();
                    current.setContactName(invocation.getArgument(2)).setRoleTitle(invocation.getArgument(3))
                            .setContactChannelRef(invocation.getArgument(4)).setMaskedContact(invocation.getArgument(5))
                            .setIsPrimary(invocation.getArgument(6)).setStatus(invocation.getArgument(7))
                            .setVersion(current.getVersion() + 1);
                    return 1;
                });

        when(mapper.insertOpportunity(any())).thenAnswer(invocation -> {
            opportunity.set(invocation.getArgument(0));
            return 1;
        });
        when(mapper.insertFollowUp(any())).thenReturn(1);
        when(mapper.selectOpportunityForUpdate(eq(1L), anyString())).thenAnswer(invocation -> {
            CrmOpportunityDO current = opportunity.get();
            return current != null && Objects.equals(current.getOpportunityId(), invocation.getArgument(1)) ? clone(current) : null;
        });
        when(mapper.updateOpportunity(anyLong(), anyString(), anyString(), anyString(), anyString(), anyLong(), anyString(), any(), any(), anyLong(), any()))
                .thenAnswer(invocation -> {
                    CrmOpportunityDO current = opportunity.get();
                    current.setOpportunityCode(invocation.getArgument(2)).setOpportunityName(invocation.getArgument(3))
                            .setStage(invocation.getArgument(4)).setExpectedAmountMinor(invocation.getArgument(5))
                            .setCurrencyCode(invocation.getArgument(6)).setExpectedCloseDate(invocation.getArgument(7))
                            .setOwnerPrincipalId(invocation.getArgument(8)).setVersion(current.getVersion() + 1);
                    return 1;
                });
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void createsAndUpdatesCustomerWithSharedOutbox() {
        CrmCommand create = CrmCommand.builder().operation(CrmOperation.CREATE_CUSTOMER).idempotencyKey("crm-customer-create")
                .runId("run-1").correlationId("01981c02-997a-34a8-a9e9-e3ce92adeac6")
                .causationId("98e1e2bb-1148-35ed-a810-116a208fdd00")
                .occurredAt(Instant.parse("2026-08-08T00:00:00Z"))
                .customer(CrmCommand.CustomerDefinition.builder().customerCode("CUST_001").customerName("ACME")
                        .lifecycleStatus("ACTIVE").poolStatus("OWNED").ownerPrincipalId("principal-sales-1")
                        .sourceCode("REFERRAL").industryCode("APPAREL").regionCode("CN").build())
                .build();

        CrmCommandResult created = service.execute(create, "principal-admin-1");
        assertThat(created.getAggregateType()).isEqualTo("CUSTOMER");
        assertThat(created.getCurrentStatus()).isEqualTo("ACTIVE");

        CrmCommand update = CrmCommand.builder().operation(CrmOperation.UPDATE_CUSTOMER).idempotencyKey("crm-customer-update")
                .runId("run-2")
                .customer(CrmCommand.CustomerDefinition.builder().customerId(created.getAggregateId()).customerCode("CUST_001")
                        .customerName("ACME China").lifecycleStatus("DORMANT").poolStatus("OWNED")
                        .ownerPrincipalId("principal-sales-1").sourceCode("REFERRAL").industryCode("APPAREL")
                        .regionCode("CN").expectedVersion(created.getAggregateVersion()).build())
                .build();

        CrmCommandResult updated = service.execute(update, "principal-admin-1");
        assertThat(updated.getAggregateVersion()).isEqualTo(2L);
        ArgumentCaptor<AppendDomainEventCommand> event = ArgumentCaptor.forClass(AppendDomainEventCommand.class);
        verify(outboxAppender, atLeast(2)).append(event.capture());
        assertThat(event.getAllValues()).anySatisfy(value -> {
            assertThat(value.getEventType()).isEqualTo("crm.customer.status_changed");
            assertThat(value.getSourceSystem()).isEqualTo("cloudmold-crm");
            assertThat(value.getCorrelationId()).isEqualTo("01981c02-997a-34a8-a9e9-e3ce92adeac6");
            assertThat(value.getCausationId()).isEqualTo("98e1e2bb-1148-35ed-a810-116a208fdd00");
        });
    }

    @Test
    void claimsAndReturnsCustomerPoolWithOwnerEvent() {
        customer.set(new CrmCustomerDO().setCustomerId("customer-1").setTenantId(1L).setCustomerCode("CUST_001")
                .setCustomerName("ACME").setLifecycleStatus("ACTIVE").setPoolStatus("IN_POOL").setOwnerPrincipalId(null)
                .setVersion(1L));

        CrmCommand claim = CrmCommand.builder().operation(CrmOperation.CLAIM_CUSTOMER).idempotencyKey("crm-customer-claim")
                .runId("run-claim")
                .customer(CrmCommand.CustomerDefinition.builder().customerId("customer-1").expectedVersion(1L).build())
                .build();
        CrmCommandResult claimed = service.execute(claim, "principal-admin-1");
        assertThat(claimed.getPoolStatus()).isEqualTo("OWNED");
        assertThat(claimed.getOwnerPrincipalId()).isEqualTo("principal-admin-1");

        CrmCommand giveBack = CrmCommand.builder().operation(CrmOperation.RETURN_CUSTOMER_TO_POOL).idempotencyKey("crm-customer-return")
                .runId("run-return")
                .customer(CrmCommand.CustomerDefinition.builder().customerId("customer-1").expectedVersion(2L).build())
                .build();
        CrmCommandResult returned = service.execute(giveBack, "principal-admin-1");
        assertThat(returned.getPoolStatus()).isEqualTo("IN_POOL");
        assertThat(returned.getOwnerPrincipalId()).isNull();

        ArgumentCaptor<AppendDomainEventCommand> event = ArgumentCaptor.forClass(AppendDomainEventCommand.class);
        verify(outboxAppender, atLeast(4)).append(event.capture());
        assertThat(event.getAllValues()).anySatisfy(value ->
                assertThat(value.getEventType()).isEqualTo("crm.customer.owner_changed"));
        verify(mapper, times(2)).insertCustomerOwnerHistory(any());
    }

    @Test
    void createsLeadContactOpportunityAndFollowUp() {
        CrmCommandResult leadResult = service.execute(CrmCommand.builder().operation(CrmOperation.CREATE_LEAD)
                .idempotencyKey("crm-lead-create").runId("lead-run")
                .lead(CrmCommand.LeadDefinition.builder().leadCode("LEAD_001").leadName("Prospect")
                        .sourceCode("EXPO").status("NEW").ownerPrincipalId("principal-sales-1")
                        .contactChannelRef("wechat:masked").maskedContact("138****0001").build())
                .build(), "principal-admin-1");
        assertThat(leadResult.getAggregateType()).isEqualTo("LEAD");

        CrmCommandResult contactResult = service.execute(CrmCommand.builder().operation(CrmOperation.CREATE_CONTACT)
                .idempotencyKey("crm-contact-create").runId("contact-run")
                .contact(CrmCommand.ContactDefinition.builder().customerId("customer-2").contactName("Buyer")
                        .roleTitle("Manager").contactChannelRef("email:masked").maskedContact("bu***@ex.com")
                        .isPrimary(true).status("ACTIVE").build())
                .build(), "principal-admin-1");
        assertThat(contactResult.getAggregateType()).isEqualTo("CONTACT");

        CrmCommandResult opportunityResult = service.execute(CrmCommand.builder().operation(CrmOperation.CREATE_OPPORTUNITY)
                .idempotencyKey("crm-opportunity-create").runId("opp-run")
                .opportunity(CrmCommand.OpportunityDefinition.builder().customerId("customer-2").opportunityCode("OPP_001")
                        .opportunityName("Winter Bulk").stage("DISCOVERY").expectedAmountMinor(10000L)
                        .currencyCode("CNY").expectedCloseDate(LocalDate.parse("2026-09-01"))
                        .ownerPrincipalId("principal-sales-1").build())
                .build(), "principal-admin-1");
        assertThat(opportunityResult.getAggregateType()).isEqualTo("OPPORTUNITY");

        CrmCommandResult followUpResult = service.execute(CrmCommand.builder().operation(CrmOperation.RECORD_FOLLOW_UP)
                .idempotencyKey("crm-follow-up").runId("follow-run")
                .followUp(CrmCommand.FollowUpDefinition.builder().followUpId(UUID.randomUUID().toString()).subjectType("LEAD")
                        .subjectId(leadResult.getAggregateId()).methodCode("CALL").summary("intro call")
                        .nextFollowUpAt(LocalDateTime.parse("2026-08-10T10:00:00")).build())
                .build(), "principal-admin-1");
        assertThat(followUpResult.getAggregateType()).isEqualTo("FOLLOW_UP");

        ArgumentCaptor<AppendDomainEventCommand> event = ArgumentCaptor.forClass(AppendDomainEventCommand.class);
        verify(outboxAppender, atLeast(4)).append(event.capture());
        assertThat(event.getAllValues()).anySatisfy(value -> assertThat(value.getEventType()).isEqualTo("crm.lead.status_changed"));
        assertThat(event.getAllValues()).anySatisfy(value -> assertThat(value.getEventType()).isEqualTo("crm.contact.status_changed"));
        assertThat(event.getAllValues()).anySatisfy(value -> assertThat(value.getEventType()).isEqualTo("crm.opportunity.stage_changed"));
        assertThat(event.getAllValues()).anySatisfy(value -> assertThat(value.getEventType()).isEqualTo("crm.follow_up.recorded"));
    }

    @Test
    void replaysDuplicateOperationWithoutCallingOutboxAgain() {
        CrmCommand command = CrmCommand.builder().operation(CrmOperation.CREATE_CUSTOMER).idempotencyKey("crm-dup")
                .runId("dup-run")
                .customer(CrmCommand.CustomerDefinition.builder().customerCode("CUST_001").customerName("ACME")
                        .lifecycleStatus("ACTIVE").poolStatus("IN_POOL").build())
                .build();
        String requestHash = cn.hutool.crypto.digest.DigestUtil.sha256Hex("1\nprincipal-admin-1\n" + JsonUtils.toJsonString(command));
        CrmCommandResult first = CrmCommandResult.builder().operationId(11L).aggregateType("CUSTOMER")
                .aggregateId("customer-dup").aggregateVersion(1L).currentStatus("ACTIVE").build();
        when(mapper.selectOperationForUpdate(11L, 1L)).thenReturn(new CrmOperationDO().setOperationId(11L)
                .setTenantId(1L).setAttemptToken("existing").setRequestHash(requestHash).setStatus(CrmCommandServiceImpl.OPERATION_SUCCEEDED)
                .setResultJson(JsonUtils.toJsonString(first)));

        CrmCommandResult replay = service.execute(command, "principal-admin-1");

        assertThat(replay.isDuplicate()).isTrue();
        verify(outboxAppender, never()).append(any());
    }

    @Test
    void automationCommandResolvesVerifiedRpcActorAndOwnsNewLead() {
        when(actorPrincipalPort.resolveSystemAdmin(226L)).thenReturn("principal-sales-ai");
        CrmCommand command = CrmCommand.builder().operation(CrmOperation.CREATE_LEAD)
                .idempotencyKey("crm-automation-lead").runId("automation-run")
                .lead(CrmCommand.LeadDefinition.builder().leadCode("LEAD_AI_001").leadName("AI Prospect")
                        .sourceCode("AI_MANAGED_OUTREACH").status("NEW")
                        .contactChannelRef("restricted:abc").maskedContact("***0001").build())
                .build();

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(226L);
            CrmCommandResult result = service.execute(command);

            assertThat(result.getOwnerPrincipalId()).isEqualTo("principal-sales-ai");
            assertThat(lead.get().getOwnerPrincipalId()).isEqualTo("principal-sales-ai");
            verify(actorPrincipalPort).resolveSystemAdmin(226L);
        }
    }

    @Test
    void rejectsUnmaskedContact() {
        CrmCommand command = CrmCommand.builder().operation(CrmOperation.CREATE_LEAD).idempotencyKey("crm-invalid")
                .runId("invalid-run")
                .lead(CrmCommand.LeadDefinition.builder().leadCode("LEAD_001").leadName("Prospect")
                        .sourceCode("EXPO").status("NEW").maskedContact("13800000001").build())
                .build();

        assertThatThrownBy(() -> service.execute(command, "principal-admin-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maskedContact must be masked");
    }

    private static CrmCustomerDO clone(CrmCustomerDO value) {
        return new CrmCustomerDO().setCustomerId(value.getCustomerId()).setTenantId(value.getTenantId())
                .setCustomerCode(value.getCustomerCode()).setCustomerName(value.getCustomerName())
                .setLevelCode(value.getLevelCode()).setLifecycleStatus(value.getLifecycleStatus())
                .setPoolStatus(value.getPoolStatus()).setOwnerPrincipalId(value.getOwnerPrincipalId())
                .setSourceCode(value.getSourceCode()).setIndustryCode(value.getIndustryCode()).setRegionCode(value.getRegionCode())
                .setNextFollowUpAt(value.getNextFollowUpAt()).setVersion(value.getVersion()).setCreatedAt(value.getCreatedAt())
                .setUpdatedAt(value.getUpdatedAt());
    }

    private static CrmLeadDO clone(CrmLeadDO value) {
        return new CrmLeadDO().setLeadId(value.getLeadId()).setTenantId(value.getTenantId()).setLeadCode(value.getLeadCode())
                .setLeadName(value.getLeadName()).setSourceCode(value.getSourceCode()).setStatus(value.getStatus())
                .setOwnerPrincipalId(value.getOwnerPrincipalId()).setContactChannelRef(value.getContactChannelRef())
                .setMaskedContact(value.getMaskedContact()).setNextFollowUpAt(value.getNextFollowUpAt())
                .setVersion(value.getVersion()).setCreatedAt(value.getCreatedAt()).setUpdatedAt(value.getUpdatedAt());
    }

    private static CrmContactDO clone(CrmContactDO value) {
        return new CrmContactDO().setContactId(value.getContactId()).setTenantId(value.getTenantId()).setCustomerId(value.getCustomerId())
                .setContactName(value.getContactName()).setRoleTitle(value.getRoleTitle()).setContactChannelRef(value.getContactChannelRef())
                .setMaskedContact(value.getMaskedContact()).setIsPrimary(value.getIsPrimary()).setStatus(value.getStatus())
                .setVersion(value.getVersion()).setCreatedAt(value.getCreatedAt()).setUpdatedAt(value.getUpdatedAt());
    }

    private static CrmOpportunityDO clone(CrmOpportunityDO value) {
        return new CrmOpportunityDO().setOpportunityId(value.getOpportunityId()).setTenantId(value.getTenantId())
                .setOpportunityCode(value.getOpportunityCode()).setCustomerId(value.getCustomerId())
                .setOpportunityName(value.getOpportunityName()).setStage(value.getStage())
                .setExpectedAmountMinor(value.getExpectedAmountMinor()).setCurrencyCode(value.getCurrencyCode())
                .setExpectedCloseDate(value.getExpectedCloseDate()).setOwnerPrincipalId(value.getOwnerPrincipalId())
                .setVersion(value.getVersion()).setCreatedAt(value.getCreatedAt()).setUpdatedAt(value.getUpdatedAt());
    }
}
