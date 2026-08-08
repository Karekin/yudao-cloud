package cn.iocoder.yudao.module.cloudmold.crm.service.contract;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.bpm.api.definition.BpmSystemModelApi;
import cn.iocoder.yudao.module.bpm.api.definition.dto.BpmSystemModelRegisterReqDTO;
import cn.iocoder.yudao.module.cloudmold.identity.api.IdentityQueryApi;
import cn.iocoder.yudao.module.cloudmold.identity.api.SourceIdentityReference;
import cn.iocoder.yudao.module.cloudmold.identity.api.SourceIdentityView;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesContractBpmModelRegistrarTest {

    private final BpmSystemModelApi modelApi = mock(BpmSystemModelApi.class);
    private final IdentityQueryApi identityQueryApi = mock(IdentityQueryApi.class);
    private final SalesContractApprovalProperties properties = new SalesContractApprovalProperties();
    private final SalesContractBpmModelRegistrar registrar =
            new SalesContractBpmModelRegistrar(modelApi, properties, identityQueryApi);

    @Test
    void registersCanonicalModelAndExcludesRequesterFromReviewers() {
        properties.setReviewerUserIds(new LinkedHashSet<>(List.of(226L, 229L)));
        when(identityQueryApi.resolveActiveSource(
                new SourceIdentityReference("SYSTEM", "SYSTEM_ADMIN_USER", "229")))
                .thenReturn(activeIdentity("principal-reviewer-229"));
        when(modelApi.register(org.mockito.ArgumentMatchers.any()))
                .thenReturn(CommonResult.success("definition-1"));

        List<Long> reviewers = registrar.registerAndResolveReviewers(226L);

        assertThat(reviewers).containsExactly(229L);
        verify(modelApi).register(argThat(this::isCanonicalModel));
    }

    @Test
    void failsClosedWithoutIndependentReviewer() {
        properties.setReviewerUserIds(new LinkedHashSet<>(List.of(226L)));

        assertThatThrownBy(() -> registrar.registerAndResolveReviewers(226L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("distinct from the requester");
    }

    @Test
    void failsClosedWhenReviewerHasNoCanonicalIdentity() {
        properties.setReviewerUserIds(new LinkedHashSet<>(List.of(229L)));
        when(identityQueryApi.resolveActiveSource(
                new SourceIdentityReference("SYSTEM", "SYSTEM_ADMIN_USER", "229")))
                .thenThrow(new IllegalArgumentException("active source identity does not exist"));

        assertThatThrownBy(() -> registrar.registerAndResolveReviewers(226L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("active source identity does not exist");
    }

    private SourceIdentityView activeIdentity(String principalId) {
        return SourceIdentityView.builder()
                .principalId(principalId)
                .principalStatus("ACTIVE")
                .sourceStatus("ACTIVE")
                .build();
    }

    private boolean isCanonicalModel(BpmSystemModelRegisterReqDTO request) {
        return "cloudmold_sales_contract_approval".equals(request.getKey())
                && "cloudmold-customer-sales".equals(request.getCategoryCode())
                && request.getBpmnXml().contains("sales_contract_review")
                && request.getBpmnXml().contains("candidateStrategy=\"35\"")
                && Long.valueOf(226L).equals(request.getManagerUserId());
    }
}
