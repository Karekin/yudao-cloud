package cn.iocoder.yudao.module.cloudmold.identity.service;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventResult;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.identity.api.*;
import cn.iocoder.yudao.module.cloudmold.identity.api.source.SourceAccountValidationPort;
import cn.iocoder.yudao.module.cloudmold.identity.dal.dataobject.IdentityOperationDO;
import cn.iocoder.yudao.module.cloudmold.identity.dal.dataobject.PrincipalDO;
import cn.iocoder.yudao.module.cloudmold.identity.dal.dataobject.SourceIdentityDO;
import cn.iocoder.yudao.module.cloudmold.identity.dal.mysql.IdentityOperationMapper;
import cn.iocoder.yudao.module.cloudmold.identity.dal.mysql.PrincipalMapper;
import cn.iocoder.yudao.module.cloudmold.identity.dal.mysql.SourceIdentityMapper;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IdentityCommandServiceImplTest {

    private final IdentityOperationMapper operationMapper = mock(IdentityOperationMapper.class);
    private final PrincipalMapper principalMapper = mock(PrincipalMapper.class);
    private final SourceIdentityMapper sourceIdentityMapper = mock(SourceIdentityMapper.class);
    private final SourceAccountValidationPort sourceValidator = mock(SourceAccountValidationPort.class);
    private final OutboxAppender outboxAppender = mock(OutboxAppender.class);
    private final AtomicReference<String> attemptToken = new AtomicReference<>();
    private final IdentityCommandServiceImpl service = new IdentityCommandServiceImpl(operationMapper,
            principalMapper, sourceIdentityMapper, List.of(sourceValidator), outboxAppender);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        when(sourceValidator.supports("SYSTEM", "SYSTEM_ADMIN_USER")).thenReturn(true);
        when(operationMapper.insertOrResolve(anyLong(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> { attemptToken.set(invocation.getArgument(4)); return 1; });
        when(operationMapper.selectLastInsertId()).thenReturn(11L);
        when(operationMapper.selectForUpdate(11L, 1L)).thenAnswer(ignored -> new IdentityOperationDO()
                .setOperationId(11L).setTenantId(1L).setAttemptToken(attemptToken.get()).setStatus(0));
        when(operationMapper.markSucceeded(anyLong(), anyLong(), anyString(), anyString(), any())).thenReturn(1);
        when(principalMapper.insert(any(PrincipalDO.class))).thenReturn(1);
        when(sourceIdentityMapper.insert(any(SourceIdentityDO.class))).thenReturn(1);
        when(outboxAppender.append(any())).thenReturn(new AppendDomainEventResult("event-1", "a".repeat(64), false));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldCreatePrincipalAndLinkValidatedSourceWithCanonicalEvent() {
        LinkSourceIdentityResult result = service.linkSource(command("identity-link-1"));

        assertThat(result.getPrincipalId()).isNotBlank().isNotEqualTo("42");
        assertThat(result.getPrincipalStatus()).isEqualTo("ACTIVE");
        assertThat(result.getAggregateVersion()).isEqualTo(1L);
        verify(sourceValidator).requireActive(1L, "SYSTEM", "SYSTEM_ADMIN_USER", "42");
        verify(principalMapper).insert(org.mockito.ArgumentMatchers.<PrincipalDO>argThat(row -> row.getTenantId().equals(1L)
                && row.getPrincipalId().equals(result.getPrincipalId()) && row.getVersion().equals(1L)));
        verify(sourceIdentityMapper).insert(org.mockito.ArgumentMatchers.<SourceIdentityDO>argThat(row -> row.getPrincipalId().equals(result.getPrincipalId())
                && row.getSourceSystem().equals("SYSTEM") && row.getSourceType().equals("SYSTEM_ADMIN_USER")
                && row.getSourceId().equals("42")));
        ArgumentCaptor<AppendDomainEventCommand> event = ArgumentCaptor.forClass(AppendDomainEventCommand.class);
        verify(outboxAppender).append(event.capture());
        assertThat(event.getValue().getEventType()).isEqualTo("identity.source.linked");
        assertThat(event.getValue().getTenantId()).isEqualTo(1L);
        assertThat(event.getValue().getPayload()).containsEntry("principal_id", result.getPrincipalId())
                .containsEntry("source_id", "42").doesNotContainKey("tenant_id");
    }

    @Test
    void shouldReplayImmutableFirstResultWithoutRevalidatingSource() {
        LinkSourceIdentityCommand command = command("identity-link-replay");
        LinkSourceIdentityResult first = LinkSourceIdentityResult.builder().operationId(11L)
                .principalId("10000000-0000-4000-8000-000000000001")
                .sourceIdentityId("10000000-0000-4000-8000-000000000002")
                .principalStatus("ACTIVE").aggregateVersion(1L).duplicate(false).build();
        AtomicReference<String> requestHash = new AtomicReference<>();
        when(operationMapper.insertOrResolve(anyLong(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> { requestHash.set(invocation.getArgument(3)); return 0; });
        when(operationMapper.selectForUpdate(11L, 1L)).thenAnswer(ignored -> new IdentityOperationDO()
                .setOperationId(11L).setTenantId(1L).setAttemptToken("existing")
                .setRequestHash(requestHash.get()).setStatus(10).setResultJson(JsonUtils.toJsonString(first)));

        LinkSourceIdentityResult replay = service.linkSource(command);

        assertThat(replay.getDuplicate()).isTrue();
        assertThat(replay.getPrincipalId()).isEqualTo(first.getPrincipalId());
        verifyNoInteractions(sourceValidator);
        verifyNoInteractions(principalMapper);
        verifyNoInteractions(sourceIdentityMapper);
        verifyNoInteractions(outboxAppender);
    }

    @Test
    void shouldRejectAlreadyLinkedSource() {
        when(sourceIdentityMapper.selectActiveBySource(1L, "SYSTEM", "SYSTEM_ADMIN_USER", "42"))
                .thenReturn(new SourceIdentityDO().setPrincipalId("existing-principal"));

        assertThatThrownBy(() -> service.linkSource(command("identity-link-conflict")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("source identity is already linked");
        verify(principalMapper, never()).insert((PrincipalDO) any());
        verify(outboxAppender, never()).append(any());
    }

    @Test
    void shouldFailClosedWhenNoSourceValidatorSupportsReference() {
        when(sourceValidator.supports("SYSTEM", "SYSTEM_ADMIN_USER")).thenReturn(false);

        assertThatThrownBy(() -> service.linkSource(command("identity-link-no-validator")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("source account type is not supported");
    }

    @Test
    void shouldRejectLinkWithoutTenantBeforeCallingSourceAuthority() {
        TenantContextHolder.clear();

        assertThatThrownBy(() -> service.linkSource(command("identity-link-no-tenant")))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("TenantContextHolder");
        verifyNoInteractions(sourceValidator);
        verifyNoInteractions(principalMapper);
        verifyNoInteractions(sourceIdentityMapper);
        verifyNoInteractions(outboxAppender);
    }

    @Test
    void shouldRoundTripLinkContractWithoutLosingSourceIdentity() {
        LinkSourceIdentityCommand original = command("identity-link-serialization");

        LinkSourceIdentityCommand copy = JsonUtils.parseObject(
                JsonUtils.toJsonString(original), LinkSourceIdentityCommand.class);

        assertThat(copy).usingRecursiveComparison().isEqualTo(original);
    }

    private static LinkSourceIdentityCommand command(String key) {
        return LinkSourceIdentityCommand.builder().idempotencyKey(key).runId("identity-run-1")
                .principalType("PLATFORM_OPERATOR").sourceSystem("SYSTEM").sourceType("SYSTEM_ADMIN_USER")
                .sourceId("42").correlationId("6f9619ff-8b86-d011-b42d-00cf4fc964ff")
                .occurredAt(Instant.parse("2026-07-15T00:00:00Z")).build();
    }
}
