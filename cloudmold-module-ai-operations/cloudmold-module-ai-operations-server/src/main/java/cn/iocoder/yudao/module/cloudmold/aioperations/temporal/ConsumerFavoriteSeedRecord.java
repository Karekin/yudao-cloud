package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ConsumerFavoriteSeedRecord {

    private String favoriteId;
    private String status;
    private Long version;
}
