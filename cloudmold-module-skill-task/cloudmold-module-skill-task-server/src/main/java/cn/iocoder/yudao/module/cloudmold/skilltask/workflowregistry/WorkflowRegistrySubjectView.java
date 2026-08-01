package cn.iocoder.yudao.module.cloudmold.skilltask.workflowregistry;

import com.fasterxml.jackson.annotation.JsonProperty;

public record WorkflowRegistrySubjectView(
        @JsonProperty("owner_user_id") String ownerUserId,
        @JsonProperty("tenant_id") Long tenantId) {
}
