package cn.iocoder.yudao.module.cloudmold.appcommerce.dal.dataobject;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AppCartDO {
    private String cartId;
    private Long tenantId;
    private String buyerPrincipalId;
    private Long version;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
