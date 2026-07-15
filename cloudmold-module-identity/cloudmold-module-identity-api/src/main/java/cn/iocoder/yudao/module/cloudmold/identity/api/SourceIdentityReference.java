package cn.iocoder.yudao.module.cloudmold.identity.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SourceIdentityReference {
    private String sourceSystem;
    private String sourceType;
    private String sourceId;
}
