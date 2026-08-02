package cn.iocoder.yudao.module.cloudmold.finance.api.p2p;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class JournalDimensionAssignment implements Serializable {
    private String dimensionTypeId;
    private String dimensionValueId;
}
