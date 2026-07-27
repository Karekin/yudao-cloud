package cn.iocoder.yudao.module.bpm.service.message;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.web.config.WebProperties;
import cn.iocoder.yudao.module.bpm.service.message.dto.BpmMessageSendWhenProcessInstanceApproveReqDTO;
import cn.iocoder.yudao.module.bpm.service.message.dto.BpmMessageSendWhenTaskCreatedReqDTO;
import cn.iocoder.yudao.module.system.api.notify.NotifyMessageSendApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySendSingleToUserReqDTO;
import cn.iocoder.yudao.module.system.api.sms.SmsSendApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BpmMessageServiceImplTest {

    private SmsSendApi smsSendApi;
    private NotifyMessageSendApi notifyMessageSendApi;
    private BpmMessageServiceImpl service;

    @BeforeEach
    void setUp() {
        smsSendApi = mock(SmsSendApi.class);
        notifyMessageSendApi = mock(NotifyMessageSendApi.class);
        service = new BpmMessageServiceImpl();
        ReflectionTestUtils.setField(service, "smsSendApi", smsSendApi);
        ReflectionTestUtils.setField(service, "notifyMessageSendApi", notifyMessageSendApi);

        WebProperties properties = new WebProperties();
        WebProperties.Ui adminUi = new WebProperties.Ui();
        adminUi.setUrl("http://127.0.0.1:5666/#");
        properties.setAdminUi(adminUi);
        ReflectionTestUtils.setField(service, "webProperties", properties);
    }

    @Test
    void shouldNotRollbackApprovalWhenRequesterHasNoMobile() {
        when(smsSendApi.sendSingleSmsToAdmin(any()))
                .thenReturn(CommonResult.error(1_002_013_000, "手机号不存在"));
        BpmMessageSendWhenProcessInstanceApproveReqDTO request =
                new BpmMessageSendWhenProcessInstanceApproveReqDTO()
                        .setProcessInstanceId("process-1")
                        .setProcessInstanceName("Agent 高风险动作审批")
                        .setStartUserId(226L);

        assertDoesNotThrow(() -> service.sendMessageWhenProcessInstanceApprove(request));
    }

    @Test
    void shouldKeepTaskAndUseLocalAddressWhenNotificationChannelsFail() {
        when(notifyMessageSendApi.sendSingleMessageToAdmin(any()))
                .thenReturn(CommonResult.error(500, "站内信不可用"));
        when(smsSendApi.sendSingleSmsToAdmin(any()))
                .thenReturn(CommonResult.error(1_002_013_000, "手机号不存在"));
        BpmMessageSendWhenTaskCreatedReqDTO request = new BpmMessageSendWhenTaskCreatedReqDTO()
                .setProcessInstanceId("process-2")
                .setProcessInstanceName("Agent 高风险动作审批")
                .setStartUserId(226L)
                .setStartUserNickname("得物运营中心")
                .setTaskId("task-2")
                .setTaskName("审批人审核")
                .setAssigneeUserId(227L);

        assertDoesNotThrow(() -> service.sendMessageWhenTaskAssigned(request));

        ArgumentCaptor<NotifySendSingleToUserReqDTO> captor =
                ArgumentCaptor.forClass(NotifySendSingleToUserReqDTO.class);
        verify(notifyMessageSendApi).sendSingleMessageToAdmin(captor.capture());
        Map<String, Object> params = captor.getValue().getTemplateParams();
        assertEquals("http://127.0.0.1:5666/#/bpm/process-instance/detail?id=process-2",
                params.get("detailUrl"));
    }

}
