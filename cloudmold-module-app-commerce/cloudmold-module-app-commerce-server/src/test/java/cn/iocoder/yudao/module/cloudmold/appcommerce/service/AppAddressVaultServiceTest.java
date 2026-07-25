package cn.iocoder.yudao.module.cloudmold.appcommerce.service;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.appcommerce.dal.dataobject.AppAddressSnapshotDO;
import cn.iocoder.yudao.module.cloudmold.appcommerce.dal.mysql.AppAddressSnapshotMapper;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.member.api.address.MemberAddressApi;
import cn.iocoder.yudao.module.member.api.address.dto.MemberAddressRespDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AppAddressVaultServiceTest {

    private static final String LOCAL_KEY =
            "Y2xvdWRtb2xkLWxvY2FsLWFkZHJlc3Mta2V5LXYxISE=";

    private final AppMemberPrincipalResolver principalResolver =
            mock(AppMemberPrincipalResolver.class);
    private final MemberAddressApi memberAddressApi = mock(MemberAddressApi.class);
    private final AppAddressSnapshotMapper mapper = mock(AppAddressSnapshotMapper.class);
    private final OutboxAppender outboxAppender = mock(OutboxAppender.class);
    private final AppAddressVaultCrypto crypto = new AppAddressVaultCrypto(
            new MockEnvironment(), "SANDBOX", LOCAL_KEY);
    private final AppAddressVaultService service = new AppAddressVaultService(
            principalResolver, memberAddressApi, mapper, crypto, outboxAppender);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(11L);
        when(principalResolver.requireCurrent()).thenReturn(AppMemberPrincipalView.builder()
                .memberUserId(1001L).principalId("principal-member-1")
                .principalStatus("ACTIVE").sourceSystem("MEMBER")
                .sourceType("MEMBER_USER").build());
        when(memberAddressApi.getAddress(88L, 1001L))
                .thenReturn(CommonResult.success(address()));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldEncryptPiiAndReplayOnlyTheOwnedToken() {
        AtomicReference<AppAddressSnapshotDO> stored = new AtomicReference<>();
        when(mapper.insertIgnore(any())).thenAnswer(invocation -> {
            if (stored.get() != null) {
                return 0;
            }
            stored.set(invocation.getArgument(0));
            return 1;
        });
        when(mapper.selectByIdempotency(11L, "principal-member-1", "address-idem-001"))
                .thenAnswer(invocation -> stored.get());
        when(mapper.selectOwned(eq(11L), anyString(), eq("principal-member-1")))
                .thenAnswer(invocation -> stored.get());

        AppAddressSnapshotView first = service.createSnapshot("address-idem-001", 88L);
        AppAddressSnapshotView replay = service.createSnapshot("address-idem-001", 88L);
        AppAddressSnapshotView owned = service.requireOwned(
                first.getAddressRef(), "principal-member-1");

        assertThat(first.getAddressRef()).hasSize(36);
        assertThat(first.getReceiverSummary()).isEqualTo("*三");
        assertThat(first.getMobileSummary()).isEqualTo("138****8000");
        assertThat(first.getDuplicate()).isFalse();
        assertThat(replay.getAddressRef()).isEqualTo(first.getAddressRef());
        assertThat(replay.getDuplicate()).isTrue();
        assertThat(owned.getDestinationRegionCode()).isEqualTo("440305");
        String ciphertext = new String(
                stored.get().getCiphertext(), StandardCharsets.ISO_8859_1);
        assertThat(ciphertext)
                .doesNotContain(address().getMobile())
                .doesNotContain(address().getDetailAddress());
        verify(outboxAppender, times(1)).append(any());
    }

    @Test
    void shouldRejectAddressOwnedByAnotherMember() {
        MemberAddressRespDTO foreign = address();
        foreign.setUserId(1002L);
        when(memberAddressApi.getAddress(88L, 1001L))
                .thenReturn(CommonResult.success(foreign));

        assertThatThrownBy(() -> service.createSnapshot("address-idem-002", 88L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("member address does not exist");
        verifyNoInteractions(mapper, outboxAppender);
    }

    private static MemberAddressRespDTO address() {
        MemberAddressRespDTO value = new MemberAddressRespDTO();
        value.setId(88L);
        value.setUserId(1001L);
        value.setName("张三");
        value.setMobile("13800138000");
        value.setAreaId(440305);
        value.setDetailAddress("科技园 1 号");
        value.setDefaultStatus(true);
        return value;
    }
}
