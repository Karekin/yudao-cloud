package cn.iocoder.yudao.module.cloudmold.merchant.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.identity.api.PrincipalValidationApi;
import cn.iocoder.yudao.module.cloudmold.listing.api.unpublish.ListingUnpublishSagaCommand;
import cn.iocoder.yudao.module.cloudmold.listing.api.unpublish.ListingUnpublishSagaCommandApi;
import cn.iocoder.yudao.module.cloudmold.listing.api.unpublish.ListingUnpublishSagaOperation;
import cn.iocoder.yudao.module.cloudmold.listing.api.unpublish.ListingUnpublishSagaView;
import cn.iocoder.yudao.module.cloudmold.merchant.api.*;
import cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.merchant.dal.mysql.MerchantStoreMapper;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MerchantCommandServiceImplTest {

    private Harness harness;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(7L);
        harness = new Harness();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void completesOnboardingThenActivatesMerchantAndShop() {
        MerchantCommandResult draft = harness.execute(command(MerchantOperation.CREATE_ONBOARDING_DRAFT, "m-1")
                .setLegalName("Y Shop Shanghai").setRegistrationHashToken("sha256:registration")
                .setBusinessLicenseToken("vault:license-1").setOwnerPrincipalId("principal-owner")
                .setChannelCode("YSHOPPING").setExternalShopId("shop-external-1"));
        MerchantCommandResult submitted = harness.execute(transition(MerchantOperation.SUBMIT_ONBOARDING, "m-2", draft));
        MerchantCommandResult reviewing = harness.execute(transition(MerchantOperation.START_ONBOARDING_REVIEW, "m-3", submitted));
        MerchantCommandResult approved = harness.execute(transition(MerchantOperation.APPROVE_ONBOARDING, "m-4", reviewing));

        assertThat(approved.getOnboardingStatus()).isEqualTo("APPROVED");
        assertThat(approved.getMerchantStatus()).isEqualTo("PENDING_ACTIVATION");
        assertThat(approved.getShopStatus()).isEqualTo("DRAFT");
        assertThat(approved.getOwnerAssignmentStatus()).isEqualTo("ACTIVE");
        assertThat(approved.getMerchantId()).isNotEqualTo(approved.getShopId());
        assertThat(approved.getMerchantId()).isNotEqualTo(approved.getLegalEntityId());
        verify(harness.principalValidationApi).requireActivePrincipal("principal-owner");

        MerchantCommandResult merchantActive = harness.execute(command(MerchantOperation.ACTIVATE_MERCHANT, "m-5")
                .setMerchantId(approved.getMerchantId()).setExpectedVersion(approved.getMerchantVersion()));
        MerchantCommandResult shopActive = harness.execute(command(MerchantOperation.ACTIVATE_SHOP, "m-6")
                .setShopId(approved.getShopId()).setExpectedVersion(approved.getShopVersion()));

        assertThat(merchantActive.getMerchantStatus()).isEqualTo("ACTIVE");
        assertThat(merchantActive.getMerchantVersion()).isEqualTo(2L);
        assertThat(shopActive.getShopStatus()).isEqualTo("ACTIVE");
        assertThat(shopActive.getShopVersion()).isEqualTo(2L);
        assertThat(harness.events).extracting(AppendDomainEventCommand::getEventType)
                .contains("merchant.onboarding.status_changed", "merchant.entity.status_changed",
                        "merchant.operator_assignment.changed");
        assertThat(harness.events).allSatisfy(event -> {
            assertThat(event.getSourceSystem()).isEqualTo("cloudmold-merchant");
            assertThatCode(() -> UUID.fromString(event.getCorrelationId())).doesNotThrowAnyException();
            assertThat(event.getPayload()).doesNotContainKey("tenant_id");
        });
        assertThat(harness.events).filteredOn(event -> "merchant.onboarding.status_changed".equals(event.getEventType())
                        && "APPROVED".equals(event.getPayload().get("current_status")))
                .singleElement().satisfies(event -> assertThat(event.getPayload())
                        .containsEntry("merchant_id", approved.getMerchantId())
                        .containsEntry("shop_id", approved.getShopId()));
        assertThat(harness.histories).hasSizeGreaterThanOrEqualTo(6);
    }

    @Test
    void rejectsSkippedOnboardingState() {
        MerchantCommandResult draft = harness.execute(command(MerchantOperation.CREATE_ONBOARDING_DRAFT, "skip-1")
                .setLegalName("Y Shop").setRegistrationHashToken("sha256:r")
                .setOwnerPrincipalId("principal-owner").setChannelCode("YSHOPPING")
                .setExternalShopId("external"));

        assertThatThrownBy(() -> harness.execute(transition(MerchantOperation.APPROVE_ONBOARDING, "skip-2", draft)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("UNDER_REVIEW");
        assertThat(harness.applications.get(draft.getApplicationId()).getStatus()).isEqualTo("DRAFT");
    }

    @Test
    void rejectsStaleVersionWithoutMutation() {
        MerchantCommandResult draft = harness.execute(command(MerchantOperation.CREATE_ONBOARDING_DRAFT, "version-1")
                .setLegalName("Y Shop").setRegistrationHashToken("sha256:r")
                .setOwnerPrincipalId("principal-owner").setChannelCode("YSHOPPING")
                .setExternalShopId("external"));

        assertThatThrownBy(() -> harness.execute(command(MerchantOperation.SUBMIT_ONBOARDING, "version-2")
                .setApplicationId(draft.getApplicationId()).setExpectedVersion(99L)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("version conflict");
        assertThat(harness.applications.get(draft.getApplicationId()).getVersion()).isEqualTo(1L);
    }

    @Test
    void replaysImmutableResultWithoutNewRowsOrEvents() {
        MerchantCommand command = command(MerchantOperation.CREATE_ONBOARDING_DRAFT, "replay-1")
                .setLegalName("Y Shop").setRegistrationHashToken("sha256:r")
                .setOwnerPrincipalId("principal-owner").setChannelCode("YSHOPPING")
                .setExternalShopId("external");
        MerchantCommandResult first = harness.execute(command);
        int eventCount = harness.events.size();
        int historyCount = harness.histories.size();

        MerchantCommandResult replay = harness.execute(command);

        assertThat(replay.isDuplicate()).isTrue();
        assertThat(replay.getApplicationId()).isEqualTo(first.getApplicationId());
        assertThat(harness.applications).hasSize(1);
        assertThat(harness.events).hasSize(eventCount);
        assertThat(harness.histories).hasSize(historyCount);
    }

    @Test
    void shopActivationRequiresActiveMerchant() {
        MerchantCommandResult approved = harness.approved("shop-dependency");

        assertThatThrownBy(() -> harness.execute(command(MerchantOperation.ACTIVATE_SHOP, "shop-dependency-activate")
                .setShopId(approved.getShopId()).setExpectedVersion(approved.getShopVersion())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ACTIVE merchant");
        assertThat(harness.shops.get(approved.getShopId()).getStatus()).isEqualTo("DRAFT");
    }

    @Test
    void validatesReferencesAndAuthorizesOnlyActiveAssignment() {
        MerchantCommandResult approved = harness.approved("authorization");
        harness.execute(command(MerchantOperation.ACTIVATE_MERCHANT, "authorization-m")
                .setMerchantId(approved.getMerchantId()).setExpectedVersion(approved.getMerchantVersion()));
        harness.execute(command(MerchantOperation.ACTIVATE_SHOP, "authorization-s")
                .setShopId(approved.getShopId()).setExpectedVersion(approved.getShopVersion()));

        MerchantReferenceView reference = harness.service.requireActiveReference(
                new MerchantReferenceValidationCommand().setMerchantId(approved.getMerchantId())
                        .setShopId(approved.getShopId()));
        MerchantOperatorAuthorizationView authorized = harness.service.requireAuthorizedOperator(
                new MerchantOperatorAuthorizationCommand().setMerchantId(approved.getMerchantId())
                        .setShopId(approved.getShopId()).setPrincipalId("principal-owner").setRoleCode("OWNER"));
        MerchantOperatorAuthorizationView ownerAsListingOperator = harness.service.requireAuthorizedOperator(
                new MerchantOperatorAuthorizationCommand().setMerchantId(approved.getMerchantId())
                        .setShopId(approved.getShopId()).setPrincipalId("principal-owner")
                        .setRoleCode("LISTING_OPERATOR"));

        assertThat(reference.getShopStatus()).isEqualTo("ACTIVE");
        assertThat(authorized.getAssignmentStatus()).isEqualTo("ACTIVE");
        assertThat(ownerAsListingOperator.getRoleCode()).isEqualTo("OWNER");
        assertThatThrownBy(() -> harness.service.requireAuthorizedOperator(
                new MerchantOperatorAuthorizationCommand().setMerchantId(approved.getMerchantId())
                        .setShopId(approved.getShopId()).setPrincipalId("principal-other").setRoleCode("OWNER")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not authorized");
    }

    @Test
    void suspendsAndResumesMerchantWithVersionedHistoryEventAndImmutableReplay() {
        MerchantCommandResult active = harness.active("merchant-lifecycle");
        MerchantCommand suspend = command(MerchantOperation.SUSPEND_MERCHANT, "merchant-suspend")
                .setMerchantId(active.getMerchantId()).setExpectedVersion(active.getMerchantVersion())
                .setReason("compliance review");

        MerchantCommandResult suspended = harness.execute(suspend);

        assertThat(suspended.getMerchantStatus()).isEqualTo("SUSPENDED");
        assertThat(suspended.getMerchantVersion()).isEqualTo(3L);
        assertThatThrownBy(() -> harness.service.requireActiveReference(
                new MerchantReferenceValidationCommand().setMerchantId(active.getMerchantId())
                        .setShopId(active.getShopId())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not active");
        assertThat(harness.histories).filteredOn(history -> active.getMerchantId().equals(history.getAggregateId())
                        && "SUSPENDED".equals(history.getCurrentStatus()))
                .singleElement().satisfies(history -> assertThat(history.getReason()).isEqualTo("compliance review"));
        assertThat(harness.events).filteredOn(event -> "merchant.entity.status_changed".equals(event.getEventType())
                        && active.getMerchantId().equals(event.getAggregateId())
                        && "SUSPENDED".equals(event.getPayload().get("current_status")))
                .singleElement().satisfies(event -> assertThat(event.getPayload())
                        .containsEntry("reason", "compliance review"));
        assertThat(suspended.getListingUnpublishSagaId()).isEqualTo("listing-unpublish-saga-test");
        assertThat(suspended.getAffectedListingCount()).isEqualTo(2);
        assertThat(harness.unpublishSagaCommands).singleElement().satisfies(command -> {
            assertThat(command.getOperation()).isEqualTo(ListingUnpublishSagaOperation.START);
            assertThat(command.getSourceEntityType()).isEqualTo("MERCHANT");
            assertThat(command.getMerchantId()).isEqualTo(active.getMerchantId());
            assertThat(command.getShopId()).isNull();
            assertThat(command.getSourceAggregateVersion()).isEqualTo(3L);
            assertThat(command.getReason()).isEqualTo("compliance review");
            assertThat(command.getSourceEventId()).isEqualTo(harness.events.stream()
                    .filter(event -> "merchant.entity.status_changed".equals(event.getEventType())
                            && "SUSPENDED".equals(event.getPayload().get("current_status")))
                    .findFirst().orElseThrow().getEventId());
        });
        int eventCount = harness.events.size();
        MerchantCommandResult replay = harness.execute(suspend);
        assertThat(replay.isDuplicate()).isTrue();
        assertThat(replay.getMerchantVersion()).isEqualTo(3L);
        assertThat(harness.events).hasSize(eventCount);

        MerchantCommandResult resumed = harness.execute(command(MerchantOperation.RESUME_MERCHANT, "merchant-resume")
                .setMerchantId(active.getMerchantId()).setExpectedVersion(suspended.getMerchantVersion()));
        assertThat(resumed.getMerchantStatus()).isEqualTo("ACTIVE");
        assertThat(resumed.getMerchantVersion()).isEqualTo(4L);
        assertThat(harness.unpublishSagaCommands).hasSize(1);
        assertThat(harness.service.requireActiveReference(new MerchantReferenceValidationCommand()
                .setMerchantId(active.getMerchantId()).setShopId(active.getShopId())).getMerchantStatus())
                .isEqualTo("ACTIVE");
    }

    @Test
    void pausesAndResumesShopWithVersionedHistoryAndEvent() {
        MerchantCommandResult active = harness.active("shop-lifecycle");
        MerchantCommandResult paused = harness.execute(command(MerchantOperation.PAUSE_SHOP, "shop-pause")
                .setShopId(active.getShopId()).setExpectedVersion(active.getShopVersion())
                .setReason("inventory reconciliation"));

        assertThat(paused.getMerchantStatus()).isEqualTo("ACTIVE");
        assertThat(paused.getShopStatus()).isEqualTo("PAUSED");
        assertThat(paused.getShopVersion()).isEqualTo(3L);
        assertThatThrownBy(() -> harness.service.requireActiveReference(new MerchantReferenceValidationCommand()
                .setMerchantId(active.getMerchantId()).setShopId(active.getShopId())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not active");
        assertThat(harness.events).filteredOn(event -> "merchant.entity.status_changed".equals(event.getEventType())
                        && active.getShopId().equals(event.getAggregateId())
                        && "PAUSED".equals(event.getPayload().get("current_status")))
                .singleElement().satisfies(event -> assertThat(event.getPayload())
                        .containsEntry("reason", "inventory reconciliation"));
        assertThat(harness.histories).filteredOn(history -> active.getShopId().equals(history.getAggregateId())
                        && "PAUSED".equals(history.getCurrentStatus()))
                .singleElement().satisfies(history ->
                        assertThat(history.getReason()).isEqualTo("inventory reconciliation"));
        assertThat(paused.getListingUnpublishSagaId()).isEqualTo("listing-unpublish-saga-test");
        assertThat(paused.getAffectedListingCount()).isEqualTo(2);
        assertThat(harness.unpublishSagaCommands).singleElement().satisfies(command -> {
            assertThat(command.getOperation()).isEqualTo(ListingUnpublishSagaOperation.START);
            assertThat(command.getSourceEntityType()).isEqualTo("SHOP");
            assertThat(command.getMerchantId()).isEqualTo(active.getMerchantId());
            assertThat(command.getShopId()).isEqualTo(active.getShopId());
            assertThat(command.getSourceAggregateVersion()).isEqualTo(3L);
            assertThat(command.getReason()).isEqualTo("inventory reconciliation");
            assertThat(command.getSourceEventId()).isEqualTo(harness.events.stream()
                    .filter(event -> "merchant.entity.status_changed".equals(event.getEventType())
                            && "PAUSED".equals(event.getPayload().get("current_status")))
                    .findFirst().orElseThrow().getEventId());
        });

        MerchantCommandResult resumed = harness.execute(command(MerchantOperation.RESUME_SHOP, "shop-resume")
                .setShopId(active.getShopId()).setExpectedVersion(paused.getShopVersion()));
        assertThat(resumed.getShopStatus()).isEqualTo("ACTIVE");
        assertThat(resumed.getShopVersion()).isEqualTo(4L);
        assertThat(harness.unpublishSagaCommands).hasSize(1);
        assertThat(harness.service.requireActiveReference(new MerchantReferenceValidationCommand()
                .setMerchantId(active.getMerchantId()).setShopId(active.getShopId())).getShopStatus())
                .isEqualTo("ACTIVE");
    }

    @Test
    void suspensionAndPauseRequireReasonWithoutMutation() {
        MerchantCommandResult active = harness.active("reason-required");

        assertThatThrownBy(() -> harness.execute(command(MerchantOperation.SUSPEND_MERCHANT, "reason-merchant")
                .setMerchantId(active.getMerchantId()).setExpectedVersion(active.getMerchantVersion())))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("reason is required");
        assertThatThrownBy(() -> harness.execute(command(MerchantOperation.PAUSE_SHOP, "reason-shop")
                .setShopId(active.getShopId()).setExpectedVersion(active.getShopVersion())))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("reason is required");
        assertThat(harness.merchants.get(active.getMerchantId()).getStatus()).isEqualTo("ACTIVE");
        assertThat(harness.shops.get(active.getShopId()).getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void rejectsWrongLifecycleStateAndStaleVersionWithoutMutation() {
        MerchantCommandResult active = harness.active("state-version");

        assertThatThrownBy(() -> harness.execute(command(MerchantOperation.SUSPEND_MERCHANT, "stale-merchant")
                .setMerchantId(active.getMerchantId()).setExpectedVersion(99L).setReason("stale")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("version conflict");
        assertThatThrownBy(() -> harness.execute(command(MerchantOperation.PAUSE_SHOP, "stale-shop")
                .setShopId(active.getShopId()).setExpectedVersion(99L).setReason("stale")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("version conflict");
        assertThatThrownBy(() -> harness.execute(command(MerchantOperation.RESUME_MERCHANT, "wrong-merchant-state")
                .setMerchantId(active.getMerchantId()).setExpectedVersion(active.getMerchantVersion())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("requires SUSPENDED");
        assertThatThrownBy(() -> harness.execute(command(MerchantOperation.RESUME_SHOP, "wrong-shop-state")
                .setShopId(active.getShopId()).setExpectedVersion(active.getShopVersion())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("requires PAUSED");
        assertThat(harness.merchants.get(active.getMerchantId()).getStatus()).isEqualTo("ACTIVE");
        assertThat(harness.shops.get(active.getShopId()).getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void rejectsShopResumeWhileMerchantIsSuspended() {
        MerchantCommandResult active = harness.active("shop-resume-merchant-gate");
        MerchantCommandResult paused = harness.execute(command(MerchantOperation.PAUSE_SHOP, "gate-shop-pause")
                .setShopId(active.getShopId()).setExpectedVersion(active.getShopVersion()).setReason("maintenance"));
        harness.execute(command(MerchantOperation.SUSPEND_MERCHANT, "gate-merchant-suspend")
                .setMerchantId(active.getMerchantId()).setExpectedVersion(active.getMerchantVersion())
                .setReason("compliance review"));

        assertThatThrownBy(() -> harness.execute(command(MerchantOperation.RESUME_SHOP, "gate-shop-resume")
                .setShopId(active.getShopId()).setExpectedVersion(paused.getShopVersion())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ACTIVE merchant");
        assertThat(harness.shops.get(active.getShopId()).getStatus()).isEqualTo("PAUSED");
    }

    @Test
    void validatesActiveMerchantOwnerWithoutDependingOnShop() {
        MerchantCommandResult active = harness.active("inventory-owner");

        MerchantOwnerView owner = harness.service.requireActiveMerchant(active.getMerchantId());

        assertThat(owner.getMerchantId()).isEqualTo(active.getMerchantId());
        assertThat(owner.getMerchantStatus()).isEqualTo("ACTIVE");
        assertThat(owner.getLegalEntityId()).isNotBlank();

        harness.execute(command(MerchantOperation.SUSPEND_MERCHANT, "inventory-owner-suspend")
                .setMerchantId(active.getMerchantId()).setExpectedVersion(active.getMerchantVersion())
                .setReason("inventory owner disabled"));
        assertThatThrownBy(() -> harness.service.requireActiveMerchant(active.getMerchantId()))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("merchant owner is not active");
        verify(harness.mapper, times(2)).selectActiveMerchant(7L, active.getMerchantId());
        verify(harness.mapper, never()).selectActiveReference(anyLong(), anyString(), anyString());
    }

    @Test
    void linksNormalizedSourceToRealTargetAndReplaysWithoutNewEvent() {
        MerchantCommandResult active = harness.active("source-link");
        MerchantCommand link = command(MerchantOperation.LINK_SOURCE, "source-link-command")
                .setSourceReference(source(" erp ", " merchant ", "  legacy-9001  ", null))
                .setTargetType(" merchant ").setTargetId(active.getMerchantId())
                .setValidFrom(Instant.parse("2026-07-15T07:00:00Z"))
                .setValidTo(Instant.parse("2026-08-15T07:00:00Z"))
                .setVerificationRef("ticket:merchant-source-9001")
                .setMigrationRunId("migration-merchant-v17");

        MerchantCommandResult linked = harness.execute(link);

        assertThat(linked.getSourceSystem()).isEqualTo("ERP");
        assertThat(linked.getSourceType()).isEqualTo("MERCHANT");
        assertThat(linked.getSourceId()).isEqualTo("legacy-9001");
        assertThat(linked.getTargetType()).isEqualTo("MERCHANT");
        assertThat(linked.getTargetId()).isEqualTo(active.getMerchantId());
        assertThat(linked.getSourceMappingStatus()).isEqualTo("ACTIVE");
        assertThat(linked.getSourceMappingVersion()).isEqualTo(1L);
        assertThat(harness.service.resolveActive(source(" erp ", " merchant ", " legacy-9001 ",
                Instant.parse("2026-07-16T00:00:00Z"))).getMappingId()).isEqualTo(linked.getSourceMappingId());
        assertThat(harness.events).filteredOn(event -> "merchant.source_mapping.changed".equals(event.getEventType()))
                .singleElement().satisfies(event -> {
                    assertThat(event.getSchemaVersion()).isEqualTo(1);
                    assertThat(event.getPayload()).containsEntry("run_id", "run-merchant")
                            .containsEntry("migration_run_id", "migration-merchant-v17")
                            .containsEntry("source_system", "ERP")
                            .containsEntry("source_type", "MERCHANT")
                            .containsEntry("source_id", "legacy-9001")
                            .containsEntry("target_type", "MERCHANT")
                            .containsEntry("target_id", active.getMerchantId())
                            .containsEntry("current_status", "ACTIVE")
                            .containsEntry("verification_ref", "ticket:merchant-source-9001")
                            .doesNotContainKeys("legal_name", "registration_hash_token", "business_license_token");
                });
        int eventCount = harness.events.size();
        MerchantCommandResult replay = harness.execute(link);
        assertThat(replay.isDuplicate()).isTrue();
        assertThat(replay.getSourceMappingId()).isEqualTo(linked.getSourceMappingId());
        assertThat(harness.sourceMappings).hasSize(1);
        assertThat(harness.events).hasSize(eventCount);
    }

    @Test
    void rejectsUnknownCrossEntityAndCanonicalIdReuseTargets() {
        MerchantCommandResult active = harness.active("source-target");

        assertThatThrownBy(() -> harness.execute(command(MerchantOperation.LINK_SOURCE, "source-wrong-target")
                .setSourceReference(source("ERP", "SHOP", "legacy-shop", null))
                .setTargetType("SHOP").setTargetId(active.getMerchantId())
                .setValidFrom(Instant.parse("2026-07-15T07:00:00Z"))
                .setVerificationRef("ticket:wrong-target").setMigrationRunId("migration-v17")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("target does not exist");
        assertThatThrownBy(() -> harness.execute(command(MerchantOperation.LINK_SOURCE, "source-reused-target")
                .setSourceReference(source("ERP", "MERCHANT", active.getMerchantId(), null))
                .setTargetType("MERCHANT").setTargetId(active.getMerchantId())
                .setValidFrom(Instant.parse("2026-07-15T07:00:00Z"))
                .setVerificationRef("ticket:reused-target").setMigrationRunId("migration-v17")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("must differ");
        assertThatThrownBy(() -> harness.execute(command(MerchantOperation.LINK_SOURCE, "source-unsupported-target")
                .setSourceReference(source("ERP", "STORE", "legacy-store", null))
                .setTargetType("STORE").setTargetId(active.getShopId())
                .setValidFrom(Instant.parse("2026-07-15T07:00:00Z"))
                .setVerificationRef("ticket:unsupported-target").setMigrationRunId("migration-v17")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("targetType");
        assertThat(harness.sourceMappings).isEmpty();
    }

    @Test
    void rejectsDuplicateSourceAndFailsClosedForMissingAmbiguousOrOtherTenant() {
        MerchantCommandResult active = harness.active("source-unique");
        String legalEntityId = harness.merchants.get(active.getMerchantId()).getLegalEntityId();
        MerchantCommand first = sourceLink("source-unique-link-1", "ERP", "MERCHANT", "42",
                "MERCHANT", active.getMerchantId());
        MerchantCommandResult linked = harness.execute(first);

        assertThatThrownBy(() -> harness.execute(sourceLink("source-unique-link-2", "erp", "merchant", " 42 ",
                "LEGAL_ENTITY", legalEntityId)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("active mapping");
        assertThatThrownBy(() -> harness.service.resolveActive(source("ERP", "MERCHANT", "missing",
                Instant.parse("2026-07-15T08:00:00Z"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("exactly one");

        MerchantSourceMappingDO original = harness.sourceMappings.get(linked.getSourceMappingId());
        MerchantSourceMappingDO corruptDuplicate = new MerchantSourceMappingDO()
                .setMappingId(UUID.randomUUID().toString()).setTenantId(original.getTenantId())
                .setSourceSystem(original.getSourceSystem()).setSourceType(original.getSourceType())
                .setSourceId(original.getSourceId()).setTargetType("LEGAL_ENTITY")
                .setTargetId(legalEntityId).setLegalEntityId(legalEntityId)
                .setValidFrom(original.getValidFrom()).setValidTo(original.getValidTo())
                .setVerificationRef(original.getVerificationRef()).setMigrationRunId(original.getMigrationRunId())
                .setStatus(original.getStatus()).setVersion(original.getVersion());
        harness.sourceMappings.put(corruptDuplicate.getMappingId(), corruptDuplicate);
        assertThatThrownBy(() -> harness.service.resolveActive(source("ERP", "MERCHANT", "42",
                Instant.parse("2026-07-15T08:00:00Z"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("exactly one");

        TenantContextHolder.setTenantId(8L);
        assertThatThrownBy(() -> harness.service.resolveActive(source("ERP", "MERCHANT", "42",
                Instant.parse("2026-07-15T08:00:00Z"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("exactly one");
    }

    @Test
    void enforcesEffectiveWindowThenRevokesWithVersionAndImmutableReplay() {
        MerchantCommandResult active = harness.active("source-revoke");
        assertThatThrownBy(() -> harness.execute(sourceLink("source-invalid-window", "ERP", "SHOP", "invalid-77",
                        "SHOP", active.getShopId()).setValidFrom(Instant.parse("2026-07-15T09:00:00Z"))
                .setValidTo(Instant.parse("2026-07-15T09:00:00Z"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("validTo");
        MerchantCommandResult linked = harness.execute(sourceLink("source-revoke-link", "ERP", "SHOP", "77",
                "SHOP", active.getShopId()).setValidFrom(Instant.parse("2026-07-15T07:00:00Z"))
                .setValidTo(Instant.parse("2026-07-15T09:00:00Z")));

        assertThatThrownBy(() -> harness.service.resolveActive(source("ERP", "SHOP", "77",
                Instant.parse("2026-07-15T06:59:59Z")))).hasMessageContaining("exactly one");
        assertThat(harness.service.resolveActive(source("ERP", "SHOP", "77",
                Instant.parse("2026-07-15T07:00:00Z"))).getMappingId()).isEqualTo(linked.getSourceMappingId());
        assertThatThrownBy(() -> harness.service.resolveActive(source("ERP", "SHOP", "77",
                Instant.parse("2026-07-15T09:00:00Z")))).hasMessageContaining("exactly one");
        assertThatThrownBy(() -> harness.execute(command(MerchantOperation.REVOKE_SOURCE, "source-revoke-stale")
                .setSourceMappingId(linked.getSourceMappingId()).setExpectedVersion(99L)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("version conflict");

        MerchantCommand revoke = command(MerchantOperation.REVOKE_SOURCE, "source-revoke-command")
                .setSourceMappingId(linked.getSourceMappingId()).setExpectedVersion(linked.getSourceMappingVersion());
        MerchantCommandResult revoked = harness.execute(revoke);
        assertThat(revoked.getSourceMappingStatus()).isEqualTo("REVOKED");
        assertThat(revoked.getSourceMappingVersion()).isEqualTo(2L);
        assertThat(revoked.getValidTo()).isEqualTo(Instant.parse("2026-07-15T08:00:00Z"));
        assertThatThrownBy(() -> harness.service.resolveActive(source("ERP", "SHOP", "77",
                Instant.parse("2026-07-15T07:30:00Z")))).hasMessageContaining("exactly one");
        assertThat(harness.events).filteredOn(event -> "merchant.source_mapping.changed".equals(event.getEventType())
                        && "REVOKED".equals(event.getPayload().get("current_status")))
                .singleElement().satisfies(event -> assertThat(event.getPayload())
                        .containsEntry("previous_status", "ACTIVE").containsEntry("current_status", "REVOKED"));
        int eventCount = harness.events.size();
        assertThat(harness.execute(revoke).isDuplicate()).isTrue();
        assertThat(harness.events).hasSize(eventCount);
    }

    @Test
    void emitsSalesEligibilityEntityTransitionsAsV2AndActivationAsV1() {
        MerchantCommandResult active = harness.active("entity-schema");
        MerchantCommandResult paused = harness.execute(command(MerchantOperation.PAUSE_SHOP, "entity-schema-pause")
                .setShopId(active.getShopId()).setExpectedVersion(active.getShopVersion()).setReason("maintenance"));
        harness.execute(command(MerchantOperation.RESUME_SHOP, "entity-schema-resume-shop")
                .setShopId(active.getShopId()).setExpectedVersion(paused.getShopVersion()));
        MerchantCommandResult suspended = harness.execute(command(MerchantOperation.SUSPEND_MERCHANT,
                        "entity-schema-suspend")
                .setMerchantId(active.getMerchantId()).setExpectedVersion(active.getMerchantVersion())
                .setReason("compliance"));
        harness.execute(command(MerchantOperation.RESUME_MERCHANT, "entity-schema-resume-merchant")
                .setMerchantId(active.getMerchantId()).setExpectedVersion(suspended.getMerchantVersion()));

        assertThat(harness.events).filteredOn(event -> "merchant.entity.status_changed".equals(event.getEventType())
                        && ("SUSPENDED".equals(event.getPayload().get("previous_status"))
                        || "PAUSED".equals(event.getPayload().get("previous_status"))
                        || "SUSPENDED".equals(event.getPayload().get("current_status"))
                        || "PAUSED".equals(event.getPayload().get("current_status"))))
                .hasSize(4).allSatisfy(event -> assertThat(event.getSchemaVersion()).isEqualTo(2));
        assertThat(harness.events).filteredOn(event -> "merchant.entity.status_changed".equals(event.getEventType())
                        && ("PENDING_ACTIVATION".equals(event.getPayload().get("previous_status"))
                        || "DRAFT".equals(event.getPayload().get("previous_status")))
                        && "ACTIVE".equals(event.getPayload().get("current_status")))
                .hasSize(2).allSatisfy(event -> assertThat(event.getSchemaVersion()).isEqualTo(1));
    }

    private static MerchantCommand command(MerchantOperation operation, String key) {
        return new MerchantCommand().setOperation(operation).setIdempotencyKey(key).setRunId("run-merchant")
                .setSourceSystem("CLOUDMOLD").setTraceId("trace-merchant").setOccurredAt(Instant.parse("2026-07-15T08:00:00Z"));
    }

    private static MerchantCommand transition(MerchantOperation operation, String key, MerchantCommandResult previous) {
        return command(operation, key).setApplicationId(previous.getApplicationId())
                .setExpectedVersion(previous.getApplicationVersion());
    }

    private static SourceReference source(String system, String type, String id, Instant effectiveAt) {
        return new SourceReference().setSourceSystem(system).setSourceType(type).setSourceId(id)
                .setEffectiveAt(effectiveAt);
    }

    private static MerchantCommand sourceLink(String key, String sourceSystem, String sourceType, String sourceId,
                                               String targetType, String targetId) {
        return command(MerchantOperation.LINK_SOURCE, key)
                .setSourceReference(source(sourceSystem, sourceType, sourceId, null))
                .setTargetType(targetType).setTargetId(targetId)
                .setValidFrom(Instant.parse("2026-07-15T07:00:00Z"))
                .setVerificationRef("ticket:" + key).setMigrationRunId("migration-v17");
    }

    private static final class Harness {
        final MerchantStoreMapper mapper = mock(MerchantStoreMapper.class);
        final PrincipalValidationApi principalValidationApi = mock(PrincipalValidationApi.class);
        final OutboxAppender outboxAppender = mock(OutboxAppender.class);
        final ListingUnpublishSagaCommandApi listingUnpublishSagaCommandApi = mock(ListingUnpublishSagaCommandApi.class);
        final MerchantCommandServiceImpl service = new MerchantCommandServiceImpl(mapper, principalValidationApi,
                outboxAppender, listingUnpublishSagaCommandApi);
        final AtomicLong sequence = new AtomicLong();
        final ThreadLocal<Long> lastOperation = new ThreadLocal<>();
        final Map<String, Long> operationByKey = new HashMap<>();
        final Map<Long, MerchantOperationDO> operations = new HashMap<>();
        final Map<String, MerchantOnboardingApplicationDO> applications = new HashMap<>();
        final Map<String, MerchantLegalEntityDO> legalEntities = new HashMap<>();
        final Map<String, MerchantAccountDO> merchants = new HashMap<>();
        final Map<String, MerchantShopDO> shops = new HashMap<>();
        final List<MerchantOperatorAssignmentDO> assignments = new ArrayList<>();
        final List<MerchantStatusHistoryDO> histories = new ArrayList<>();
        final List<AppendDomainEventCommand> events = new ArrayList<>();
        final List<ListingUnpublishSagaCommand> unpublishSagaCommands = new ArrayList<>();
        final Map<String, MerchantSourceMappingDO> sourceMappings = new LinkedHashMap<>();

        Harness() {
            stubOperations();
            doAnswer(invocation -> { applications.put(invocation.<MerchantOnboardingApplicationDO>getArgument(0).getApplicationId(), invocation.getArgument(0)); return 1; }).when(mapper).insertApplication(any());
            doAnswer(invocation -> { legalEntities.put(invocation.<MerchantLegalEntityDO>getArgument(0).getLegalEntityId(), invocation.getArgument(0)); return 1; }).when(mapper).insertLegalEntity(any());
            doAnswer(invocation -> { merchants.put(invocation.<MerchantAccountDO>getArgument(0).getMerchantId(), invocation.getArgument(0)); return 1; }).when(mapper).insertMerchant(any());
            doAnswer(invocation -> { shops.put(invocation.<MerchantShopDO>getArgument(0).getShopId(), invocation.getArgument(0)); return 1; }).when(mapper).insertShop(any());
            doAnswer(invocation -> { assignments.add(invocation.getArgument(0)); return 1; }).when(mapper).insertAssignment(any());
            doAnswer(invocation -> { histories.add(invocation.getArgument(0)); return 1; }).when(mapper).insertHistory(any());
            doAnswer(invocation -> {
                MerchantSourceMappingDO value = invocation.getArgument(0);
                sourceMappings.put(value.getMappingId(), value);
                return 1;
            }).when(mapper).insertSourceMapping(any());
            when(mapper.selectApplicationForUpdate(eq(7L), anyString())).thenAnswer(i -> applications.get(i.getArgument(1)));
            when(mapper.selectLegalEntityForUpdate(eq(7L), anyString())).thenAnswer(i -> legalEntities.get(i.getArgument(1)));
            when(mapper.selectMerchantForUpdate(eq(7L), anyString())).thenAnswer(i -> merchants.get(i.getArgument(1)));
            when(mapper.selectShopForUpdate(eq(7L), anyString())).thenAnswer(i -> shops.get(i.getArgument(1)));
            when(mapper.selectSourceMappingForUpdate(eq(7L), anyString()))
                    .thenAnswer(i -> sourceMappings.get(i.getArgument(1)));
            when(mapper.selectActiveSourceMappingsForUpdate(eq(7L), anyString(), anyString(), anyString()))
                    .thenAnswer(i -> activeSourceMappings(7L, i.getArgument(1), i.getArgument(2), i.getArgument(3)));
            when(mapper.selectActiveSourceMappings(anyLong(), anyString(), anyString(), anyString(), any()))
                    .thenAnswer(i -> activeSourceMappings(i.getArgument(0), i.getArgument(1), i.getArgument(2),
                            i.getArgument(3), i.getArgument(4)));
            when(mapper.selectActiveMerchant(eq(7L), anyString()))
                    .thenAnswer(i -> activeMerchant(i.getArgument(1)));
            when(mapper.transitionApplication(anyLong(), anyString(), anyLong(), anyString(), anyString(), any(), any()))
                    .thenAnswer(i -> transitionApplication(i.getArgument(1), i.getArgument(2), i.getArgument(3), i.getArgument(4), i.getArgument(5)));
            when(mapper.transitionMerchant(anyLong(), anyString(), anyLong(), anyString(), anyString(), any()))
                    .thenAnswer(i -> transitionMerchant(i.getArgument(1), i.getArgument(2), i.getArgument(3), i.getArgument(4)));
            when(mapper.transitionShop(anyLong(), anyString(), anyLong(), anyString(), anyString(), any()))
                    .thenAnswer(i -> transitionShop(i.getArgument(1), i.getArgument(2), i.getArgument(3), i.getArgument(4)));
            when(mapper.transitionLegalEntity(anyLong(), anyString(), anyLong(), anyString(), anyString(), any()))
                    .thenAnswer(i -> transitionLegalEntity(i.getArgument(1), i.getArgument(2), i.getArgument(3), i.getArgument(4)));
            when(mapper.attachApprovedEntities(anyLong(), anyString(), anyLong(), anyString(), anyString(), anyString(), any()))
                    .thenAnswer(i -> attachApprovedEntities(i.getArgument(1), i.getArgument(2), i.getArgument(3),
                            i.getArgument(4), i.getArgument(5)));
            when(mapper.revokeSourceMapping(anyLong(), anyString(), anyLong(), any(), any()))
                    .thenAnswer(i -> revokeSourceMapping(i.getArgument(0), i.getArgument(1), i.getArgument(2),
                            i.getArgument(3), i.getArgument(4)));
            when(mapper.selectActiveReference(eq(7L), anyString(), anyString())).thenAnswer(i -> activeReference(i.getArgument(1), i.getArgument(2)));
            when(mapper.selectActiveAssignment(eq(7L), anyString(), anyString(), anyString(), anyString()))
                    .thenAnswer(i -> activeAssignment(i.getArgument(1), i.getArgument(2), i.getArgument(3), i.getArgument(4)));
            doAnswer(i -> { events.add(i.getArgument(0)); return null; }).when(outboxAppender).append(any());
            when(listingUnpublishSagaCommandApi.execute(any())).thenAnswer(i -> {
                ListingUnpublishSagaCommand command = i.getArgument(0);
                unpublishSagaCommands.add(command);
                return ListingUnpublishSagaView.builder()
                        .sagaId("listing-unpublish-saga-test")
                        .expectedListingCount(2)
                        .build();
            });
        }

        MerchantCommandResult execute(MerchantCommand command) { return service.execute(command); }

        MerchantCommandResult approved(String prefix) {
            MerchantCommandResult result = execute(command(MerchantOperation.CREATE_ONBOARDING_DRAFT, prefix + "-1")
                    .setLegalName("Y Shop").setRegistrationHashToken("sha256:r")
                    .setOwnerPrincipalId("principal-owner").setChannelCode("YSHOPPING").setExternalShopId(prefix));
            result = execute(transition(MerchantOperation.SUBMIT_ONBOARDING, prefix + "-2", result));
            result = execute(transition(MerchantOperation.START_ONBOARDING_REVIEW, prefix + "-3", result));
            return execute(transition(MerchantOperation.APPROVE_ONBOARDING, prefix + "-4", result));
        }

        MerchantCommandResult active(String prefix) {
            MerchantCommandResult approved = approved(prefix);
            MerchantCommandResult merchant = execute(command(MerchantOperation.ACTIVATE_MERCHANT, prefix + "-5")
                    .setMerchantId(approved.getMerchantId()).setExpectedVersion(approved.getMerchantVersion()));
            MerchantCommandResult shop = execute(command(MerchantOperation.ACTIVATE_SHOP, prefix + "-6")
                    .setShopId(approved.getShopId()).setExpectedVersion(approved.getShopVersion()));
            return shop.setMerchantVersion(merchant.getMerchantVersion());
        }

        private void stubOperations() {
            doAnswer(i -> {
                String key = i.getArgument(1); String hash = i.getArgument(3); String attempt = i.getArgument(4);
                Long id = operationByKey.get(key);
                if (id == null) {
                    id = sequence.incrementAndGet(); operationByKey.put(key, id);
                    operations.put(id, new MerchantOperationDO().setOperationId(id).setTenantId(7L)
                            .setIdempotencyKey(key).setCommandType(i.getArgument(2)).setRequestHash(hash)
                            .setAttemptToken(attempt).setStatus(0));
                }
                lastOperation.set(id); return 1;
            }).when(mapper).insertOrResolveOperation(anyLong(), anyString(), anyString(), anyString(), anyString(), any());
            when(mapper.selectLastInsertId()).thenAnswer(i -> lastOperation.get());
            when(mapper.selectOperationForUpdate(anyLong(), eq(7L))).thenAnswer(i -> operations.get(i.getArgument(0)));
            when(mapper.markOperationSucceeded(anyLong(), eq(7L), anyString(), anyString(), any()))
                    .thenAnswer(i -> { MerchantOperationDO op = operations.get(i.getArgument(0)); op.setStatus(10).setAggregateId(i.getArgument(2)).setResultJson(i.getArgument(3)); return 1; });
        }

        private int transitionApplication(String id, Long version, String before, String after, String reason) {
            MerchantOnboardingApplicationDO value = applications.get(id);
            if (value == null || !Objects.equals(value.getVersion(), version) || !before.equals(value.getStatus())) return 0;
            return 1;
        }

        private int transitionMerchant(String id, Long version, String before, String after) {
            MerchantAccountDO value = merchants.get(id);
            if (value == null || !Objects.equals(value.getVersion(), version) || !before.equals(value.getStatus())) return 0;
            return 1;
        }

        private int transitionLegalEntity(String id, Long version, String before, String after) {
            MerchantLegalEntityDO value = legalEntities.get(id);
            if (value == null || !Objects.equals(value.getVersion(), version) || !before.equals(value.getStatus())) return 0;
            return 1;
        }

        private int attachApprovedEntities(String applicationId, Long version, String merchantId,
                                           String shopId, String assignmentId) {
            MerchantOnboardingApplicationDO value = applications.get(applicationId);
            if (value == null || !Objects.equals(value.getVersion(), version) || !"APPROVED".equals(value.getStatus())
                    || value.getMerchantId() != null || value.getShopId() != null || value.getOwnerAssignmentId() != null) return 0;
            return 1;
        }

        private int transitionShop(String id, Long version, String before, String after) {
            MerchantShopDO value = shops.get(id);
            if (value == null || !Objects.equals(value.getVersion(), version) || !before.equals(value.getStatus())) return 0;
            return 1;
        }

        private MerchantReferenceView activeReference(String merchantId, String shopId) {
            MerchantAccountDO merchant = merchants.get(merchantId); MerchantShopDO shop = shops.get(shopId);
            if (merchant == null || shop == null || !merchantId.equals(shop.getMerchantId())
                    || !"ACTIVE".equals(merchant.getStatus()) || !"ACTIVE".equals(shop.getStatus())) return null;
            return new MerchantReferenceView().setMerchantId(merchantId).setMerchantStatus(merchant.getStatus())
                    .setShopId(shopId).setShopStatus(shop.getStatus()).setChannelCode(shop.getChannelCode());
        }

        private MerchantOwnerView activeMerchant(String merchantId) {
            MerchantAccountDO merchant = merchants.get(merchantId);
            if (merchant == null || !"ACTIVE".equals(merchant.getStatus())) return null;
            return new MerchantOwnerView().setMerchantId(merchantId).setMerchantStatus(merchant.getStatus())
                    .setLegalEntityId(merchant.getLegalEntityId());
        }

        private List<MerchantSourceMappingDO> activeSourceMappings(Long tenantId, String sourceSystem,
                                                                    String sourceType, String sourceId) {
            return sourceMappings.values().stream().filter(mapping -> Objects.equals(tenantId, mapping.getTenantId())
                            && sourceSystem.equals(mapping.getSourceSystem()) && sourceType.equals(mapping.getSourceType())
                            && sourceId.equals(mapping.getSourceId()) && "ACTIVE".equals(mapping.getStatus()))
                    .toList();
        }

        private List<MerchantSourceMappingDO> activeSourceMappings(Long tenantId, String sourceSystem,
                                                                   String sourceType, String sourceId,
                                                                   java.time.LocalDateTime effectiveAt) {
            return activeSourceMappings(tenantId, sourceSystem, sourceType, sourceId).stream()
                    .filter(mapping -> !mapping.getValidFrom().isAfter(effectiveAt)
                            && (mapping.getValidTo() == null || mapping.getValidTo().isAfter(effectiveAt)))
                    .toList();
        }

        private int revokeSourceMapping(Long tenantId, String mappingId, Long version,
                                        java.time.LocalDateTime validTo, java.time.LocalDateTime now) {
            MerchantSourceMappingDO mapping = sourceMappings.get(mappingId);
            if (mapping == null || !Objects.equals(tenantId, mapping.getTenantId())
                    || !Objects.equals(version, mapping.getVersion()) || !"ACTIVE".equals(mapping.getStatus())) return 0;
            return 1;
        }

        private MerchantOperatorAuthorizationView activeAssignment(String merchantId, String shopId,
                                                                      String principalId, String roleCode) {
            return assignments.stream().filter(a -> merchantId.equals(a.getMerchantId()) && shopId.equals(a.getShopId())
                            && principalId.equals(a.getPrincipalId()) && roleCode.equals(a.getRoleCode())
                            && "ACTIVE".equals(a.getStatus()))
                    .map(a -> new MerchantOperatorAuthorizationView().setAssignmentId(a.getAssignmentId())
                            .setMerchantId(merchantId).setShopId(shopId).setPrincipalId(principalId)
                            .setRoleCode(roleCode).setAssignmentStatus(a.getStatus())).findFirst().orElse(null);
        }
    }
}
