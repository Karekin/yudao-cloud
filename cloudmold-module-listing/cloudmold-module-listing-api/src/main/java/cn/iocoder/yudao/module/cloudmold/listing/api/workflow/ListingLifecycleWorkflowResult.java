package cn.iocoder.yudao.module.cloudmold.listing.api.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListingLifecycleWorkflowResult {

    private String entityType;
    private String businessId;
    private String status;
    private String lifecycleState;
    private Boolean terminal;
    private String evidenceSource;
    private String summary;
}
