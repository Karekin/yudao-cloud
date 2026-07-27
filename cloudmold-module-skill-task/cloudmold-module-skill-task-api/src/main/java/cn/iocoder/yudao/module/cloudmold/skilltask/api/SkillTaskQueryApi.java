package cn.iocoder.yudao.module.cloudmold.skilltask.api;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskDetailView;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskRunPageRequest;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskRunView;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskWorkflowView;

import java.util.List;

public interface SkillTaskQueryApi {

    SkillTaskView get(String taskId);

    SkillTaskView getByRequestKey(String skillId, String clientRequestKey);

    List<SkillTaskStepView> listSteps(String taskId);

    SkillTaskTerminalProofView getTerminalProof(String taskId);

    List<ManagedSkillTaskWorkflowView> listManagedWorkflows();

    PageResult<ManagedSkillTaskRunView> pageManagedRuns(ManagedSkillTaskRunPageRequest request);

    ManagedSkillTaskDetailView getManagedRun(String taskId);
}
