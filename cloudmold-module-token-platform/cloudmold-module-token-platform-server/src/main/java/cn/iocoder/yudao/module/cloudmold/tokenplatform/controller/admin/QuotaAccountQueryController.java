package cn.iocoder.yudao.module.cloudmold.tokenplatform.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.cloudmold.tokenplatform.controller.admin.vo.QuotaAccountPageReqVO;
import cn.iocoder.yudao.module.cloudmold.tokenplatform.service.query.QuotaAccountPageItem;
import cn.iocoder.yudao.module.cloudmold.tokenplatform.service.query.QuotaAccountQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - Canonical Token Platform Quota Account Query")
@RestController
@RequestMapping("/cloudmold/token-platform")
public class QuotaAccountQueryController {

    @Resource
    private QuotaAccountQueryService quotaAccountQueryService;

    @GetMapping("/page")
    @Operation(summary = "分页查询规范 AI Token 配额账户，只返回当前租户 CloudMold 权威数据")
    @PreAuthorize("@ss.hasPermission('cloudmold:token-platform:query')")
    public CommonResult<PageResult<QuotaAccountPageItem>> getPage(@Valid QuotaAccountPageReqVO request) {
        return success(quotaAccountQueryService.getPage(request));
    }
}
