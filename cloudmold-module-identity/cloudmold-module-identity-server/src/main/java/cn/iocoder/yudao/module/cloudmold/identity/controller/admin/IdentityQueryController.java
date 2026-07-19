package cn.iocoder.yudao.module.cloudmold.identity.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.cloudmold.identity.controller.admin.vo.IdentityOperationPageReqVO;
import cn.iocoder.yudao.module.cloudmold.identity.controller.admin.vo.IdentityPrincipalPageReqVO;
import cn.iocoder.yudao.module.cloudmold.identity.controller.admin.vo.IdentitySourcePageReqVO;
import cn.iocoder.yudao.module.cloudmold.identity.service.query.IdentityAdminQueryService;
import cn.iocoder.yudao.module.cloudmold.identity.service.query.IdentityOperationPageItem;
import cn.iocoder.yudao.module.cloudmold.identity.service.query.IdentityPrincipalPageItem;
import cn.iocoder.yudao.module.cloudmold.identity.service.query.IdentitySourcePageItem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * CloudMold 规范身份只读查询。
 * 仅读取 cloudmold_identity_principal / source_identity / operation 权威表，按当前租户隔离。
 */
@Tag(name = "CloudMold - Canonical Identity Query")
@RestController
@RequestMapping("/cloudmold/identity")
public class IdentityQueryController {

    @Resource
    private IdentityAdminQueryService identityAdminQueryService;

    @GetMapping("/principals/page")
    @Operation(summary = "分页查询规范身份主体，只返回当前租户 CloudMold 权威数据")
    @PreAuthorize("@ss.hasPermission('cloudmold:identity:query')")
    public CommonResult<PageResult<IdentityPrincipalPageItem>> getPrincipalPage(
            @Valid IdentityPrincipalPageReqVO request) {
        return success(identityAdminQueryService.getPrincipalPage(request));
    }

    @GetMapping("/sources/page")
    @Operation(summary = "分页查询规范来源身份，只返回当前租户 CloudMold 权威数据")
    @PreAuthorize("@ss.hasPermission('cloudmold:identity:query')")
    public CommonResult<PageResult<IdentitySourcePageItem>> getSourcePage(
            @Valid IdentitySourcePageReqVO request) {
        return success(identityAdminQueryService.getSourcePage(request));
    }

    @GetMapping("/operations/page")
    @Operation(summary = "分页查询身份操作记录，只返回当前租户 CloudMold 权威数据")
    @PreAuthorize("@ss.hasPermission('cloudmold:identity:query')")
    public CommonResult<PageResult<IdentityOperationPageItem>> getOperationPage(
            @Valid IdentityOperationPageReqVO request) {
        return success(identityAdminQueryService.getOperationPage(request));
    }
}
