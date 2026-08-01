package cn.iocoder.yudao.module.cloudmold.aioperations.skillcatalog;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - AI Operations Job Capability Query")
@RestController
@RequestMapping("/cloudmold/ai-operations/job-capabilities")
@Validated
public class AiOperationsSkillCatalogController {

    private final DeerFlowSkillCatalogClient skillCatalogClient;

    public AiOperationsSkillCatalogController(DeerFlowSkillCatalogClient skillCatalogClient) {
        this.skillCatalogClient = skillCatalogClient;
    }

    @GetMapping
    @Operation(summary = "按业务板块、业务领域和岗位查询 DeerFlow 运行中 Skill")
    @PreAuthorize("@ss.hasPermission('cloudmold:ai-operations:query')")
    public CommonResult<JsonNode> getCatalog() {
        return success(skillCatalogClient.getCatalog());
    }

    @GetMapping("/{skillName}")
    @Operation(summary = "只读查询 DeerFlow 运行中 Skill 的内容与摘要")
    @PreAuthorize("@ss.hasPermission('cloudmold:ai-operations:query')")
    public CommonResult<JsonNode> getSkill(@PathVariable String skillName) {
        return success(skillCatalogClient.getSkill(skillName));
    }
}
