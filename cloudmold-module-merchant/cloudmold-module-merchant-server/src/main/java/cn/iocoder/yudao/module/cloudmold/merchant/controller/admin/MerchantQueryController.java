package cn.iocoder.yudao.module.cloudmold.merchant.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.cloudmold.merchant.api.workflow.MerchantManagedAdmissionWorkflowQueryApi;
import cn.iocoder.yudao.module.cloudmold.merchant.api.workflow.MerchantManagedAdmissionWorkflowResult;
import cn.iocoder.yudao.module.cloudmold.merchant.controller.admin.vo.MerchantPageReqVO;
import cn.iocoder.yudao.module.cloudmold.merchant.controller.admin.vo.MerchantShopPageReqVO;
import cn.iocoder.yudao.module.cloudmold.merchant.service.query.MerchantPageItem;
import cn.iocoder.yudao.module.cloudmold.merchant.service.query.MerchantQueryService;
import cn.iocoder.yudao.module.cloudmold.merchant.service.query.MerchantShopPageItem;
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
 * CloudMold 规范商家只读查询。
 * 仅读取 cloudmold_merchant_account / cloudmold_merchant_shop 权威表，
 * 不读取 yudao 旧 member/merchant 业务表；按当前租户隔离。
 */
@Tag(name = "CloudMold - Canonical Merchant Query")
@RestController
@RequestMapping("/cloudmold/merchant")
public class MerchantQueryController {

    @Resource
    private MerchantQueryService merchantQueryService;
    @Resource
    private MerchantManagedAdmissionWorkflowQueryApi merchantManagedAdmissionWorkflowQueryApi;

    @GetMapping("/merchants/page")
    @Operation(summary = "分页查询规范商家，只返回当前租户 CloudMold 权威数据")
    @PreAuthorize("@ss.hasPermission('cloudmold:merchant:query')")
    public CommonResult<PageResult<MerchantPageItem>> getMerchantPage(
            @Valid MerchantPageReqVO request) {
        return success(merchantQueryService.getMerchantPage(request));
    }

    @GetMapping("/shops/page")
    @Operation(summary = "分页查询规范店铺，只返回当前租户 CloudMold 权威数据")
    @PreAuthorize("@ss.hasPermission('cloudmold:merchant:query')")
    public CommonResult<PageResult<MerchantShopPageItem>> getShopPage(
            @Valid MerchantShopPageReqVO request) {
        return success(merchantQueryService.getShopPage(request));
    }

    @GetMapping("/managed-admission/workflow")
    @Operation(summary = "读取托管商家准入/验厂只读回传")
    @PreAuthorize("@ss.hasPermission('cloudmold:merchant:query')")
    public CommonResult<MerchantManagedAdmissionWorkflowResult> inspectManagedAdmissionWorkflow(String applicationId) {
        return success(merchantManagedAdmissionWorkflowQueryApi.inspect(applicationId));
    }

    @GetMapping("/managed-admission/workflow-by-merchant")
    @Operation(summary = "按规范商家读取托管准入、验厂与成长只读回传")
    @PreAuthorize("@ss.hasPermission('cloudmold:merchant:query')")
    public CommonResult<MerchantManagedAdmissionWorkflowResult> inspectManagedAdmissionWorkflowByMerchant(
            String merchantId) {
        return success(merchantManagedAdmissionWorkflowQueryApi.inspectByMerchantId(merchantId));
    }
}
