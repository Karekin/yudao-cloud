package cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_asn")
public class AsnDO {
    private String asnId;
    private Long tenantId;
    private String asnNo;
    private String sourceBusinessType;
    private String sourceBusinessRef;
    private String supplierRef;
    private String warehouseId;
    private String status;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
