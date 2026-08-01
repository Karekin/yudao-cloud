package cn.iocoder.yudao.module.cloudmold.aioperations.skillcatalog;

import cn.iocoder.yudao.module.cloudmold.aioperations.controller.admin.skillcatalog.AiOperationsSkillCatalogController;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.util.AntPathMatcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiOperationsSkillCatalogControllerTest {

    private final DeerFlowSkillCatalogClient client = mock(DeerFlowSkillCatalogClient.class);
    private final AiOperationsSkillCatalogController controller = new AiOperationsSkillCatalogController(client);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldBelongToAdminControllerPackageSoFrameworkAddsAdminApiPrefix() {
        assertThat(new AntPathMatcher(".").match(
                "**.controller.admin.**",
                AiOperationsSkillCatalogController.class.getPackageName())).isTrue();
    }

    @Test
    void shouldExposeReadOnlyCatalogAndSkillContent() throws Exception {
        var catalog = objectMapper.readTree("{\"skill_count\":35}");
        var detail = objectMapper.readTree("{\"name\":\"merchant-skill\",\"content\":\"# Skill\"}");
        when(client.getCatalog()).thenReturn(catalog);
        when(client.getSkill("merchant-skill")).thenReturn(detail);

        var catalogResult = controller.getCatalog();
        var detailResult = controller.getSkill("merchant-skill");

        assertThat(catalogResult.isSuccess()).isTrue();
        assertThat(catalogResult.getData()).isSameAs(catalog);
        assertThat(detailResult.isSuccess()).isTrue();
        assertThat(detailResult.getData()).isSameAs(detail);
        verify(client).getCatalog();
        verify(client).getSkill("merchant-skill");
    }
}
