package cn.iocoder.yudao.module.cloudmold.metadata.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.cloudmold.metadata.controller.admin.vo.MetadataDefinitionPageReqVO;
import cn.iocoder.yudao.module.cloudmold.metadata.service.query.MetadataDefinitionPageItem;
import cn.iocoder.yudao.module.cloudmold.metadata.service.query.MetadataDefinitionQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - Canonical Metadata Definition Query")
@RestController
@RequestMapping("/cloudmold/metadata")
public class MetadataDefinitionQueryController {

    @Resource
    private MetadataDefinitionQueryService metadataDefinitionQueryService;

    @GetMapping("/definition/page")
    @Operation(summary = "分页查询规范元数据定义，只返回当前租户 CloudMold 权威数据")
    @PreAuthorize("@ss.hasPermission('cloudmold:metadata:query')")
    public CommonResult<PageResult<MetadataDefinitionPageItem>> getPage(@Valid MetadataDefinitionPageReqVO request) {
        return success(metadataDefinitionQueryService.getPage(request));
    }
}
