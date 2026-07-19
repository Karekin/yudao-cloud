package cn.iocoder.yudao.module.cloudmold.aftersale.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.cloudmold.aftersale.controller.admin.vo.AfterSalePageReqVO;
import cn.iocoder.yudao.module.cloudmold.aftersale.service.query.AfterSalePageItem;
import cn.iocoder.yudao.module.cloudmold.aftersale.service.query.AfterSaleQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - Canonical After Sale Query")
@RestController
@RequestMapping("/cloudmold/aftersale")
public class AfterSaleQueryController {

    @Resource
    private AfterSaleQueryService afterSaleQueryService;

    @GetMapping("/page")
    @Operation(summary = "分页查询规范售后案例，只返回当前租户 CloudMold 权威数据")
    @PreAuthorize("@ss.hasPermission('cloudmold:aftersale:query')")
    public CommonResult<PageResult<AfterSalePageItem>> getPage(@Valid AfterSalePageReqVO request) {
        return success(afterSaleQueryService.getPage(request));
    }
}
