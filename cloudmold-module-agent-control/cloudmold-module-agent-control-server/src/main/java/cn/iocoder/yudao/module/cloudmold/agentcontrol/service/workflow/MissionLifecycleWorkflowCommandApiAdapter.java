package cn.iocoder.yudao.module.cloudmold.agentcontrol.service.workflow;

import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.workflow.MissionLifecycleWorkflowCommand;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.workflow.MissionLifecycleWorkflowCommandApi;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.workflow.MissionLifecycleWorkflowCommandResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MissionLifecycleWorkflowCommandApiAdapter implements MissionLifecycleWorkflowCommandApi {

    private final MissionLifecycleWorkflowCommandService delegate;

    @Override
    public MissionLifecycleWorkflowCommandResult startStockoutMission(MissionLifecycleWorkflowCommand command) {
        return delegate.startStockoutMission(command);
    }
}
