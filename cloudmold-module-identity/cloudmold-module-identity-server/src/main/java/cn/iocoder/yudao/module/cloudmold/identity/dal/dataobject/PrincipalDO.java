package cn.iocoder.yudao.module.cloudmold.identity.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_identity_principal")
public class PrincipalDO {
    @TableId(type = IdType.INPUT)
    private String principalId;
    private Long tenantId;
    private String principalType;
    private String status;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
