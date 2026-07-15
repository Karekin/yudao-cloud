package cn.iocoder.yudao.module.cloudmold.merchant.api;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.Instant;

/** Tenant-scoped external source identity. The service normalizes system/type/id before use. */
@Data
@Accessors(chain = true)
public class SourceReference {
    private String sourceSystem;
    private String sourceType;
    private String sourceId;
    /** Effective instant to resolve. Defaults to the current instant when omitted. */
    private Instant effectiveAt;
}
