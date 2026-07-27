package cn.iocoder.yudao.module.cloudmold.skilltask.definition;

import cn.iocoder.yudao.module.cloudmold.executor.CloudMoldCapabilityCatalog;
import cn.iocoder.yudao.module.cloudmold.rpc.CloudMoldRpcProperties;
import cn.iocoder.yudao.module.cloudmold.skilltask.SkillTaskProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProductToListingSkillDefinitionCompatibilityTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void validatesProductToListingCompositionAgainstTheRealCapabilityCatalog() throws IOException {
        Path skillRoot = findWorkspaceSkillRoot();
        Assumptions.assumeTrue(skillRoot != null,
                "Sibling useful-scripts registry is not available in this checkout");

        SkillTaskDefinitionRegistry registry = new SkillTaskDefinitionRegistry(
                objectMapper,
                new SkillTaskProperties(),
                new CloudMoldCapabilityCatalog(new CloudMoldRpcProperties()));

        SkillTaskDefinition catalog = read(skillRoot.resolve("cloudmold-commerce-catalog-matrix/skill-task.json"));
        SkillTaskDefinition master = read(skillRoot.resolve("cloudmold-commerce-reuse-ready-master/skill-task.json"));
        SkillTaskDefinition productToListing = read(skillRoot.resolve("cloudmold-product-to-listing/skill-task.json"));

        var indexed = registry.validateAndIndex(List.of(catalog, master, productToListing));
        SkillTaskDefinition definition = indexed.get("skill.cloudmold.commerce.product-to-listing.v1@1.0.0");

        assertThat(definition).isNotNull();
        assertThat(definition.getRiskLevel()).isEqualTo("R3");
        assertThat(definition.getSteps()).hasSize(11);
        assertThat(definition.getDefinitionSha256()).hasSize(64);
        assertThat(definition.getDefinitionClosureSha256()).hasSize(64);
    }

    private SkillTaskDefinition read(Path path) throws IOException {
        return objectMapper.readValue(path.toFile(), SkillTaskDefinition.class);
    }

    private static Path findWorkspaceSkillRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            for (Path candidate : List.of(current.resolve("useful-scripts/skills"),
                    current.resolveSibling("useful-scripts").resolve("skills"))) {
                if (Files.isDirectory(candidate)) {
                    return candidate;
                }
            }
            current = current.getParent();
        }
        return null;
    }
}
