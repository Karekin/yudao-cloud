package cn.iocoder.yudao.module.cloudmold.risk.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.cloudmold.risk.controller.admin.vo.RiskReviewPageReqVO;
import cn.iocoder.yudao.module.cloudmold.risk.service.query.RiskReviewPageItem;
import cn.iocoder.yudao.module.cloudmold.risk.service.query.RiskReviewQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - Canonical Risk Review Query")
@RestController
@RequestMapping("/cloudmold/risk")
public class RiskReviewQueryController {

    @Resource
    private RiskReviewQueryService riskReviewQueryService;

    @GetMapping("/review/page")
    @Operation(summary = "分页查询规范风控审核案例，只返回当前租户 CloudMold 权威数据")
    @PreAuthorize("@ss.hasPermission('cloudmold:risk:query')")
    public CommonResult<PageResult<RiskReviewPageItem>> getPage(@Valid RiskReviewPageReqVO request) {
        return success(riskReviewQueryService.getPage(request));
    }
}
