package cn.iocoder.yudao.module.cloudmold.agentcontrol.integration.bpm;

import java.util.List;
import java.util.Locale;

/**
 * Maps a frozen business action to the tenant-governed responsibility roles
 * that must countersign an R3 decision.
 *
 * <p>The codes identify governance roles, never users. Tenant administrators
 * decide which active users hold each role through actor-role grants.</p>
 */
final class AgentApprovalR3Policy {

    private AgentApprovalR3Policy() {
    }

    static List<String> requiredRoleCodes(String actionCode) {
        String action = actionCode == null ? "" : actionCode.trim().toLowerCase(Locale.ROOT);
        if (startsWithAny(action, "buyer.", "purchase.", "procurement.", "replenishment.",
                "supply-planning.")) {
            return List.of("buyer", "finance");
        }
        if (startsWithAny(action, "catalog.", "product.", "listing.", "pricing.", "merchandising.")) {
            return List.of("merchandising", "risk");
        }
        if (startsWithAny(action, "merchant.", "merchant-operations.", "seller.", "shop.")) {
            return List.of("merchant-operations", "risk", "legal");
        }
        if (startsWithAny(action, "customer-service.", "after-sale.", "aftersale.", "refund.",
                "compensation.", "payment.refund", "trade.refund")) {
            return List.of("customer-service", "finance");
        }
        if (startsWithAny(action, "quality.", "recall.", "capa.")) {
            return List.of("quality", "risk", "operations-lead");
        }
        throw new IllegalStateException("R3 action domain has no responsibility policy: " + actionCode);
    }

    private static boolean startsWithAny(String value, String... prefixes) {
        for (String prefix : prefixes) {
            if (value.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
