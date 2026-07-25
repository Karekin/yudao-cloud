package cn.iocoder.yudao.module.cloudmold.appcommerce.controller.app;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.cloudmold.appcommerce.service.AppCartService;
import cn.iocoder.yudao.module.cloudmold.appcommerce.service.AppCartView;
import cn.iocoder.yudao.module.cloudmold.appcommerce.service.AppCheckoutView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "用户 App - CloudMold 服务端购物车")
@RestController("cloudmoldAppCartController")
@RequestMapping("/cloudmold/app/cart")
@Validated
public class AppCartController {

    @Resource
    private AppCartService cartService;

    @GetMapping
    @Operation(summary = "查询当前登录会员的服务端购物车")
    public CommonResult<AppCartView> get() {
        return success(cartService.get());
    }

    @PostMapping("/items")
    @Operation(summary = "加入购物车或合并相同行")
    public CommonResult<AppCartView> addOrMerge(@Valid @RequestBody AddCartItemReq request) {
        return success(cartService.addOrMerge(request.getIdempotencyKey(), request.getExpectedCartVersion(),
                request.getListingId(), request.getListingOfferId(), request.getCanonicalSkuId(),
                request.getQuantity(), request.getSelected()));
    }

    @PutMapping("/items/{lineId}/quantity")
    @Operation(summary = "更新购物车行数量")
    public CommonResult<AppCartView> updateQuantity(@PathVariable String lineId,
                                                    @Valid @RequestBody UpdateQuantityReq request) {
        return success(cartService.updateQuantity(request.getIdempotencyKey(), request.getExpectedCartVersion(),
                lineId, request.getQuantity()));
    }

    @PutMapping("/items/{lineId}/selection")
    @Operation(summary = "更新购物车行选中状态")
    public CommonResult<AppCartView> updateSelection(@PathVariable String lineId,
                                                     @Valid @RequestBody UpdateSelectionReq request) {
        return success(cartService.updateSelection(request.getIdempotencyKey(), request.getExpectedCartVersion(),
                lineId, request.isSelected()));
    }

    @PostMapping("/items/{lineId}/remove")
    @Operation(summary = "删除购物车行")
    public CommonResult<AppCartView> remove(@PathVariable String lineId,
                                            @Valid @RequestBody VersionedMutationReq request) {
        return success(cartService.removeLine(request.getIdempotencyKey(), request.getExpectedCartVersion(), lineId));
    }

    @PostMapping("/clear")
    @Operation(summary = "清空购物车")
    public CommonResult<AppCartView> clear(@Valid @RequestBody VersionedMutationReq request) {
        return success(cartService.clear(request.getIdempotencyKey(), request.getExpectedCartVersion()));
    }

    @PostMapping("/checkout/preview")
    @Operation(summary = "从购物车中唯一选中行创建结算预览")
    public CommonResult<AppCheckoutView> previewSelected(@Valid @RequestBody PreviewSelectedReq request) {
        return success(cartService.previewSelected(request.getIdempotencyKey(), request.getExpectedCartVersion(),
                request.getAddressRef()));
    }

    @Data
    public static class VersionedMutationReq {
        @NotBlank
        @Size(min = 8, max = 128)
        private String idempotencyKey;
        @NotNull
        @PositiveOrZero
        private Long expectedCartVersion;
    }

    @Data
    public static class AddCartItemReq extends VersionedMutationReq {
        @NotBlank
        private String listingId;
        @NotBlank
        private String listingOfferId;
        @NotBlank
        private String canonicalSkuId;
        @Min(1)
        @Max(99)
        private int quantity;
        private Boolean selected;
    }

    @Data
    public static class UpdateQuantityReq extends VersionedMutationReq {
        @Min(1)
        @Max(99)
        private int quantity;
    }

    @Data
    public static class UpdateSelectionReq extends VersionedMutationReq {
        private boolean selected;
    }

    @Data
    public static class PreviewSelectedReq extends VersionedMutationReq {
        @NotBlank
        @Pattern(regexp = "^[0-9a-fA-F-]{36}$")
        private String addressRef;
    }
}
