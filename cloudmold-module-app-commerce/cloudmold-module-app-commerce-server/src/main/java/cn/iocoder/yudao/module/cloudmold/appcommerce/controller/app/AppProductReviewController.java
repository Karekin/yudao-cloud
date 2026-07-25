package cn.iocoder.yudao.module.cloudmold.appcommerce.controller.app;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.cloudmold.appcommerce.service.AppProductReviewEligibilityView;
import cn.iocoder.yudao.module.cloudmold.appcommerce.service.AppProductReviewService;
import cn.iocoder.yudao.module.cloudmold.appcommerce.service.AppProductReviewView;
import cn.iocoder.yudao.module.cloudmold.appcommerce.service.AppPublicProductReviewPageView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "用户 App - CloudMold 商品评价")
@RestController
@RequestMapping("/cloudmold/app/product-reviews")
@Validated
public class AppProductReviewController {

    @Resource
    private AppProductReviewService productReviewService;

    @GetMapping("/eligibility")
    @Operation(summary = "查询当前订单行是否可评价")
    public CommonResult<AppProductReviewEligibilityView> eligibility(
            @RequestParam @NotBlank String orderId,
            @RequestParam @NotBlank String orderItemId) {
        return success(productReviewService.getEligibility(orderId, orderItemId));
    }

    @PostMapping
    @Operation(summary = "创建商品评价")
    public CommonResult<AppProductReviewView> create(@Valid @RequestBody CreateProductReviewReq request) {
        return success(productReviewService.create(request.getIdempotencyKey(), request.getOrderId(),
                request.getOrderItemId(), request.getProductScore(), request.getServiceScore(),
                request.getLogisticsScore(), request.getBody()));
    }

    @GetMapping("/listings/{listingId}")
    @PermitAll
    @Operation(summary = "查询刊登下公开 APPROVED 评价")
    public CommonResult<AppPublicProductReviewPageView> byListing(
            @PathVariable String listingId,
            @RequestParam(defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int pageSize) {
        return success(productReviewService.listApprovedByListing(listingId, pageNo, pageSize));
    }

    @GetMapping("/products/{canonicalSpuId}")
    @PermitAll
    @Operation(summary = "查询商品下公开 APPROVED 评价")
    public CommonResult<AppPublicProductReviewPageView> byProduct(
            @PathVariable String canonicalSpuId,
            @RequestParam(defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int pageSize) {
        return success(productReviewService.listApprovedByProduct(canonicalSpuId, pageNo, pageSize));
    }

    @Data
    public static class CreateProductReviewReq {
        @NotBlank
        @Size(min = 8, max = 128)
        private String idempotencyKey;
        @NotBlank
        private String orderId;
        @NotBlank
        private String orderItemId;
        @Min(1)
        @Max(5)
        private int productScore;
        @Min(1)
        @Max(5)
        private int serviceScore;
        @Min(1)
        @Max(5)
        private int logisticsScore;
        @NotBlank
        @Size(max = 2000)
        private String body;
    }
}
