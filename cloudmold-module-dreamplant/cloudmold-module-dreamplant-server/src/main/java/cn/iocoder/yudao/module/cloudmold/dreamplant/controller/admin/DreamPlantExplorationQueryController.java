package cn.iocoder.yudao.module.cloudmold.dreamplant.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.cloudmold.dreamplant.controller.admin.vo.DreamPlantExplorationPageReqVO;
import cn.iocoder.yudao.module.cloudmold.dreamplant.service.query.DreamPlantExplorationPageItem;
import cn.iocoder.yudao.module.cloudmold.dreamplant.service.query.DreamPlantExplorationQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - Canonical DreamPlant Exploration Query")
@RestController
@RequestMapping("/cloudmold/dreamplant")
public class DreamPlantExplorationQueryController {

    @Resource
    private DreamPlantExplorationQueryService dreamPlantExplorationQueryService;

    @GetMapping("/explorations/page")
    @Operation(summary = "分页查询规范 DreamPlant 探索运行，只返回当前租户 CloudMold 权威数据")
    @PreAuthorize("@ss.hasPermission('cloudmold:dreamplant:query')")
    public CommonResult<PageResult<DreamPlantExplorationPageItem>> getPage(@Valid DreamPlantExplorationPageReqVO request) {
        return success(dreamPlantExplorationQueryService.getPage(request));
    }
}
