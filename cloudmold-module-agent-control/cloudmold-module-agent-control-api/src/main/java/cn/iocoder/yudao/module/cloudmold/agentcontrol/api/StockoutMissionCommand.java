package cn.iocoder.yudao.module.cloudmold.agentcontrol.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockoutMissionCommand implements Serializable {
    private String missionId;
    private String title;
    private String objectiveJson;
    private String correlationId;
    private Long inventoryAgentUserId;
    private Long buyerAgentUserId;
    private Long customerServiceAgentUserId;
    private Instant deadlineAt;
}
