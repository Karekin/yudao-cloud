package cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.dal.WorkflowRegistryPointerMapper;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.dal.WorkflowRegistryVersionMapper;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.dal.dataobject.WorkflowRegistryPointerDO;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.dal.dataobject.WorkflowRegistryVersionDO;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.request.WorkflowProposalRegistryRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowProposalRegistryServiceTest {

    private final WorkflowRegistryPointerMapper pointerMapper = mock(WorkflowRegistryPointerMapper.class);
    private final WorkflowRegistryVersionMapper versionMapper = mock(WorkflowRegistryVersionMapper.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WorkflowBaseAttestationVerifier attestationVerifier = mock(WorkflowBaseAttestationVerifier.class);
    private final WorkflowProposalRegistryService service =
            new WorkflowProposalRegistryService(pointerMapper, versionMapper, objectMapper, attestationVerifier);
    private MockedStatic<SecurityFrameworkUtils> security;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(162L);
        security = mockStatic(SecurityFrameworkUtils.class);
        security.when(SecurityFrameworkUtils::getLoginUser).thenReturn(loginUser(162L));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        security.close();
    }

    @Test
    void bootstrapsStableAndCandidateAtomically() {
        WorkflowProposalRegistryRequest request = request(0L);
        when(versionMapper.selectByProposalId(162L, proposalId(request))).thenReturn(null);
        when(pointerMapper.selectForUpdate(162L, skillId())).thenReturn(null);

        var result = service.submit(request);

        ArgumentCaptor<WorkflowRegistryVersionDO> versions = ArgumentCaptor.forClass(WorkflowRegistryVersionDO.class);
        verify(versionMapper, org.mockito.Mockito.times(2)).insert(versions.capture());
        assertThat(versions.getAllValues()).extracting(WorkflowRegistryVersionDO::getRegistryStatus)
                .containsExactly("ACTIVE", "SUBMITTED");
        assertThat(versions.getAllValues().get(1).getValidationJson()).contains("\"passed\":true");
        verify(pointerMapper).insert(any(WorkflowRegistryPointerDO.class));
        assertThat(result.workflowId()).isEqualTo(skillId());
        assertThat(result.pointerVersion()).isEqualTo(1L);
        assertThat(result.candidateStatus()).isEqualTo("SUBMITTED");
    }

    @Test
    void allowsBaseAttestationToBeOmittedForWireCompatibility() {
        WorkflowProposalRegistryRequest request = request(0L);
        request.setBaseAttestation(null);
        when(versionMapper.selectByProposalId(162L, proposalId(request))).thenReturn(null);
        when(pointerMapper.selectForUpdate(162L, skillId())).thenReturn(null);

        var result = service.submit(request);

        assertThat(result.pointerVersion()).isEqualTo(1L);
        verify(attestationVerifier, never()).verifyFresh(any(), any(), any(), any());
    }

    @Test
    void rejectsStalePointerAndExistingCandidate() {
        WorkflowProposalRegistryRequest request = request(2L);
        when(versionMapper.selectByProposalId(162L, proposalId(request))).thenReturn(null);
        when(pointerMapper.selectForUpdate(162L, skillId())).thenReturn(pointer(3L, null));

        assertThatThrownBy(() -> service.submit(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stale");

        request.setExpectedPointerVersion(3L);
        when(pointerMapper.selectForUpdate(162L, skillId())).thenReturn(pointer(3L, "candidate-existing"));
        assertThatThrownBy(() -> service.submit(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("candidate");
        verify(versionMapper, never()).insert(any(WorkflowRegistryVersionDO.class));
    }

    @Test
    void acceptsSameProposalAsIdempotentAndRejectsTampering() {
        WorkflowProposalRegistryRequest request = request(3L);
        when(versionMapper.selectByProposalId(162L, proposalId(request))).thenReturn(null);
        when(pointerMapper.selectForUpdate(162L, skillId())).thenReturn(pointer(3L, null));
        when(versionMapper.selectByRegistryVersionId(162L, "stable-1")).thenReturn(stable(request));
        when(pointerMapper.setCandidateCas(any(), any(), any(), any(), any(), any())).thenReturn(1);

        var first = service.submit(request);
        ArgumentCaptor<WorkflowRegistryVersionDO> candidate = ArgumentCaptor.forClass(WorkflowRegistryVersionDO.class);
        verify(versionMapper).insert(candidate.capture());
        WorkflowRegistryVersionDO stored = candidate.getValue();
        WorkflowRegistryPointerDO current = pointer(4L, stored.getRegistryVersionId());
        when(versionMapper.selectByProposalId(162L, proposalId(request))).thenReturn(stored);
        when(pointerMapper.selectForUpdate(162L, skillId())).thenReturn(current);
        when(versionMapper.selectByRegistryVersionId(162L, "stable-1")).thenReturn(stable(request));

        request.setExpectedPointerVersion(4L);
        var duplicate = service.submit(request);

        assertThat(first.duplicate()).isFalse();
        assertThat(duplicate.duplicate()).isTrue();

        stored.setDefinitionSha256("0".repeat(64));
        assertThatThrownBy(() -> service.submit(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("different immutable content");
    }

    @Test
    void canonicalHashMatchesDeerFlowContract() throws Exception {
        var definition = objectMapper.readTree("""
                {"z":1,"中文":"值","a":{"y":true,"x":[3,2,1]},"n":null}
                """);

        assertThat(service.canonicalizeDefinition(definition))
                .isEqualTo("{\"a\":{\"x\":[3,2,1],\"y\":true},\"n\":null,\"z\":1,\"中文\":\"值\"}\n");
        assertThat(WorkflowProposalRegistryService.sha256Hex(service.canonicalizeDefinition(definition)))
                .isEqualTo("75bab8832d79e171c91a96f23e975586333936e17fd0ae4a1018d40dcebd055b");
    }

    @Test
    void validatesHashesRiskAndTenantSubjectBeforePersistence() {
        WorkflowProposalRegistryRequest invalidRisk = request(0L);
        ((ObjectNode) invalidRisk.getProposal()).put("risk_level", "HIGH");
        assertThatThrownBy(() -> service.submit(invalidRisk)).hasMessageContaining("risk_level");

        WorkflowProposalRegistryRequest invalidHash = request(0L);
        ((ObjectNode) invalidHash.getProposal()).put("candidate_sha256", "0".repeat(64));
        assertThatThrownBy(() -> service.submit(invalidHash)).hasMessageContaining("candidate_sha256");

        security.when(SecurityFrameworkUtils::getLoginUser).thenReturn(loginUser(999L));
        WorkflowProposalRegistryRequest tenantMismatch = request(0L);
        assertThatThrownBy(() -> service.submit(tenantMismatch))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tenant");
        verify(pointerMapper, never()).insert(any(WorkflowRegistryPointerDO.class));
    }

    @Test
    void returnsEmptyStatusWithCasVersionZero() {
        when(pointerMapper.selectBySkillId(162L, skillId())).thenReturn(null);

        var result = service.getStatus(skillId());

        assertThat(result.workflowId()).isEqualTo(skillId());
        assertThat(result.pointerVersion()).isZero();
        assertThat(result.stableVersionId()).isNull();
    }

    private WorkflowProposalRegistryRequest request(long expectedPointerVersion) {
        ObjectNode base = definition("1.0.0", "base");
        ObjectNode candidate = definition("1.0.1", "candidate");
        String baseSha = WorkflowProposalRegistryService.sha256Hex(service.canonicalizeDefinition(base));
        String candidateSha = WorkflowProposalRegistryService.sha256Hex(service.canonicalizeDefinition(candidate));
        ObjectNode proposal = objectMapper.createObjectNode()
                .put("proposal_id", "proposal-" + candidateSha.substring(0, 16))
                .put("workflow_id", skillId())
                .put("base_sha256", baseSha)
                .put("candidate_sha256", candidateSha)
                .put("risk_level", "E1")
                .put("status", "READY_FOR_EXTERNAL_VALIDATION");
        ObjectNode validation = objectMapper.createObjectNode()
                .put("passed", true)
                .put("base_sha256", baseSha)
                .put("draft_sha256", candidateSha);
        WorkflowProposalRegistryRequest request = new WorkflowProposalRegistryRequest();
        request.setExpectedPointerVersion(expectedPointerVersion);
        request.setProposal(proposal);
        request.setBaseDefinition(base);
        request.setBaseAttestation(objectMapper.createObjectNode().put("proof", "server-issued"));
        request.setCandidateDefinition(candidate);
        request.setValidation(validation);
        return request;
    }

    private ObjectNode definition(String version, String name) {
        ObjectNode definition = objectMapper.createObjectNode()
                .put("schema_version", "cloudmold.skill-task-definition/v1")
                .put("skill_id", skillId())
                .put("skill_version", version)
                .put("risk_level", "R1");
        definition.putArray("steps").addObject()
                .put("step_code", name)
                .put("step_order", 1)
                .put("capability_id", "cap.read")
                .put("operation_type", "READ");
        return definition;
    }

    private WorkflowRegistryPointerDO pointer(long version, String candidateId) {
        return new WorkflowRegistryPointerDO().setTenantId(162L).setSkillId(skillId())
                .setStableVersionId("stable-1").setCandidateVersionId(candidateId)
                .setPointerVersion(version).setCreatedAt(LocalDateTime.now()).setUpdatedAt(LocalDateTime.now());
    }

    private WorkflowRegistryVersionDO stable(WorkflowProposalRegistryRequest request) {
        return new WorkflowRegistryVersionDO().setRegistryVersionId("stable-1").setTenantId(162L)
                .setSkillId(skillId()).setSkillSemanticVersion("1.0.0")
                .setDefinitionSha256(request.getProposal().path("base_sha256").asText())
                .setRegistryStatus("ACTIVE");
    }

    private LoginUser loginUser(Long tenantId) {
        LoginUser user = new LoginUser();
        user.setId(101L);
        user.setUserType(2);
        user.setTenantId(tenantId);
        user.setVisitTenantId(tenantId);
        return user;
    }

    private String proposalId(WorkflowProposalRegistryRequest request) {
        return request.getProposal().path("proposal_id").asText();
    }

    private String skillId() {
        return "skill.cloudmold.inventory.stockout-diagnosis.v1";
    }
}
