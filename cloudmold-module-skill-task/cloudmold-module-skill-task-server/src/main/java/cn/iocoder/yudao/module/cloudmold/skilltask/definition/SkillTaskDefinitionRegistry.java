package cn.iocoder.yudao.module.cloudmold.skilltask.definition;

import cn.iocoder.yudao.module.cloudmold.executor.CapabilityDescriptor;
import cn.iocoder.yudao.module.cloudmold.executor.CloudMoldCapabilityCatalog;
import cn.iocoder.yudao.module.cloudmold.skilltask.SkillTaskProperties;
import cn.iocoder.yudao.module.cloudmold.skilltask.definition.dynamic.DynamicSkillTaskDefinitionSource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.HexFormat;

@Component
public class SkillTaskDefinitionRegistry {

    public static final String SCHEMA_VERSION = "cloudmold.skill-task-definition/v1";
    private static final String ORCHESTRATION_API_PACKAGE =
            "cn.iocoder.yudao.module.cloudmold.skilltask.api.";

    private final ObjectMapper objectMapper;
    private final SkillTaskProperties properties;
    private final CloudMoldCapabilityCatalog capabilityCatalog;
    private final DynamicSkillTaskDefinitionSource dynamicDefinitionSource;
    private volatile Map<String, SkillTaskDefinition> definitions = Map.of();

    public SkillTaskDefinitionRegistry(ObjectMapper objectMapper, SkillTaskProperties properties,
                                       CloudMoldCapabilityCatalog capabilityCatalog) {
        this(objectMapper, properties, capabilityCatalog, Optional.empty());
    }

    @Autowired
    public SkillTaskDefinitionRegistry(ObjectMapper objectMapper, SkillTaskProperties properties,
                                       CloudMoldCapabilityCatalog capabilityCatalog,
                                       Optional<DynamicSkillTaskDefinitionSource> dynamicDefinitionSource) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.capabilityCatalog = capabilityCatalog;
        this.dynamicDefinitionSource = dynamicDefinitionSource.orElse(null);
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
        String normalizedSkillId = requireText(skillId, "skill_id", 191);
        String normalizedSkillVersion = requireText(skillVersion, "skill_version", 64);
        if (dynamicDefinitionSource != null && properties.getDynamic().isEnabled()) {
            return requireDynamic(normalizedSkillId, normalizedSkillVersion);
        }
        SkillTaskDefinition definition = definitions.get(key(normalizedSkillId, normalizedSkillVersion));
        if (definition == null) {
            return requireDynamic(normalizedSkillId, normalizedSkillVersion);
        }
        return definition;
    }

    public List<SkillTaskDefinition> all() {
        return definitions.values().stream().sorted(Comparator.comparing(SkillTaskDefinition::getSkillId)).toList();
    }

    private SkillTaskDefinition requireDynamic(String skillId, String skillVersion) {
        if (dynamicDefinitionSource == null) {
            throw new IllegalArgumentException("Skill Task definition is not registered: " + skillId + "@"
                    + skillVersion);
        }
        Map<String, SkillTaskDefinition> bundle = new LinkedHashMap<>();
        Map<String, DynamicSkillTaskDefinitionSource.ResolvedDefinition> dynamicSnapshots = new LinkedHashMap<>();
        String resolvedKey = loadDefinitionRecursive(skillId, skillVersion, bundle, dynamicSnapshots, new HashSet<>(), true);
        Map<String, SkillTaskDefinition> validated = validateAndIndex(new ArrayList<>(bundle.values()));
        dynamicSnapshots.forEach((definitionKey, snapshot) -> {
            SkillTaskDefinition validatedDefinition = validated.get(definitionKey);
            if (validatedDefinition != null) {
                dynamicDefinitionSource.recordLineage(snapshot, validatedDefinition);
            }
        });
        SkillTaskDefinition resolved = validated.get(resolvedKey);
        if (resolved == null) {
            throw new IllegalArgumentException("Skill Task definition is not registered: " + skillId + "@"
                    + skillVersion);
        }
        return resolved;
    }

    private String loadDefinitionRecursive(String skillId,
                                           String skillVersion,
                                           Map<String, SkillTaskDefinition> bundle,
                                           Map<String, DynamicSkillTaskDefinitionSource.ResolvedDefinition> dynamicSnapshots,
                                           Set<String> loading,
                                           boolean preferDynamic) {
        String directKey = key(skillId, skillVersion);
        if (bundle.containsKey(directKey)) {
            return directKey;
        }
        if (!preferDynamic) {
            SkillTaskDefinition fileDefinition = definitions.get(directKey);
            if (fileDefinition != null) {
                bundle.put(directKey, cloneDefinition(fileDefinition));
                loadChildren(bundle.get(directKey), bundle, dynamicSnapshots, loading);
                return directKey;
            }
        }
        DynamicSkillTaskDefinitionSource.ResolvedDefinition resolved = dynamicDefinitionSource.resolve(skillId, skillVersion);
        if (resolved != null) {
            String actualKey = key(resolved.skillId(), resolved.skillVersion());
            if (!bundle.containsKey(actualKey)) {
                bundle.put(actualKey, parseDynamicDefinition(resolved));
                dynamicSnapshots.put(actualKey, resolved);
                loadChildren(bundle.get(actualKey), bundle, dynamicSnapshots, loading);
            }
            return actualKey;
        }
        SkillTaskDefinition fileDefinition = definitions.get(directKey);
        if (fileDefinition != null) {
            bundle.put(directKey, cloneDefinition(fileDefinition));
            loadChildren(bundle.get(directKey), bundle, dynamicSnapshots, loading);
            return directKey;
        }
        throw new IllegalArgumentException("Skill Task definition is not registered: " + skillId + "@"
                + skillVersion);
    }

    private void loadChildren(SkillTaskDefinition definition,
                              Map<String, SkillTaskDefinition> bundle,
                              Map<String, DynamicSkillTaskDefinitionSource.ResolvedDefinition> dynamicSnapshots,
                              Set<String> loading) {
        String currentKey = key(definition.getSkillId(), definition.getSkillVersion());
        if (!loading.add(currentKey)) {
            return;
        }
        try {
            for (SkillTaskDefinition.Step step : definition.getSteps()) {
                if ("SUBMIT_CHILD".equals(step.getStepKind())) {
                    loadDefinitionRecursive(step.getChildSkillId(), step.getChildSkillVersion(),
                            bundle, dynamicSnapshots, loading, true);
                }
            }
        } finally {
            loading.remove(currentKey);
        }
    }

    private SkillTaskDefinition cloneDefinition(SkillTaskDefinition definition) {
        return objectMapper.convertValue(definition, SkillTaskDefinition.class);
    }

    private SkillTaskDefinition parseDynamicDefinition(DynamicSkillTaskDefinitionSource.ResolvedDefinition resolved) {
        try {
            return objectMapper.readValue(resolved.canonicalDefinitionJson(), SkillTaskDefinition.class);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read dynamic Skill Task definition: "
                    + resolved.skillId() + "@" + resolved.skillVersion(), exception);
        }
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
            String workflowLevel = definition.getWorkflowLevel() == null
                    ? "BUSINESS_ROLE"
                    : definition.getWorkflowLevel().trim().toUpperCase(Locale.ROOT);
            if (!Set.of("BUSINESS_ROLE", "INTERNAL_SUBFLOW").contains(workflowLevel)) {
                throw new IllegalArgumentException("workflow_level must be BUSINESS_ROLE or INTERNAL_SUBFLOW: "
                        + definition.getSkillId());
            }
            definition.setWorkflowLevel(workflowLevel);
            if (definition.getOwnerRole() != null) {
                definition.setOwnerRole(requireText(definition.getOwnerRole(), "owner_role", 64));
            }
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
        validateComposition(indexed);
        populateDefinitionProofs(indexed);
        return Map.copyOf(indexed);
    }

    private void populateDefinitionProofs(Map<String, SkillTaskDefinition> indexed) {
        for (SkillTaskDefinition definition : indexed.values()) {
            definition.setDefinitionSha256(sha256(canonical(objectMapper.valueToTree(definition))));
        }
        for (String definitionKey : indexed.keySet()) {
            definitionClosureSha256(definitionKey, indexed, new LinkedHashMap<>());
        }
    }

    private String definitionClosureSha256(String definitionKey, Map<String, SkillTaskDefinition> indexed,
                                           Map<String, String> resolved) {
        String existing = resolved.get(definitionKey);
        if (existing != null) {
            return existing;
        }
        SkillTaskDefinition definition = indexed.get(definitionKey);
        var closure = objectMapper.createObjectNode();
        closure.put("schema", "cloudmold.skill-task-definition-closure/v1");
        closure.put("definitionSha256", definition.getDefinitionSha256());
        var children = closure.putArray("children");
        definition.getSteps().stream().filter(step -> "SUBMIT_CHILD".equals(step.getStepKind()))
                .sorted(Comparator.comparing(SkillTaskDefinition.Step::getStepCode))
                .forEach(step -> {
                    String childKey = key(step.getChildSkillId(), step.getChildSkillVersion());
                    var child = children.addObject();
                    child.put("stepCode", step.getStepCode());
                    child.put("skillId", step.getChildSkillId());
                    child.put("skillVersion", step.getChildSkillVersion());
                    child.put("definitionClosureSha256", definitionClosureSha256(childKey, indexed, resolved));
                });
        String hash = sha256(canonical(closure));
        resolved.put(definitionKey, hash);
        definition.setDefinitionClosureSha256(hash);
        return hash;
    }

    private String canonical(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(sort(value));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot canonicalize Skill Task definition", exception);
        }
    }

    private JsonNode sort(JsonNode value) {
        if (value.isObject()) {
            var result = objectMapper.createObjectNode();
            List<String> names = new ArrayList<>();
            value.fieldNames().forEachRemaining(names::add);
            names.stream().sorted().forEach(name -> result.set(name, sort(value.get(name))));
            return result;
        }
        if (value.isArray()) {
            var result = objectMapper.createArrayNode();
            value.forEach(item -> result.add(sort(item)));
            return result;
        }
        return value.deepCopy();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
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
        String stepKind = step.getStepKind() == null || step.getStepKind().isBlank()
                ? "CAPABILITY" : step.getStepKind().trim().toUpperCase(Locale.ROOT);
        step.setStepKind(stepKind);
        if ("SUBMIT_CHILD".equals(stepKind)) {
            validateSubmitChild(definition, step, riskLevel);
            return;
        }
        if ("WAIT_CHILD".equals(stepKind)) {
            validateWaitChild(definition, step, riskLevel);
            return;
        }
        if ("WAIT_CAPABILITY".equals(stepKind)) {
            validateWaitCapability(step);
            return;
        }
        if (!"CAPABILITY".equals(stepKind)) {
            throw new IllegalArgumentException("Unsupported step_kind: " + stepKind);
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

    private static void validateSubmitChild(SkillTaskDefinition definition, SkillTaskDefinition.Step step,
                                            String riskLevel) {
        if (!"R3".equals(riskLevel)) {
            throw new IllegalArgumentException("SUBMIT_CHILD steps require an R3 parent Skill: "
                    + definition.getSkillId());
        }
        step.setChildSkillId(requireText(step.getChildSkillId(), "child_skill_id", 191));
        step.setChildSkillVersion(requireText(step.getChildSkillVersion(), "child_skill_version", 64));
        if (step.getArguments() == null || !step.getArguments().isObject()) {
            throw new IllegalArgumentException("SUBMIT_CHILD arguments must be a JSON object: " + step.getStepCode());
        }
        if (step.getChildRunId() != null && !step.getChildRunId().isBlank()) {
            step.setChildRunId(requireText(step.getChildRunId(), "child_run_id", 255));
        } else {
            step.setChildRunId(null);
        }
        step.setCapabilityId(null);
        step.setOperationType("ORCHESTRATE");
        step.setApprovalRequired(false);
        step.setIdempotencyBinding(null);
        step.setPollIntervalSeconds(null);
    }

    private static void validateWaitChild(SkillTaskDefinition definition, SkillTaskDefinition.Step step,
                                          String riskLevel) {
        if (!"R3".equals(riskLevel)) {
            throw new IllegalArgumentException("WAIT_CHILD steps require an R3 parent Skill: "
                    + definition.getSkillId());
        }
        if (step.getArguments() == null || !step.getArguments().isArray() || step.getArguments().size() != 1) {
            throw new IllegalArgumentException("WAIT_CHILD arguments must contain exactly one child task ID: "
                    + step.getStepCode());
        }
        int pollSeconds = step.getPollIntervalSeconds() == null ? 2 : step.getPollIntervalSeconds();
        if (pollSeconds < 1 || pollSeconds > 300) {
            throw new IllegalArgumentException("poll_interval_seconds must be between 1 and 300: "
                    + step.getStepCode());
        }
        step.setPollIntervalSeconds(pollSeconds);
        step.setCapabilityId(null);
        step.setOperationType("ORCHESTRATE");
        step.setApprovalRequired(false);
        step.setIdempotencyBinding(null);
        step.setChildSkillId(null);
        step.setChildSkillVersion(null);
        step.setChildRunId(null);
    }

    private void validateWaitCapability(SkillTaskDefinition.Step step) {
        step.setCapabilityId(requireText(step.getCapabilityId(), "capability_id", 255));
        CapabilityDescriptor descriptor = capabilityCatalog.require(step.getCapabilityId());
        if (descriptor.interfaceName().startsWith(ORCHESTRATION_API_PACKAGE)) {
            throw new IllegalArgumentException("Skill Task orchestration APIs cannot be polled as domain state: "
                    + step.getCapabilityId());
        }
        if (!"READ".equals(descriptor.operationType().name())) {
            throw new IllegalArgumentException("WAIT_CAPABILITY may only poll a READ capability: "
                    + step.getCapabilityId());
        }
        if (step.getArguments() == null || !step.getArguments().isArray()
                || step.getArguments().size() != descriptor.parameterTypes().size()) {
            throw new IllegalArgumentException("WAIT_CAPABILITY arguments do not match the capability contract: "
                    + step.getStepCode());
        }
        validateWaitSuccess(step.getWaitSuccess(), step.getStepCode());
        validateWaitFailure(step.getWaitFailure(), step.getStepCode());
        int pollSeconds = step.getPollIntervalSeconds() == null ? 2 : step.getPollIntervalSeconds();
        if (pollSeconds < 1 || pollSeconds > 300) {
            throw new IllegalArgumentException("poll_interval_seconds must be between 1 and 300: "
                    + step.getStepCode());
        }
        step.setPollIntervalSeconds(pollSeconds);
        step.setOperationType("READ");
        step.setApprovalRequired(false);
        step.setIdempotencyBinding(null);
        step.setChildSkillId(null);
        step.setChildSkillVersion(null);
        step.setChildRunId(null);
    }

    private static void validateWaitSuccess(JsonNode condition, String stepCode) {
        if (condition == null || !condition.isObject() || condition.isEmpty()) {
            throw new IllegalArgumentException("WAIT_CAPABILITY wait_success must be a non-empty object: " + stepCode);
        }
        condition.fields().forEachRemaining(entry -> {
            validateJsonPointer(entry.getKey(), stepCode);
            if (!entry.getValue().isValueNode() || entry.getValue().isNull()) {
                throw new IllegalArgumentException("WAIT_CAPABILITY success values must be non-null scalars: "
                        + stepCode);
            }
        });
    }

    private static void validateWaitFailure(JsonNode condition, String stepCode) {
        if (condition == null || condition.isNull()) {
            return;
        }
        if (!condition.isObject()) {
            throw new IllegalArgumentException("WAIT_CAPABILITY wait_failure must be an object: " + stepCode);
        }
        condition.fields().forEachRemaining(entry -> {
            validateJsonPointer(entry.getKey(), stepCode);
            if (!entry.getValue().isArray() || entry.getValue().isEmpty()) {
                throw new IllegalArgumentException("WAIT_CAPABILITY failure values must be non-empty arrays: "
                        + stepCode);
            }
            entry.getValue().forEach(value -> {
                if (!value.isValueNode() || value.isNull()) {
                    throw new IllegalArgumentException("WAIT_CAPABILITY failure values must be non-null scalars: "
                            + stepCode);
                }
            });
        });
    }

    private static void validateJsonPointer(String pointer, String stepCode) {
        if (pointer == null || pointer.isBlank() || !pointer.startsWith("/")) {
            throw new IllegalArgumentException("WAIT_CAPABILITY conditions require JSON Pointer keys: " + stepCode);
        }
    }

    private static void validateComposition(Map<String, SkillTaskDefinition> indexed) {
        for (SkillTaskDefinition parent : indexed.values()) {
            for (SkillTaskDefinition.Step step : parent.getSteps()) {
                if (!"SUBMIT_CHILD".equals(step.getStepKind())) {
                    continue;
                }
                SkillTaskDefinition child = indexed.get(key(step.getChildSkillId(), step.getChildSkillVersion()));
                if (child == null) {
                    throw new IllegalArgumentException("Child Skill Task definition is not registered: "
                            + step.getChildSkillId() + "@" + step.getChildSkillVersion());
                }
                if (riskRank(child.getRiskLevel()) > riskRank(parent.getRiskLevel())) {
                    throw new IllegalArgumentException("Child Skill risk cannot exceed its parent: "
                            + step.getChildSkillId());
                }
            }
        }
        for (String definitionKey : indexed.keySet()) {
            detectCycle(definitionKey, indexed, new HashSet<>(), new HashSet<>());
        }
    }

    private static void detectCycle(String current, Map<String, SkillTaskDefinition> indexed,
                                    Set<String> visiting, Set<String> visited) {
        if (visited.contains(current)) {
            return;
        }
        if (!visiting.add(current)) {
            throw new IllegalArgumentException("Skill Task composition cycle detected at " + current);
        }
        for (SkillTaskDefinition.Step step : indexed.get(current).getSteps()) {
            if ("SUBMIT_CHILD".equals(step.getStepKind())) {
                detectCycle(key(step.getChildSkillId(), step.getChildSkillVersion()), indexed, visiting, visited);
            }
        }
        visiting.remove(current);
        visited.add(current);
    }

    private static int riskRank(String risk) {
        return switch (risk) {
            case "R1" -> 1;
            case "R2" -> 2;
            case "R3" -> 3;
            default -> throw new IllegalArgumentException("Unsupported risk level: " + risk);
        };
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
        if ((boundTemplate.isMissingNode() || boundTemplate.isNull()) && "/idempotencyKey".equals(pointer)
                && argumentTemplate.isObject() && argumentTemplate.has("$object")
                && argumentTemplate.path("$overrides").isObject()) {
            boundTemplate = argumentTemplate.path("$overrides").path("idempotencyKey");
            if (boundTemplate.isMissingNode()) {
                boundTemplate = argumentTemplate.path("$overrides").path("/idempotencyKey");
            }
        }
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
