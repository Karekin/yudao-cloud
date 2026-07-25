package cn.iocoder.yudao.module.cloudmold.appcommerce.controller.app;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.cloudmold.appcommerce.service.AppCommunityService;
import cn.iocoder.yudao.module.cloudmold.appcommerce.service.AppCommunityView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "用户 App - CloudMold 社区发现")
@RestController
@RequestMapping("/cloudmold/app/community")
@Validated
public class AppCommunityController {

    @Resource
    private AppCommunityService communityService;

    @GetMapping("/feed")
    @Operation(summary = "查询已发布社区内容及当前用户互动状态")
    public CommonResult<AppCommunityView.Page> feed(
            @RequestParam(defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return success(communityService.feed(pageNo, pageSize));
    }

    @GetMapping("/posts/{contentId}")
    @Operation(summary = "查询已发布社区内容详情并重验关联商品可售状态")
    public CommonResult<AppCommunityView> detail(
            @PathVariable @Pattern(regexp = "^[0-9a-fA-F-]{36}$") String contentId) {
        return success(communityService.detail(contentId));
    }

    @GetMapping("/posts/{contentId}/comments")
    @Operation(summary = "查询社区内容的加密评论投影")
    public CommonResult<AppCommunityView.CommentPage> comments(
            @PathVariable @Pattern(regexp = "^[0-9a-fA-F-]{36}$") String contentId,
            @RequestParam(defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int pageSize) {
        return success(communityService.comments(contentId, pageNo, pageSize));
    }

    @PostMapping("/posts")
    @Operation(summary = "以当前会员身份创建、提交并由 LOCAL_TEST 策略审核社区内容")
    public CommonResult<AppCommunityView> createPost(@Valid @RequestBody CreatePostReq request) {
        return success(communityService.createPost(request.getIdempotencyKey(), request.getBody(),
                request.getListingId(), request.getListingOfferId(),
                request.getCanonicalSpuId(), request.getCanonicalSkuId()));
    }

    @PostMapping("/posts/{contentId}/comments")
    @Operation(summary = "以当前会员身份发表加密评论")
    public CommonResult<AppCommunityView> addComment(
            @PathVariable @Pattern(regexp = "^[0-9a-fA-F-]{36}$") String contentId,
            @Valid @RequestBody CommentReq request) {
        return success(communityService.addComment(request.getIdempotencyKey(), contentId, request.getBody()));
    }

    @PutMapping("/posts/{contentId}/like")
    @Operation(summary = "幂等设置当前会员点赞状态")
    public CommonResult<AppCommunityView> setLiked(
            @PathVariable @Pattern(regexp = "^[0-9a-fA-F-]{36}$") String contentId,
            @Valid @RequestBody LikeReq request) {
        return success(communityService.setLiked(request.getIdempotencyKey(), contentId, request.isLiked()));
    }

    @Data
    public static class CreatePostReq {
        @NotBlank
        @Size(min = 8, max = 96)
        private String idempotencyKey;
        @NotBlank
        @Size(max = 2000)
        private String body;
        @NotBlank
        @Pattern(regexp = "^[0-9a-fA-F-]{36}$")
        private String listingId;
        @NotBlank
        @Pattern(regexp = "^[0-9a-fA-F-]{36}$")
        private String listingOfferId;
        @NotBlank
        @Pattern(regexp = "^[0-9a-fA-F-]{36}$")
        private String canonicalSpuId;
        @NotBlank
        @Pattern(regexp = "^[0-9a-fA-F-]{36}$")
        private String canonicalSkuId;
    }

    @Data
    public static class CommentReq {
        @NotBlank
        @Size(min = 8, max = 96)
        private String idempotencyKey;
        @NotBlank
        @Size(max = 1000)
        private String body;
    }

    @Data
    public static class LikeReq {
        @NotBlank
        @Size(min = 8, max = 96)
        private String idempotencyKey;
        private boolean liked;
    }
}
