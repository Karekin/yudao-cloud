package cn.iocoder.yudao.module.cloudmold.gamification.api;

import lombok.*;
import lombok.experimental.Accessors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class GamificationView {
    private Long operationId;
    private Boolean duplicate;
    private String gameId;
    private Long gameVersion;
    private String gameStatus;
    private String virtualCurrencyCode;
    private String accountId;
    private Long accountVersion;
    private Long balanceMicrounits;
    private String sessionId;
    private Long sessionVersion;
    private String sessionStatus;
    private String roundId;
    private Long roundVersion;
    private String roundStatus;
    private String rewardDefinitionId;
    private Long rewardVersion;
    private String rewardGrantId;
    private String drawPoolId;
    private Long drawPoolVersion;
    private String drawRequestId;
    private String drawResultId;
    private String assistRecordId;
    private Integer assistsUsed;
    private Integer assistLimit;
    private String taskDefinitionId;
    private Long taskVersion;
    private String taskProgressId;
    private Long completedUnits;
    private Long targetUnits;
    private String taskStatus;
    private String fragmentCode;
    private Long fragmentQuantity;
    private String giftTransferId;
    private String ledgerTransactionId;
}
