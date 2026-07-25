package cn.iocoder.yudao.module.cloudmold.commercebehavior.security;

import cn.iocoder.yudao.framework.ratelimiter.core.annotation.RateLimiter;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommerceBehaviorClientIpRateLimiterKeyResolverTest {

    private final CommerceBehaviorClientIpRateLimiterKeyResolver resolver =
            new CommerceBehaviorClientIpRateLimiterKeyResolver();

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void ignoresPayloadArgumentsWithinOneEndpointAndIp() {
        JoinPoint first = joinPoint("recordBehavior", new Object[]{"event-a"});
        JoinPoint second = joinPoint("recordBehavior", new Object[]{"event-b"});
        bindRequest("192.0.2.10");

        assertThat(resolver.resolver(first, rateLimiter()))
                .isEqualTo(resolver.resolver(second, rateLimiter()));
    }

    @Test
    void separatesDifferentClientIps() {
        JoinPoint point = joinPoint("recordBehavior", new Object[]{"event-a"});
        bindRequest("192.0.2.10");
        String first = resolver.resolver(point, rateLimiter());
        bindRequest("192.0.2.11");

        assertThat(resolver.resolver(point, rateLimiter())).isNotEqualTo(first);
    }

    private static JoinPoint joinPoint(String method, Object[] arguments) {
        JoinPoint point = mock(JoinPoint.class);
        Signature signature = mock(Signature.class);
        when(signature.toLongString()).thenReturn(
                "public CommonResult AppCommerceBehaviorController." + method + "(Request)");
        when(point.getSignature()).thenReturn(signature);
        when(point.getArgs()).thenReturn(arguments);
        return point;
    }

    private static RateLimiter rateLimiter() {
        return mock(RateLimiter.class);
    }

    private static void bindRequest(String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
