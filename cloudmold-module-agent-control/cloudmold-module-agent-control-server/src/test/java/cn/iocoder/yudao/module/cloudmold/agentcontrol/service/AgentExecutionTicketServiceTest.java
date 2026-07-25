package cn.iocoder.yudao.module.cloudmold.agentcontrol.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.SkillTaskApprovalSigningProperties;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.SkillTaskApprovalSigningProperties.SigningKey;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentExecutionTicketCommand;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentExecutionTicketResult;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.ActorRoleGrant;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.AgentRunLease;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.Approval;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.ApprovalAuthorityGrant;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.AuditEvent;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.RoleActionPolicy;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.WorkOrder;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.mysql.AgentControlStoreMapper;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.approval.SkillTaskApprovalPermitClaims;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.approval.SkillTaskApprovalRefCodec;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.approval.SkillTaskApprovalScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.security.KeyPair;
import java.security.KeyPairGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentExecutionTicketServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-24T10:00:00Z");
    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    private final AgentControlStoreMapper mapper = mock(AgentControlStoreMapper.class);
    private final SkillTaskApprovalSigningProperties properties = new SkillTaskApprovalSigningProperties();
    private final HmacAgentExecutionPermitSigner signer = new HmacAgentExecutionPermitSigner(properties);
    private final AgentExecutionTicketService service = new AgentExecutionTicketService(
            mapper, properties, signer, Clock.fixed(NOW, ZoneOffset.UTC));

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        properties.setHmacSecret("");
        properties.setLegacyHmacEnabled(true);
        properties.setActiveKeyId("");
        properties.setIssueVersion("auto");
        properties.setMaxValidity(Duration.ofHours(4));
        properties.getKeys().clear();
    }

    @Test
    void issuesShortLivedCma1TicketForApprovedExecutableReadyWorkOrder() {
        TenantContextHolder.setTenantId(17L);
        properties.setHmacSecret(SECRET);
        properties.setMaxValidity(Duration.ofMinutes(15));
        WorkOrder workOrder = approvedExecutableReadyWorkOrder();
        Approval approval = approvedApproval(workOrder);
        ApprovalAuthorityGrant grant = activeApprovalGrant(workOrder, approval, 600);
        when(mapper.selectWorkOrderForUpdate(17L, "wo-r3-1")).thenReturn(workOrder);
        when(mapper.selectApprovalForUpdate(17L, "approval-r3-1")).thenReturn(approval);
        when(mapper.selectActionPolicy(17L, "buyer", "buyer.execute-replenishment"))
                .thenReturn(executionPolicy(workOrder));
        when(mapper.selectEffectiveActorRoleGrant(eq(17L), eq(101L), eq("buyer"), any()))
                .thenReturn(activeRoleGrant(101L, 600));
        when(mapper.selectEffectiveApprovalAuthorityGrant(eq(17L), eq(200L), eq("approval-r3-1"),
                eq("buyer"), eq("buyer.execute-replenishment"), eq("R3"), eq(approval.getScopeHash()), any()))
                .thenReturn(grant);
        when(mapper.insertAuditEvent(any())).thenReturn(1);

        AgentExecutionTicketResult result = service.issue(AgentExecutionTicketCommand.builder()
                .workOrderId("wo-r3-1").approvalId("approval-r3-1").workOrderExpectedVersion(3L)
                .validForSeconds(300L).build(), 101L);

        assertThat(result.getApprovalRef()).startsWith("cma1:approval-r3-1:");
        assertThat(result.getApprovalRefSha256()).isEqualTo(DigestUtil.sha256Hex(result.getApprovalRef()));
        assertThat(result.getExpiresAt()).isEqualTo(NOW.plusSeconds(300));

        SkillTaskApprovalRefCodec.ParsedApprovalRef parsed = SkillTaskApprovalRefCodec.parse(result.getApprovalRef());
        assertThat(parsed.approvalId()).isEqualTo("approval-r3-1");
        assertThat(parsed.expiresAt()).isEqualTo(NOW.plusSeconds(300));
        assertThat(SkillTaskApprovalRefCodec.sign(SECRET.getBytes(), SkillTaskApprovalRefCodec.message(
                new SkillTaskApprovalScope(17L, 101L, UserTypeEnum.ADMIN.getValue(),
                        workOrder.getSkillId(), workOrder.getSkillVersion(),
                        workOrder.getExecutionInputSha256(), workOrder.getRiskLevel()),
                parsed.approvalId(), parsed.expiresAt().getEpochSecond()))).isEqualTo(parsed.signature());
        verify(mapper).insertAuditEvent(argThat(event -> {
            String detail = event.getDetailJson();
            return event.getTenantId().equals(17L)
                    && event.getAggregateType().equals("role_approval")
                    && event.getAggregateId().equals("approval-r3-1")
                    && event.getEventType().equals("agent_control.execution_ticket.issued")
                    && detail.contains("\"approvalRefSha256\":\"" + result.getApprovalRefSha256() + "\"")
                    && detail.contains("\"workOrderId\":\"wo-r3-1\"")
                    && detail.contains("\"inputSha256\":\"" + workOrder.getExecutionInputSha256() + "\"")
                    && detail.contains("\"definitionClosureSha256\":\"" + workOrder.getSkillDefinitionClosureSha256() + "\"")
                    && !detail.contains(result.getApprovalRef())
                    && !detail.contains(SECRET);
        }));
        verify(mapper).selectEffectiveApprovalAuthorityGrant(eq(17L), eq(200L), eq("approval-r3-1"),
                eq("buyer"), eq("buyer.execute-replenishment"), eq("R3"), eq(approval.getScopeHash()), any());
    }

    @Test
    void issuesKeyedCma2TicketAndAuditsTheKeyIdentity() {
        TenantContextHolder.setTenantId(17L);
        properties.setLegacyHmacEnabled(false);
        properties.setActiveKeyId("risk-2026-07");
        properties.getKeys().put("risk-2026-07", new SigningKey().setSecret(SECRET)
                .setNotBefore(NOW.minusSeconds(60)).setExpiresAt(NOW.plusSeconds(600)));
        WorkOrder workOrder = stubSuccessfulIssuance();

        AgentExecutionTicketResult result = service.issue(command(), 101L);

        assertThat(result.getApprovalRef()).startsWith("cma2:risk-2026-07:approval-r3-1:");
        SkillTaskApprovalRefCodec.ParsedKeyedApprovalRef parsed =
                SkillTaskApprovalRefCodec.parseKeyed(result.getApprovalRef());
        assertThat(parsed.keyId()).isEqualTo("risk-2026-07");
        assertThat(parsed.issuedAt()).isEqualTo(NOW);
        assertThat(parsed.expiresAt()).isEqualTo(NOW.plusSeconds(300));
        assertThat(SkillTaskApprovalRefCodec.sign(SECRET.getBytes(), SkillTaskApprovalRefCodec.keyedMessage(
                new SkillTaskApprovalScope(17L, 101L, UserTypeEnum.ADMIN.getValue(),
                        workOrder.getSkillId(), workOrder.getSkillVersion(),
                        workOrder.getExecutionInputSha256(), workOrder.getRiskLevel()),
                parsed.keyId(), parsed.approvalId(), parsed.issuedAt().getEpochSecond(),
                parsed.expiresAt().getEpochSecond()))).isEqualTo(parsed.signature());
        verify(mapper).insertAuditEvent(argThat(event -> event.getDetailJson().contains(
                        "\"approvalRefVersion\":\"cma2\"")
                && event.getDetailJson().contains("\"signingKeyId\":\"risk-2026-07\"")
                && !event.getDetailJson().contains(result.getApprovalRef())
                && !event.getDetailJson().contains(SECRET)));
    }

    @Test
    void issuesClaimsBoundCma3TicketAndBindsTheFrozenRootRequestIdentity() {
        TenantContextHolder.setTenantId(17L);
        properties.setIssueVersion("cma3");
        properties.setHmacSecret(SECRET);
        WorkOrder workOrder = stubSuccessfulIssuance();

        AgentExecutionTicketResult result = service.issue(command(), 101L);

        assertThat(result.getApprovalRef()).startsWith("cma3:local-hmac:");
        SkillTaskApprovalRefCodec.ParsedClaimsApprovalRef parsed =
                SkillTaskApprovalRefCodec.parseClaims(result.getApprovalRef());
        SkillTaskApprovalPermitClaims claims = parsed.claims();
        assertThat(claims.workOrderId()).isEqualTo("wo-r3-1");
        assertThat(claims.approvalId()).isEqualTo("approval-r3-1");
        assertThat(claims.skillId()).isEqualTo(workOrder.getSkillId());
        assertThat(claims.skillVersion()).isEqualTo(workOrder.getSkillVersion());
        assertThat(claims.definitionClosureSha256()).isEqualTo(workOrder.getSkillDefinitionClosureSha256());
        assertThat(claims.inputSha256()).isEqualTo(workOrder.getExecutionInputSha256());
        assertThat(claims.subject()).isEqualTo("2:101");
        assertThat(claims.rootRequestIdentity()).isEqualTo(DigestUtil.sha256Hex(String.join("\n",
                "17", "wo-r3-1", "approval-r3-1", workOrder.getSkillId(), workOrder.getSkillVersion(),
                workOrder.getSkillDefinitionClosureSha256(), workOrder.getExecutionInputSha256(),
                workOrder.getRiskLevel(), "2:101")));
        assertThat(SkillTaskApprovalRefCodec.sign(SECRET.getBytes(),
                SkillTaskApprovalRefCodec.claimsMessage("local-hmac",
                        new String(Base64.getUrlDecoder().decode(result.getApprovalRef().split(":", -1)[2])))))
                .isEqualTo(parsed.signature());
    }

    @Test
    void issuesMissionPermitOnlyAfterLeaseClaimAndBindsTheCompleteFence() {
        TenantContextHolder.setTenantId(17L);
        properties.setIssueVersion("cma3");
        properties.setHmacSecret(SECRET);
        WorkOrder workOrder = stubSuccessfulMissionIssuance();

        AgentExecutionTicketResult result = service.issue(missionCommand("run-2", "worker-2", "lease-token-2", 3L),
                101L);

        SkillTaskApprovalPermitClaims claims = SkillTaskApprovalRefCodec.parseClaims(result.getApprovalRef()).claims();
        assertThat(claims.missionRunId()).isEqualTo("run-2");
        assertThat(claims.fencingToken()).isEqualTo(3L);
        assertThat(claims.leaseOwner()).isEqualTo("worker-2");
        assertThat(claims.leaseEpoch()).isEqualTo(4L);
        assertThat(claims.rootRequestIdentity()).isEqualTo(DigestUtil.sha256Hex(String.join("\n",
                "17", "wo-r3-1", "approval-r3-1", workOrder.getSkillId(), workOrder.getSkillVersion(),
                workOrder.getSkillDefinitionClosureSha256(), workOrder.getExecutionInputSha256(),
                workOrder.getRiskLevel(), "2:101", "run-2", "3", "worker-2", "4")));
        verify(mapper).insertAuditEvent(argThat(event -> event.getDetailJson().contains("\"missionRunId\":\"run-2\"")
                && event.getDetailJson().contains("\"fencingToken\":3")
                && event.getDetailJson().contains("\"leaseOwner\":\"worker-2\"")
                && event.getDetailJson().contains("\"leaseEpoch\":4")));
    }

    @Test
    void rejectsOldLeaseIdentityAfterSameOperatorTakeover() {
        TenantContextHolder.setTenantId(17L);
        properties.setIssueVersion("cma3");
        properties.setHmacSecret(SECRET);
        stubSuccessfulMissionIssuance();

        assertThatThrownBy(() -> service.issue(
                missionCommand("run-1", "worker-1", "lease-token-1", 2L), 101L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("mission execution ticket lease identity is stale");
        verify(mapper, never()).insertAuditEvent(argThat(event ->
                event.getEventType().equals("agent_control.execution_ticket.issued")));
    }

    @Test
    void rejectsMissionTicketBeforeAWorkerAcquiresTheLease() {
        TenantContextHolder.setTenantId(17L);
        properties.setIssueVersion("cma3");
        properties.setHmacSecret(SECRET);
        WorkOrder workOrder = approvedExecutableReadyWorkOrder().setMissionId("mission-1");
        when(mapper.selectWorkOrderForUpdate(17L, "wo-r3-1")).thenReturn(workOrder);

        assertThatThrownBy(() -> service.issue(
                missionCommand("run-1", "worker-1", "lease-token-1", 1L), 101L))
                .hasMessage("mission execution ticket requires an acquired IN_PROGRESS lease");
    }

    @Test
    void issuesPemRsaCma3TicketFromExternalKeyMaterial() throws Exception {
        TenantContextHolder.setTenantId(17L);
        KeyPair pair = rsaKeyPair();
        properties.setAuthorityMode("PEM_RSA");
        properties.setIssueVersion("cma3");
        properties.setActiveKeyId("risk-rsa-1");
        properties.setIssuer("risk.authority.cloudmold");
        properties.setAudience("cloudmold.skill-task");
        properties.getAsymmetricKeys().put("risk-rsa-1",
                new SkillTaskApprovalSigningProperties.AsymmetricSigningKey()
                        .setPrivateKeyPem(privateKeyPem(pair))
                        .setPublicKeyPem(publicKeyPem(pair))
                        .setNotBefore(NOW.minusSeconds(60))
                        .setExpiresAt(NOW.plusSeconds(600)));
        WorkOrder workOrder = stubSuccessfulIssuance();

        AgentExecutionTicketResult result = service.issue(command(), 101L);

        SkillTaskApprovalRefCodec.ParsedClaimsApprovalRef parsed =
                SkillTaskApprovalRefCodec.parseClaims(result.getApprovalRef());
        assertThat(parsed.claims().keyId()).isEqualTo("risk-rsa-1");
        assertThat(parsed.claims().issuer()).isEqualTo("risk.authority.cloudmold");
        assertThat(parsed.claims().audience()).isEqualTo("cloudmold.skill-task");
        java.security.Signature verifier = java.security.Signature.getInstance("SHA256withRSA");
        verifier.initVerify(pair.getPublic());
        verifier.update(SkillTaskApprovalRefCodec.claimsMessage("risk-rsa-1", parsed.payload()).getBytes());
        assertThat(verifier.verify(parsed.signature())).isTrue();
        assertThat(HexFormat.of().formatHex(parsed.signature())).doesNotContain(SECRET);
        assertThat(result.getApprovalRef()).startsWith("cma3:risk-rsa-1:");
        assertThat(parsed.claims().definitionClosureSha256()).isEqualTo(workOrder.getSkillDefinitionClosureSha256());
    }

    @Test
    void rejectsHmacIssuanceOutsideLocalTestAuthorityMode() {
        TenantContextHolder.setTenantId(17L);
        properties.setAuthorityMode("PEM_RSA");
        properties.setIssueVersion("cma2");
        properties.setHmacSecret(SECRET);
        stubSuccessfulIssuance();

        assertThatThrownBy(() -> service.issue(command(), 101L))
                .hasMessageContaining("LOCAL_TEST");
    }

    @Test
    void rejectsRevokedOrInsufficientLifetimeActiveSigningKey() {
        TenantContextHolder.setTenantId(17L);
        properties.setLegacyHmacEnabled(false);
        properties.setActiveKeyId("risk-revoked");
        properties.getKeys().put("risk-revoked", new SigningKey().setSecret(SECRET)
                .setNotBefore(NOW.minusSeconds(60)).setExpiresAt(NOW.plusSeconds(600)).setRevokedAt(NOW));
        stubSuccessfulIssuance();

        assertThatThrownBy(() -> service.issue(command(), 101L))
                .hasMessage("active Skill Task approval signing key is revoked");

        properties.setActiveKeyId("risk-expiring");
        properties.getKeys().put("risk-expiring", new SigningKey().setSecret(SECRET)
                .setNotBefore(NOW.minusSeconds(60)).setExpiresAt(NOW.plusSeconds(120)));
        assertThatThrownBy(() -> service.issue(command(), 101L))
                .hasMessage("execution ticket validity exceeds the active signing key lifetime");
    }

    @Test
    void clampsTicketExpiryToTheShortestRemainingAuthorityWindow() {
        TenantContextHolder.setTenantId(17L);
        properties.setHmacSecret(SECRET);
        properties.setMaxValidity(Duration.ofMinutes(15));
        WorkOrder workOrder = approvedExecutableReadyWorkOrder();
        Approval approval = approvedApproval(workOrder);
        when(mapper.selectWorkOrderForUpdate(17L, "wo-r3-1")).thenReturn(workOrder);
        when(mapper.selectApprovalForUpdate(17L, "approval-r3-1")).thenReturn(approval);
        when(mapper.selectActionPolicy(17L, "buyer", "buyer.execute-replenishment"))
                .thenReturn(executionPolicy(workOrder));
        when(mapper.selectEffectiveActorRoleGrant(eq(17L), eq(101L), eq("buyer"), any()))
                .thenReturn(activeRoleGrant(101L, 120));
        when(mapper.selectEffectiveApprovalAuthorityGrant(eq(17L), eq(200L), eq("approval-r3-1"),
                eq("buyer"), eq("buyer.execute-replenishment"), eq("R3"), eq(approval.getScopeHash()), any()))
                .thenReturn(activeApprovalGrant(workOrder, approval, 180));
        when(mapper.insertAuditEvent(any())).thenReturn(1);

        AgentExecutionTicketResult result = service.issue(command(), 101L);

        assertThat(result.getExpiresAt()).isEqualTo(NOW.plusSeconds(120));
    }

    @Test
    void rejectsTicketIssuanceWhenApprovalIsNotApproved() {
        TenantContextHolder.setTenantId(17L);
        properties.setHmacSecret(SECRET);
        WorkOrder workOrder = approvedExecutableReadyWorkOrder();
        Approval approval = approvedApproval(workOrder).setStatus("PENDING");
        when(mapper.selectWorkOrderForUpdate(17L, "wo-r3-1")).thenReturn(workOrder);
        when(mapper.selectApprovalForUpdate(17L, "approval-r3-1")).thenReturn(approval);
        when(mapper.selectEffectiveActorRoleGrant(eq(17L), eq(101L), eq("buyer"), any()))
                .thenReturn(activeRoleGrant(101L, 600));

        assertThatThrownBy(() -> service.issue(command(), 101L))
                .hasMessage("execution ticket requires APPROVED approval");
    }

    @Test
    void rejectsTicketIssuanceWhenFrozenScopeOrInputDrifts() {
        TenantContextHolder.setTenantId(17L);
        properties.setHmacSecret(SECRET);
        WorkOrder workOrder = approvedExecutableReadyWorkOrder().setExecutionInputSha256("b".repeat(64));
        Approval approval = approvedApproval(approvedExecutableReadyWorkOrder());
        when(mapper.selectWorkOrderForUpdate(17L, "wo-r3-1")).thenReturn(workOrder);
        when(mapper.selectApprovalForUpdate(17L, "approval-r3-1")).thenReturn(approval);
        when(mapper.selectEffectiveActorRoleGrant(eq(17L), eq(101L), eq("buyer"), any()))
                .thenReturn(activeRoleGrant(101L, 600));

        assertThatThrownBy(() -> service.issue(command(), 101L))
                .hasMessage("approval scope drifted after the request was frozen");
    }

    @Test
    void rejectsTicketIssuanceWhenCallerIsNotCurrentAssignee() {
        TenantContextHolder.setTenantId(17L);
        properties.setHmacSecret(SECRET);
        WorkOrder workOrder = approvedExecutableReadyWorkOrder().setAssigneeUserId(202L);
        Approval approval = approvedApproval(workOrder);
        when(mapper.selectWorkOrderForUpdate(17L, "wo-r3-1")).thenReturn(workOrder);
        when(mapper.selectApprovalForUpdate(17L, "approval-r3-1")).thenReturn(approval);

        assertThatThrownBy(() -> service.issue(command(), 101L))
                .hasMessage("only the authenticated assignee can issue the execution ticket");
    }

    @Test
    void rejectsTicketIssuanceWhenExactApproverGrantIsMissingOrDrifted() {
        TenantContextHolder.setTenantId(17L);
        properties.setHmacSecret(SECRET);
        WorkOrder workOrder = approvedExecutableReadyWorkOrder();
        Approval approval = approvedApproval(workOrder);
        when(mapper.selectWorkOrderForUpdate(17L, "wo-r3-1")).thenReturn(workOrder);
        when(mapper.selectApprovalForUpdate(17L, "approval-r3-1")).thenReturn(approval);
        when(mapper.selectActionPolicy(17L, "buyer", "buyer.execute-replenishment"))
                .thenReturn(executionPolicy(workOrder));
        when(mapper.selectEffectiveActorRoleGrant(eq(17L), eq(101L), eq("buyer"), any()))
                .thenReturn(activeRoleGrant(101L, 600));
        when(mapper.selectEffectiveApprovalAuthorityGrant(eq(17L), eq(200L), eq("approval-r3-1"),
                eq("buyer"), eq("buyer.execute-replenishment"), eq("R3"), eq(approval.getScopeHash()), any()))
                .thenReturn(null);

        assertThatThrownBy(() -> service.issue(command(), 101L))
                .hasMessage("approved execution has no exact active approver grant");
    }

    @Test
    void rejectsTicketIssuanceWhenNoRemainingGrantWindowExists() {
        TenantContextHolder.setTenantId(17L);
        properties.setHmacSecret(SECRET);
        WorkOrder workOrder = approvedExecutableReadyWorkOrder();
        Approval approval = approvedApproval(workOrder);
        when(mapper.selectWorkOrderForUpdate(17L, "wo-r3-1")).thenReturn(workOrder);
        when(mapper.selectApprovalForUpdate(17L, "approval-r3-1")).thenReturn(approval);
        when(mapper.selectActionPolicy(17L, "buyer", "buyer.execute-replenishment"))
                .thenReturn(executionPolicy(workOrder));
        when(mapper.selectEffectiveActorRoleGrant(eq(17L), eq(101L), eq("buyer"), any()))
                .thenReturn(activeRoleGrant(101L, 600));
        when(mapper.selectEffectiveApprovalAuthorityGrant(eq(17L), eq(200L), eq("approval-r3-1"),
                eq("buyer"), eq("buyer.execute-replenishment"), eq("R3"), eq(approval.getScopeHash()), any()))
                .thenReturn(activeApprovalGrant(workOrder, approval, -1));

        assertThatThrownBy(() -> service.issue(command(), 101L))
                .hasMessage("execution ticket has no remaining authority validity window");
        verify(mapper, never()).insertAuditEvent(any());
    }

    @Test
    void failsClosedWhenNoSigningSecretIsConfigured() {
        TenantContextHolder.setTenantId(17L);
        properties.setHmacSecret("");
        stubSuccessfulIssuance();

        assertThatThrownBy(() -> service.issue(command(), 101L))
                .hasMessageContaining("disabled because no signing secret is configured");
    }

    @Test
    void rejectsRequestedValidityThatExceedsConfiguredMaximum() {
        TenantContextHolder.setTenantId(17L);
        properties.setHmacSecret(SECRET);
        properties.setMaxValidity(Duration.ofMinutes(5));
        stubSuccessfulIssuance();

        assertThatThrownBy(() -> service.issue(AgentExecutionTicketCommand.builder()
                .workOrderId("wo-r3-1").approvalId("approval-r3-1").workOrderExpectedVersion(3L)
                .validForSeconds(301L).build(), 101L))
                .hasMessage("execution ticket validity exceeds the configured maximum");
    }

    @Test
    void failsClosedWhenAuditAppendFails() {
        TenantContextHolder.setTenantId(17L);
        properties.setHmacSecret(SECRET);
        WorkOrder workOrder = approvedExecutableReadyWorkOrder();
        Approval approval = approvedApproval(workOrder);
        when(mapper.selectWorkOrderForUpdate(17L, "wo-r3-1")).thenReturn(workOrder);
        when(mapper.selectApprovalForUpdate(17L, "approval-r3-1")).thenReturn(approval);
        when(mapper.selectActionPolicy(17L, "buyer", "buyer.execute-replenishment"))
                .thenReturn(executionPolicy(workOrder));
        when(mapper.selectEffectiveActorRoleGrant(eq(17L), eq(101L), eq("buyer"), any()))
                .thenReturn(activeRoleGrant(101L, 600));
        when(mapper.selectEffectiveApprovalAuthorityGrant(eq(17L), eq(200L), eq("approval-r3-1"),
                eq("buyer"), eq("buyer.execute-replenishment"), eq("R3"), eq(approval.getScopeHash()), any()))
                .thenReturn(activeApprovalGrant(workOrder, approval, 600));
        when(mapper.insertAuditEvent(any())).thenReturn(0);

        assertThatThrownBy(() -> service.issue(command(), 101L))
                .hasMessage("failed to append execution-ticket audit event");
    }

    private static AgentExecutionTicketCommand command() {
        return AgentExecutionTicketCommand.builder().workOrderId("wo-r3-1").approvalId("approval-r3-1")
                .workOrderExpectedVersion(3L).validForSeconds(300L).build();
    }

    private WorkOrder stubSuccessfulIssuance() {
        WorkOrder workOrder = approvedExecutableReadyWorkOrder();
        Approval approval = approvedApproval(workOrder);
        when(mapper.selectWorkOrderForUpdate(17L, "wo-r3-1")).thenReturn(workOrder);
        when(mapper.selectApprovalForUpdate(17L, "approval-r3-1")).thenReturn(approval);
        when(mapper.selectActionPolicy(17L, "buyer", "buyer.execute-replenishment"))
                .thenReturn(executionPolicy(workOrder));
        when(mapper.selectEffectiveActorRoleGrant(eq(17L), eq(101L), eq("buyer"), any()))
                .thenReturn(activeRoleGrant(101L, 600));
        when(mapper.selectEffectiveApprovalAuthorityGrant(eq(17L), eq(200L), eq("approval-r3-1"),
                eq("buyer"), eq("buyer.execute-replenishment"), eq("R3"), eq(approval.getScopeHash()), any()))
                .thenReturn(activeApprovalGrant(workOrder, approval, 600));
        when(mapper.insertAuditEvent(any())).thenReturn(1);
        return workOrder;
    }

    private WorkOrder stubSuccessfulMissionIssuance() {
        WorkOrder workOrder = approvedExecutableReadyWorkOrder().setMissionId("mission-1")
                .setStatus("IN_PROGRESS").setActiveRunId("run-2");
        Approval approval = approvedApproval(workOrder);
        when(mapper.selectWorkOrderForUpdate(17L, "wo-r3-1")).thenReturn(workOrder);
        when(mapper.selectRunLeaseForUpdate(17L, "wo-r3-1")).thenReturn(currentMissionLease());
        when(mapper.selectApprovalForUpdate(17L, "approval-r3-1")).thenReturn(approval);
        when(mapper.selectActionPolicy(17L, "buyer", "buyer.execute-replenishment"))
                .thenReturn(executionPolicy(workOrder));
        when(mapper.selectEffectiveActorRoleGrant(eq(17L), eq(101L), eq("buyer"), any()))
                .thenReturn(activeRoleGrant(101L, 600));
        when(mapper.selectEffectiveApprovalAuthorityGrant(eq(17L), eq(200L), eq("approval-r3-1"),
                eq("buyer"), eq("buyer.execute-replenishment"), eq("R3"), eq(approval.getScopeHash()), any()))
                .thenReturn(activeApprovalGrant(workOrder, approval, 600));
        when(mapper.insertAuditEvent(any())).thenReturn(1);
        return workOrder;
    }

    private static AgentExecutionTicketCommand missionCommand(String runId, String leaseOwner,
                                                              String leaseToken, long fencingToken) {
        return AgentExecutionTicketCommand.builder()
                .workOrderId("wo-r3-1").approvalId("approval-r3-1")
                .workOrderExpectedVersion(3L).validForSeconds(300L)
                .missionRunId(runId).leaseOwner(leaseOwner)
                .leaseToken(leaseToken).fencingToken(fencingToken).build();
    }

    private static AgentRunLease currentMissionLease() {
        return new AgentRunLease().setTenantId(17L).setWorkOrderId("wo-r3-1").setMissionId("mission-1")
                .setRunId("run-2").setActorUserId(101L).setRoleCode("buyer")
                .setLeaseOwner("worker-2").setLeaseToken("lease-token-2").setFencingToken(3L)
                .setLeaseUntil(java.time.LocalDateTime.ofInstant(NOW.plusSeconds(60), ZoneOffset.UTC))
                .setStatus("ACTIVE").setVersion(4L);
    }

    private static WorkOrder approvedExecutableReadyWorkOrder() {
        String inputSha = "a".repeat(64);
        return new WorkOrder().setWorkOrderId("wo-r3-1").setTenantId(17L).setRoleCode("buyer")
                .setActionCode("buyer.execute-replenishment").setStatus("READY")
                .setRequesterUserId(100L).setAssigneeUserId(101L).setApprovalId("approval-r3-1")
                .setActionPolicyId("policy-r3-1").setActionPolicyVersion(2L).setRiskLevel("R3")
                .setExecutionRequired(true).setSkillId("skill.cloudmold.commerce.full-chain-hsf.v1")
                .setSkillVersion("1.2.0").setSkillDefinitionClosureSha256("c".repeat(64))
                .setExecutionInputSha256(inputSha).setVersion(3L);
    }

    private static Approval approvedApproval(WorkOrder workOrder) {
        return new Approval().setApprovalId("approval-r3-1").setTenantId(17L).setWorkOrderId(workOrder.getWorkOrderId())
                .setActionCode(workOrder.getActionCode()).setRequesterUserId(workOrder.getRequesterUserId())
                .setApproverUserId(200L).setScopeHash(scopeHash(workOrder)).setStatus("APPROVED").setVersion(2L);
    }

    private static ApprovalAuthorityGrant exactApprovalGrant(WorkOrder workOrder, Approval approval) {
        return new ApprovalAuthorityGrant().setGrantId("approval-grant-1").setTenantId(17L)
                .setApproverUserId(200L).setRequesterUserId(workOrder.getRequesterUserId())
                .setApprovalId(approval.getApprovalId()).setRoleCode(workOrder.getRoleCode())
                .setActionCode(workOrder.getActionCode()).setRiskLevel(workOrder.getRiskLevel())
                .setScopeHash(approval.getScopeHash()).setStatus("ACTIVE").setVersion(1L);
    }

    private static ActorRoleGrant activeRoleGrant(long actorUserId, long validForSeconds) {
        return new ActorRoleGrant().setActorUserId(actorUserId).setRoleCode("buyer").setStatus("ACTIVE")
                .setValidUntil(java.time.LocalDateTime.ofInstant(NOW.plusSeconds(validForSeconds), ZoneOffset.UTC));
    }

    private static ApprovalAuthorityGrant activeApprovalGrant(WorkOrder workOrder, Approval approval, long validForSeconds) {
        return exactApprovalGrant(workOrder, approval)
                .setValidUntil(java.time.LocalDateTime.ofInstant(NOW.plusSeconds(validForSeconds), ZoneOffset.UTC));
    }

    private static RoleActionPolicy executionPolicy(WorkOrder workOrder) {
        return new RoleActionPolicy().setPolicyId(workOrder.getActionPolicyId()).setTenantId(17L)
                .setRoleCode(workOrder.getRoleCode()).setActionCode(workOrder.getActionCode())
                .setPermissionMode("ALLOW").setRiskLevel(workOrder.getRiskLevel()).setApprovalRequired(true)
                .setExecutionRequired(true).setSkillId(workOrder.getSkillId())
                .setSkillVersion(workOrder.getSkillVersion())
                .setSkillDefinitionClosureSha256(workOrder.getSkillDefinitionClosureSha256())
                .setEnabled(true).setVersion(workOrder.getActionPolicyVersion());
    }

    private static String scopeHash(WorkOrder workOrder) {
        return DigestUtil.sha256Hex(String.join("\n",
                String.valueOf(workOrder.getTenantId()), workOrder.getWorkOrderId(), workOrder.getRoleCode(),
                workOrder.getActionCode(), workOrder.getActionPolicyId(),
                String.valueOf(workOrder.getActionPolicyVersion()), workOrder.getSkillId(),
                workOrder.getSkillVersion(), workOrder.getSkillDefinitionClosureSha256(),
                workOrder.getExecutionInputSha256(), workOrder.getRiskLevel()));
    }

    private static KeyPair rsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static String privateKeyPem(KeyPair pair) {
        return pem("PRIVATE KEY", pair.getPrivate().getEncoded());
    }

    private static String publicKeyPem(KeyPair pair) {
        return pem("PUBLIC KEY", pair.getPublic().getEncoded());
    }

    private static String pem(String type, byte[] encoded) {
        return "-----BEGIN " + type + "-----\n"
                + java.util.Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(encoded)
                + "\n-----END " + type + "-----";
    }
}
