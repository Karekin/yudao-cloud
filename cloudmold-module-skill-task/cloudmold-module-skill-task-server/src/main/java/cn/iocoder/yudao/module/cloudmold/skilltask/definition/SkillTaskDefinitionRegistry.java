package cn.iocoder.yudao.module.cloudmold.skilltask.definition;

import cn.iocoder.yudao.module.cloudmold.executor.CapabilityDescriptor;
import cn.iocoder.yudao.module.cloudmold.executor.CloudMoldCapabilityCatalog;
import cn.iocoder.yudao.module.cloudmold.skilltask.SkillTaskProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class SkillTaskDefinitionRegistry {

    public static final String SCHEMA_VERSION = "cloudmold.skill-task-definition/v1";
    private static final String ORCHESTRATION_API_PACKAGE =
            "cn.iocoder.yudao.module.cloudmold.skilltask.api.";

    private final ObjectMapper objectMapper;
    private final SkillTaskProperties properties;
    private final CloudMoldCapabilityCatalog capabilityCatalog;
    private volatile Map<String, SkillTaskDefinition> definitions = Map.of();

    public SkillTaskDefinitionRegistry(ObjectMapper objectMapper, SkillTaskProperties properties,
                                       CloudMoldCapabilityCatalog capabilityCatalog) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.capabilityCatalog = capabilityCatalog;
    }

    @PostConstruct
    public void reload() {
        Path root = properties.getRegistryRoot();
        if (!Files.isDirectory(root)) {
            if (properties.isFailOnEmptyRegistry()) {
                throw new IllegalStateException("Skill Task registry root is unavailable: " + root);
            }
            definitions = Map.of();
            return;
        }
        List<Path> files;
        try (var paths = Files.walk(root, 2)) {
            files = paths.filter(path -> path.getFileName().toString().equals("skill-task.json"))
                    .sorted().toList();
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot scan Skill Task registry: " + root, ex);
        }
        List<SkillTaskDefinition> loaded = new ArrayList<>();
        for (Path file : files) {
            try {
                loaded.add(objectMapper.readValue(file.toFile(), SkillTaskDefinition.class));
            } catch (IOException ex) {
                throw new IllegalStateException("Cannot read Skill Task definition: " + file, ex);
            }
        }
        if (loaded.isEmpty() && properties.isFailOnEmptyRegistry()) {
            throw new IllegalStateException("Skill Task registry contains no skill-task.json definitions: " + root);
        }
        definitions = validateAndIndex(loaded);
    }

    public SkillTaskDefinition require(String skillId, String skillVersion) {
        SkillTaskDefinition definition = definitions.get(key(skillId, skillVersion));
        if (definition == null) {
            throw new IllegalArgumentException("Skill Task definition is not registered: " + skillId + "@" + skillVersion);
        }
        return definition;
    }

    public List<SkillTaskDefinition> all() {
        return definitions.values().stream().sorted(Comparator.comparing(SkillTaskDefinition::getSkillId)).toList();
    }

    Map<String, SkillTaskDefinition> validateAndIndex(List<SkillTaskDefinition> candidates) {
        Map<String, SkillTaskDefinition> indexed = new LinkedHashMap<>();
        for (SkillTaskDefinition definition : candidates) {
            definition.setSchemaVersion(requireText(definition.getSchemaVersion(), "schema_version", 64));
            if (!SCHEMA_VERSION.equals(definition.getSchemaVersion())) {
                throw new IllegalArgumentException("Unsupported Skill Task schema: " + definition.getSchemaVersion());
            }
            definition.setSkillId(requireText(definition.getSkillId(), "skill_id", 191));
            definition.setSkillVersion(requireText(definition.getSkillVersion(), "skill_version", 64));
            String riskLevel = normalizeRisk(definition.getRiskLevel());
            definition.setRiskLevel(riskLevel);
            int maxAttempts = definition.getMaxAttempts() == null
                    ? properties.getDefaultMaxAttempts() : definition.getMaxAttempts();
            if (maxAttempts <= 0 || maxAttempts > 20) {
                throw new IllegalArgumentException("max_attempts must be between 1 and 20");
            }
            definition.setMaxAttempts(maxAttempts);
            if (definition.getSteps() == null || definition.getSteps().isEmpty()) {
                throw new IllegalArgumentException("Skill Task definition has no steps: " + definition.getSkillId());
            }
            Set<String> stepCodes = new HashSet<>();
            Set<Integer> stepOrders = new HashSet<>();
            for (SkillTaskDefinition.Step step : definition.getSteps()) {
                validateStep(definition, step, riskLevel, stepCodes, stepOrders);
            }
            definition.setSteps(definition.getSteps().stream()
                    .sorted(Comparator.comparing(SkillTaskDefinition.Step::getStepOrder)).toList());
            String key = key(definition.getSkillId(), definition.getSkillVersion());
            if (indexed.putIfAbsent(key, definition) != null) {
                throw new IllegalArgumentException("Duplicate Skill Task definition: " + key);
            }
        }
        return Map.copyOf(indexed);
    }

    private void validateStep(SkillTaskDefinition definition, SkillTaskDefinition.Step step, String riskLevel,
                              Set<String> stepCodes, Set<Integer> stepOrders) {
        step.setStepCode(requireText(step.getStepCode(), "step_code", 128));
        if (!stepCodes.add(step.getStepCode())) {
            throw new IllegalArgumentException("Duplicate step_code: " + step.getStepCode());
        }
        if (step.getStepOrder() == null || step.getStepOrder() <= 0 || !stepOrders.add(step.getStepOrder())) {
            throw new IllegalArgumentException("step_order must be positive and unique: " + step.getStepCode());
        }
        step.setCapabilityId(requireText(step.getCapabilityId(), "capability_id", 255));
        CapabilityDescriptor descriptor = capabilityCatalog.require(step.getCapabilityId());
        if (descriptor.interfaceName().startsWith(ORCHESTRATION_API_PACKAGE)) {
            throw new IllegalArgumentException("Skill Task orchestration APIs cannot be nested as domain steps: "
                    + step.getCapabilityId());
        }
        String actualType = descriptor.operationType().name();
        String declaredType = requireText(step.getOperationType(), "operation_type", 8).toUpperCase(Locale.ROOT);
        if (!actualType.equals(declaredType)) {
            throw new IllegalArgumentException("Capability operation type mismatch for " + step.getCapabilityId());
        }
        step.setOperationType(declaredType);
        if (step.getArguments() == null || !step.getArguments().isArray()) {
            throw new IllegalArgumentException("Step arguments must be a JSON array: " + step.getStepCode());
        }
        if (step.getArguments().size() != descriptor.parameterTypes().size()) {
            throw new IllegalArgumentException("Step argument count does not match capability contract: "
                    + step.getCapabilityId());
        }
        if ("WRITE".equals(declaredType)) {
            if ("R1".equals(riskLevel)) {
                throw new IllegalArgumentException("WRITE steps require R2 or R3 risk: " + definition.getSkillId());
            }
            if (!Boolean.TRUE.equals(step.getApprovalRequired())) {
                throw new IllegalArgumentException("WRITE steps must require approval: " + step.getStepCode());
            }
            validateIdempotencyBinding(step, descriptor);
        } else {
            step.setApprovalRequired(false);
            step.setIdempotencyBinding(null);
        }
    }

    private static void validateIdempotencyBinding(SkillTaskDefinition.Step step, CapabilityDescriptor descriptor) {
        SkillTaskDefinition.IdempotencyBinding binding = step.getIdempotencyBinding();
        if (binding == null || binding.getArgumentIndex() == null) {
            throw new IllegalArgumentException("WRITE step must declare an explicit idempotency_binding: "
                    + step.getStepCode());
        }
        int argumentIndex = binding.getArgumentIndex();
        if (argumentIndex < 0 || argumentIndex >= descriptor.parameterTypes().size()) {
            throw new IllegalArgumentException("idempotency_binding argument_index is outside the capability contract: "
                    + step.getStepCode());
        }
        String pointer = binding.getJsonPointer() == null ? "" : binding.getJsonPointer().trim();
        if (!pointer.isEmpty() && !pointer.startsWith("/")) {
            throw new IllegalArgumentException("idempotency_binding json_pointer must be empty or start with '/': "
                    + step.getStepCode());
        }
        JsonNode argumentTemplate = step.getArguments().get(argumentIndex);
        JsonNode boundTemplate = pointer.isEmpty() ? argumentTemplate : argumentTemplate.at(pointer);
        if (!boundTemplate.isTextual() || !"$task.stepIdempotencyKey".equals(boundTemplate.asText())) {
            throw new IllegalArgumentException("WRITE step idempotency_binding must point exactly to "
                    + "$task.stepIdempotencyKey: " + step.getStepCode());
        }
        binding.setJsonPointer(pointer);
    }

    private static String key(String skillId, String version) {
        return skillId + "@" + version;
    }

    private static String normalizeRisk(String value) {
        String risk = requireText(value, "risk_level", 2).toUpperCase(Locale.ROOT);
        if (!Set.of("R1", "R2", "R3").contains(risk)) {
            throw new IllegalArgumentException("risk_level must be R1, R2, or R3");
        }
        return risk;
    }

    private static String requireText(String value, String name, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(name + " exceeds " + maxLength + " characters");
        }
        return normalized;
    }
}
