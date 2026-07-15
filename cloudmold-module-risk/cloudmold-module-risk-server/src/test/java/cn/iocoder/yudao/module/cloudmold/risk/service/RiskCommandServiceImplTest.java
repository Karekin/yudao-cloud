package cn.iocoder.yudao.module.cloudmold.risk.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.risk.api.*;
import cn.iocoder.yudao.module.cloudmold.risk.dal.dataobject.RiskRecords.*;
import cn.iocoder.yudao.module.cloudmold.risk.dal.mysql.RiskStoreMapper;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RiskCommandServiceImplTest {
    private final RiskStoreMapper mapper = mock(RiskStoreMapper.class);
    private final RiskEventService eventService = mock(RiskEventService.class);
    private final RiskCommandServiceImpl service = new RiskCommandServiceImpl(mapper, eventService);
    private final Map<String, Operation> operations = new HashMap<>();
    private final Map<String, Policy> policies = new HashMap<>();
    private final Map<String, Relation> relations = new HashMap<>();
    private final Map<String, Cluster> clusters = new HashMap<>();
    private final Map<String, ReviewCase> reviews = new HashMap<>();
    private final Map<String, Decision> decisions = new HashMap<>();
    private final Set<String> clusterMembers = new HashSet<>();
    private final AtomicLong operationSequence = new AtomicLong();
    private final AtomicReference<Long> lastOperationId = new AtomicReference<>();

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
    void shouldPublishImmutablePolicyVersionReplayIdempotentlyAndRejectStaleVersion() {
        RiskView policy = service.execute(createPolicy("policy-create-01", "ACCOUNT_GRAPH"));
        RiskCommand publish = base(RiskOperation.PUBLISH_POLICY_VERSION, "policy-publish-01")
                .policyId(policy.getPolicyId()).expectedVersion(0L).approvedByPrincipalId("principal-reviewer-1")
                .effectiveFrom(Instant.now().plusSeconds(60)).rules(List.of(rule("SHARED_DEVICE"))).build();

        RiskView first = service.execute(publish);
        RiskView replay = service.execute(publish);
        assertThat(first.getPolicyVersion()).isEqualTo(1L);
        assertThat(first.getPolicyStatus()).isEqualTo("PUBLISHED");
        assertThat(replay.getDuplicate()).isTrue();
        verify(mapper, times(1)).insertPolicyVersion(any(PolicyVersion.class));
        verify(mapper, times(1)).insertPolicyRule(any(PolicyRule.class));
        verify(eventService, times(1)).appendPolicy(anyLong(), any(), any(), eq("DRAFT"), same(publish), any(), any());

        assertThatThrownBy(() -> service.execute(base(RiskOperation.PUBLISH_POLICY_VERSION, "policy-stale-01")
                .policyId(policy.getPolicyId()).expectedVersion(0L).approvedByPrincipalId("principal-reviewer-1")
                .effectiveFrom(Instant.now().plusSeconds(120)).rules(List.of(rule("REUSED_ADDRESS"))).build()))
                .hasMessage("policy version conflict");
    }

    @Test
    void shouldDetectSignalOnlyAgainstPublishedPolicyAndIsolateTenantReads() {
        RiskView policy = createPublishedPolicy();
        RiskView signal = service.execute(base(RiskOperation.DETECT_SIGNAL, "signal-detect-01")
                .signalId("signal-01").policyId(policy.getPolicyId()).subjectPrincipalId("principal-subject-1")
                .signalType("SHARED_DEVICE").severity("HIGH")
                .evidenceRef("sha256:" + "a".repeat(64)).build());
        assertThat(signal.getSignalId()).isEqualTo("signal-01");
        assertThat(signal.getPolicyVersion()).isEqualTo(1L);
        verify(eventService).appendSignal(any(Signal.class), any(), any());

        TenantContextHolder.setTenantId(2L);
        assertThatThrownBy(() -> service.getPolicy(policy.getPolicyId())).hasMessage("risk policy does not exist");
        verify(mapper).selectPolicy(2L, policy.getPolicyId());
    }

    @Test
    void shouldRejectRawPiiSelfLoopsAndReverseDuplicateRelationship() {
        RiskCommand.RiskCommandBuilder relationship = base(RiskOperation.OBSERVE_RELATIONSHIP, "relation-raw-01")
                .subjectPrincipalId("principal-a").relatedPrincipalId("principal-b").mediumType("PHONE")
                .keyVersion(3).firstSeenAt(Instant.now().minusSeconds(120)).lastSeenAt(Instant.now().minusSeconds(60))
                .confidenceBasisPoints(8500);
        assertThatThrownBy(() -> service.execute(relationship.mediumToken("13800138000").build()))
                .hasMessageContaining("raw PII is forbidden");

        assertThatThrownBy(() -> service.execute(base(RiskOperation.OBSERVE_RELATIONSHIP, "relation-self-01")
                .subjectPrincipalId("principal-a").relatedPrincipalId("principal-a").mediumType("DEVICE")
                .mediumToken("b".repeat(64)).keyVersion(1).firstSeenAt(Instant.now().minusSeconds(120))
                .lastSeenAt(Instant.now().minusSeconds(60)).confidenceBasisPoints(5000).build()))
                .hasMessage("risk relationship self-loop is forbidden");

        RiskView recorded = service.execute(base(RiskOperation.OBSERVE_RELATIONSHIP, "relation-ok-01")
                .subjectPrincipalId("principal-b").relatedPrincipalId("principal-a").mediumType("DEVICE")
                .mediumToken("c".repeat(64)).keyVersion(2).firstSeenAt(Instant.now().minusSeconds(120))
                .lastSeenAt(Instant.now().minusSeconds(60)).confidenceBasisPoints(9000).build());
        assertThat(recorded.getRelationId()).isNotBlank();
        assertThat(relations.get(recorded.getRelationId()).getSubjectPrincipalId()).isEqualTo("principal-a");

        assertThatThrownBy(() -> service.execute(base(RiskOperation.OBSERVE_RELATIONSHIP, "relation-reverse-01")
                .subjectPrincipalId("principal-a").relatedPrincipalId("principal-b").mediumType("DEVICE")
                .mediumToken("c".repeat(64)).keyVersion(2).firstSeenAt(Instant.now().minusSeconds(120))
                .lastSeenAt(Instant.now().minusSeconds(60)).confidenceBasisPoints(9000).build()))
                .hasMessage("risk relationship already exists");
    }

    @Test
    void shouldVersionClusterMembershipAndRejectDuplicateOrStaleMembers() {
        RiskView relation = service.execute(base(RiskOperation.OBSERVE_RELATIONSHIP, "cluster-relation-01")
                .subjectPrincipalId("principal-a").relatedPrincipalId("principal-b").mediumType("IP")
                .mediumToken("d".repeat(64)).keyVersion(1).firstSeenAt(Instant.now().minusSeconds(120))
                .lastSeenAt(Instant.now().minusSeconds(60)).confidenceBasisPoints(7000).build());
        RiskView cluster = service.execute(base(RiskOperation.CREATE_CLUSTER, "cluster-create-01")
                .clusterCode("GRAPH_001").riskLevel("HIGH").build());
        RiskView principalMember = service.execute(base(RiskOperation.ADD_CLUSTER_MEMBER, "cluster-member-01")
                .clusterId(cluster.getClusterId()).expectedVersion(1L).memberType("PRINCIPAL")
                .memberRef("principal-a").build());
        RiskView edgeMember = service.execute(base(RiskOperation.ADD_CLUSTER_MEMBER, "cluster-member-02")
                .clusterId(cluster.getClusterId()).expectedVersion(2L).memberType("RELATION")
                .memberRef(relation.getRelationId()).build());
        assertThat(principalMember.getClusterVersion()).isEqualTo(2L);
        assertThat(edgeMember.getClusterVersion()).isEqualTo(3L);
        assertThat(edgeMember.getMemberCount()).isEqualTo(2);
        assertThat(edgeMember.getEdgeCount()).isEqualTo(1);

        assertThatThrownBy(() -> service.execute(base(RiskOperation.ADD_CLUSTER_MEMBER, "cluster-duplicate-01")
                .clusterId(cluster.getClusterId()).expectedVersion(3L).memberType("PRINCIPAL")
                .memberRef("principal-a").build())).hasMessage("duplicate or invalid risk cluster member");

        RiskView underReview = service.execute(base(RiskOperation.CHANGE_CLUSTER_STATUS, "cluster-state-01")
                .clusterId(cluster.getClusterId()).expectedVersion(3L).clusterStatus("UNDER_REVIEW")
                .riskLevel("HIGH").build());
        assertThat(underReview.getClusterStatus()).isEqualTo("UNDER_REVIEW");
        assertThat(underReview.getClusterVersion()).isEqualTo(4L);
        assertThatThrownBy(() -> service.execute(base(RiskOperation.CHANGE_CLUSTER_STATUS, "cluster-state-invalid-01")
                .clusterId(cluster.getClusterId()).expectedVersion(4L).clusterStatus("OPEN")
                .riskLevel("HIGH").build()))
                .hasMessage("risk cluster cannot transition from UNDER_REVIEW to OPEN");

        assertThatThrownBy(() -> service.execute(base(RiskOperation.ADD_CLUSTER_MEMBER, "cluster-stale-01")
                .clusterId(cluster.getClusterId()).expectedVersion(2L).memberType("PRINCIPAL")
                .memberRef("principal-c").build())).hasMessage("cluster version conflict");
    }

    @Test
    void shouldRequireAssignedHumanReviewerAndExactDecisionFeedbackReferences() {
        RiskView cluster = service.execute(base(RiskOperation.CREATE_CLUSTER, "review-cluster-01")
                .clusterCode("REVIEW_001").riskLevel("MEDIUM").build());
        RiskView review = service.execute(base(RiskOperation.OPEN_REVIEW, "review-open-01")
                .clusterId(cluster.getClusterId()).reviewerPrincipalId("principal-reviewer-1").build());
        RiskView started = service.execute(base(RiskOperation.START_REVIEW, "review-start-01")
                .caseId(review.getCaseId()).expectedVersion(1L).build());
        assertThat(started.getReviewStatus()).isEqualTo("IN_REVIEW");

        assertThatThrownBy(() -> service.execute(base(RiskOperation.DECIDE_REVIEW, "review-wrong-actor-01")
                .caseId(review.getCaseId()).expectedVersion(2L).decisionType("MONITOR")
                .reasonCode("LINK_REQUIRES_OBSERVATION").decidedByPrincipalId("principal-other").build()))
                .hasMessage("decision actor must be the assigned human reviewer");
        assertThatThrownBy(() -> service.execute(base(RiskOperation.DECIDE_REVIEW, "review-punitive-01")
                .caseId(review.getCaseId()).expectedVersion(2L).decisionType("BLOCK_ACCOUNT")
                .reasonCode("LINK_REQUIRES_OBSERVATION").decidedByPrincipalId("principal-reviewer-1").build()))
                .hasMessage("decisionType must be a non-punitive review outcome");

        RiskView decided = service.execute(base(RiskOperation.DECIDE_REVIEW, "review-decide-01")
                .caseId(review.getCaseId()).expectedVersion(2L).decisionType("MONITOR")
                .reasonCode("LINK_REQUIRES_OBSERVATION").decidedByPrincipalId("principal-reviewer-1").build());
        assertThat(decided.getReviewStatus()).isEqualTo("DECIDED");
        assertThat(decided.getDecisionId()).isNotBlank();

        assertThatThrownBy(() -> service.execute(base(RiskOperation.RECORD_FEEDBACK, "feedback-wrong-case-01")
                .decisionId(decided.getDecisionId()).caseId("different-case").feedbackType("CONFIRMED")
                .reasonCode("REVIEW_VALIDATED").recordedByPrincipalId("principal-qa-1").build()))
                .hasMessage("feedback case does not match decision case");
        RiskView feedback = service.execute(base(RiskOperation.RECORD_FEEDBACK, "feedback-ok-01")
                .decisionId(decided.getDecisionId()).caseId(review.getCaseId()).feedbackType("CONFIRMED")
                .reasonCode("REVIEW_VALIDATED").recordedByPrincipalId("principal-qa-1").build());
        assertThat(feedback.getFeedbackId()).isNotBlank();
        verify(eventService).appendDecision(any(Decision.class), any(), any());
        verify(eventService).appendFeedback(any(Feedback.class), any(), any());
    }

    @Test
    void shouldReplaySameCommandAndRejectIdempotencyPayloadConflict() {
        RiskCommand command = createPolicy("policy-replay-01", "REPLAY_POLICY");
        RiskView first = service.execute(command);
        RiskView replay = service.execute(command);
        assertThat(replay.getPolicyId()).isEqualTo(first.getPolicyId());
        assertThat(replay.getDuplicate()).isTrue();
        verify(mapper, times(1)).insertPolicy(any(Policy.class));
        assertThatThrownBy(() -> service.execute(createPolicy("policy-replay-01", "DIFFERENT_POLICY")))
                .hasMessage("idempotency key conflicts with different risk payload");
    }

    private RiskView createPublishedPolicy() {
        RiskView policy = service.execute(createPolicy("policy-create-published", "PUBLISHED_POLICY"));
        return service.execute(base(RiskOperation.PUBLISH_POLICY_VERSION, "policy-publish-ready")
                .policyId(policy.getPolicyId()).expectedVersion(0L).approvedByPrincipalId("principal-reviewer-1")
                .effectiveFrom(Instant.now().plusSeconds(30)).rules(List.of(rule("SHARED_DEVICE"))).build());
    }

    private void wirePersistence() {
        when(mapper.insertPolicy(any())).thenAnswer(invocation -> {
            Policy value = invocation.getArgument(0); policies.put(key(value.getTenantId(), value.getPolicyId()), value); return 1;
        });
        when(mapper.selectPolicy(anyLong(), anyString())).thenAnswer(invocation -> policies.get(key(invocation.getArgument(0), invocation.getArgument(1))));
        when(mapper.selectPolicyForUpdate(anyLong(), anyString())).thenAnswer(invocation -> policies.get(key(invocation.getArgument(0), invocation.getArgument(1))));
        when(mapper.insertPolicyVersion(any())).thenReturn(1);
        when(mapper.insertPolicyRule(any())).thenReturn(1);
        when(mapper.publishPolicy(anyLong(), anyString(), anyLong(), any())).thenReturn(1);
        when(mapper.insertSignal(any())).thenReturn(1);
        when(mapper.insertRelation(any())).thenAnswer(invocation -> { Relation value = invocation.getArgument(0); relations.put(value.getRelationId(), value); return 1; });
        when(mapper.selectRelation(anyLong(), anyString())).thenAnswer(invocation -> {
            Relation value = relations.get(invocation.getArgument(1)); return value != null && value.getTenantId().equals(invocation.getArgument(0)) ? value : null;
        });
        when(mapper.selectRelationByKey(anyLong(), anyString(), anyString(), anyString(), anyString(), anyInt()))
                .thenAnswer(invocation -> relations.values().stream().filter(value -> value.getTenantId().equals(invocation.getArgument(0))
                        && value.getSubjectPrincipalId().equals(invocation.getArgument(1))
                        && value.getRelatedPrincipalId().equals(invocation.getArgument(2))
                        && value.getMediumType().equals(invocation.getArgument(3))
                        && value.getMediumToken().equals(invocation.getArgument(4))
                        && value.getKeyVersion().equals(invocation.getArgument(5))).findFirst().orElse(null));
        when(mapper.insertCluster(any())).thenAnswer(invocation -> { Cluster value = invocation.getArgument(0); clusters.put(key(value.getTenantId(), value.getClusterId()), value); return 1; });
        when(mapper.selectCluster(anyLong(), anyString())).thenAnswer(invocation -> clusters.get(key(invocation.getArgument(0), invocation.getArgument(1))));
        when(mapper.selectClusterForUpdate(anyLong(), anyString())).thenAnswer(invocation -> clusters.get(key(invocation.getArgument(0), invocation.getArgument(1))));
        when(mapper.insertClusterMember(any())).thenAnswer(invocation -> {
            ClusterMember value = invocation.getArgument(0);
            return clusterMembers.add(value.getTenantId() + "|" + value.getClusterId() + "|" + value.getMemberType() + "|" + value.getMemberRef()) ? 1 : 0;
        });
        when(mapper.addClusterMember(anyLong(), anyString(), anyLong(), anyInt(), any())).thenReturn(1);
        when(mapper.transitionCluster(anyLong(), anyString(), anyLong(), anyString(), anyString(), anyString(), any())).thenReturn(1);
        when(mapper.insertReviewCase(any())).thenAnswer(invocation -> { ReviewCase value = invocation.getArgument(0); reviews.put(key(value.getTenantId(), value.getCaseId()), value); return 1; });
        when(mapper.selectReviewCase(anyLong(), anyString())).thenAnswer(invocation -> reviews.get(key(invocation.getArgument(0), invocation.getArgument(1))));
        when(mapper.selectReviewCaseForUpdate(anyLong(), anyString())).thenAnswer(invocation -> reviews.get(key(invocation.getArgument(0), invocation.getArgument(1))));
        when(mapper.transitionReviewCase(anyLong(), anyString(), anyLong(), anyString(), anyString(), any())).thenReturn(1);
        when(mapper.insertDecision(any())).thenAnswer(invocation -> { Decision value = invocation.getArgument(0); decisions.put(key(value.getTenantId(), value.getDecisionId()), value); return 1; });
        when(mapper.selectDecision(anyLong(), anyString())).thenAnswer(invocation -> decisions.get(key(invocation.getArgument(0), invocation.getArgument(1))));
        when(mapper.insertFeedback(any())).thenReturn(1);
        when(mapper.insertHistory(any())).thenReturn(1);
    }

    private void wireOperationStore() {
        when(mapper.insertOrResolveOperation(anyLong(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    Long tenantId = invocation.getArgument(0); String idempotencyKey = invocation.getArgument(1);
                    String key = tenantId + "|" + idempotencyKey; Operation existing = operations.get(key);
                    if (existing == null) {
                        long id = operationSequence.incrementAndGet();
                        operations.put(key, new Operation().setOperationId(id).setTenantId(tenantId)
                                .setIdempotencyKey(idempotencyKey).setCommandType(invocation.getArgument(2))
                                .setRequestHash(invocation.getArgument(3)).setAttemptToken(invocation.getArgument(4)).setStatus(0));
                        lastOperationId.set(id);
                    } else lastOperationId.set(existing.getOperationId());
                    return 1;
                });
        when(mapper.selectLastInsertId()).thenAnswer(ignored -> lastOperationId.get());
        when(mapper.selectOperationForUpdate(anyLong(), anyLong())).thenAnswer(invocation -> operations.values().stream()
                .filter(value -> value.getOperationId().equals(invocation.getArgument(0))
                        && value.getTenantId().equals(invocation.getArgument(1))).findFirst().orElse(null));
        when(mapper.markOperationSucceeded(anyLong(), anyLong(), anyString(), anyString(), any())).thenAnswer(invocation -> {
            Operation value = operations.values().stream().filter(operation -> operation.getOperationId().equals(invocation.getArgument(0))
                    && operation.getTenantId().equals(invocation.getArgument(1))).findFirst().orElse(null);
            if (value == null || value.getStatus() != 0) return 0;
            value.setStatus(10).setAggregateId(invocation.getArgument(2)).setResultJson(invocation.getArgument(3)); return 1;
        });
    }

    private static RiskCommand createPolicy(String key, String code) {
        return base(RiskOperation.CREATE_POLICY, key).policyCode(code).policyName("Explainable " + code).build();
    }

    private static RiskCommand.RuleDefinition rule(String code) {
        return RiskCommand.RuleDefinition.builder().ruleCode(code).signalType(code).operatorCode("GTE")
                .thresholdValue("2").outcomeCode("REVIEW_REQUIRED")
                .explanationTemplate("Observed count must be greater than or equal to threshold").build();
    }

    private static RiskCommand.RiskCommandBuilder base(RiskOperation operation, String key) {
        return RiskCommand.builder().operation(operation).idempotencyKey(key).runId("risk-run-001")
                .correlationId("risk-correlation-001").occurredAt(Instant.now().minusSeconds(30));
    }

    private static String key(Object tenantId, Object id) { return tenantId + "|" + id; }
}
