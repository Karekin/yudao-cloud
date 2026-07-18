package cn.iocoder.yudao.module.cloudmold.rpc;

import java.util.Objects;

public record CloudMoldRpcCallContext(
        long tenantId,
        long operatorId,
        int operatorType,
        String skillId,
        String runId) {

    private static final ThreadLocal<CloudMoldRpcCallContext> CURRENT = new ThreadLocal<>();

    public CloudMoldRpcCallContext {
        if (tenantId <= 0 || operatorId <= 0 || operatorType <= 0) {
            throw new IllegalArgumentException("tenantId, operatorId and operatorType must be positive");
        }
        skillId = requireText(skillId, "skillId");
        runId = requireText(runId, "runId");
    }

    public static Scope open(CloudMoldRpcCallContext context) {
        Objects.requireNonNull(context, "context");
        CloudMoldRpcCallContext previous = CURRENT.get();
        CURRENT.set(context);
        return () -> {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        };
    }

    public static CloudMoldRpcCallContext requireCurrent() {
        CloudMoldRpcCallContext context = CURRENT.get();
        if (context == null) {
            throw new IllegalStateException("CloudMold RPC context is required");
        }
        return context;
    }

    public static void clear() {
        CURRENT.remove();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
