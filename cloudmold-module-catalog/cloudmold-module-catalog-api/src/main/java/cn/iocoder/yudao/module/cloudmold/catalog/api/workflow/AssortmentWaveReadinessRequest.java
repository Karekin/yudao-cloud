package cn.iocoder.yudao.module.cloudmold.catalog.api.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssortmentWaveReadinessRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Integer planningYear;
    private String seasonCode;
    private String waveCode;
}
