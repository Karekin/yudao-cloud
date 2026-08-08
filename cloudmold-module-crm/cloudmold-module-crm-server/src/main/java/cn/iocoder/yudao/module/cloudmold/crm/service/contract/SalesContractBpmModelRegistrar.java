package cn.iocoder.yudao.module.cloudmold.crm.service.contract;

import cn.iocoder.yudao.module.bpm.api.definition.BpmSystemModelApi;
import cn.iocoder.yudao.module.bpm.api.definition.dto.BpmSystemModelRegisterReqDTO;
import cn.iocoder.yudao.module.cloudmold.identity.api.IdentityQueryApi;
import cn.iocoder.yudao.module.cloudmold.identity.api.SourceIdentityReference;
import cn.iocoder.yudao.module.cloudmold.identity.api.SourceIdentityView;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SalesContractBpmModelRegistrar {

    static final String PROCESS_DEFINITION_KEY = "cloudmold_sales_contract_approval";
    static final String REVIEW_TASK_KEY = "sales_contract_review";
    private static final String MODEL_RESOURCE =
            "approval-workflow/cloudmold-sales-contract-approval-v1.bpmn20.xml";

    private final BpmSystemModelApi bpmSystemModelApi;
    private final SalesContractApprovalProperties properties;
    private final IdentityQueryApi identityQueryApi;

    public List<Long> registerAndResolveReviewers(Long requesterUserId) {
        require(requesterUserId != null && requesterUserId > 0, "sales contract requester is required");
        List<Long> reviewers = properties.getReviewerUserIds().stream()
                .filter(userId -> userId != null && userId > 0 && !userId.equals(requesterUserId))
                .distinct()
                .toList();
        require(!reviewers.isEmpty(),
                "sales contract approval requires a reviewer distinct from the requester");
        reviewers.forEach(this::requireCanonicalReviewerIdentity);
        bpmSystemModelApi.register(new BpmSystemModelRegisterReqDTO()
                .setKey(PROCESS_DEFINITION_KEY)
                .setName("CloudMold 销售合同审批")
                .setDescription("赢单后由独立合同审查岗位核对客户、销售主体、商品、金额和有效期。")
                .setCategoryCode("cloudmold-customer-sales")
                .setCategoryName("客户与销售")
                .setBpmnXml(readModel())
                .setFormCustomCreatePath("/cloudmold/crm/sales-contract")
                .setFormCustomViewPath("/cloudmold/crm/sales-contract/index.vue")
                .setManagerUserId(requesterUserId)).checkError();
        return reviewers;
    }

    private void requireCanonicalReviewerIdentity(Long reviewerUserId) {
        SourceIdentityView identity = identityQueryApi.resolveActiveSource(
                new SourceIdentityReference("SYSTEM", "SYSTEM_ADMIN_USER", reviewerUserId.toString()));
        require(identity != null && identity.getPrincipalId() != null && !identity.getPrincipalId().isBlank()
                        && "ACTIVE".equals(identity.getPrincipalStatus())
                        && "ACTIVE".equals(identity.getSourceStatus()),
                "sales contract reviewer requires an active canonical identity");
    }

    private String readModel() {
        try {
            return new ClassPathResource(MODEL_RESOURCE).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("sales contract BPMN resource is unavailable", exception);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
