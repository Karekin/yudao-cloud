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
        if (action.equals("commerce.autonomous-day")
                || action.equals("commerce.full-chain")
                || action.equals("consumer.journey")) {
            return List.of("customer-service", "finance");
        }
        if (action.equals("category.daily-operations")) {
            return List.of("risk", "operations-lead");
        }
        if (startsWithAny(action, "buyer.", "purchase.", "procurement.", "replenishment.",
                "supply-planning.", "supplier.")) {
            return List.of("buyer", "finance");
        }
        if (startsWithAny(action, "warehouse.", "wms.")) {
            return List.of("inventory-control", "operations-control");
        }
        if (action.startsWith("finance.")) {
            return List.of("risk", "operations-control");
        }
        if (startsWithAny(action, "mission.stockout", "inventory.stockout")) {
            return List.of("risk", "finance");
        }
        if (startsWithAny(action, "catalog.", "product.", "listing.", "pricing.", "merchandising.")) {
            return List.of("risk", "finance");
        }
        if (startsWithAny(action, "merchant.", "merchant-operations.", "seller.", "shop.")) {
            return List.of("risk", "legal");
        }
        if (startsWithAny(action, "customer-service.", "after-sale.", "aftersale.", "refund.",
                "compensation.", "payment.refund", "trade.refund")) {
            return List.of("customer-service", "finance");
        }
        if (action.equals("quality.inspection-recall")) {
            return List.of("quality", "operations-lead");
        }
        if (action.equals("fulfillment.exception-resolution")) {
            return List.of("customer-service", "operations-lead");
        }
        if (action.equals("crossborder.fulfillment-compliance")
                || action.equals("crossborder.bonded-customs")) {
            return List.of("risk", "legal");
        }
        if (action.equals("partner-marketing.kol-media-operations")) {
            return List.of("risk", "legal");
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
