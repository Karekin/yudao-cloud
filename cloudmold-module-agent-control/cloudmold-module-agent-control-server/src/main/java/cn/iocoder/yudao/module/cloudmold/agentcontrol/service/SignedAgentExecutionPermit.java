package cn.iocoder.yudao.module.cloudmold.agentcontrol.service;

import java.time.Instant;

public record SignedAgentExecutionPermit(String approvalRef, String approvalRefSha256,
                                         String permitVersion, String keyId, Instant expiresAt) {
}
