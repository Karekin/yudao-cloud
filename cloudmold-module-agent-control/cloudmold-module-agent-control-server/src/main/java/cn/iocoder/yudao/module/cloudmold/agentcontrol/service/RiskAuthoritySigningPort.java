package cn.iocoder.yudao.module.cloudmold.agentcontrol.service;

import java.time.Duration;

/**
 * Outbound boundary to the independent Risk Authority. Agent Control owns the
 * permit claims, while the authority owns all production private-key material.
 */
public interface RiskAuthoritySigningPort {

    RiskAuthoritySignResponse sign(RiskAuthoritySignRequest request, Duration timeout);

    record RiskAuthoritySignRequest(String requestId, String permitId, String algorithm, String claimsPayload) {
    }

    record RiskAuthoritySignResponse(String requestId, String permitId, String keyId,
                                     String algorithm, String signature) {
    }
}
