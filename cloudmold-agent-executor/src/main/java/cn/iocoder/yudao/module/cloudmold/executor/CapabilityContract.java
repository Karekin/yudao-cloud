package cn.iocoder.yudao.module.cloudmold.executor;

import com.fasterxml.jackson.databind.JsonNode;

public record CapabilityContract(
        String capabilityId,
        String interfaceName,
        String methodName,
        CapabilityOperationType operationType,
        boolean writeApprovalRequired,
        String group,
        String version,
        int timeoutMillis,
        JsonNode argumentsSchema,
        JsonNode resultSchema) {
}
