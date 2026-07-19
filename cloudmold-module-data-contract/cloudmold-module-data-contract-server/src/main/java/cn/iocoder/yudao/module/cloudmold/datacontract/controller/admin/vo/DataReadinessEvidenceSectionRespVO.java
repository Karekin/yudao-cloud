package cn.iocoder.yudao.module.cloudmold.datacontract.controller.admin.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DataReadinessEvidenceSectionRespVO {

    private String status;
    private String connectionStatus;
    private LocalDateTime generatedAt;
    private String boundary;
}
