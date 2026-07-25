package cn.iocoder.yudao.module.cloudmold.appcommerce.service;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.appcommerce.dal.dataobject.AppCartDO;
import cn.iocoder.yudao.module.cloudmold.appcommerce.dal.dataobject.AppCartItemDO;
import cn.iocoder.yudao.module.cloudmold.appcommerce.dal.mysql.AppCartItemMapper;
import cn.iocoder.yudao.module.cloudmold.appcommerce.dal.mysql.AppCartMapper;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuProjectionApi;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventorySkuAvailabilityView;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryV3AvailabilityQueryApi;
import cn.iocoder.yudao.module.cloudmold.listing.api.ListingOfferView;
import cn.iocoder.yudao.module.cloudmold.listing.api.ListingQueryApi;
import cn.iocoder.yudao.module.cloudmold.listing.api.PublishedListingView;
import cn.iocoder.yudao.module.cloudmold.listing.api.PublishedOfferValidationCommand;
import cn.iocoder.yudao.module.cloudmold.quality.api.QualityConsumerEvidenceApi;
import cn.iocoder.yudao.module.cloudmold.quality.api.QualityConsumerEvidenceView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AppCartService {

    private static final int MAX_LINE_COUNT = 50;
    private static final int MAX_QTY = 99;
    private static final String CURRENCY_CNY = "CNY";

    private final AppMemberPrincipalResolver principalResolver;
    private final AppCartMapper cartMapper;
    private final AppCartItemMapper cartItemMapper;
    private final CatalogSkuProjectionApi catalogSkuProjectionApi;
    private final cn.iocoder.yudao.module.cloudmold.listing.api.AppListingQueryApi appListingQueryApi;
    private final ListingQueryApi listingQueryApi;
    private final InventoryV3AvailabilityQueryApi inventoryAvailabilityQueryApi;
    private final QualityConsumerEvidenceApi qualityConsumerEvidenceApi;
    private final AppFacadeOperationService facadeOperationService;
    private final AppCheckoutService checkoutService;
    private final OutboxAppender outboxAppender;

    public AppCartView get() {
        AppMemberPrincipalView principal = principalResolver.requireCurrent();
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        AppCartDO cart = cartMapper.selectOwned(tenantId, principal.getPrincipalId());
        List<AppCartItemDO> lines = cart == null ? List.of() : cartItemMapper.selectByCart(tenantId, cart.getCartId());
        return toView(principal, cart, lines, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public AppCartView addOrMerge(String idempotencyKey, Long expectedCartVersion, String listingId,
                                  String listingOfferId, String canonicalSkuId, int quantity, Boolean selected) {
        requireKey(idempotencyKey);
        requireVersion(expectedCartVersion);
        requireQuantity(quantity);
        requireNotBlank(listingId, "listingId is required");
        requireNotBlank(listingOfferId, "listingOfferId is required");
        requireNotBlank(canonicalSkuId, "canonicalSkuId is required");
        boolean selectedValue = selected == null || selected;
        AppMemberPrincipalView principal = principalResolver.requireCurrent();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("expectedCartVersion", expectedCartVersion);
        payload.put("listingId", listingId);
        payload.put("listingOfferId", listingOfferId);
        payload.put("canonicalSkuId", canonicalSkuId);
        payload.put("quantity", quantity);
        payload.put("selected", selectedValue);
        return replayView(facadeOperationService.execute("CART_ADD", idempotencyKey, principal.getPrincipalId(),
                payload, AppCartView.class,
                occurredAt -> addOrMergeOnce(principal, expectedCartVersion, listingId, listingOfferId,
                        canonicalSkuId, quantity, selectedValue, occurredAt)));
    }

    @Transactional(rollbackFor = Exception.class)
    public AppCartView updateQuantity(String idempotencyKey, Long expectedCartVersion,
                                      String lineId, int quantity) {
        requireKey(idempotencyKey);
        requireVersion(expectedCartVersion);
        requireQuantity(quantity);
        requireNotBlank(lineId, "lineId is required");
        AppMemberPrincipalView principal = principalResolver.requireCurrent();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("expectedCartVersion", expectedCartVersion);
        payload.put("lineId", lineId);
        payload.put("quantity", quantity);
        return replayView(facadeOperationService.execute("CART_QTY", idempotencyKey, principal.getPrincipalId(),
                payload, AppCartView.class,
                occurredAt -> updateQuantityOnce(principal, expectedCartVersion, lineId, quantity, occurredAt)));
    }

    @Transactional(rollbackFor = Exception.class)
    public AppCartView updateSelection(String idempotencyKey, Long expectedCartVersion,
                                       String lineId, boolean selected) {
        requireKey(idempotencyKey);
        requireVersion(expectedCartVersion);
        requireNotBlank(lineId, "lineId is required");
        AppMemberPrincipalView principal = principalResolver.requireCurrent();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("expectedCartVersion", expectedCartVersion);
        payload.put("lineId", lineId);
        payload.put("selected", selected);
        return replayView(facadeOperationService.execute("CART_SELECT", idempotencyKey, principal.getPrincipalId(),
                payload, AppCartView.class,
                occurredAt -> updateSelectionOnce(principal, expectedCartVersion, lineId, selected, occurredAt)));
    }

    @Transactional(rollbackFor = Exception.class)
    public AppCartView removeLine(String idempotencyKey, Long expectedCartVersion, String lineId) {
        requireKey(idempotencyKey);
        requireVersion(expectedCartVersion);
        requireNotBlank(lineId, "lineId is required");
        AppMemberPrincipalView principal = principalResolver.requireCurrent();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("expectedCartVersion", expectedCartVersion);
        payload.put("lineId", lineId);
        return replayView(facadeOperationService.execute("CART_REMOVE", idempotencyKey, principal.getPrincipalId(),
                payload, AppCartView.class,
                occurredAt -> removeLineOnce(principal, expectedCartVersion, lineId, occurredAt)));
    }

    @Transactional(rollbackFor = Exception.class)
    public AppCartView clear(String idempotencyKey, Long expectedCartVersion) {
        requireKey(idempotencyKey);
        requireVersion(expectedCartVersion);
        AppMemberPrincipalView principal = principalResolver.requireCurrent();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("expectedCartVersion", expectedCartVersion);
        return replayView(facadeOperationService.execute("CART_CLEAR", idempotencyKey, principal.getPrincipalId(),
                payload, AppCartView.class,
                occurredAt -> clearOnce(principal, expectedCartVersion, occurredAt)));
    }

    @Transactional(rollbackFor = Exception.class)
    public AppCheckoutView previewSelected(String idempotencyKey, Long expectedCartVersion, String addressRef) {
        requireKey(idempotencyKey);
        requireVersion(expectedCartVersion);
        requireNotBlank(addressRef, "addressRef is required");
        AppMemberPrincipalView principal = principalResolver.requireCurrent();
        MutableCart cart = loadMutableCart(principal, expectedCartVersion);
        List<AppCartItemDO> selectedLines = cart.lines().stream()
                .filter(line -> Boolean.TRUE.equals(line.getSelected()))
                .toList();
        require(selectedLines.size() == 1,
                selectedLines.isEmpty()
                        ? "cart checkout requires exactly one selected line"
                        : "cart checkout does not support multi-select");
        AppCartItemDO line = selectedLines.get(0);
        return checkoutService.preview(idempotencyKey, line.getListingId(), line.getListingOfferId(),
                line.getCanonicalSkuId(), line.getQuantity().intValueExact(), addressRef);
    }

    private AppCartView addOrMergeOnce(AppMemberPrincipalView principal, Long expectedCartVersion,
                                       String listingId, String listingOfferId, String canonicalSkuId,
                                       int quantity, boolean selected, Instant occurredAt) {
        MutableCart cart = loadMutableCart(principal, expectedCartVersion);
        AppCartItemDO existing = cartItemMapper.selectByIdentity(cart.tenantId(), cart.cart().getCartId(),
                listingId, listingOfferId, canonicalSkuId);
        int targetQty = quantity;
        if (existing != null) {
            targetQty = Math.addExact(existing.getQuantity().intValueExact(), quantity);
            require(targetQty <= MAX_QTY, "cart line quantity must stay between 1 and 99");
        } else {
            require(cart.lines().size() < MAX_LINE_COUNT, "cart cannot contain more than 50 lines");
        }
        requireSellable(listingId, listingOfferId, canonicalSkuId, targetQty);
        LocalDateTime now = utcNow();
        if (existing == null) {
            cartItemMapper.insert(new AppCartItemDO()
                    .setLineId(UUID.randomUUID().toString())
                    .setTenantId(cart.tenantId())
                    .setCartId(cart.cart().getCartId())
                    .setBuyerPrincipalId(principal.getPrincipalId())
                    .setListingId(listingId)
                    .setListingOfferId(listingOfferId)
                    .setCanonicalSkuId(canonicalSkuId)
                    .setQuantity(BigDecimal.valueOf(targetQty))
                    .setSelected(selected)
                    .setCreatedAt(now)
                    .setUpdatedAt(now));
        } else if (existing.getQuantity().intValueExact() != targetQty || !Objects.equals(existing.getSelected(), selected)) {
            cartItemMapper.updateQuantityAndSelection(cart.tenantId(), cart.cart().getCartId(), existing.getLineId(),
                    BigDecimal.valueOf(targetQty), selected, now);
        } else {
            return toView(principal, cart.cart(), cart.lines(), false);
        }
        return finalizeMutation(principal, cart.cart(), "ADD_OR_MERGE", occurredAt);
    }

    private AppCartView updateQuantityOnce(AppMemberPrincipalView principal, Long expectedCartVersion,
                                           String lineId, int quantity, Instant occurredAt) {
        MutableCart cart = loadMutableCart(principal, expectedCartVersion);
        AppCartItemDO line = requireOwnedLine(cart, lineId);
        requireSellable(line.getListingId(), line.getListingOfferId(), line.getCanonicalSkuId(), quantity);
        if (line.getQuantity().intValueExact() == quantity) {
            return toView(principal, cart.cart(), cart.lines(), false);
        }
        cartItemMapper.updateQuantityAndSelection(cart.tenantId(), cart.cart().getCartId(), lineId,
                BigDecimal.valueOf(quantity), line.getSelected(), utcNow());
        return finalizeMutation(principal, cart.cart(), "UPDATE_QTY", occurredAt);
    }

    private AppCartView updateSelectionOnce(AppMemberPrincipalView principal, Long expectedCartVersion,
                                            String lineId, boolean selected, Instant occurredAt) {
        MutableCart cart = loadMutableCart(principal, expectedCartVersion);
        AppCartItemDO line = requireOwnedLine(cart, lineId);
        if (Objects.equals(line.getSelected(), selected)) {
            return toView(principal, cart.cart(), cart.lines(), false);
        }
        cartItemMapper.updateSelection(cart.tenantId(), cart.cart().getCartId(), lineId, selected, utcNow());
        return finalizeMutation(principal, cart.cart(), "UPDATE_SELECTION", occurredAt);
    }

    private AppCartView removeLineOnce(AppMemberPrincipalView principal, Long expectedCartVersion,
                                       String lineId, Instant occurredAt) {
        MutableCart cart = loadMutableCart(principal, expectedCartVersion);
        requireOwnedLine(cart, lineId);
        cartItemMapper.deleteLine(cart.tenantId(), cart.cart().getCartId(), lineId);
        return finalizeMutation(principal, cart.cart(), "REMOVE_LINE", occurredAt);
    }

    private AppCartView clearOnce(AppMemberPrincipalView principal, Long expectedCartVersion, Instant occurredAt) {
        MutableCart cart = loadMutableCart(principal, expectedCartVersion);
        if (cart.lines().isEmpty()) {
            return toView(principal, cart.cart(), List.of(), false);
        }
        cartItemMapper.deleteByCart(cart.tenantId(), cart.cart().getCartId());
        return finalizeMutation(principal, cart.cart(), "CLEAR", occurredAt);
    }

    private AppCartView finalizeMutation(AppMemberPrincipalView principal, AppCartDO cart,
                                         String operation, Instant occurredAt) {
        Long nextVersion = cart.getVersion() + 1;
        require(cartMapper.updateVersion(TenantContextHolder.getRequiredTenantId(), cart.getCartId(),
                        cart.getVersion(), nextVersion, utcNow()) == 1,
                "cart version transition conflict");
        cart.setVersion(nextVersion);
        List<AppCartItemDO> lines = cartItemMapper.selectByCart(TenantContextHolder.getRequiredTenantId(),
                cart.getCartId());
        appendChanged(cart, lines, operation, occurredAt);
        return toView(principal, cart, lines, false);
    }

    private MutableCart loadMutableCart(AppMemberPrincipalView principal, Long expectedCartVersion) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String cartId = deterministicCartId(tenantId, principal.getPrincipalId());
        cartMapper.insertIgnore(cartId, tenantId, principal.getPrincipalId(), utcNow());
        AppCartDO cart = cartMapper.selectOwnedForUpdate(tenantId, principal.getPrincipalId());
        require(cart != null, "cart does not exist");
        require(Objects.equals(cart.getVersion(), expectedCartVersion), "cart version conflict");
        return new MutableCart(tenantId, cart, cartItemMapper.selectByCart(tenantId, cartId));
    }

    private AppCartItemDO requireOwnedLine(MutableCart cart, String lineId) {
        AppCartItemDO line = cartItemMapper.selectOwnedLine(cart.tenantId(), cart.cart().getCartId(), lineId);
        require(line != null && Objects.equals(line.getBuyerPrincipalId(), cart.cart().getBuyerPrincipalId()),
                "cart line does not exist");
        return line;
    }

    private void requireSellable(String listingId, String listingOfferId, String canonicalSkuId, int quantity) {
        LineState state = resolveLineState(listingId, listingOfferId, canonicalSkuId, BigDecimal.valueOf(quantity));
        require(state.invalidReasonCode() == null,
                state.invalidReasonMessage() == null ? "cart line is not sellable" : state.invalidReasonMessage());
    }

    private AppCartView replayView(AppFacadeOperationService.Replay<AppCartView> replay) {
        replay.value().setDuplicate(Boolean.TRUE.equals(replay.value().getDuplicate()) || replay.duplicate());
        return replay.value();
    }

    private AppCartView toView(AppMemberPrincipalView principal, AppCartDO cart, List<AppCartItemDO> lines,
                               boolean duplicate) {
        String principalId = principal.getPrincipalId();
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String cartId = cart == null ? deterministicCartId(tenantId, principalId) : cart.getCartId();
        Long version = cart == null ? 0L : cart.getVersion();
        List<AppCartView.Line> views = lines.stream().map(this::toLineView).toList();
        int selectedCount = (int) views.stream().filter(line -> Boolean.TRUE.equals(line.getSelected())).count();
        return AppCartView.builder()
                .cartId(cartId)
                .principalId(principalId)
                .aggregateVersion(version)
                .lineCount(views.size())
                .selectedLineCount(selectedCount)
                .duplicate(duplicate)
                .lines(views)
                .build();
    }

    private AppCartView.Line toLineView(AppCartItemDO line) {
        LineState state = resolveLineState(line.getListingId(), line.getListingOfferId(),
                line.getCanonicalSkuId(), line.getQuantity());
        Long unitPrice = state.offer() == null ? null : state.offer().getPriceMinor();
        Long productAmount = unitPrice == null ? null
                : Math.multiplyExact(unitPrice, line.getQuantity().intValueExact());
        String currencyCode = state.offer() == null ? null : state.offer().getCurrencyCode();
        String title = state.listing() == null ? null : state.listing().getTitle();
        String image = state.listing() == null ? null : state.listing().getPrimaryImageUrl();
        BigDecimal available = state.inventory() == null || state.inventory().getAllocatableQuantity() == null
                ? BigDecimal.ZERO : state.inventory().getAllocatableQuantity();
        String qualityStatus = state.quality() == null ? null : state.quality().getStatus();
        return AppCartView.Line.builder()
                .lineId(line.getLineId())
                .listingId(line.getListingId())
                .listingOfferId(line.getListingOfferId())
                .canonicalSkuId(line.getCanonicalSkuId())
                .title(title)
                .primaryImageUrl(image)
                .quantity(line.getQuantity())
                .selected(line.getSelected())
                .valid(state.invalidReasonCode() == null)
                .invalidReasonCode(state.invalidReasonCode())
                .currentUnitPriceMinor(unitPrice)
                .currentProductAmountMinor(productAmount)
                .currencyCode(currencyCode)
                .availableQuantity(available)
                .qualityStatus(qualityStatus)
                .build();
    }

    private LineState resolveLineState(String listingId, String listingOfferId, String canonicalSkuId,
                                       BigDecimal quantity) {
        PublishedListingView listing;
        try {
            listing = appListingQueryApi.requirePublished(listingId);
        } catch (Exception ignored) {
            return new LineState(null, null, null, null,
                    "LISTING_NOT_PUBLISHED", "listing is no longer published");
        }
        ListingOfferView offer = listing.getOffers() == null ? null : listing.getOffers().stream()
                .filter(candidate -> Objects.equals(candidate.getListingOfferId(), listingOfferId)
                        && Objects.equals(candidate.getCanonicalSkuId(), canonicalSkuId)
                        && Boolean.TRUE.equals(candidate.getEnabled()))
                .findFirst().orElse(null);
        if (offer == null) {
            return new LineState(listing, null, null, null,
                    "LISTING_OFFER_UNAVAILABLE", "listing offer is no longer sellable");
        }
        if (!CURRENCY_CNY.equals(offer.getCurrencyCode())) {
            return new LineState(listing, offer, null, null,
                    "UNSUPPORTED_CURRENCY", "consumer cart supports CNY only");
        }
        try {
            listingQueryApi.requirePublishedOffer(PublishedOfferValidationCommand.builder()
                    .listingId(listingId)
                    .listingOfferId(listingOfferId)
                    .canonicalSkuId(canonicalSkuId)
                    .expectedPriceMinor(offer.getPriceMinor())
                    .currencyCode(offer.getCurrencyCode())
                    .build());
        } catch (Exception ex) {
            return new LineState(listing, offer, null, null,
                    "LISTING_OFFER_INVALID", defaultMessage(ex, "listing offer validation failed"));
        }
        if (catalogSkuProjectionApi.getActiveSku(canonicalSkuId) == null) {
            return new LineState(listing, offer, null, null,
                    "CATALOG_SKU_INACTIVE", "canonical SKU is inactive");
        }
        QualityConsumerEvidenceView quality = qualityConsumerEvidenceApi.getLatestBySku(canonicalSkuId);
        if (quality == null) {
            return new LineState(listing, offer, null, null,
                    "QUALITY_EVIDENCE_MISSING", "canonical SKU quality evidence is unavailable");
        }
        if (!"VERIFIED".equals(quality.getStatus())) {
            return new LineState(listing, offer, null, quality,
                    "QUALITY_NOT_VERIFIED", "canonical SKU lacks approved consumer quality evidence");
        }
        InventorySkuAvailabilityView inventory = inventoryAvailabilityQueryApi.getBySku(canonicalSkuId, Instant.now());
        if (inventory == null || inventory.getAllocatableQuantity() == null) {
            return new LineState(listing, offer, inventory, quality,
                    "INVENTORY_UNAVAILABLE", "inventory availability is unavailable");
        }
        if (inventory.getAllocatableQuantity().compareTo(quantity) < 0) {
            return new LineState(listing, offer, inventory, quality,
                    "INSUFFICIENT_ALLOCATABLE_INVENTORY", "insufficient allocatable inventory");
        }
        return new LineState(listing, offer, inventory, quality, null, null);
    }

    private void appendChanged(AppCartDO cart, List<AppCartItemDO> lines, String operation, Instant occurredAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("buyer_principal_id", cart.getBuyerPrincipalId());
        payload.put("line_count", lines.size());
        payload.put("selected_line_count", lines.stream().filter(line -> Boolean.TRUE.equals(line.getSelected())).count());
        payload.put("lines", lines.stream().map(line -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("line_id", line.getLineId());
            item.put("listing_id", line.getListingId());
            item.put("listing_offer_id", line.getListingOfferId());
            item.put("canonical_sku_id", line.getCanonicalSkuId());
            item.put("quantity", line.getQuantity());
            item.put("selected", line.getSelected());
            return item;
        }).toList());
        outboxAppender.append(AppendDomainEventCommand.builder()
                .eventType("app.cart.changed")
                .schemaVersion(1)
                .sourceSystem("cloudmold-app-commerce")
                .tenantId(cart.getTenantId())
                .aggregateType("app_cart")
                .aggregateId(cart.getCartId())
                .aggregateVersion(cart.getVersion())
                .eventSequence((short) 1)
                .occurredAt(occurredAt)
                .correlationId(cart.getCartId())
                .causationId(operation + ":" + cart.getVersion())
                .idempotencyKey("app-cart:" + cart.getCartId() + ":v" + cart.getVersion())
                .payload(payload)
                .headers(Map.of("operation", operation, "pii_safe", true))
                .destination("lakehouse")
                .build());
    }

    static String deterministicCartId(Long tenantId, String principalId) {
        require(tenantId != null, "tenantId is required");
        requireNotBlank(principalId, "principalId is required");
        return UUID.nameUUIDFromBytes((tenantId + "|" + principalId).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private static String defaultMessage(Exception ex, String fallback) {
        return ex == null || !StrUtil.isNotBlank(ex.getMessage()) ? fallback : ex.getMessage();
    }

    private static void requireQuantity(int quantity) {
        require(quantity >= 1 && quantity <= MAX_QTY, "quantity must be between 1 and 99");
    }

    private static void requireVersion(Long expectedCartVersion) {
        require(expectedCartVersion != null && expectedCartVersion >= 0,
                "expectedCartVersion must be zero or positive");
    }

    private static void requireKey(String value) {
        require(value != null && value.length() >= 8 && value.length() <= 128,
                "idempotencyKey must contain 8 to 128 characters");
    }

    private static void requireNotBlank(String value, String message) {
        require(value != null && !value.isBlank(), message);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private record MutableCart(Long tenantId, AppCartDO cart, List<AppCartItemDO> lines) {}

    private record LineState(PublishedListingView listing, ListingOfferView offer,
                             InventorySkuAvailabilityView inventory, QualityConsumerEvidenceView quality,
                             String invalidReasonCode, String invalidReasonMessage) {}
}
