package cn.iocoder.yudao.module.cloudmold.agentcontrol.integration.bpm;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.ActorRoleGrant;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.ApprovalWorkflowBinding;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.ApprovalWorkflowStartCandidate;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.WorkOrder;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.mysql.AgentControlStoreMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class AgentApprovalResponsibilityResolver {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final TypeReference<List<Long>> LONG_LIST = new TypeReference<>() {};

    private final AgentControlStoreMapper mapper;

    public Resolution resolve(ApprovalWorkflowStartCandidate candidate, LocalDateTime now) {
        require(candidate != null && candidate.getTenantId() != null && candidate.getTenantId() > 0,
                "R3 responsibility resolution requires a tenant");
        if (!"R3".equals(candidate.getRiskLevel())) {
            Resolution resolution = Resolution.none();
            apply(candidate, resolution);
            return resolution;
        }
        require(candidate.getApprovalId() != null && candidate.getScopeHash() != null,
                "R3 responsibility resolution requires frozen approval identity");
        List<String> roleCodes = AgentApprovalR3Policy.requiredRoleCodes(candidate.getActionCode());
        List<ActorRoleGrant> grants = new ArrayList<>();
        Set<Long> assignedUsers = new HashSet<>();
        Set<Long> forbiddenUsers = new HashSet<>();
        addPositive(forbiddenUsers, candidate.getRequesterUserId());
        addPositive(forbiddenUsers, candidate.getExecutorUserId());
        addPositive(forbiddenUsers, candidate.getApproverUserId());

        for (String roleCode : roleCodes) {
            List<ActorRoleGrant> roleGrants = mapper.selectEffectiveActorRoleGrantsByRole(
                    candidate.getTenantId(), roleCode, now);
            require(roleGrants != null && !roleGrants.isEmpty(),
                    "R3 responsibility group has no effective tenant member: " + roleCode);
            List<ActorRoleGrant> eligibleGrants = roleGrants.stream()
                    .sorted(Comparator.comparing(ActorRoleGrant::getActorUserId)
                            .thenComparing(ActorRoleGrant::getGrantId))
                    .peek(grant -> validateGrant(candidate.getTenantId(), roleCode, grant, now))
                    .filter(grant -> !forbiddenUsers.contains(grant.getActorUserId()))
                    .toList();
            require(!eligibleGrants.isEmpty(),
                    "R3 responsibility group has no independent effective tenant member: " + roleCode);
            eligibleGrants.forEach(grant -> {
                require(assignedUsers.add(grant.getActorUserId()),
                        "R3 responsibility groups must not resolve to the same user");
                grants.add(grant);
            });
        }
        List<Long> approverUserIds = grants.stream().map(ActorRoleGrant::getActorUserId).toList();
        String authoritySha256 = authoritySha256(candidate, roleCodes, grants);
        Resolution resolution = new Resolution(List.copyOf(roleCodes), approverUserIds, authoritySha256);
        apply(candidate, resolution);
        return resolution;
    }

    public Resolution assertCurrent(ApprovalWorkflowBinding binding, WorkOrder workOrder,
                                    Long terminalOperatorUserId, String terminalTaskDefinitionKey,
                                    String decision, LocalDateTime now) {
        Resolution current = assertSnapshotCurrent(binding, workOrder, now);

        boolean operatingPrincipalTask =
                YudaoBpmApprovalWorkflowAdapter.APPROVAL_TASK_KEY.equals(terminalTaskDefinitionKey);
        boolean responsibilityTask =
                YudaoBpmApprovalWorkflowAdapter.RESPONSIBILITY_TASK_KEY.equals(terminalTaskDefinitionKey);
        if ("APPROVE".equals(decision)) {
            require(responsibilityTask && current.approverUserIds().contains(terminalOperatorUserId),
                    "R3 approval must finish at an authorized responsibility countersign task");
        } else {
            require((operatingPrincipalTask && Objects.equals(binding.getApproverUserId(), terminalOperatorUserId))
                            || (responsibilityTask && current.approverUserIds().contains(terminalOperatorUserId)),
                    "R3 rejection must come from an authorized operating or responsibility approver");
        }
        return current;
    }

    public Resolution assertSnapshotCurrent(ApprovalWorkflowBinding binding, WorkOrder workOrder,
                                            LocalDateTime now) {
        ApprovalWorkflowStartCandidate frozen = new ApprovalWorkflowStartCandidate()
                .setTenantId(binding.getTenantId()).setApprovalId(binding.getApprovalId())
                .setWorkOrderId(binding.getWorkOrderId()).setActionCode(binding.getActionCode())
                .setRoleCode(binding.getRoleCode()).setRiskLevel(binding.getRiskLevel())
                .setRequesterUserId(binding.getRequesterUserId()).setExecutorUserId(workOrder.getAssigneeUserId())
                .setApproverUserId(binding.getApproverUserId()).setScopeHash(binding.getScopeHash());
        Resolution current = resolve(frozen, now);
        require(Objects.equals(binding.getResponsibilityAuthoritySha256(), current.authoritySha256()),
                "R3 responsibility authorization snapshot is stale or drifted");
        require(Objects.equals(parseStrings(binding.getResponsibilityRoleCodesJson()), current.roleCodes()),
                "R3 responsibility role snapshot drifted");
        require(Objects.equals(parseLongs(binding.getResponsibilityApproverUserIdsJson()), current.approverUserIds()),
                "R3 responsibility approver snapshot drifted");
        return current;
    }

    private static void apply(ApprovalWorkflowStartCandidate candidate, Resolution resolution) {
        candidate.setResponsibilityRoleCodes(resolution.roleCodes())
                .setResponsibilityApproverUserIds(resolution.approverUserIds())
                .setResponsibilityAuthoritySha256(resolution.authoritySha256());
    }

    private static void validateGrant(Long tenantId, String roleCode, ActorRoleGrant grant, LocalDateTime now) {
        require(grant != null
                        && Objects.equals(grant.getTenantId(), tenantId)
                        && Objects.equals(grant.getRoleCode(), roleCode),
                "R3 responsibility grant crosses a tenant or role boundary");
        require(grant.getActorUserId() != null && grant.getActorUserId() > 0
                        && "ACTIVE".equals(grant.getStatus())
                        && grant.getValidFrom() != null && grant.getValidUntil() != null
                        && !grant.getValidFrom().isAfter(now) && grant.getValidUntil().isAfter(now)
                        && grant.getVersion() != null && grant.getVersion() > 0,
                "R3 responsibility grant is not currently effective");
    }

    private static String authoritySha256(ApprovalWorkflowStartCandidate candidate, List<String> roleCodes,
                                          List<ActorRoleGrant> grants) {
        StringBuilder canonical = new StringBuilder()
                .append(candidate.getTenantId()).append('|')
                .append(candidate.getApprovalId()).append('|')
                .append(candidate.getActionCode()).append('|')
                .append(candidate.getScopeHash()).append('|')
                .append(String.join(",", roleCodes));
        grants.stream()
                .sorted(Comparator.comparing(ActorRoleGrant::getRoleCode)
                        .thenComparing(ActorRoleGrant::getActorUserId)
                        .thenComparing(ActorRoleGrant::getGrantId))
                .forEach(grant -> canonical.append('|')
                        .append(grant.getRoleCode()).append(':')
                        .append(grant.getActorUserId()).append(':')
                        .append(grant.getGrantId()).append(':')
                        .append(grant.getVersion()).append(':')
                        .append(grant.getValidFrom()).append(':')
                        .append(grant.getValidUntil()));
        return DigestUtil.sha256Hex(canonical.toString());
    }

    private static List<String> parseStrings(String json) {
        List<String> values = JsonUtils.parseObjectQuietly(json, STRING_LIST);
        require(values != null, "R3 responsibility role snapshot is missing or invalid");
        return List.copyOf(values);
    }

    private static List<Long> parseLongs(String json) {
        List<Long> values = JsonUtils.parseObjectQuietly(json, LONG_LIST);
        require(values != null, "R3 responsibility approver snapshot is missing or invalid");
        return List.copyOf(values);
    }

    private static void addPositive(Set<Long> values, Long value) {
        if (value != null && value > 0) {
            values.add(value);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    public record Resolution(List<String> roleCodes, List<Long> approverUserIds, String authoritySha256) {
        static Resolution none() {
            return new Resolution(List.of(), List.of(), null);
        }

        public String roleCodesJson() {
            return authoritySha256 == null ? null : JsonUtils.toJsonString(roleCodes);
        }

        public String approverUserIdsJson() {
            return authoritySha256 == null ? null : JsonUtils.toJsonString(approverUserIds);
        }
    }
}
