package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.client.schedules.ScheduleClient;
import io.temporal.client.schedules.ScheduleClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({AiOperationsTemporalProperties.class, AiOperationsTemporalSeedProperties.class})
@ConditionalOnProperty(prefix = "cloudmold.ai-operations.temporal", name = "enabled", havingValue = "true")
public class AiOperationsTemporalConfiguration {

    @Bean(destroyMethod = "shutdown")
    public WorkflowServiceStubs aiOperationsTemporalService(AiOperationsTemporalProperties properties) {
        return WorkflowServiceStubs.newServiceStubs(WorkflowServiceStubsOptions.newBuilder()
                .setTarget(properties.getTarget()).build());
    }

    @Bean
    public WorkflowClient aiOperationsTemporalWorkflowClient(WorkflowServiceStubs service,
                                                              AiOperationsTemporalProperties properties) {
        return WorkflowClient.newInstance(service, WorkflowClientOptions.newBuilder()
                .setNamespace(properties.getNamespace()).build());
    }

    @Bean
    public ScheduleClient aiOperationsTemporalScheduleClient(WorkflowServiceStubs service,
                                                              AiOperationsTemporalProperties properties) {
        return ScheduleClient.newInstance(service, ScheduleClientOptions.newBuilder()
                .setNamespace(properties.getNamespace()).build());
    }

    @Bean(initMethod = "start", destroyMethod = "shutdown")
    public WorkerFactory aiOperationsTemporalWorkerFactory(
            WorkflowClient client, TemporalManagedRunActivitiesImpl activities,
            AiOperationsTemporalProperties properties) {
        WorkerFactory factory = WorkerFactory.newInstance(client);
        Worker worker = factory.newWorker(properties.getTaskQueue());
        worker.registerWorkflowImplementationTypes(TemporalManagedRunWorkflowImpl.class);
        worker.registerWorkflowImplementationTypes(ApprovalGateChildWorkflowImpl.class);
        worker.registerWorkflowImplementationTypes(SkillTaskChildWorkflowImpl.class);
        worker.registerWorkflowImplementationTypes(BusinessEventWaitChildWorkflowImpl.class);
        worker.registerActivitiesImplementations(activities);
        return factory;
    }
}
