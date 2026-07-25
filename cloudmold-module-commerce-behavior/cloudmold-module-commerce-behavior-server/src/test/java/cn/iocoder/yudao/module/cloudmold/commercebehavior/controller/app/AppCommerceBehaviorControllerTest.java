package cn.iocoder.yudao.module.cloudmold.commercebehavior.controller.app;

import cn.iocoder.yudao.module.cloudmold.commercebehavior.api.CommerceBehaviorCommandApi;
import cn.iocoder.yudao.module.cloudmold.commercebehavior.api.CommerceBehaviorCommandApi.CommerceBehaviorCommandResult;
import cn.iocoder.yudao.module.cloudmold.commercebehavior.api.CommerceBehaviorCommandApi.RecordCommerceBehaviorCommand;
import cn.iocoder.yudao.module.cloudmold.commercebehavior.api.CommerceBehaviorCommandApi.StartCommerceSessionCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppCommerceBehaviorControllerTest {

    private final CommerceBehaviorCommandApi commandApi = mock(CommerceBehaviorCommandApi.class);
    private final AppCommerceBehaviorController controller = new AppCommerceBehaviorController();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "commandApi", commandApi);
        when(commandApi.startSession(any())).thenReturn(result("ACTIVE", 1L));
        when(commandApi.recordBehavior(any())).thenReturn(result("ACTIVE", 2L));
    }

    @Test
    void startSessionBuildsServerGovernedCommand() {
        String sessionId = UUID.randomUUID().toString();
        AppCommerceBehaviorController.AppStartCommerceSessionReqVO request =
                new AppCommerceBehaviorController.AppStartCommerceSessionReqVO();
        request.setSessionId(sessionId);
        request.setChannelCode("H5");
        request.setEntrypointCode("SEARCH");

        assertThat(controller.startSession(request).getData().getStatus()).isEqualTo("ACTIVE");

        ArgumentCaptor<StartCommerceSessionCommand> captor =
                ArgumentCaptor.forClass(StartCommerceSessionCommand.class);
        verify(commandApi).startSession(captor.capture());
        StartCommerceSessionCommand command = captor.getValue();
        assertThat(command.getRunId()).isEqualTo(sessionId);
        assertThat(command.getSourceSystem()).isEqualTo("YSHOPPING_UNIAPP");
        assertThat(command.getSourceType()).isEqualTo("MOBILE_SESSION");
        assertThat(command.getIdempotencyKey()).isEqualTo("mobile-session:" + sessionId);
        assertThat(command.getOccurredAt()).isNotNull();
    }

    @Test
    void recordSearchDoesNotAcceptClientOwnedGovernanceFields() {
        String sessionId = UUID.randomUUID().toString();
        String behaviorId = UUID.randomUUID().toString();
        AppCommerceBehaviorController.AppRecordCommerceBehaviorReqVO request =
                new AppCommerceBehaviorController.AppRecordCommerceBehaviorReqVO();
        request.setSessionId(sessionId);
        request.setBehaviorId(behaviorId);
        request.setBehaviorType("SEARCH_REQUESTED");
        request.setSearchToken("search-token:" + UUID.randomUUID());

        assertThat(controller.recordBehavior(request).getData().getAggregateVersion()).isEqualTo(2L);

        ArgumentCaptor<RecordCommerceBehaviorCommand> captor =
                ArgumentCaptor.forClass(RecordCommerceBehaviorCommand.class);
        verify(commandApi).recordBehavior(captor.capture());
        RecordCommerceBehaviorCommand command = captor.getValue();
        assertThat(command.getRunId()).isEqualTo(sessionId);
        assertThat(command.getCorrelationId()).isEqualTo(sessionId);
        assertThat(command.getSourceSystem()).isEqualTo("YSHOPPING_UNIAPP");
        assertThat(command.getSourceType()).isEqualTo("MOBILE_EVENT");
        assertThat(command.getIdempotencyKey()).isEqualTo("mobile-event:" + behaviorId);
        assertThat(command.getSearchToken()).startsWith("search-token:");
        assertThat(command.getPrincipalId()).isNull();
    }

    private static CommerceBehaviorCommandResult result(String status, long version) {
        return CommerceBehaviorCommandResult.builder()
                .aggregateId(UUID.randomUUID().toString())
                .aggregateType("commerce_session")
                .status(status)
                .aggregateVersion(version)
                .duplicate(false)
                .build();
    }
}
