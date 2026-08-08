package cn.iocoder.yudao.module.cloudmold.crm.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrmFollowUpView implements Serializable {
    private String followUpId;
    private String subjectType;
    private String subjectId;
    private String subjectCode;
    private String subjectName;
    private String methodCode;
    private String summary;
    private LocalDateTime nextFollowUpAt;
    private String actorPrincipalId;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
}
