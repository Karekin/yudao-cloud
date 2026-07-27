package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class TemporalManagedBusinessResult implements Serializable {
    private String outcomeCode;
    private String summary;
    private String domainObjectType;
    private String domainObjectId;
    private String evidenceRef;
}
