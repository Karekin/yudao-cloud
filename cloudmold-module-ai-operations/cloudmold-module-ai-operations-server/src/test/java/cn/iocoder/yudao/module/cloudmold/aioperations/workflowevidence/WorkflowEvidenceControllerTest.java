package cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence;

import cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.request.WorkflowEvidenceDigestQueryRequest;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.request.WorkflowEvidenceFeedbackRequest;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.request.WorkflowEvidenceIngestRequest;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.request.WorkflowEvidenceProblemRequest;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.request.WorkflowEvidenceTimelineQueryRequest;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.view.WorkflowEvidenceDigestView;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.view.WorkflowEvidenceWriteView;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowEvidenceControllerTest {

    private final WorkflowEvidenceService service = mock(WorkflowEvidenceService.class);
    private final WorkflowEvidenceController controller = new WorkflowEvidenceController(service);

    @Test
    void delegatesCommandsAndQueries() {
        WorkflowEvidenceIngestRequest ingestRequest = new WorkflowEvidenceIngestRequest();
        WorkflowEvidenceProblemRequest problemRequest = new WorkflowEvidenceProblemRequest();
        WorkflowEvidenceFeedbackRequest feedbackRequest = new WorkflowEvidenceFeedbackRequest();
        WorkflowEvidenceTimelineQueryRequest timelineRequest = new WorkflowEvidenceTimelineQueryRequest();
        WorkflowEvidenceDigestQueryRequest digestRequest = new WorkflowEvidenceDigestQueryRequest();
        WorkflowEvidenceWriteView writeView = WorkflowEvidenceWriteView.builder()
                .aggregateType("WORKFLOW_OBSERVATION").aggregateId("wfo-1").build();
        WorkflowEvidenceDigestView digestView = WorkflowEvidenceDigestView.builder().workflowId("wf-1").build();

        when(service.ingest(ingestRequest)).thenReturn(writeView);
        when(service.recordProblem(problemRequest)).thenReturn(writeView);
        when(service.recordFeedback(feedbackRequest)).thenReturn(writeView);
        when(service.dailyDigest(digestRequest)).thenReturn(digestView);
        when(service.weeklyDigest(digestRequest)).thenReturn(digestView);

        assertThat(controller.ingest(ingestRequest).getData()).isSameAs(writeView);
        assertThat(controller.recordProblem(problemRequest).getData()).isSameAs(writeView);
        assertThat(controller.recordFeedback(feedbackRequest).getData()).isSameAs(writeView);
        assertThat(controller.dailyDigest(digestRequest).getData()).isSameAs(digestView);
        assertThat(controller.weeklyDigest(digestRequest).getData()).isSameAs(digestView);
        controller.query(timelineRequest);

        verify(service).ingest(ingestRequest);
        verify(service).recordProblem(problemRequest);
        verify(service).recordFeedback(feedbackRequest);
        verify(service).query(timelineRequest);
        verify(service).dailyDigest(digestRequest);
        verify(service).weeklyDigest(digestRequest);
    }
}
