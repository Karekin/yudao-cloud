package cn.iocoder.yudao.module.cloudmold.crm.service.contract;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

@Data
@Component
@ConfigurationProperties(prefix = "cloudmold.crm.sales-contract-approval")
public class SalesContractApprovalProperties {

    /** Dedicated System users allowed to review a sales contract. */
    private Set<Long> reviewerUserIds = new LinkedHashSet<>();
}
