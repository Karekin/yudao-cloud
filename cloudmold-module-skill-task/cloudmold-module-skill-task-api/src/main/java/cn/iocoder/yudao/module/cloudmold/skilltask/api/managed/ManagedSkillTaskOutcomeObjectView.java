package cn.iocoder.yudao.module.cloudmold.skilltask.api.managed;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManagedSkillTaskOutcomeObjectView implements Serializable {

    private String objectType;
    private String label;
    private String businessId;
    private String businessCode;
    private String status;
}
