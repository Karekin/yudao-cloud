package cn.iocoder.yudao.module.cloudmold.quality.service.workflow;

import cn.iocoder.yudao.module.cloudmold.quality.api.workflow.QualityRecallWorkflowQueryApi;
import cn.iocoder.yudao.module.cloudmold.quality.api.workflow.QualityRecallWorkflowQueryPort;
import cn.iocoder.yudao.module.cloudmold.quality.api.workflow.QualityRecallWorkflowResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QualityRecallWorkflowQueryApiAdapter implements QualityRecallWorkflowQueryApi {

    private final QualityRecallWorkflowQueryPort delegate;

    @Override
    public QualityRecallWorkflowResult inspect(String recallActionId) {
        return delegate.inspect(recallActionId);
    }
}
