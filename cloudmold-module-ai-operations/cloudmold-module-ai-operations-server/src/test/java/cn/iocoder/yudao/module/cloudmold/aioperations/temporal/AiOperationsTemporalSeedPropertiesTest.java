package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiOperationsTemporalSeedPropertiesTest {

    @Test
    void shouldTreatBracketPlaceholderAsEmptyTenantList() {
        AiOperationsTemporalSeedProperties properties = bind(Map.of(
                "cloudmold.ai-operations.temporal.seed.tenant-ids", "[]"));

        assertThat(properties.getTenantIds()).isEmpty();
    }

    @Test
    void shouldParseCommaSeparatedTenantIdsWithoutEnvironmentOverride() {
        AiOperationsTemporalSeedProperties properties = bind(Map.of(
                "cloudmold.ai-operations.temporal.seed.tenant-ids", "162, 163"));

        assertThat(properties.getTenantIds()).containsExactly(162L, 163L);
    }

    @Test
    void shouldDefaultHourlySeedToGovernedProductToListingWorkflow() {
        AiOperationsTemporalSeedProperties properties = new AiOperationsTemporalSeedProperties();

        assertThat(properties.getSkillId())
                .isEqualTo("skill.cloudmold.commerce.product-to-listing.v1");
        assertThat(properties.getSkillVersion()).isEqualTo("1.0.0");
        assertThat(properties.getRoleCode()).isEqualTo("merchandising");
        assertThat(properties.getActionCode()).isEqualTo("catalog.publish");
        assertThat(properties.getIntervalSeconds()).isEqualTo(3600L);
    }

    private static AiOperationsTemporalSeedProperties bind(Map<String, Object> values) {
        return new Binder(new MapConfigurationPropertySource(values))
                .bind("cloudmold.ai-operations.temporal.seed",
                        Bindable.of(AiOperationsTemporalSeedProperties.class))
                .orElseGet(AiOperationsTemporalSeedProperties::new);
    }
}
