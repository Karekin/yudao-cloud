package cn.iocoder.yudao.module.cloudmold.merchant.service.workflow;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.merchant.api.workflow.MerchantOnboardingWorkflowQueryPort;
import cn.iocoder.yudao.module.cloudmold.merchant.api.workflow.MerchantOnboardingWorkflowResult;
import cn.iocoder.yudao.module.cloudmold.merchant.api.workflow.MerchantOnboardingWorkflowResult.Artifact;
import cn.iocoder.yudao.module.cloudmold.merchant.api.workflow.MerchantOnboardingWorkflowResult.Status;
import cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.merchant.dal.mysql.MerchantDepositStoreMapper;
import cn.iocoder.yudao.module.cloudmold.merchant.dal.mysql.MerchantStoreMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MerchantOnboardingWorkflowQueryService implements MerchantOnboardingWorkflowQueryPort {

    private final MerchantStoreMapper merchantMapper;
    private final MerchantDepositStoreMapper depositMapper;

    @Override
    public MerchantOnboardingWorkflowResult inspect(String applicationId) {
        requireId(applicationId);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String key = applicationId.trim();
        MerchantOnboardingApplicationDO app = merchantMapper.selectApplication(tenantId, key);
        if (app == null) {
            return result(key, Status.PREPARE, "入驻资料准备", false,
                    "尚未创建商家入驻申请", 0L,
                    List.of("Merchant SoR 中不存在入驻申请"),
                    List.of("创建入驻草稿并补齐主体、店铺和负责人资料"), List.of());
        }
        List<Artifact> artifacts = new ArrayList<>();
        artifacts.add(artifact("ONBOARDING_APPLICATION", app.getApplicationId(), app.getStatus(), app.getVersion(), "商家入驻申请"));
        if ("REJECTED".equals(app.getStatus()) || "WITHDRAWN".equals(app.getStatus())) {
            return result(key, Status.FAILED, "入驻已终止", true,
                    "商家入驻申请已" + ("REJECTED".equals(app.getStatus()) ? "驳回" : "撤回"),
                    app.getVersion(), List.of(app.getDecisionReason() == null ? "入驻流程已终止" : app.getDecisionReason()),
                    List.of("修正资料后创建新的入驻申请"), artifacts);
        }
        if (!"APPROVED".equals(app.getStatus())) {
            String next = switch (app.getStatus()) {
                case "DRAFT" -> "提交入驻申请";
                case "SUBMITTED" -> "开始入驻审核";
                case "UNDER_REVIEW" -> "完成入驻审核";
                default -> "核对入驻申请状态";
            };
            return result(key, "DRAFT".equals(app.getStatus()) ? Status.PREPARE : Status.WAITING,
                    "商家入驻审核", false, "商家入驻尚未审核通过", app.getVersion(),
                    List.of("当前入驻状态：" + app.getStatus()), List.of(next), artifacts);
        }

        List<String> blockers = new ArrayList<>();
        List<String> next = new ArrayList<>();
        MerchantAccountDO merchant = text(app.getMerchantId()) ? merchantMapper.selectMerchant(tenantId, app.getMerchantId()) : null;
        MerchantShopDO shop = text(app.getShopId()) ? merchantMapper.selectShop(tenantId, app.getShopId()) : null;
        MerchantOperatorAssignmentDO assignment = text(app.getOwnerAssignmentId())
                ? merchantMapper.selectAssignment(tenantId, app.getOwnerAssignmentId()) : null;
        if (merchant == null) {
            blockers.add("审核通过后尚未生成规范商家");
            next.add("补建规范商家");
        } else {
            artifacts.add(artifact("MERCHANT", merchant.getMerchantId(), merchant.getStatus(), merchant.getVersion(), "规范商家"));
            if (!"ACTIVE".equals(merchant.getStatus())) {
                blockers.add("商家尚未启用");
                next.add("启用商家");
            }
        }
        if (shop == null) {
            blockers.add("审核通过后尚未生成规范店铺");
            next.add("补建规范店铺");
        } else {
            artifacts.add(artifact("SHOP", shop.getShopId(), shop.getStatus(), shop.getVersion(), "规范店铺"));
            if (!"ACTIVE".equals(shop.getStatus())) {
                blockers.add("店铺尚未启用");
                next.add("启用店铺");
            }
        }
        if (assignment == null || !"ACTIVE".equals(assignment.getStatus())) {
            blockers.add("运营负责人授权尚未生效");
            next.add("激活店铺 OWNER 授权");
        } else {
            artifacts.add(artifact("OWNER_ASSIGNMENT", assignment.getAssignmentId(), assignment.getStatus(),
                    assignment.getVersion(), "运营负责人授权"));
        }
        List<MerchantDepositAccountDO> deposits = merchant == null ? List.of()
                : safe(depositMapper.selectAccountsByMerchant(tenantId, merchant.getMerchantId()));
        deposits.forEach(account -> artifacts.add(artifact("DEPOSIT_ACCOUNT", account.getAccountId(),
                account.getEnforcementStatus() + "/" + account.getCoverageStatus(), account.getVersion(),
                account.getCurrency() + " 保证金账户")));
        boolean covered = deposits.stream().anyMatch(account -> "ENFORCED".equals(account.getEnforcementStatus())
                && "SUFFICIENT".equals(account.getCoverageStatus()));
        if (!covered) {
            blockers.add("保证金权威尚未证明已强制启用且覆盖充足");
            next.add("补足保证金并启用强制门禁");
        }
        long version = artifacts.stream().map(Artifact::getVersion).filter(v -> v != null).mapToLong(Long::longValue).max().orElse(app.getVersion());
        if (!blockers.isEmpty()) {
            return result(key, Status.WAITING, "经营资格激活", false,
                    "入驻已通过，但商家尚未具备完整经营资格", version, blockers, next, artifacts);
        }
        return result(key, Status.SUCCEEDED, "商家可经营", true,
                "商家 " + merchant.getMerchantCode() + " 与店铺 " + shop.getShopId() + " 已完成入驻并具备经营资格",
                version, List.of(), List.of(), artifacts);
    }

    private static MerchantOnboardingWorkflowResult result(String key, Status status, String phase, boolean terminal,
                                                            String summary, Long version, List<String> blockers,
                                                            List<String> next, List<Artifact> artifacts) {
        return MerchantOnboardingWorkflowResult.builder().workflowType("MerchantOnboardingWorkflow")
                .workflowInstanceKey("merchant-onboarding:" + key).businessKey(key).status(status).phase(phase)
                .terminal(terminal).actionRequired(!next.isEmpty()).summary(summary).aggregateVersion(version)
                .blockers(blockers).nextActions(next).artifacts(artifacts).build();
    }

    private static Artifact artifact(String type, String id, String status, Long version, String label) {
        return Artifact.builder().type(type).id(id).status(status).version(version).label(label).build();
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static boolean text(String value) {
        return StringUtils.hasText(value);
    }

    private static void requireId(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("applicationId is required");
        }
    }
}
