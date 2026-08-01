package cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.dal.ExternalEvidenceSnapshotMapper;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.dal.WorkflowEvidenceDigestRow;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.dal.WorkflowEvidenceOperationMapper;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.dal.WorkflowEvidenceQueryMapper;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.dal.WorkflowEvidenceTimelineRow;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.dal.WorkflowFeedbackMapper;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.dal.WorkflowObservationMapper;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.dal.WorkflowProblemMapper;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.request.WorkflowEvidenceDigestQueryRequest;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.request.WorkflowEvidenceTimelineQueryRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowEvidenceQueryServiceTest {

    private final WorkflowEvidenceQueryMapper queryMapper = mock(WorkflowEvidenceQueryMapper.class);
    private final WorkflowEvidenceService service = new WorkflowEvidenceService(
            mock(WorkflowEvidenceOperationMapper.class),
            mock(WorkflowObservationMapper.class),
            mock(WorkflowProblemMapper.class),
            mock(WorkflowFeedbackMapper.class),
            mock(ExternalEvidenceSnapshotMapper.class),
            queryMapper,
            new ObjectMapper());
    private MockedStatic<SecurityFrameworkUtils> security;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(162L);
        security = mockStatic(SecurityFrameworkUtils.class);
        security.when(SecurityFrameworkUtils::getLoginUser).thenReturn(loginUser());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        security.close();
    }

    @Test
    void queryCapsPaginationAndParsesRows() {
        WorkflowEvidenceTimelineQueryRequest request = new WorkflowEvidenceTimelineQueryRequest();
        request.setWorkflowId("wf.fulfillment");
        request.setWindowStart(LocalDateTime.of(2026, 8, 1, 0, 0));
        request.setWindowEnd(LocalDateTime.of(2026, 8, 2, 0, 0));
        request.setPageNo(1);
        request.setPageSize(180);

        WorkflowEvidenceTimelineRow row = new WorkflowEvidenceTimelineRow();
        row.setRecordType("OBSERVATION");
        row.setRecordId("wfo-1");
        row.setWorkflowId("wf.fulfillment");
        row.setLineageId("lineage-1");
        row.setWorkflowVersion("v7");
        row.setProposalId("proposal-01");
        row.setSourceType("SYSTEM_RUN");
        row.setHeadline("Reconcile run");
        row.setMetricsJson("{\"success\":1}");
        row.setSummarySourceRefsJson("[\"cloudmold://runs/1\"]");
        row.setRecordedAt(LocalDateTime.of(2026, 8, 1, 10, 0));

        when(queryMapper.countTimeline(162L, "wf.fulfillment", request.getWindowStart(), request.getWindowEnd()))
                .thenReturn(1L);
        when(queryMapper.selectTimeline(162L, "wf.fulfillment", request.getWindowStart(), request.getWindowEnd(),
                0L, 100)).thenReturn(List.of(row));

        PageResult<?> result = service.query(request);

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getList()).hasSize(1);
        verify(queryMapper).selectTimeline(162L, "wf.fulfillment", request.getWindowStart(), request.getWindowEnd(),
                0L, 100);
    }

    @Test
    void buildsDailyAndWeeklyDigests() {
        WorkflowEvidenceDigestQueryRequest request = new WorkflowEvidenceDigestQueryRequest();
        request.setWorkflowId("wf.fulfillment");
        request.setWindowStart(LocalDateTime.of(2026, 7, 28, 0, 0));
        request.setWindowEnd(LocalDateTime.of(2026, 8, 3, 0, 0));
        WorkflowEvidenceDigestRow row = new WorkflowEvidenceDigestRow();
        row.setBucketStart(LocalDate.of(2026, 7, 28));
        row.setBucketEnd(LocalDate.of(2026, 8, 4));
        row.setTotalCount(4L);
        when(queryMapper.selectDailyDigest(162L, "wf.fulfillment", request.getWindowStart(), request.getWindowEnd()))
                .thenReturn(List.of(row));
        when(queryMapper.selectWeeklyDigest(162L, "wf.fulfillment", request.getWindowStart(), request.getWindowEnd()))
                .thenReturn(List.of(row));

        var daily = service.dailyDigest(request);
        var weekly = service.weeklyDigest(request);

        assertThat(daily.buckets()).hasSize(1);
        assertThat(weekly.buckets()).hasSize(1);
        verify(queryMapper).selectDailyDigest(162L, "wf.fulfillment", request.getWindowStart(), request.getWindowEnd());
        verify(queryMapper).selectWeeklyDigest(162L, "wf.fulfillment", request.getWindowStart(), request.getWindowEnd());
    }

    private LoginUser loginUser() {
        LoginUser user = new LoginUser();
        user.setId(101L);
        user.setUserType(2);
        user.setTenantId(162L);
        user.setVisitTenantId(162L);
        return user;
    }
}
