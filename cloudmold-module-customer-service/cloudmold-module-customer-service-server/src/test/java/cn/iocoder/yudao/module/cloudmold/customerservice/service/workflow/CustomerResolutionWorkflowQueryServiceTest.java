package cn.iocoder.yudao.module.cloudmold.customerservice.service.workflow;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.customerservice.api.workflow.CustomerResolutionWorkflowResult.Status;
import cn.iocoder.yudao.module.cloudmold.customerservice.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.customerservice.dal.mysql.CustomerServiceStoreMapper;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CustomerResolutionWorkflowQueryServiceTest {
    private final CustomerServiceStoreMapper mapper = mock(CustomerServiceStoreMapper.class);
    private final CustomerResolutionWorkflowQueryService service = new CustomerResolutionWorkflowQueryService(mapper);

    @BeforeEach void setUp() { TenantContextHolder.setTenantId(9L); }
    @AfterEach void tearDown() { TenantContextHolder.clear(); }

    @Test
    void closedTicketWithoutFeedbackRemainsWaiting() {
        when(mapper.selectTicket(9L, "t-1")).thenReturn(ticket());
        when(mapper.selectClaimsByTicket(9L, "t-1")).thenReturn(List.of());
        when(mapper.selectCompensationEntriesByTicket(9L, "t-1")).thenReturn(List.of());
        when(mapper.selectBuyerFeedbackByTicket(9L, "t-1")).thenReturn(List.of());

        assertThat(service.inspect("t-1")).satisfies(result -> {
            assertThat(result.getStatus()).isEqualTo(Status.WAITING);
            assertThat(result.getBlockers()).contains("尚未完成客户回访");
        });
    }

    @Test
    void succeedsWithPaidClaimEvidenceAndFeedback() {
        when(mapper.selectTicket(9L, "t-1")).thenReturn(ticket());
        when(mapper.selectClaimsByTicket(9L, "t-1")).thenReturn(List.of(new CustomerServiceClaimDO()
                .setClaimId("c-1").setClaimCode("CLAIM-1").setStatus("PAID")
                .setCompensationEntryId("e-1").setVersion(3L)));
        when(mapper.selectCompensationEntriesByTicket(9L, "t-1")).thenReturn(List.of(
                new CustomerServiceCompensationEntryDO().setCompensationEntryId("e-1").setEntryType("PAYMENT")));
        when(mapper.selectBuyerFeedbackByTicket(9L, "t-1")).thenReturn(List.of(
                new CustomerServiceBuyerFeedbackDO().setFeedbackId("f-1").setSentimentCode("POSITIVE")));

        assertThat(service.inspect("t-1")).satisfies(result -> {
            assertThat(result.getStatus()).isEqualTo(Status.SUCCEEDED);
            assertThat(result.getSummary()).contains("分类、分派、处理、赔付核验和客户回访");
        });
    }

    private static CustomerServiceTicketDO ticket() {
        return new CustomerServiceTicketDO().setTicketId("t-1").setTicketNo("CS-1")
                .setCategoryCode("REFUND").setAssignedAgentPrincipalId("agent-1")
                .setStatus("CLOSED").setVersion(5L);
    }
}
