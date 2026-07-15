package cn.iocoder.yudao.module.cloudmold.identity.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LinkSourceIdentityResult {
    private Long operationId;
    private String principalId;
    private String sourceIdentityId;
    private String principalStatus;
    private Long aggregateVersion;
    private Boolean duplicate;
}
