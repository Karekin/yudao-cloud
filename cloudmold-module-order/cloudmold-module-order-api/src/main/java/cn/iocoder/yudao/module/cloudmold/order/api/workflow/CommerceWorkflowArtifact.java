package cn.iocoder.yudao.module.cloudmold.order.api.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommerceWorkflowArtifact {
    private String type;
    private String id;
    private String status;
    private Long version;
    private String label;
}
