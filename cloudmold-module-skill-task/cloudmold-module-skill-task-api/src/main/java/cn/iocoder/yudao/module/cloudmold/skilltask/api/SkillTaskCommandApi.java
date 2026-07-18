package cn.iocoder.yudao.module.cloudmold.skilltask.api;

public interface SkillTaskCommandApi {

    SkillTaskView submit(SkillTaskSubmitCommand command);

    SkillTaskView retry(SkillTaskRetryCommand command);
}
