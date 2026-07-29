package cn.iocoder.yudao.module.cloudmold.operationsintelligence.service.workflow;

import cn.iocoder.yudao.module.cloudmold.operationsintelligence.api.workflow.BusinessControlWorkflowResult;
import cn.iocoder.yudao.module.cloudmold.operationsintelligence.api.workflow.DailyBusinessControlQueryPort;
import cn.iocoder.yudao.module.cloudmold.operationsintelligence.api.workflow.DailyBusinessControlWorkflowQueryApi;
import cn.iocoder.yudao.module.cloudmold.operationsintelligence.api.workflow.WeeklyBusinessReviewQueryPort;
import cn.iocoder.yudao.module.cloudmold.operationsintelligence.api.workflow.WeeklyBusinessReviewWorkflowQueryApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OperationsBusinessControlWorkflowQueryApiAdapter
        implements DailyBusinessControlWorkflowQueryApi, WeeklyBusinessReviewWorkflowQueryApi {

    private final DailyBusinessControlQueryPort dailyQuery;
    private final WeeklyBusinessReviewQueryPort weeklyQuery;

    @Override
    public BusinessControlWorkflowResult inspectDaily() {
        return dailyQuery.inspectDaily();
    }

    @Override
    public BusinessControlWorkflowResult inspectWeekly() {
        return weeklyQuery.inspectWeekly();
    }
}
