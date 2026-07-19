package cn.iocoder.yudao.module.cloudmold.datacontract.controller.admin.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DataReadinessOverviewRespVO {

    private LocalDateTime generatedAt;
    private DataReadinessOutboxOverviewRespVO outbox;
    private DataReadinessEvidenceSectionRespVO cdc;
    private DataReadinessEvidenceSectionRespVO dqc;
    private DataReadinessEvidenceSectionRespVO ads;
    private DataReadinessEvidenceSectionRespVO sourceGraduation;
}
