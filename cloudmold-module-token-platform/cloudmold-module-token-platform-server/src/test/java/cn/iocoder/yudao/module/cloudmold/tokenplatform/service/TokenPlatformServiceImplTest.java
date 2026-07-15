package cn.iocoder.yudao.module.cloudmold.tokenplatform.service;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.tokenplatform.api.*;
import cn.iocoder.yudao.module.cloudmold.tokenplatform.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.tokenplatform.dal.mysql.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TokenPlatformServiceImplTest {

    private static final long TENANT_ID = 7L;
    private static final Instant OCCURRED_AT = Instant.parse("2026-07-16T02:00:00Z");
    private static final String CORRELATION_ID = "6f9619ff-8b86-4d11-b42d-00cf4fc964ff";

    private final TokenPlatformOperationMapper operationMapper = mock(TokenPlatformOperationMapper.class);
    private final ModelOfferingMapper offeringMapper = mock(ModelOfferingMapper.class);
    private final PricingVersionMapper pricingMapper = mock(PricingVersionMapper.class);
    private final AccessCredentialMapper credentialMapper = mock(AccessCredentialMapper.class);
    private final QuotaAccountMapper accountMapper = mock(QuotaAccountMapper.class);
    private final QuotaLedgerMapper ledgerMapper = mock(QuotaLedgerMapper.class);
    private final InvocationUsageMapper usageMapper = mock(InvocationUsageMapper.class);
    private final OutboxAppender outboxAppender = mock(OutboxAppender.class);
    private final AtomicReference<String> attemptToken = new AtomicReference<>();
    private final TokenPlatformServiceImpl service = new TokenPlatformServiceImpl(operationMapper, offeringMapper,
            pricingMapper, credentialMapper, accountMapper, ledgerMapper, usageMapper, outboxAppender);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
        when(operationMapper.insertOrResolve(anyLong(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    attemptToken.set(invocation.getArgument(4));
                    return 1;
                });
        when(operationMapper.selectLastInsertId()).thenReturn(42L);
        when(operationMapper.selectForUpdate(42L, TENANT_ID)).thenAnswer(ignored -> new TokenPlatformOperationDO()
                .setOperationId(42L).setTenantId(TENANT_ID).setAttemptToken(attemptToken.get()).setStatus(0));
        when(operationMapper.markSucceeded(anyLong(), anyLong(), anyString(), anyString(), anyString(), any()))
                .thenReturn(1);
        when(offeringMapper.insert(any(ModelOfferingDO.class))).thenReturn(1);
        when(pricingMapper.insert(any(PricingVersionDO.class))).thenReturn(1);
        when(credentialMapper.insert(any(AccessCredentialDO.class))).thenReturn(1);
        when(accountMapper.insert(any(QuotaAccountDO.class))).thenReturn(1);
        when(ledgerMapper.insert(any(QuotaLedgerDO.class))).thenReturn(1);
        when(usageMapper.insert(any(InvocationUsageDO.class))).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void createsOfferingWithImmutablePricingAndCanonicalEvent() {
        TokenPlatformCommandResult result = service.execute(createOfferingCommand("offering-create-1"));

        assertThat(result.getAggregateType()).isEqualTo("token_platform_model_offering");
        assertThat(result.getAggregateId()).isEqualTo("offering-1");
        assertThat(result.getAggregateVersion()).isEqualTo(1L);
        assertThat(result.getStatus()).isEqualTo("DRAFT");

        ArgumentCaptor<ModelOfferingDO> offering = ArgumentCaptor.forClass(ModelOfferingDO.class);
        ArgumentCaptor<PricingVersionDO> pricing = ArgumentCaptor.forClass(PricingVersionDO.class);
        verify(offeringMapper).insert(offering.capture());
        verify(pricingMapper).insert(pricing.capture());
        assertThat(offering.getValue()).extracting(ModelOfferingDO::getTenantId,
                        ModelOfferingDO::getOfferingCode, ModelOfferingDO::getProviderCode,
                        ModelOfferingDO::getCurrentPricingVersionId, ModelOfferingDO::getStatus)
                .containsExactly(TENANT_ID, "gpt-primary", "OPENAI", "price-1", "DRAFT");
        assertThat(pricing.getValue()).extracting(PricingVersionDO::getPricingVersionNo,
                        PricingVersionDO::getInputPriceMicrounitsPerMillionTokens,
                        PricingVersionDO::getCachedInputPriceMicrounitsPerMillionTokens,
                        PricingVersionDO::getOutputPriceMicrounitsPerMillionTokens)
                .containsExactly(1L, 2_000_000L, 500_000L, 4_000_000L);

        AppendDomainEventCommand event = captureSingleEvent();
        assertCanonicalEvent(event, "token_platform.model_offering.status_changed",
                "token_platform_model_offering", "offering-1", 1L);
        assertThat(event.getPayload()).containsEntry("offering_id", "offering-1")
                .containsEntry("previous_status", null).containsEntry("current_status", "DRAFT")
                .containsEntry("operation", "CREATE_MODEL_OFFERING");
    }

    @Test
    void replaysImmutableResultWithoutDomainMutationOrDuplicateEvent() {
        TokenPlatformCommand command = createOfferingCommand("offering-replay-1");
        TokenPlatformCommandResult first = TokenPlatformCommandResult.builder().operationId(42L)
                .aggregateType("token_platform_model_offering").aggregateId("offering-1")
                .aggregateVersion(1L).status("DRAFT").duplicate(false).build();
        AtomicReference<String> requestHash = new AtomicReference<>();
        when(operationMapper.insertOrResolve(anyLong(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    requestHash.set(invocation.getArgument(3));
                    return 0;
                });
        when(operationMapper.selectForUpdate(42L, TENANT_ID)).thenReturn(new TokenPlatformOperationDO()
                .setOperationId(42L).setTenantId(TENANT_ID).setAttemptToken("existing-attempt")
                .setRequestHash(TokenPlatformServiceImpl.fingerprint(TENANT_ID, command))
                .setStatus(TokenPlatformServiceImpl.OPERATION_SUCCEEDED).setResultJson(JsonUtils.toJsonString(first)));

        TokenPlatformCommandResult replay = service.execute(command);

        assertThat(requestHash.get()).isEqualTo(TokenPlatformServiceImpl.fingerprint(TENANT_ID, command));
        assertThat(replay.isDuplicate()).isTrue();
        assertThat(replay.getAggregateId()).isEqualTo("offering-1");
        verifyNoInteractions(offeringMapper, pricingMapper, credentialMapper, accountMapper, ledgerMapper,
                usageMapper, outboxAppender);
        verify(operationMapper, never()).markSucceeded(anyLong(), anyLong(), anyString(), anyString(), anyString(),
                any());
    }

    @Test
    void storesOnlyCredentialMetadataAndNeverEmitsSecretLocator() {
        when(offeringMapper.selectForUpdate(TENANT_ID, "offering-1")).thenReturn(activeOffering());
        TokenPlatformCommand command = envelope(TokenPlatformOperation.CREATE_ACCESS_CREDENTIAL,
                "credential-create-1").accessCredential(TokenPlatformCommand.AccessCredentialDefinition.builder()
                .credentialId("credential-1").principalId("principal-1").offeringId("offering-1")
                .credentialFingerprint("A".repeat(64)).secretRef("vault://cloudmold/token-platform/credential-1")
                .last4("ab12").keyVersion(1).expiresAt(OCCURRED_AT.plus(Duration.ofDays(30))).build()).build();

        service.execute(command);

        ArgumentCaptor<AccessCredentialDO> credential = ArgumentCaptor.forClass(AccessCredentialDO.class);
        verify(credentialMapper).insert(credential.capture());
        assertThat(credential.getValue().getCredentialFingerprint()).isEqualTo("a".repeat(64));
        assertThat(credential.getValue().getSecretRef()).isEqualTo("vault://cloudmold/token-platform/credential-1");
        assertThat(credential.getValue().getLast4()).isEqualTo("ab12");
        AppendDomainEventCommand event = captureSingleEvent();
        assertCanonicalEvent(event, "token_platform.access_credential.status_changed",
                "token_platform_access_credential", "credential-1", 1L);
        assertThat(event.getPayload()).containsEntry("credential_fingerprint", "a".repeat(64))
                .containsEntry("key_version", 1).doesNotContainKeys("secret_ref", "last4", "api_key", "key");
        assertThat(JsonUtils.toJsonString(event.getPayload())).doesNotContain("vault://");
    }

    @Test
    void rejectsCredentialValueDisguisedAsSecretLocator() {
        TokenPlatformCommand command = envelope(TokenPlatformOperation.CREATE_ACCESS_CREDENTIAL,
                "credential-secret-1").accessCredential(TokenPlatformCommand.AccessCredentialDefinition.builder()
                .principalId("principal-1").offeringId("offering-1").credentialFingerprint("a".repeat(64))
                .secretRef("vault://providers/openai/sk-super-secret").last4("cret").keyVersion(1).build()).build();

        assertThatThrownBy(() -> service.execute(command)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("secretRef must not contain a credential value");
        verifyNoInteractions(credentialMapper, outboxAppender);
    }

    @Test
    void opensTenantScopedQuotaAccountWithZeroSumOpeningLedger() {
        TokenPlatformCommand command = envelope(TokenPlatformOperation.OPEN_QUOTA_ACCOUNT, "quota-open-1")
                .quotaAccount(TokenPlatformCommand.QuotaAccountDefinition.builder().accountId("account-1")
                        .principalId("principal-1").build()).build();

        service.execute(command);

        ArgumentCaptor<QuotaAccountDO> account = ArgumentCaptor.forClass(QuotaAccountDO.class);
        ArgumentCaptor<QuotaLedgerDO> ledger = ArgumentCaptor.forClass(QuotaLedgerDO.class);
        verify(accountMapper).insert(account.capture());
        verify(ledgerMapper).insert(ledger.capture());
        assertThat(account.getValue()).extracting(QuotaAccountDO::getTenantId, QuotaAccountDO::getBalanceMicrounits,
                QuotaAccountDO::getVersion).containsExactly(TENANT_ID, 0L, 1L);
        assertThat(ledger.getValue()).extracting(QuotaLedgerDO::getEntryType,
                        QuotaLedgerDO::getSignedDeltaMicrounits, QuotaLedgerDO::getBalanceAfterMicrounits,
                        QuotaLedgerDO::getAccountVersion)
                .containsExactly("OPENING", 0L, 0L, 1L);
        AppendDomainEventCommand event = captureSingleEvent();
        assertCanonicalEvent(event, "token_platform.quota.ledger_posted", "token_platform_quota_account",
                "account-1", 1L);
        assertThat(event.getPayload()).containsEntry("signed_delta_microunits", 0L)
                .containsEntry("balance_after_microunits", 0L);
    }

    @Test
    void recordsUsageWithExactTokenPricingQuotaConservationAndTwoOutboxFacts() {
        when(accountMapper.selectForUpdate(TENANT_ID, "account-1")).thenReturn(new QuotaAccountDO()
                .setAccountId("account-1").setTenantId(TENANT_ID).setPrincipalId("principal-1")
                .setBalanceMicrounits(10_000L).setStatus("ACTIVE").setVersion(2L));
        when(credentialMapper.selectForUpdate(TENANT_ID, "credential-1")).thenReturn(new AccessCredentialDO()
                .setCredentialId("credential-1").setTenantId(TENANT_ID).setPrincipalId("principal-1")
                .setOfferingId("offering-1").setStatus("ACTIVE").setExpiresAt(LocalDateTime.ofInstant(
                        OCCURRED_AT.plus(Duration.ofDays(1)), ZoneOffset.UTC)));
        when(offeringMapper.selectForUpdate(TENANT_ID, "offering-1")).thenReturn(activeOffering());
        when(pricingMapper.selectOneById(TENANT_ID, "price-1")).thenReturn(new PricingVersionDO()
                .setPricingVersionId("price-1").setTenantId(TENANT_ID).setOfferingId("offering-1")
                .setInputPriceMicrounitsPerMillionTokens(2_000_000L)
                .setCachedInputPriceMicrounitsPerMillionTokens(500_000L)
                .setOutputPriceMicrounitsPerMillionTokens(4_000_000L)
                .setEffectiveAt(LocalDateTime.ofInstant(OCCURRED_AT.minusSeconds(1), ZoneOffset.UTC)));
        when(accountMapper.updateBalanceCas(eq(TENANT_ID), eq("account-1"), eq(2L), eq(7_100L), any()))
                .thenReturn(1);

        TokenPlatformCommand command = invocationCommand(2_900L, 1_300L);
        service.execute(command);

        verify(accountMapper).updateBalanceCas(eq(TENANT_ID), eq("account-1"), eq(2L), eq(7_100L), any());
        ArgumentCaptor<QuotaLedgerDO> ledger = ArgumentCaptor.forClass(QuotaLedgerDO.class);
        ArgumentCaptor<InvocationUsageDO> usage = ArgumentCaptor.forClass(InvocationUsageDO.class);
        verify(ledgerMapper).insert(ledger.capture());
        verify(usageMapper).insert(usage.capture());
        assertThat(ledger.getValue()).extracting(QuotaLedgerDO::getEntryType,
                        QuotaLedgerDO::getSignedDeltaMicrounits, QuotaLedgerDO::getBalanceAfterMicrounits,
                        QuotaLedgerDO::getAccountVersion)
                .containsExactly("INVOCATION_USAGE", -2_900L, 7_100L, 3L);
        assertThat(usage.getValue()).extracting(InvocationUsageDO::getInputTokens,
                        InvocationUsageDO::getCachedInputTokens, InvocationUsageDO::getOutputTokens,
                        InvocationUsageDO::getTotalTokens, InvocationUsageDO::getQuotaCostMicrounits,
                        InvocationUsageDO::getPricingVersionId)
                .containsExactly(1_000L, 200L, 300L, 1_300L, 2_900L, "price-1");
        assertThat(usage.getValue().getLedgerEntryId()).isEqualTo(ledger.getValue().getLedgerEntryId());

        ArgumentCaptor<AppendDomainEventCommand> events = ArgumentCaptor.forClass(AppendDomainEventCommand.class);
        verify(outboxAppender, times(2)).append(events.capture());
        assertThat(events.getAllValues()).extracting(AppendDomainEventCommand::getEventType)
                .containsExactly("token_platform.quota.ledger_posted", "token_platform.invocation.usage_recorded");
        assertCanonicalEvent(events.getAllValues().get(0), "token_platform.quota.ledger_posted",
                "token_platform_quota_account", "account-1", 3L);
        AppendDomainEventCommand usageEvent = events.getAllValues().get(1);
        assertCanonicalEvent(usageEvent, "token_platform.invocation.usage_recorded",
                "token_platform_invocation_usage", "usage-1", 1L);
        assertThat(usageEvent.getPayload()).containsEntry("input_tokens", 1_000L)
                .containsEntry("cached_input_tokens", 200L).containsEntry("output_tokens", 300L)
                .containsEntry("total_tokens", 1_300L).containsEntry("duration_millis", 875L)
                .containsEntry("quota_cost_microunits", 2_900L).containsEntry("pricing_version_id", "price-1")
                .containsEntry("ledger_entry_id", ledger.getValue().getLedgerEntryId());
    }

    @Test
    void rejectsUsageWhenTokenConservationDoesNotHoldBeforeBalanceMutation() {
        TokenPlatformCommand command = invocationCommand(2_900L, 1_301L);

        assertThatThrownBy(() -> service.execute(command)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("totalTokens must equal inputTokens + outputTokens");
        verify(accountMapper, never()).updateBalanceCas(anyLong(), anyString(), anyLong(), anyLong(), any());
        verifyNoInteractions(ledgerMapper, usageMapper, outboxAppender);
    }

    @Test
    void migrationEnforcesTenantScopeAppendOnlyLedgerAndNoSecretValueColumn() throws IOException {
        String resource = "db/migration/V20260716_01__cloudmold_token_platform_first_slice.sql";
        String sql;
        try (var input = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(resource))) {
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        }

        assertThat(sql).contains("create table cloudmold_token_platform_model_offering",
                "create table cloudmold_token_platform_pricing_version",
                "create table cloudmold_token_platform_access_credential",
                "create table cloudmold_token_platform_quota_account",
                "create table cloudmold_token_platform_quota_ledger",
                "create table cloudmold_token_platform_invocation_usage",
                "unique key uk_tp_usage_request (tenant_id, request_id)",
                "unique key uk_tp_ledger_account_ver (tenant_id, account_id, account_version)",
                "credential_fingerprint char(64) not null", "secret_ref varchar(256) not null",
                "total_tokens = input_tokens + output_tokens");
        assertThat(sql).doesNotContain("api_key", "access_key", "secret_value", "credential_value");
    }

    private TokenPlatformCommand invocationCommand(long cost, long totalTokens) {
        return envelope(TokenPlatformOperation.RECORD_INVOCATION_USAGE, "usage-record-1")
                .invocationUsage(TokenPlatformCommand.InvocationUsageDefinition.builder().usageId("usage-1")
                        .requestId("request-1").accountId("account-1").principalId("principal-1")
                        .credentialId("credential-1").offeringId("offering-1").pricingVersionId("price-1")
                        .resultStatus("SUCCEEDED").inputTokens(1_000L).cachedInputTokens(200L).outputTokens(300L)
                        .totalTokens(totalTokens).durationMillis(875L).quotaCostMicrounits(cost)
                        .expectedAccountVersion(2L).build()).build();
    }

    private TokenPlatformCommand createOfferingCommand(String key) {
        return envelope(TokenPlatformOperation.CREATE_MODEL_OFFERING, key)
                .modelOffering(TokenPlatformCommand.ModelOfferingDefinition.builder().offeringId("offering-1")
                        .offeringCode("gpt-primary").providerCode("openai").modelCode("gpt-5.4")
                        .pricing(TokenPlatformCommand.PricingVersionDefinition.builder().pricingVersionId("price-1")
                                .inputPriceMicrounitsPerMillionTokens(2_000_000L)
                                .cachedInputPriceMicrounitsPerMillionTokens(500_000L)
                                .outputPriceMicrounitsPerMillionTokens(4_000_000L).build()).build()).build();
    }

    private static TokenPlatformCommand.TokenPlatformCommandBuilder envelope(TokenPlatformOperation operation,
                                                                               String key) {
        return TokenPlatformCommand.builder().operation(operation).idempotencyKey(key)
                .correlationId(CORRELATION_ID).occurredAt(OCCURRED_AT);
    }

    private static ModelOfferingDO activeOffering() {
        return new ModelOfferingDO().setOfferingId("offering-1").setTenantId(TENANT_ID)
                .setOfferingCode("gpt-primary").setProviderCode("OPENAI").setModelCode("gpt-5.4")
                .setCurrentPricingVersionId("price-1").setStatus("ACTIVE").setVersion(2L);
    }

    private AppendDomainEventCommand captureSingleEvent() {
        ArgumentCaptor<AppendDomainEventCommand> event = ArgumentCaptor.forClass(AppendDomainEventCommand.class);
        verify(outboxAppender).append(event.capture());
        return event.getValue();
    }

    private static void assertCanonicalEvent(AppendDomainEventCommand event, String eventType,
                                             String aggregateType, String aggregateId, long version) {
        assertThat(event.getEventType()).isEqualTo(eventType);
        assertThat(event.getSchemaVersion()).isEqualTo(1);
        assertThat(event.getSourceSystem()).isEqualTo("cloudmold-token-platform");
        assertThat(event.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(event.getAggregateType()).isEqualTo(aggregateType);
        assertThat(event.getAggregateId()).isEqualTo(aggregateId);
        assertThat(event.getAggregateVersion()).isEqualTo(version);
        assertThat(event.getOccurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(event.getCorrelationId()).isEqualTo(CORRELATION_ID);
        assertThat(event.getDestination()).isEqualTo("lakehouse");
        assertThat(event.getPayload()).doesNotContainKey("tenant_id");
    }
}
