package cn.iocoder.yudao.module.cloudmold.appcommerce.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_app_recommendation_decision")
public class AppRecommendationDecisionDO {
    @TableId(type = IdType.INPUT)
    private String decisionId;
    private Long tenantId;
    private String sessionId;
    private String sceneCode;
    private String requestHash;
    private String decisionToken;
    private String resultSetToken;
    private String policyVersion;
    private String status;
    private Integer ttlSeconds;
    private Integer itemCount;
    private LocalDateTime generatedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
