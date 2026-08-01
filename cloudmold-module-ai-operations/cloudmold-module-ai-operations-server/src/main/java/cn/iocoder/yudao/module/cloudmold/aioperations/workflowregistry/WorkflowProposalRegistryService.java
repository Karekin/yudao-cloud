package cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.dal.WorkflowRegistryPointerMapper;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.dal.WorkflowRegistryVersionMapper;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.dal.dataobject.WorkflowRegistryPointerDO;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.dal.dataobject.WorkflowRegistryVersionDO;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.request.WorkflowProposalRegistryRequest;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.view.WorkflowProposalRegistryStatusView;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class WorkflowProposalRegistryService {

    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9_.-]{1,191}$");
    private static final Pattern PROPOSAL_ID = Pattern.compile("^proposal-[0-9a-f]{16}$");
    private static final Pattern SHA256 = Pattern.compile("^[0-9a-f]{64}$");
    private static final java.util.Set<String> RISKS = java.util.Set.of("E0", "E1", "E2", "E3");

    private final WorkflowRegistryPointerMapper pointerMapper;
    private final WorkflowRegistryVersionMapper versionMapper;
    private final ObjectMapper objectMapper;

    public WorkflowProposalRegistryService(WorkflowRegistryPointerMapper pointerMapper,
                                           WorkflowRegistryVersionMapper versionMapper,
                                           ObjectMapper objectMapper) {
        this.pointerMapper = pointerMapper;
        this.versionMapper = versionMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public WorkflowProposalRegistryStatusView submit(WorkflowProposalRegistryRequest request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String actor = requireActor(tenantId);
        Submission submission = validate(request);

        WorkflowRegistryVersionDO existing = versionMapper.selectByProposalId(tenantId, submission.proposalId());
        if (existing != null) {
            if (!Objects.equals(existing.getProposalSha256(), submission.bundleSha256())
                    || !Objects.equals(existing.getDefinitionSha256(), submission.candidateSha256())
                    || !Objects.equals(existing.getBaseDefinitionSha256(), submission.baseSha256())
                    || !Objects.equals(existing.getSkillId(), submission.skillId())) {
                throw new IllegalArgumentException("proposal_id already exists with different immutable content");
            }
            WorkflowRegistryPointerDO pointer = pointerMapper.selectForUpdate(tenantId, submission.skillId());
            if (pointer == null || !Objects.equals(pointer.getCandidateVersionId(), existing.getRegistryVersionId())) {
                throw new IllegalArgumentException("idempotent proposal is no longer the current candidate");
            }
            return view(pointer, versionMapper.selectByRegistryVersionId(tenantId, pointer.getStableVersionId()),
                    existing, true);
        }

        WorkflowRegistryPointerDO pointer = pointerMapper.selectForUpdate(tenantId, submission.skillId());
        LocalDateTime now = LocalDateTime.now();
        if (pointer == null) {
            if (request.getExpectedPointerVersion() != 0L) {
                throw new IllegalArgumentException("expected_pointer_version must be 0 for the first proposal");
            }
            WorkflowRegistryVersionDO stable = version(submission, true, null, actor, now);
            WorkflowRegistryVersionDO candidate = version(submission, false, stable.getRegistryVersionId(), actor, now);
            versionMapper.insert(stable);
            versionMapper.insert(candidate);
            pointer = new WorkflowRegistryPointerDO()
                    .setPointerId("wrp-" + UUID.randomUUID())
                    .setTenantId(tenantId)
                    .setSkillId(submission.skillId())
                    .setStableVersionId(stable.getRegistryVersionId())
                    .setCandidateVersionId(candidate.getRegistryVersionId())
                    .setPointerVersion(1L)
                    .setCreatedBy(actor)
                    .setUpdatedBy(actor)
                    .setCreatedAt(now)
                    .setUpdatedAt(now);
            pointerMapper.insert(pointer);
            return view(pointer, stable, candidate, false);
        }

        if (!Objects.equals(pointer.getPointerVersion(), request.getExpectedPointerVersion())) {
            throw new IllegalArgumentException("expected_pointer_version is stale");
        }
        if (pointer.getCandidateVersionId() != null) {
            throw new IllegalArgumentException("another candidate is already pending for this workflow");
        }
        WorkflowRegistryVersionDO stable = versionMapper.selectByRegistryVersionId(tenantId, pointer.getStableVersionId());
        if (stable == null || !Objects.equals(stable.getDefinitionSha256(), submission.baseSha256())) {
            throw new IllegalArgumentException("proposal base_definition does not match the stable pointer");
        }
        WorkflowRegistryVersionDO candidate = version(submission, false, stable.getRegistryVersionId(), actor, now);
        versionMapper.insert(candidate);
        int updated = pointerMapper.setCandidateCas(tenantId, submission.skillId(), candidate.getRegistryVersionId(),
                request.getExpectedPointerVersion(), actor, now);
        if (updated != 1) {
            throw new IllegalStateException("workflow candidate pointer changed concurrently");
        }
        pointer.setCandidateVersionId(candidate.getRegistryVersionId())
                .setPointerVersion(pointer.getPointerVersion() + 1)
                .setUpdatedBy(actor)
                .setUpdatedAt(now);
        return view(pointer, stable, candidate, false);
    }

    public WorkflowProposalRegistryStatusView getStatus(String skillId) {
        requireSafeId(skillId, "workflow_id");
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        requireActor(tenantId);
        WorkflowRegistryPointerDO pointer = pointerMapper.selectBySkillId(tenantId, skillId);
        if (pointer == null) {
            return WorkflowProposalRegistryStatusView.builder().workflowId(skillId).pointerVersion(0L)
                    .duplicate(false).build();
        }
        WorkflowRegistryVersionDO stable = versionMapper.selectByRegistryVersionId(tenantId, pointer.getStableVersionId());
        WorkflowRegistryVersionDO candidate = pointer.getCandidateVersionId() == null ? null
                : versionMapper.selectByRegistryVersionId(tenantId, pointer.getCandidateVersionId());
        return view(pointer, stable, candidate, false);
    }

    private Submission validate(WorkflowProposalRegistryRequest request) {
        Objects.requireNonNull(request, "request is required");
        if (request.getExpectedPointerVersion() == null || request.getExpectedPointerVersion() < 0) {
            throw new IllegalArgumentException("expected_pointer_version must be non-negative");
        }
        JsonNode proposal = requireObject(request.getProposal(), "proposal");
        JsonNode base = requireObject(request.getBaseDefinition(), "base_definition");
        JsonNode candidate = requireObject(request.getCandidateDefinition(), "candidate_definition");
        JsonNode validation = requireObject(request.getValidation(), "validation");
        String skillId = text(proposal, "workflow_id");
        String proposalId = text(proposal, "proposal_id");
        String risk = text(proposal, "risk_level");
        requireSafeId(skillId, "workflow_id");
        if (!PROPOSAL_ID.matcher(proposalId).matches()) {
            throw new IllegalArgumentException("proposal_id must be derived from the candidate hash");
        }
        if (!RISKS.contains(risk)) {
            throw new IllegalArgumentException("risk_level must be E0, E1, E2, or E3");
        }
        if (!skillId.equals(text(base, "skill_id")) || !skillId.equals(text(candidate, "skill_id"))) {
            throw new IllegalArgumentException("workflow_id must match both definition skill_id values");
        }
        String baseVersion = text(base, "skill_version");
        String candidateVersion = text(candidate, "skill_version");
        requireSafeId(baseVersion, "base skill_version");
        requireSafeId(candidateVersion, "candidate skill_version");
        if (baseVersion.equals(candidateVersion)) {
            throw new IllegalArgumentException("candidate skill_version must differ from the stable version");
        }
        String baseCanonical = canonicalizeDefinition(base);
        String candidateCanonical = canonicalizeDefinition(candidate);
        String baseSha = sha256Hex(baseCanonical);
        String candidateSha = sha256Hex(candidateCanonical);
        requireHash(proposal, "base_sha256", baseSha);
        requireHash(proposal, "candidate_sha256", candidateSha);
        if (!proposalId.equals("proposal-" + candidateSha.substring(0, 16))) {
            throw new IllegalArgumentException("proposal_id does not match candidate_sha256");
        }
        if (!validation.path("passed").asBoolean(false)
                || !baseSha.equals(validation.path("base_sha256").asText())
                || !candidateSha.equals(validation.path("draft_sha256").asText())) {
            throw new IllegalArgumentException("validation is not bound to the submitted definitions");
        }
        String proposalJson = canonicalizeDefinition(proposal);
        String validationJson = canonicalizeDefinition(validation);
        ObjectNode bundle = objectMapper.createObjectNode();
        bundle.set("proposal", proposal);
        bundle.set("base_definition", base);
        bundle.set("candidate_definition", candidate);
        bundle.set("validation", validation);
        return new Submission(skillId, proposalId, risk, baseVersion, candidateVersion, baseSha, candidateSha,
                baseCanonical, candidateCanonical, proposalJson, validationJson,
                sha256Hex(canonicalizeDefinition(bundle)));
    }

    private WorkflowRegistryVersionDO version(Submission s, boolean stable, String parent, String actor,
                                              LocalDateTime now) {
        return new WorkflowRegistryVersionDO()
                .setRegistryVersionId("wrv-" + UUID.randomUUID())
                .setTenantId(TenantContextHolder.getRequiredTenantId())
                .setSkillId(s.skillId())
                .setSkillSemanticVersion(stable ? s.baseVersion() : s.candidateVersion())
                .setParentRegistryVersionId(parent)
                .setProposalId(stable ? "bootstrap-" + s.baseSha256().substring(0, 16) : s.proposalId())
                .setBaseDefinitionSha256(stable ? null : s.baseSha256())
                .setDefinitionSha256(stable ? s.baseSha256() : s.candidateSha256())
                .setCanonicalDefinitionJson(stable ? s.baseCanonical() : s.candidateCanonical())
                .setProposalSha256(stable ? s.baseSha256() : s.bundleSha256())
                .setProposalJson(stable ? "{\"bootstrap\":true}" : s.proposalJson())
                .setValidationJson(stable ? null : s.validationJson())
                .setRiskLevel(stable ? "E0" : s.riskLevel())
                .setRegistryStatus(stable ? "ACTIVE" : "SUBMITTED")
                .setProposedBy(actor)
                .setCreatedAt(now);
    }

    private WorkflowProposalRegistryStatusView view(WorkflowRegistryPointerDO pointer,
                                                    WorkflowRegistryVersionDO stable,
                                                    WorkflowRegistryVersionDO candidate,
                                                    boolean duplicate) {
        return WorkflowProposalRegistryStatusView.builder()
                .workflowId(pointer.getSkillId())
                .proposalId(candidate == null ? null : candidate.getProposalId())
                .stableVersionId(stable == null ? null : stable.getRegistryVersionId())
                .stableVersion(stable == null ? null : stable.getSkillSemanticVersion())
                .candidateVersionId(candidate == null ? null : candidate.getRegistryVersionId())
                .candidateVersion(candidate == null ? null : candidate.getSkillSemanticVersion())
                .candidateStatus(candidate == null ? null : candidate.getRegistryStatus())
                .pointerVersion(pointer.getPointerVersion())
                .duplicate(duplicate)
                .build();
    }

    private String requireActor(Long tenantId) {
        LoginUser user = Objects.requireNonNull(SecurityFrameworkUtils.getLoginUser(),
                "Workflow proposal registry requires an authenticated login subject");
        Long userId = Objects.requireNonNull(user.getId(), "Workflow proposal registry requires a login user id");
        Long subjectTenant = user.getVisitTenantId() != null ? user.getVisitTenantId() : user.getTenantId();
        if (subjectTenant != null && !Objects.equals(subjectTenant, tenantId)) {
            throw new IllegalStateException("Workflow proposal subject is not bound to the current tenant");
        }
        return String.valueOf(user.getUserType()) + ":" + userId;
    }

    public String canonicalizeDefinition(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(sort(node)) + "\n";
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("workflow JSON cannot be canonicalized", e);
        }
    }

    private JsonNode sort(JsonNode node) {
        if (node.isObject()) {
            ObjectNode sorted = objectMapper.createObjectNode();
            Map<String, JsonNode> fields = new TreeMap<>();
            Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
            iterator.forEachRemaining(entry -> fields.put(entry.getKey(), entry.getValue()));
            fields.forEach((key, value) -> sorted.set(key, sort(value)));
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode sorted = objectMapper.createArrayNode();
            node.forEach(value -> sorted.add(sort(value)));
            return sorted;
        }
        return node;
    }

    public static String sha256Hex(String material) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private JsonNode requireObject(JsonNode node, String field) {
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException(field + " must be a JSON object");
        }
        return node;
    }

    private String text(JsonNode node, String field) {
        String value = node.path(field).asText("");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private void requireSafeId(String value, String field) {
        if (!SAFE_ID.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " contains unsupported characters");
        }
    }

    private void requireHash(JsonNode node, String field, String actual) {
        String supplied = text(node, field);
        if (!SHA256.matcher(supplied).matches() || !supplied.equals(actual)) {
            throw new IllegalArgumentException(field + " does not match canonical JSON");
        }
    }

    private record Submission(String skillId, String proposalId, String riskLevel,
                              String baseVersion, String candidateVersion,
                              String baseSha256, String candidateSha256,
                              String baseCanonical, String candidateCanonical,
                              String proposalJson, String validationJson, String bundleSha256) {
    }
}
