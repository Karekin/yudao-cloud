package cn.iocoder.yudao.module.cloudmold.identity.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceIdentityView {
    private String sourceIdentityId;
    private String principalId;
    private String principalType;
    private String principalStatus;
    private String sourceSystem;
    private String sourceType;
    private String sourceId;
    private String sourceStatus;
    private Long principalVersion;
    private Long sourceVersion;
    private Instant validFrom;
    private Instant validTo;
}
