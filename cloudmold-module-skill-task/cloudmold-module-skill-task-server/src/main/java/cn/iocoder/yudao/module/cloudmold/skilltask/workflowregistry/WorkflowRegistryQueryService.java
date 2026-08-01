package cn.iocoder.yudao.module.cloudmold.skilltask.workflowregistry;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.skilltask.definition.SkillTaskDefinition;
import cn.iocoder.yudao.module.cloudmold.skilltask.definition.SkillTaskDefinitionRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class WorkflowRegistryQueryService {

    private final SkillTaskDefinitionRegistry definitionRegistry;
    private final WorkflowRegistryAttestationService attestationService;
    private final ObjectMapper objectMapper;

    public WorkflowRegistryQueryService(
            SkillTaskDefinitionRegistry definitionRegistry,
            WorkflowRegistryAttestationService attestationService,
            ObjectMapper objectMapper) {
        this.definitionRegistry = definitionRegistry;
        this.attestationService = attestationService;
        this.objectMapper = objectMapper;
    }

    public WorkflowRegistryDefinitionView getDefinition(String skillId, String skillVersion) {
        WorkflowRegistrySubjectView subject = getCurrentSubject();
        SkillTaskDefinition definition = definitionRegistry.require(skillId, skillVersion);
        JsonNode definitionView = objectMapper.valueToTree(definition);
        WorkflowRegistryAttestationView attestation = attestationService.attest(
                skillId,
                subject.ownerUserId(),
                definition.getSkillVersion(),
                definitionView);
        return new WorkflowRegistryDefinitionView(definitionView, attestation);
    }

    public WorkflowRegistrySubjectView getCurrentSubject() {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LoginUser loginUser = Objects.requireNonNull(SecurityFrameworkUtils.getLoginUser(),
                "Workflow registry requires an authenticated login subject");
        Long loginUserId = Objects.requireNonNull(loginUser.getId(),
                "Workflow registry requires the current login subject id");
        Long subjectTenantId = loginUser.getVisitTenantId() != null ? loginUser.getVisitTenantId() : loginUser.getTenantId();
        if (!Objects.equals(subjectTenantId, tenantId)) {
            throw new IllegalStateException("Workflow registry subject is not bound to the current tenant context");
        }
        return new WorkflowRegistrySubjectView(String.valueOf(loginUserId), tenantId);
    }
}
