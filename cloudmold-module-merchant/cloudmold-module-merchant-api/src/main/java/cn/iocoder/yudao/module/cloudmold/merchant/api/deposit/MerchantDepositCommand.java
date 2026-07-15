package cn.iocoder.yudao.module.cloudmold.merchant.api.deposit;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.Instant;

@Data
@Accessors(chain = true)
public class MerchantDepositCommand {
    private MerchantDepositOperation operation;
    private String idempotencyKey;
    private String runId;
    private String merchantId;
    /** Null only when creating the account with ASSESS_REQUIRED. */
    private Long expectedAccountVersion;
    /** Always an integer amount in the currency's minor unit. */
    private Long amountMinor;
    private String currency;
    private String policyVersion;
    private String businessReference;
    private String reasonCode;
    /** Opaque restricted-store or evidence reference. Never raw payment/bank/voucher data. */
    private String evidenceRef;
    private String sourceSystem;
    private String traceId;
    private String correlationId;
    private String causationId;
    private Instant occurredAt;
}
