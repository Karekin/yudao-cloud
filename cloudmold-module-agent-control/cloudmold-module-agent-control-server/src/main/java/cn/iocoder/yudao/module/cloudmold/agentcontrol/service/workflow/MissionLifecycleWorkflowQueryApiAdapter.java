package cn.iocoder.yudao.module.cloudmold.agentcontrol.service.workflow;

import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.workflow.MissionLifecycleWorkflowQueryApi;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.workflow.MissionLifecycleWorkflowView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MissionLifecycleWorkflowQueryApiAdapter implements MissionLifecycleWorkflowQueryApi {

    private final MissionLifecycleWorkflowQueryService delegate;

    @Override
    public MissionLifecycleWorkflowView inspect(String missionId) {
        return delegate.inspect(missionId);
    }
}
