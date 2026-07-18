package cn.iocoder.yudao.module.cloudmold.order.api.cancellation;

import java.util.Arrays;

public enum OrderCancellationResponsibilityCode {
    MERCHANT_STOCKOUT(OrderCancellationResponsibilityParty.MERCHANT),
    MERCHANT_SELLER_REJECTED(OrderCancellationResponsibilityParty.MERCHANT),
    MERCHANT_MANUAL_CANCEL(OrderCancellationResponsibilityParty.MERCHANT),
    BUYER_CHANGED_MIND(OrderCancellationResponsibilityParty.BUYER),
    BUYER_ADDRESS_ERROR(OrderCancellationResponsibilityParty.BUYER),
    BUYER_PAYMENT_ABANDONED(OrderCancellationResponsibilityParty.BUYER),
    PLATFORM_RISK_BLOCK(OrderCancellationResponsibilityParty.PLATFORM),
    PLATFORM_COMPLIANCE_BLOCK(OrderCancellationResponsibilityParty.PLATFORM),
    PLATFORM_PRICE_ERROR(OrderCancellationResponsibilityParty.PLATFORM),
    CARRIER_UNSERVICEABLE(OrderCancellationResponsibilityParty.CARRIER),
    CARRIER_CAPACITY_BLOCK(OrderCancellationResponsibilityParty.CARRIER),
    CARRIER_DAMAGE_RISK(OrderCancellationResponsibilityParty.CARRIER);

    private final OrderCancellationResponsibilityParty party;

    OrderCancellationResponsibilityCode(OrderCancellationResponsibilityParty party) {
        this.party = party;
    }

    public OrderCancellationResponsibilityParty party() {
        return party;
    }

    public static boolean matches(String party, String code) {
        if (!OrderCancellationResponsibilityParty.isSupported(party) || code == null) {
            return false;
        }
        return Arrays.stream(values())
                .anyMatch(value -> value.name().equals(code) && value.party.name().equals(party));
    }
}
