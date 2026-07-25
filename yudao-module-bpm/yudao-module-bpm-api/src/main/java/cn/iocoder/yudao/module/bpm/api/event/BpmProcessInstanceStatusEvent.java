package cn.iocoder.yudao.module.bpm.api.event;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.context.ApplicationEvent;

/**
 * 流程实例的状态（结果）发生变化的 Event
 *
 * @author 芋道源码
 */
@SuppressWarnings("ALL")
@Data
public class BpmProcessInstanceStatusEvent extends ApplicationEvent {

    /**
     * 流程实例的编号
     */
    @NotNull(message = "流程实例的编号不能为空")
    private String id;
    /**
     * 流程实例的 key
     */
    @NotNull(message = "流程实例的 key 不能为空")
    private String processDefinitionKey;
    /**
     * 流程实例的结果
     */
    @NotNull(message = "流程实例的状态不能为空")
    private Integer status;
    /**
     * 流程实例结束的原因
     */
    private String reason;

    /**
     * 流程实例对应的业务标识
     * 例如说，请假
     */
    private String businessKey;

    /**
     * 触发流程终态的认证操作人编号。
     *
     * <p>自动完成、管理员取消等无法归因到具体任务操作人的终态可为空；需要
     * 强身份归因的消费方必须对此字段 fail closed。</p>
     */
    private Long terminalOperatorUserId;
    /**
     * 触发流程终态的任务实例编号。
     */
    private String terminalTaskId;
    /**
     * 触发流程终态的任务定义键。
     */
    private String terminalTaskDefinitionKey;

    public BpmProcessInstanceStatusEvent() {
        // new Object() 保证非空
        super(new Object());
    }

    public BpmProcessInstanceStatusEvent(Object source) {
        super(source);
    }

}
