package cn.iocoder.yudao.module.cloudmold.identity.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.cloudmold.identity.api.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - Canonical Identity")
@RestController
@RequestMapping("/cloudmold/identity")
public class IdentityCommandController {

    @Resource
    private IdentityCommandApi identityCommandApi;
    @Resource
    private IdentityQueryApi identityQueryApi;

    @PostMapping("/source/link")
    @Operation(summary = "Link one validated source account to a canonical Principal")
    @PreAuthorize("@ss.hasPermission('cloudmold:identity:source:link')")
    public CommonResult<LinkSourceIdentityResult> linkSource(@RequestBody LinkSourceIdentityCommand command) {
        return success(identityCommandApi.linkSource(command));
    }

    @PostMapping("/source/resolve")
    @Operation(summary = "Resolve an active source account to its canonical Principal")
    @PreAuthorize("@ss.hasPermission('cloudmold:identity:source:query')")
    public CommonResult<SourceIdentityView> resolveActiveSource(@RequestBody SourceIdentityReference reference) {
        return success(identityQueryApi.resolveActiveSource(reference));
    }
}
