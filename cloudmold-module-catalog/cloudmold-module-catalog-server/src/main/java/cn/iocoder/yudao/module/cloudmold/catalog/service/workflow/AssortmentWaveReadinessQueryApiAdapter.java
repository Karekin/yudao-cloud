package cn.iocoder.yudao.module.cloudmold.catalog.service.workflow;

import cn.iocoder.yudao.module.cloudmold.catalog.api.workflow.AssortmentWaveReadinessQueryPort;
import cn.iocoder.yudao.module.cloudmold.catalog.api.workflow.AssortmentWaveReadinessRequest;
import cn.iocoder.yudao.module.cloudmold.catalog.api.workflow.AssortmentWaveReadinessResult;
import cn.iocoder.yudao.module.cloudmold.catalog.api.workflow.AssortmentWaveReadinessWorkflowQueryApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AssortmentWaveReadinessQueryApiAdapter implements AssortmentWaveReadinessWorkflowQueryApi {

    private final AssortmentWaveReadinessQueryPort queryPort;

    @Override
    public AssortmentWaveReadinessResult inspectWave(AssortmentWaveReadinessRequest request) {
        return queryPort.inspectWave(request);
    }
}
