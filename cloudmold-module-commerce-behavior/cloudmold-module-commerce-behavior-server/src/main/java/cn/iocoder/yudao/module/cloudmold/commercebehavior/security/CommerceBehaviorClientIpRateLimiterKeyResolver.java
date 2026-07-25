package cn.iocoder.yudao.module.cloudmold.commercebehavior.security;

import cn.hutool.crypto.SecureUtil;
import cn.iocoder.yudao.framework.common.util.servlet.ServletUtils;
import cn.iocoder.yudao.framework.ratelimiter.core.annotation.RateLimiter;
import cn.iocoder.yudao.framework.ratelimiter.core.keyresolver.RateLimiterKeyResolver;
import org.aspectj.lang.JoinPoint;
import org.springframework.stereotype.Component;

/**
 * Limits anonymous behavior ingestion by endpoint and client IP.
 *
 * <p>The upstream IP resolver also includes method arguments. That is useful for
 * business operations, but would let every unique behavior payload allocate a
 * separate bucket. Anonymous telemetry needs one shared bucket per endpoint/IP.</p>
 */
@Component
public class CommerceBehaviorClientIpRateLimiterKeyResolver
        implements RateLimiterKeyResolver {

    @Override
    public String resolver(JoinPoint joinPoint, RateLimiter rateLimiter) {
        return SecureUtil.sha256(
                joinPoint.getSignature().toLongString() + ":" + ServletUtils.getClientIP());
    }
}
