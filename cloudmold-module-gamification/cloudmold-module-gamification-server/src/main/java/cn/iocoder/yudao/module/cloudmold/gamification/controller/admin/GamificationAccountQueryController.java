package cn.iocoder.yudao.module.cloudmold.gamification.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.cloudmold.gamification.controller.admin.vo.GamificationAccountPageReqVO;
import cn.iocoder.yudao.module.cloudmold.gamification.service.query.GamificationAccountPageItem;
import cn.iocoder.yudao.module.cloudmold.gamification.service.query.GamificationAccountQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - Canonical Gamification Account Query")
@RestController
@RequestMapping("/cloudmold/gamification")
public class GamificationAccountQueryController {

    @Resource
    private GamificationAccountQueryService gamificationAccountQueryService;

    @GetMapping("/account/page")
    @Operation(summary = "分页查询规范游戏币账户，只返回当前租户 CloudMold 权威数据")
    @PreAuthorize("@ss.hasPermission('cloudmold:gamification:query')")
    public CommonResult<PageResult<GamificationAccountPageItem>> getPage(@Valid GamificationAccountPageReqVO request) {
        return success(gamificationAccountQueryService.getPage(request));
    }
}
