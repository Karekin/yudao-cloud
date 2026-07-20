package cn.iocoder.yudao.module.cloudmold.agentcontrol.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = false)
public class AgentActionAssemblyCommand implements Serializable {

    private static final long serialVersionUID = 1L;

    private String workOrderId;
    private Long workOrderExpectedVersion;

}
