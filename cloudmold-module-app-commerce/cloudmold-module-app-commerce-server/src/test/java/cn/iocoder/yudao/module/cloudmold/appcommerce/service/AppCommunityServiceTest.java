package cn.iocoder.yudao.module.cloudmold.appcommerce.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.engagement.api.EngagementCommandApi;
import cn.iocoder.yudao.module.cloudmold.engagement.api.EngagementQueryApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AppCommunityServiceTest {

    private static final String PRINCIPAL = "1633b87f-28d8-4c55-85f0-f521fef508af";
    private static final String LISTING = "695e8fb1-5389-4d45-bf17-2bcb55090920";
    private static final String OFFER = "07e90fe2-b479-45b1-80ed-f3e7759cf2a7";
    private static final String SPU = "6b6401a0-7f32-48f2-b179-a5c1be76e660";
    private static final String SKU = "c04dd424-7682-4fd5-a856-eec247a1e693";

    private final AppMemberPrincipalResolver principalResolver = mock(AppMemberPrincipalResolver.class);
    private final AppProductReadService productReadService = mock(AppProductReadService.class);
    private final AppAddressVaultCrypto crypto = mock(AppAddressVaultCrypto.class);
    private final EngagementCommandApi commandApi = mock(EngagementCommandApi.class);
    private final EngagementQueryApi queryApi = mock(EngagementQueryApi.class);
    private final AppCommunityService service =
            new AppCommunityService(principalResolver, productReadService, crypto, commandApi, queryApi);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(7L);
        ReflectionTestUtils.setField(service, "moderationMode", "LOCAL_TEST");
        when(principalResolver.requireCurrent()).thenReturn(AppMemberPrincipalView.builder()
                .memberUserId(9L).principalId(PRINCIPAL).principalStatus("ACTIVE").build());
        when(productReadService.detail(LISTING)).thenReturn(AppProductView.builder()
                .listingId(LISTING).title("sellable product").primaryImageUrl("https://cdn/product.png")
                .canonicalSpuId(SPU).skus(List.of(AppProductView.Sku.builder()
                        .listingOfferId(OFFER).canonicalSkuId(SKU).enabled(true).inStock(true)
                        .availableQuantity(BigDecimal.ONE).qualityStatus("VERIFIED").build())).build());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldUseCurrentPrincipalEncryptBodyAndPublishOnlyThroughModerationTransitions() {
        String body = "真实种草正文";
        byte[] plaintext = body.getBytes(StandardCharsets.UTF_8);
        byte[] iv = new byte[12];
        byte[] ciphertext = "ciphertext-with-auth-tag".getBytes(StandardCharsets.UTF_8);
        when(crypto.encrypt(anyString(), aryEq(plaintext)))
                .thenReturn(new AppAddressVaultCrypto.Encrypted("sandbox-v1", iv, ciphertext));
        when(crypto.decrypt(anyString(), eq("sandbox-v1"), aryEq(iv), aryEq(ciphertext)))
                .thenReturn(plaintext);
        when(queryApi.getPublishedCommunityContent(anyString(), eq(PRINCIPAL))).thenAnswer(invocation ->
                EngagementQueryApi.CommunityContentView.builder().contentId(invocation.getArgument(0))
                        .authorPrincipalId(PRINCIPAL).bodyKeyId("sandbox-v1").bodyIv(iv)
                        .bodyCiphertext(ciphertext).bodyDigestSha256(DigestUtil.sha256Hex(plaintext))
                        .canonicalSpuId(SPU).canonicalSkuId(SKU).listingId(LISTING).listingOfferId(OFFER)
                        .status("PUBLISHED").version(3L).createdAt(Instant.now()).build());

        AppCommunityView result = service.createPost("community-create-1", body, LISTING, OFFER, SPU, SKU);

        assertThat(result.getBody()).isEqualTo(body);
        assertThat(result.getProductLink().isNavigable()).isTrue();
        ArgumentCaptor<EngagementCommandApi.CreateCommunityContentCommand> create =
                ArgumentCaptor.forClass(EngagementCommandApi.CreateCommunityContentCommand.class);
        verify(commandApi).createCommunityContent(create.capture());
        assertThat(create.getValue().getAuthorPrincipalId()).isEqualTo(PRINCIPAL);
        assertThat(create.getValue().getDesiredStatus()).isEqualTo("DRAFT");
        assertThat(create.getValue().getBodyCiphertext()).isEqualTo(ciphertext);
        assertThat(create.getValue().getBodyDigestSha256()).isEqualTo(DigestUtil.sha256Hex(plaintext));
        assertThat(new String(create.getValue().getBodyCiphertext(), StandardCharsets.UTF_8))
                .doesNotContain(body);
        ArgumentCaptor<EngagementCommandApi.TransitionCommunityContentCommand> transitions =
                ArgumentCaptor.forClass(EngagementCommandApi.TransitionCommunityContentCommand.class);
        verify(commandApi, times(2)).transitionCommunityContent(transitions.capture());
        assertThat(transitions.getAllValues())
                .extracting(EngagementCommandApi.TransitionCommunityContentCommand::getDesiredStatus)
                .containsExactly("PENDING_MODERATION", "PUBLISHED");
        assertThat(transitions.getAllValues())
                .allSatisfy(command -> assertThat(command.getActorPrincipalId()).isEqualTo(PRINCIPAL));
    }
}
