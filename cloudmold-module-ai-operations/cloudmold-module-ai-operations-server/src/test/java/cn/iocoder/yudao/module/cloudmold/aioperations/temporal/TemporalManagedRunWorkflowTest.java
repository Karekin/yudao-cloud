package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TemporalManagedRunWorkflowTest {

    private static final String TASK_QUEUE = "ai-ops-temporal-test";

    private TestWorkflowEnvironment environment;

    @AfterEach
    void tearDown() {
        if (environment != null) {
            environment.close();
        }
    }

    @Test
    void shouldCompleteR1WithoutApproval() {
        FakeActivities activities = new FakeActivities(FakeActivities.Mode.R1_SUCCESS);
        TemporalManagedRunState result = runSynchronously(activities, request(false, 30L), "r1-success");

        assertThat(result.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(result.getTaskId()).startsWith("task-");
        assertThat(result.getBusinessResult().getOutcomeCode()).isEqualTo("SKILL_TASK_SUCCEEDED");
        assertThat(activities.submitCount.get()).isEqualTo(1);
        assertThat(activities.refreshCount.get()).isEqualTo(1);
    }

    @Test
    void shouldApproveR2AndSubmitOnlyOnceWhenSignalRepeated() {
        FakeActivities activities = new FakeActivities(FakeActivities.Mode.R2_APPROVE_SUCCESS);
        TemporalManagedRunWorkflow workflow = startAsync(activities, request(true, 30L), "r2-approve");

        waitUntilWaitingApproval(workflow);
        workflow.approvalDecision("APPROVE");
        workflow.approvalDecision("APPROVE");

        TemporalManagedRunState result = waitForCompletion("r2-approve");

        assertThat(result.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(result.getApprovalDecision()).isEqualTo("APPROVE");
        assertThat(activities.submitCount.get()).isEqualTo(1);
        assertThat(activities.rejectCount.get()).isZero();
        assertThat(activities.timeoutCount.get()).isZero();
    }

    @Test
    void shouldRejectR2WithoutSubmittingSkillTask() {
        FakeActivities activities = new FakeActivities(FakeActivities.Mode.R2_REJECT);
        TemporalManagedRunWorkflow workflow = startAsync(activities, request(true, 30L), "r2-reject");

        waitUntilWaitingApproval(workflow);
        workflow.approvalDecision("REJECT");

        TemporalManagedRunState result = waitForCompletion("r2-reject");

        assertThat(result.getStatus()).isEqualTo("REJECTED");
        assertThat(activities.submitCount.get()).isZero();
        assertThat(activities.rejectCount.get()).isEqualTo(1);
        assertThat(activities.timeoutCount.get()).isZero();
    }

    @Test
    void shouldTimeoutR2WhenNoApprovalArrives() {
        FakeActivities activities = new FakeActivities(FakeActivities.Mode.R2_TIMEOUT);
        TemporalManagedRunWorkflow workflow = startAsync(activities, request(true, 1L), "r2-timeout");

        waitUntilWaitingApproval(workflow);
        environment.sleep(Duration.ofSeconds(2));
        TemporalManagedRunState result = waitForCompletion("r2-timeout");

        assertThat(result.getStatus()).isEqualTo("TIMED_OUT");
        assertThat(activities.submitCount.get()).isZero();
        assertThat(activities.rejectCount.get()).isZero();
        assertThat(activities.timeoutCount.get()).isEqualTo(1);
    }

    @Test
    void shouldIgnoreLateDuplicateRejectAfterApprovalAlreadyChosen() {
        FakeActivities activities = new FakeActivities(FakeActivities.Mode.R2_APPROVE_SUCCESS);
        TemporalManagedRunWorkflow workflow = startAsync(activities, request(true, 30L), "r2-duplicate");

        waitUntilWaitingApproval(workflow);
        workflow.approvalDecision("APPROVE");
        workflow.approvalDecision("REJECT");

        TemporalManagedRunState result = waitForCompletion("r2-duplicate");

        assertThat(result.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(result.getApprovalDecision()).isEqualTo("APPROVE");
        assertThat(activities.submitCount.get()).isEqualTo(1);
        assertThat(activities.rejectCount.get()).isZero();
    }

    @Test
    void shouldResumeBusinessEventWaitOnlyOnceForDuplicateSignal() {
        FakeActivities activities = new FakeActivities(FakeActivities.Mode.R1_BUSINESS_WAIT);
        TemporalManagedRunWorkflow workflow = startAsync(
                activities, request(false, 30L), "replenishment-business-event");

        waitUntilStatus(workflow, "WAITING_EVENT");
        workflow.businessEvent("SUPPLIER_CONFIRMED", "9001");
        workflow.businessEvent("SUPPLIER_CONFIRMED", "9001");

        TemporalManagedRunState result = waitForCompletion("replenishment-business-event");

        assertThat(result.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(result.getBusinessResult().getOutcomeCode()).isEqualTo("REPLENISHMENT_COMPLETED");
        assertThat(activities.businessEventRefreshCount.get()).isEqualTo(1);
    }

    private TemporalManagedRunState runSynchronously(FakeActivities activities,
                                                     TemporalManagedRunRequest request,
                                                     String workflowId) {
        environment = buildEnvironment(activities);
        TemporalManagedRunWorkflow workflow = environment.getWorkflowClient().newWorkflowStub(
                TemporalManagedRunWorkflow.class,
                WorkflowOptions.newBuilder().setTaskQueue(TASK_QUEUE).setWorkflowId(workflowId).build());
        return workflow.run(request);
    }

    private TemporalManagedRunWorkflow startAsync(FakeActivities activities,
                                                  TemporalManagedRunRequest request,
                                                  String workflowId) {
        environment = buildEnvironment(activities);
        TemporalManagedRunWorkflow workflow = environment.getWorkflowClient().newWorkflowStub(
                TemporalManagedRunWorkflow.class,
                WorkflowOptions.newBuilder().setTaskQueue(TASK_QUEUE).setWorkflowId(workflowId).build());
        WorkflowClient.start(workflow::run, request);
        return workflow;
    }

    private TemporalManagedRunState waitForCompletion(String workflowId) {
        WorkflowStub stub = environment.getWorkflowClient().newUntypedWorkflowStub(workflowId);
        return stub.getResult(TemporalManagedRunState.class);
    }

    private void waitUntilWaitingApproval(TemporalManagedRunWorkflow workflow) {
        waitUntilStatus(workflow, "WAITING_APPROVAL");
    }

    private void waitUntilStatus(TemporalManagedRunWorkflow workflow, String expectedStatus) {
        for (int i = 0; i < 10; i++) {
            environment.sleep(Duration.ofSeconds(1));
            TemporalManagedRunState state = workflow.state();
            if (state != null && expectedStatus.equals(state.getStatus())) {
                return;
            }
        }
        throw new AssertionError("workflow never entered " + expectedStatus);
    }

    private TestWorkflowEnvironment buildEnvironment(FakeActivities activities) {
        TestWorkflowEnvironment testEnvironment = TestWorkflowEnvironment.newInstance();
        Worker worker = testEnvironment.newWorker(TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(
                TemporalManagedRunWorkflowImpl.class,
                ApprovalGateChildWorkflowImpl.class,
                SkillTaskChildWorkflowImpl.class,
                BusinessEventWaitChildWorkflowImpl.class);
        worker.registerActivitiesImplementations(activities);
        testEnvironment.start();
        return testEnvironment;
    }

    private static TemporalManagedRunRequest request(boolean approvalRequired, Long timeoutSeconds) {
        return TemporalManagedRunRequest.builder()
                .tenantId(162L)
                .scheduleId("cloudmold-t162-auto-shelf-hourly")
                .skillId("skill.cloudmold.commerce.catalog-matrix.v1")
                .skillVersion("1.0.0")
                .inputJson("{\"fixture\":true}")
                .operatorUserId(225L)
                .operatorUserType(2)
                .approvalTimeoutSeconds(timeoutSeconds)
                .roleCode(approvalRequired ? "merchandising" : null)
                .actionCode(approvalRequired ? "catalog.publish" : null)
                .build();
    }

    private static final class FakeActivities implements TemporalManagedRunActivities {

        enum Mode {
            R1_SUCCESS,
            R1_BUSINESS_WAIT,
            R2_APPROVE_SUCCESS,
            R2_REJECT,
            R2_TIMEOUT
        }

        private final Mode mode;
        private final AtomicInteger submitCount = new AtomicInteger();
        private final AtomicInteger refreshCount = new AtomicInteger();
        private final AtomicInteger rejectCount = new AtomicInteger();
        private final AtomicInteger timeoutCount = new AtomicInteger();
        private final AtomicInteger businessEventRefreshCount = new AtomicInteger();

        private FakeActivities(Mode mode) {
            this.mode = mode;
        }

        @Override
        public TemporalManagedRunState prepare(TemporalManagedRunRequest request, String workflowId, String runId) {
            if (mode == Mode.R1_SUCCESS || mode == Mode.R1_BUSINESS_WAIT) {
                return TemporalManagedRunState.builder()
                        .status("READY_FOR_SUBMISSION")
                        .phase("SKILL_TASK")
                        .waitingOn("SKILL_TASK_SUBMISSION")
                        .temporalWorkflowId(workflowId)
                        .temporalRunId(runId)
                        .executionUserId(request.getOperatorUserId())
                        .build();
            }
            return TemporalManagedRunState.builder()
                    .status("WAITING_APPROVAL")
                    .phase("APPROVAL_GATE")
                    .waitingOn("BPM_APPROVAL")
                    .resumableStatus("WAITING_APPROVAL")
                    .temporalWorkflowId(workflowId)
                    .temporalRunId(runId)
                    .executionUserId(request.getOperatorUserId())
                    .workOrderId("wo-" + runId)
                    .approvalId("ap-" + runId)
                    .build();
        }

        @Override
        public TemporalManagedRunState resumeApproved(TemporalManagedRunRequest request,
                                                      TemporalManagedRunState prepared) {
            int sequence = submitCount.incrementAndGet();
            return prepared.toBuilder()
                    .status("RUNNING")
                    .phase("SKILL_TASK")
                    .waitingOn("SKILL_TASK_RESULT")
                    .approvalDecision("APPROVE")
                    .managedRunId("run-" + sequence)
                    .taskId("task-" + prepared.getTemporalRunId())
                    .businessResult(TemporalManagedBusinessResult.builder()
                            .outcomeCode("SKILL_TASK_SUBMITTED")
                            .summary("submitted")
                            .domainObjectType("SKILL_TASK")
                            .domainObjectId("task-" + prepared.getTemporalRunId())
                            .evidenceRef("run-" + sequence)
                            .build())
                    .build();
        }

        @Override
        public TemporalManagedRunState reject(TemporalManagedRunRequest request,
                                              TemporalManagedRunState prepared) {
            rejectCount.incrementAndGet();
            return prepared.toBuilder()
                    .status("REJECTED")
                    .phase("COMPLETED")
                    .waitingOn(null)
                    .approvalDecision("REJECT")
                    .errorCode("BPM_REJECTED")
                    .build();
        }

        @Override
        public TemporalManagedRunState pause(TemporalManagedRunRequest request,
                                             TemporalManagedRunState current,
                                             String reason) {
            return current.toBuilder()
                    .status("PAUSED")
                    .phase("MANUAL_CONTROL")
                    .waitingOn("RESUME_OR_CANCEL")
                    .pauseReason(reason)
                    .resumableStatus(current.getStatus())
                    .build();
        }

        @Override
        public TemporalManagedRunState resume(TemporalManagedRunRequest request,
                                              TemporalManagedRunState current,
                                              String reason) {
            String resumable = current.getResumableStatus() == null ? "WAITING_APPROVAL" : current.getResumableStatus();
            return current.toBuilder()
                    .status(resumable)
                    .phase("WAITING_APPROVAL".equals(resumable) ? "APPROVAL_GATE" : "SKILL_TASK")
                    .waitingOn("WAITING_APPROVAL".equals(resumable) ? "BPM_APPROVAL" : "SKILL_TASK_RESULT")
                    .pauseReason(reason)
                    .build();
        }

        @Override
        public TemporalManagedRunState cancel(TemporalManagedRunRequest request,
                                              TemporalManagedRunState current,
                                              String reason) {
            return current.toBuilder()
                    .status("CANCELLED")
                    .phase("COMPLETED")
                    .waitingOn(null)
                    .cancelReason(reason)
                    .errorCode("MANUAL_CANCELLED")
                    .build();
        }

        @Override
        public TemporalManagedRunState timeout(TemporalManagedRunRequest request,
                                               TemporalManagedRunState current) {
            timeoutCount.incrementAndGet();
            return current.toBuilder()
                    .status("TIMED_OUT")
                    .phase("COMPLETED")
                    .waitingOn(null)
                    .errorCode("APPROVAL_TIMEOUT")
                    .build();
        }

        @Override
        public TemporalManagedRunState refreshSkillTask(TemporalManagedRunRequest request,
                                                        TemporalManagedRunState current) {
            refreshCount.incrementAndGet();
            if (mode == Mode.R1_BUSINESS_WAIT) {
                return current.toBuilder()
                        .status("WAITING_EVENT")
                        .phase("BUSINESS_EVENT_GATE")
                        .waitingOn("SUPPLIER_CONFIRMATION")
                        .businessReferenceId("recommendation-1")
                        .businessResult(TemporalManagedBusinessResult.builder()
                                .outcomeCode("WAITING_BUSINESS_EVENT")
                                .summary("等待供应商确认")
                                .domainObjectType("PROCUREMENT_ORDER")
                                .domainObjectId("po-1")
                                .evidenceRef("projection-1")
                                .build())
                        .build();
            }
            return current.toBuilder()
                    .status("SUCCEEDED")
                    .phase("COMPLETED")
                    .waitingOn(null)
                    .businessResult(TemporalManagedBusinessResult.builder()
                            .outcomeCode("SKILL_TASK_SUCCEEDED")
                            .summary("done")
                            .domainObjectType("SKILL_TASK")
                            .domainObjectId(current.getTaskId())
                            .evidenceRef("proof-" + current.getTaskId())
                            .build())
                    .build();
        }

        @Override
        public TemporalManagedRunState enterBusinessEventWait(TemporalManagedRunRequest request,
                                                              TemporalManagedRunState current,
                                                              String waitReference) {
            return current;
        }

        @Override
        public TemporalManagedRunState refreshBusinessEventWait(TemporalManagedRunRequest request,
                                                                TemporalManagedRunState current,
                                                                String waitReference) {
            businessEventRefreshCount.incrementAndGet();
            return current.toBuilder()
                    .status("SUCCEEDED")
                    .phase("COMPLETED")
                    .waitingOn(null)
                    .waitReference(waitReference)
                    .businessResult(TemporalManagedBusinessResult.builder()
                            .outcomeCode("REPLENISHMENT_COMPLETED")
                            .summary("补货链路已完成入库与上架")
                            .domainObjectType("PROCUREMENT_ORDER")
                            .domainObjectId("po-1")
                            .evidenceRef(waitReference)
                            .build())
                    .build();
        }

        @Override
        public TemporalManagedRunState timeoutBusinessEventWait(TemporalManagedRunRequest request,
                                                                TemporalManagedRunState current) {
            timeoutCount.incrementAndGet();
            return current.toBuilder()
                    .status("TIMED_OUT")
                    .phase("COMPLETED")
                    .waitingOn(null)
                    .errorCode("BUSINESS_EVENT_TIMEOUT")
                    .build();
        }
    }
}
