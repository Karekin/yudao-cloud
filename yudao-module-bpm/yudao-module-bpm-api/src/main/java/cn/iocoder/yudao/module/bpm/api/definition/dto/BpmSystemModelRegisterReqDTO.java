package cn.iocoder.yudao.module.bpm.api.definition.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BpmSystemModelRegisterReqDTO {

    @NotBlank
    private String key;
    @NotBlank
    private String name;
    @NotBlank
    private String description;
    @NotBlank
    private String categoryCode;
    @NotBlank
    private String categoryName;
    @NotBlank
    private String bpmnXml;
    @NotBlank
    private String formCustomCreatePath;
    @NotBlank
    private String formCustomViewPath;
    @NotNull
    private Long managerUserId;

}
