package cn.iocoder.yudao.module.cloudmold.commercebehavior.controller.app;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.ratelimiter.core.annotation.RateLimiter;
import cn.iocoder.yudao.module.cloudmold.commercebehavior.api.CommerceBehaviorCommandApi;
import cn.iocoder.yudao.module.cloudmold.commercebehavior.api.CommerceBehaviorCommandApi.CommerceBehaviorCommandResult;
import cn.iocoder.yudao.module.cloudmold.commercebehavior.api.CommerceBehaviorCommandApi.RecordCommerceBehaviorCommand;
import cn.iocoder.yudao.module.cloudmold.commercebehavior.api.CommerceBehaviorCommandApi.StartCommerceSessionCommand;
import cn.iocoder.yudao.module.cloudmold.commercebehavior.security.CommerceBehaviorClientIpRateLimiterKeyResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "用户 App - CloudMold 消费者行为")
@RestController
@RequestMapping("/cloudmold/commerce-behavior")
@Validated
public class AppCommerceBehaviorController {

    private static final String SOURCE_SYSTEM = "YSHOPPING_UNIAPP";
    private static final String SESSION_SOURCE_TYPE = "MOBILE_SESSION";
    private static final String EVENT_SOURCE_TYPE = "MOBILE_EVENT";

    @Resource
    private CommerceBehaviorCommandApi commandApi;

    @PostMapping("/session/start")
    @Operation(summary = "开始一个匿名消费者会话")
    @PermitAll
    @RateLimiter(time = 60, count = 60,
            keyResolver = CommerceBehaviorClientIpRateLimiterKeyResolver.class)
    public CommonResult<CommerceBehaviorCommandResult> startSession(
            @Valid @RequestBody AppStartCommerceSessionReqVO request) {
        Instant occurredAt = Instant.now();
        return success(commandApi.startSession(StartCommerceSessionCommand.builder()
                .idempotencyKey("mobile-session:" + request.getSessionId())
                .runId(request.getSessionId())
                .sessionId(request.getSessionId())
                .channelCode(request.getChannelCode())
                .entrypointCode(request.getEntrypointCode())
                .sourceSystem(SOURCE_SYSTEM)
                .sourceType(SESSION_SOURCE_TYPE)
                .sourceId("mobile-session:" + request.getSessionId())
                .correlationId(request.getSessionId())
                .occurredAt(occurredAt)
                .build()));
    }

    @PostMapping("/event/record")
    @Operation(summary = "记录一个脱敏的消费者行为事件")
    @PermitAll
    @RateLimiter(time = 60, count = 300,
            keyResolver = CommerceBehaviorClientIpRateLimiterKeyResolver.class)
    public CommonResult<CommerceBehaviorCommandResult> recordBehavior(
            @Valid @RequestBody AppRecordCommerceBehaviorReqVO request) {
        Instant occurredAt = Instant.now();
        return success(commandApi.recordBehavior(RecordCommerceBehaviorCommand.builder()
                .idempotencyKey("mobile-event:" + request.getBehaviorId())
                .runId(request.getSessionId())
                .behaviorId(request.getBehaviorId())
                .sessionId(request.getSessionId())
                .behaviorType(request.getBehaviorType())
                .canonicalSpuId(request.getCanonicalSpuId())
                .skuId(request.getSkuId())
                .listingId(request.getListingId())
                .listingOfferId(request.getListingOfferId())
                .expectedPriceMinor(request.getExpectedPriceMinor())
                .currencyCode(request.getCurrencyCode())
                .searchToken(request.getSearchToken())
                .resultSetToken(request.getResultSetToken())
                .resultPosition(request.getResultPosition())
                .quantity(request.getQuantity())
                .checkoutToken(request.getCheckoutToken())
                .sourceSystem(SOURCE_SYSTEM)
                .sourceType(EVENT_SOURCE_TYPE)
                .sourceId("mobile-event:" + request.getBehaviorId())
                .correlationId(request.getSessionId())
                .occurredAt(occurredAt)
                .build()));
    }

    @Data
    public static class AppStartCommerceSessionReqVO {

        @NotBlank
        @Pattern(regexp = "^[0-9a-fA-F-]{36}$")
        private String sessionId;

        @NotBlank
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,31}$")
        private String channelCode;

        @NotBlank
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,31}$")
        private String entrypointCode;
    }

    @Data
    public static class AppRecordCommerceBehaviorReqVO {

        @NotBlank
        @Pattern(regexp = "^[0-9a-fA-F-]{36}$")
        private String behaviorId;

        @NotBlank
        @Pattern(regexp = "^[0-9a-fA-F-]{36}$")
        private String sessionId;

        @NotBlank
        @Pattern(regexp = "^(PDP_VIEWED|SEARCH_REQUESTED|SEARCH_RESULT_EXPOSED|SEARCH_RESULT_CLICKED|CART_ADDED|CART_REMOVED|CHECKOUT_STARTED|CHECKOUT_ABANDONED)$")
        private String behaviorType;

        @Pattern(regexp = "^[0-9a-fA-F-]{36}$")
        private String canonicalSpuId;

        @Pattern(regexp = "^[0-9a-fA-F-]{36}$")
        private String skuId;

        @Pattern(regexp = "^[0-9a-fA-F-]{36}$")
        private String listingId;

        @Pattern(regexp = "^[0-9a-fA-F-]{36}$")
        private String listingOfferId;

        @Min(0)
        private Long expectedPriceMinor;

        @Size(max = 8)
        private String currencyCode;

        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9:_-]{7,127}$")
        private String searchToken;

        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9:_-]{7,127}$")
        private String resultSetToken;

        @Min(0)
        private Integer resultPosition;

        @Positive
        private Integer quantity;

        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9:_-]{7,127}$")
        private String checkoutToken;
    }
}
