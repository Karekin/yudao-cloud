package cn.iocoder.yudao.module.cloudmold.quality.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QualityResult implements Serializable {
    private Long operationId;
    private boolean duplicate;
    private String aggregateType;
    private String aggregateId;
    private Long aggregateVersion;
    private String status;
}
