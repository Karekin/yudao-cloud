package cn.iocoder.yudao.module.cloudmold.appcommerce.service;

import cn.iocoder.yudao.module.cloudmold.commercebehavior.api.CommerceBehaviorCommandApi;
import cn.iocoder.yudao.module.cloudmold.commercebehavior.api.CommerceBehaviorCommandApi.AttributePaidOrderCommand;
import cn.iocoder.yudao.module.cloudmold.commercebehavior.api.CommerceBehaviorCommandApi.CommerceBehaviorCommandResult;
import cn.iocoder.yudao.module.cloudmold.commercebehavior.api.CommerceBehaviorCommandApi.LinkCommerceSessionIdentityCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AppCommerceBehaviorService {

    private static final String SOURCE_SYSTEM = "YSHOPPING_UNIAPP";
    private static final String SOURCE_TYPE = "AUTHENTICATED_APP_FACADE";

    private final AppMemberPrincipalResolver principalResolver;
    private final CommerceBehaviorCommandApi commandApi;

    public CommerceBehaviorCommandResult linkCurrentMember(String idempotencyKey, String sessionId,
                                                           Long expectedSessionVersion) {
        AppMemberPrincipalView principal = principalResolver.requireCurrent();
        Instant occurredAt = Instant.now();
        return commandApi.linkSessionIdentity(LinkCommerceSessionIdentityCommand.builder()
                .idempotencyKey(idempotencyKey)
                .runId(sessionId)
                .sessionId(sessionId)
                .principalId(principal.getPrincipalId())
                .expectedSessionVersion(expectedSessionVersion)
                .sourceSystem(SOURCE_SYSTEM)
                .sourceType(SOURCE_TYPE)
                .sourceId("member-session:" + sessionId)
                .correlationId(sessionId)
                .occurredAt(occurredAt)
                .build());
    }

    public CommerceBehaviorCommandResult attributePayment(String idempotencyKey, String sessionId,
                                                          Long expectedSessionVersion, String checkoutToken,
                                                          String orderId, String paymentId) {
        principalResolver.requireCurrent();
        Instant occurredAt = Instant.now();
        String attributionId = UUID.nameUUIDFromBytes(
                ("app-payment-attribution:" + paymentId).getBytes(StandardCharsets.UTF_8)).toString();
        return commandApi.attributePaidOrder(AttributePaidOrderCommand.builder()
                .idempotencyKey(idempotencyKey)
                .runId(sessionId)
                .attributionId(attributionId)
                .sessionId(sessionId)
                .expectedSessionVersion(expectedSessionVersion)
                .checkoutToken(checkoutToken)
                .orderId(orderId)
                .paymentId(paymentId)
                .sourceSystem(SOURCE_SYSTEM)
                .sourceType(SOURCE_TYPE)
                .sourceId("member-payment:" + paymentId)
                .correlationId(sessionId)
                .causationId(orderId)
                .occurredAt(occurredAt)
                .build());
    }
}
