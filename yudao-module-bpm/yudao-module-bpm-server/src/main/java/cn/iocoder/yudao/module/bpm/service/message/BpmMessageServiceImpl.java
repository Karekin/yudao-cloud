package cn.iocoder.yudao.module.bpm.service.message;

import cn.iocoder.yudao.framework.web.config.WebProperties;
import cn.iocoder.yudao.module.bpm.convert.message.BpmMessageConvert;
import cn.iocoder.yudao.module.bpm.enums.message.BpmMessageEnum;
import cn.iocoder.yudao.module.bpm.service.message.dto.BpmMessageSendWhenProcessInstanceApproveReqDTO;
import cn.iocoder.yudao.module.bpm.service.message.dto.BpmMessageSendWhenProcessInstanceRejectReqDTO;
import cn.iocoder.yudao.module.bpm.service.message.dto.BpmMessageSendWhenTaskCreatedReqDTO;
import cn.iocoder.yudao.module.bpm.service.message.dto.BpmMessageSendWhenTaskTimeoutReqDTO;
import cn.iocoder.yudao.module.system.api.notify.NotifyMessageSendApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySendSingleToUserReqDTO;
import cn.iocoder.yudao.module.system.api.sms.SmsSendApi;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.HashMap;
import java.util.Map;

/**
 * BPM 消息 Service 实现类
 *
 * @author 芋道源码
 */
@Service
@Validated
@Slf4j
public class BpmMessageServiceImpl implements BpmMessageService {

    @Resource
    private SmsSendApi smsSendApi;
    @Resource
    private NotifyMessageSendApi notifyMessageSendApi;

    @Resource
    private WebProperties webProperties;

    @Override
    public void sendMessageWhenProcessInstanceApprove(BpmMessageSendWhenProcessInstanceApproveReqDTO reqDTO) {
        Map<String, Object> templateParams = new HashMap<>();
        templateParams.put("processInstanceName", reqDTO.getProcessInstanceName());
        templateParams.put("detailUrl", getProcessInstanceDetailUrl(reqDTO.getProcessInstanceId()));
        smsSendApi.sendSingleSmsToAdmin(BpmMessageConvert.INSTANCE.convert(reqDTO.getStartUserId(),
                BpmMessageEnum.PROCESS_INSTANCE_APPROVE.getSmsTemplateCode(), templateParams)).checkError();
    }

    @Override
    public void sendMessageWhenProcessInstanceReject(BpmMessageSendWhenProcessInstanceRejectReqDTO reqDTO) {
        Map<String, Object> templateParams = new HashMap<>();
        templateParams.put("processInstanceName", reqDTO.getProcessInstanceName());
        templateParams.put("reason", reqDTO.getReason());
        templateParams.put("detailUrl", getProcessInstanceDetailUrl(reqDTO.getProcessInstanceId()));
        smsSendApi.sendSingleSmsToAdmin(BpmMessageConvert.INSTANCE.convert(reqDTO.getStartUserId(),
                BpmMessageEnum.PROCESS_INSTANCE_REJECT.getSmsTemplateCode(), templateParams)).checkError();
    }

    @Override
    public void sendMessageWhenTaskAssigned(BpmMessageSendWhenTaskCreatedReqDTO reqDTO) {
        Map<String, Object> templateParams = new HashMap<>();
        templateParams.put("processInstanceName", reqDTO.getProcessInstanceName());
        templateParams.put("taskName", reqDTO.getTaskName());
        templateParams.put("startUserNickname", reqDTO.getStartUserNickname());
        templateParams.put("detailUrl", getProcessInstanceDetailUrl(reqDTO.getProcessInstanceId()));
        // 待办本身是审批权威；站内信负责让审批人及时发现待办。通知失败不能阻断任务创建。
        try {
            notifyMessageSendApi.sendSingleMessageToAdmin(new NotifySendSingleToUserReqDTO()
                    .setUserId(reqDTO.getAssigneeUserId())
                    .setTemplateCode(BpmMessageEnum.TASK_ASSIGNED.getSmsTemplateCode())
                    .setTemplateParams(templateParams)).checkError();
        } catch (RuntimeException exception) {
            log.warn("[sendMessageWhenTaskAssigned][站内信发送失败，审批待办仍可办理 assigneeUserId={} processInstanceId={}]",
                    reqDTO.getAssigneeUserId(), reqDTO.getProcessInstanceId(), exception);
        }
        smsSendApi.sendSingleSmsToAdmin(BpmMessageConvert.INSTANCE.convert(reqDTO.getAssigneeUserId(),
                BpmMessageEnum.TASK_ASSIGNED.getSmsTemplateCode(), templateParams)).checkError();
    }

    @Override
    public void sendMessageWhenTaskTimeout(BpmMessageSendWhenTaskTimeoutReqDTO reqDTO) {
        Map<String, Object> templateParams = new HashMap<>();
        templateParams.put("processInstanceName", reqDTO.getProcessInstanceName());
        templateParams.put("taskName", reqDTO.getTaskName());
        templateParams.put("detailUrl", getProcessInstanceDetailUrl(reqDTO.getProcessInstanceId()));
        smsSendApi.sendSingleSmsToAdmin(BpmMessageConvert.INSTANCE.convert(reqDTO.getAssigneeUserId(),
                BpmMessageEnum.TASK_TIMEOUT.getSmsTemplateCode(), templateParams)).checkError();
    }

    private String getProcessInstanceDetailUrl(String taskId) {
        return webProperties.getAdminUi().getUrl() + "/bpm/process-instance/detail?id=" + taskId;
    }

}
