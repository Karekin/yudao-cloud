package cn.iocoder.yudao.module.cloudmold.appcommerce.controller.app;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.cloudmold.appcommerce.service.AppRecommendationDecisionView;
import cn.iocoder.yudao.module.cloudmold.appcommerce.service.AppRecommendationService;
import cn.iocoder.yudao.module.cloudmold.commercebehavior.api.CommerceBehaviorCommandApi;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "用户 App - CloudMold 推荐")
@RestController
@RequestMapping("/cloudmold/app/recommendations")
@Validated
public class AppRecommendationController {

    @Resource
    private AppRecommendationService recommendationService;

    @GetMapping
    @PermitAll
    @Operation(summary = "按确定性本地策略返回真实可售推荐")
    public CommonResult<AppRecommendationDecisionView> recommend(
            @RequestParam @Pattern(regexp = "^[0-9a-fA-F-]{36}$") String sessionId,
            @RequestParam(defaultValue = "HOME_FEED") @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,31}$") String sceneCode,
            @RequestParam(defaultValue = "12") @Min(1) @Max(20) int pageSize) {
        return success(recommendationService.recommend(sessionId, sceneCode, pageSize));
    }

    @PostMapping("/exposures")
    @PermitAll
    @Operation(summary = "记录推荐曝光，服务端校验 token、listing、rank 与 TTL")
    public CommonResult<CommerceBehaviorCommandApi.CommerceBehaviorCommandResult> exposure(
            @Valid @RequestBody RecommendationBehaviorReq request) {
        return success(recommendationService.recordExposure(request.getIdempotencyKey(), request.getSessionId(),
                request.getDecisionToken(), request.getListingId(), request.getRank()));
    }

    @PostMapping("/clicks")
    @PermitAll
    @Operation(summary = "记录推荐点击，服务端校验 token、listing、rank 与 TTL")
    public CommonResult<CommerceBehaviorCommandApi.CommerceBehaviorCommandResult> click(
            @Valid @RequestBody RecommendationBehaviorReq request) {
        return success(recommendationService.recordClick(request.getIdempotencyKey(), request.getSessionId(),
                request.getDecisionToken(), request.getListingId(), request.getRank()));
    }

    @Data
    public static class RecommendationBehaviorReq {
        @NotBlank
        @Size(min = 8, max = 128)
        private String idempotencyKey;

        @NotBlank
        @Pattern(regexp = "^[0-9a-fA-F-]{36}$")
        private String sessionId;

        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9:_-]{7,127}$")
        private String decisionToken;

        @NotBlank
        @Pattern(regexp = "^[0-9a-fA-F-]{36}$")
        private String listingId;

        @Min(1)
        private int rank;
    }
}
