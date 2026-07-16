package cn.iocoder.yudao.module.cloudmold.gamification.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.gamification.api.*;
import cn.iocoder.yudao.module.cloudmold.gamification.dal.dataobject.GamificationRecords.*;
import cn.iocoder.yudao.module.cloudmold.gamification.dal.mysql.GamificationStoreMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class GamificationCommandServiceImpl implements GamificationCommandApi, GamificationQueryApi {
    static final int OPERATION_SUCCEEDED = 10;
    static final String GAME_CURRENCY_ASSET_CLASS = "GAME_VIRTUAL_CURRENCY";
    static final String GAME_FRAGMENT_ASSET_CLASS = "GAME_FRAGMENT";
    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{1,63}");
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{1,127}");
    private static final Pattern GAME_CURRENCY = Pattern.compile("GAME_COIN_[A-Z0-9_]{2,40}");
    private static final Pattern GAME_FRAGMENT = Pattern.compile("GAME_FRAGMENT_[A-Z0-9_]{2,40}");
    private static final Pattern EVIDENCE = Pattern.compile("sha256:[0-9a-f]{64}");
    private static final Set<String> ROUND_OUTCOMES = Set.of("WIN", "LOSE", "DRAW", "ABORTED");

    private final GamificationStoreMapper mapper;
    private final GamificationEventService eventService;
    private final GamificationRandomSource randomSource;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GamificationView execute(GamificationCommand command) {
        validateCommon(command);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Instant occurredAt = command.getOccurredAt() == null ? now.toInstant(ZoneOffset.UTC) : command.getOccurredAt();
        require(!occurredAt.isAfter(Instant.now().plusSeconds(300)), "occurredAt cannot be materially in the future");
        String hash = DigestUtil.sha256Hex(tenantId + "|" + JsonUtils.toJsonString(command));
        String token = UUID.randomUUID().toString();
        mapper.insertOrResolveOperation(tenantId, command.getIdempotencyKey(), command.getOperation().name(), hash,
                token, now);
        Long operationId = mapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve gamification operation");
        Operation operation = mapper.selectOperationForUpdate(operationId, tenantId);
        require(operation != null, "gamification operation disappeared");
        if (!token.equals(operation.getAttemptToken())) {
            require(Objects.equals(hash, operation.getRequestHash()),
                    "idempotency key conflicts with different gamification payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing gamification operation is incomplete");
            GamificationView replay = JsonUtils.parseObject(operation.getResultJson(), GamificationView.class);
            replay.setDuplicate(true);
            return replay;
        }

        GamificationView result = switch (command.getOperation()) {
            case CREATE_GAME -> createGame(tenantId, operationId, command, occurredAt, now);
            case PUBLISH_GAME_VERSION -> publishGameVersion(tenantId, operationId, command, occurredAt, now);
            case OPEN_PLAYER_ACCOUNT -> openPlayerAccount(tenantId, operationId, command, occurredAt, now);
            case OPEN_SESSION -> openSession(tenantId, operationId, command, occurredAt, now);
            case START_ROUND -> startRound(tenantId, operationId, command, occurredAt, now);
            case COMPLETE_ROUND -> completeRound(tenantId, operationId, command, occurredAt, now);
            case DEFINE_REWARD -> defineReward(tenantId, operationId, command, occurredAt, now);
            case DEFINE_DRAW_POOL -> defineDrawPool(tenantId, operationId, command, occurredAt, now);
            case GRANT_REWARD -> grantReward(tenantId, operationId, command, occurredAt, now);
            case DRAW -> draw(tenantId, operationId, command, occurredAt, now);
            case RECORD_ASSIST -> recordAssist(tenantId, operationId, command, occurredAt, now);
            case DEFINE_TASK -> defineTask(tenantId, operationId, command, occurredAt, now);
            case ADVANCE_TASK -> advanceTask(tenantId, operationId, command, occurredAt, now);
            case TRANSFER_GIFT -> transferGift(tenantId, operationId, command, occurredAt, now);
            case DEFINE_SEASON_SERIES -> defineSeasonSeries(tenantId, operationId, command, occurredAt, now);
            case DEFINE_SEASON -> defineSeason(tenantId, operationId, command, occurredAt, now);
            case DEFINE_COLLECTIBLE -> defineCollectible(tenantId, operationId, command, occurredAt, now);
            case GRANT_COLLECTIBLE -> grantCollectible(tenantId, operationId, command, occurredAt, now);
            case CREATE_REDEMPTION_INTENT -> createRedemption(tenantId, operationId, command, occurredAt, now);
            case RECORD_REDEMPTION_RESULT -> recordRedemptionResult(tenantId, operationId, command, occurredAt, now);
            case CREATE_REWARD_CLAIM -> createRewardClaim(tenantId, operationId, command, occurredAt, now);
            case CLAIM_REWARD -> claimReward(tenantId, operationId, command, occurredAt, now);
            case EXPIRE_REWARD_CLAIM -> expireRewardClaim(tenantId, operationId, command, occurredAt, now);
        };
        String aggregateId = firstNonNull(result.getRewardClaimId(), result.getRedemptionIntentId(),
                result.getCollectibleDefinitionId(), result.getSeasonId(), result.getSeasonSeriesId(),
                result.getGiftTransferId(), result.getTaskProgressId(),
                result.getAssistRecordId(), result.getDrawRequestId(), result.getRewardGrantId(), result.getRoundId(),
                result.getSessionId(), result.getAccountId(), result.getGameId());
        require(mapper.markOperationSucceeded(operationId, tenantId, aggregateId, JsonUtils.toJsonString(result), now) == 1,
                "gamification operation completion conflict");
        return result;
    }

    @Override
    public GamificationView getGame(String gameId) {
        requireId(gameId, "gameId");
        Game game = mapper.selectGame(TenantContextHolder.getRequiredTenantId(), gameId);
        require(game != null, "game does not exist");
        return gameView(null, game, false);
    }

    @Override
    public GamificationView getPlayerAccount(String gameId, String principalId) {
        requireId(gameId, "gameId");
        requireId(principalId, "principalId");
        Account account = mapper.selectAccountByOwner(TenantContextHolder.getRequiredTenantId(), gameId, "PLAYER",
                principalId);
        require(account != null, "player game-currency account does not exist");
        return accountView(null, account, false);
    }

    @Override
    public GamificationView getSession(String sessionId) {
        requireId(sessionId, "sessionId");
        Session session = mapper.selectSession(TenantContextHolder.getRequiredTenantId(), sessionId);
        require(session != null, "game session does not exist");
        return sessionView(null, session, false);
    }

    @Override
    public GamificationView getTaskProgress(String taskDefinitionId, Long taskVersion, String principalId) {
        requireId(taskDefinitionId, "taskDefinitionId");
        require(taskVersion != null && taskVersion > 0, "taskVersion must be positive");
        requireId(principalId, "principalId");
        TaskProgress progress = mapper.selectTaskProgress(TenantContextHolder.getRequiredTenantId(), taskDefinitionId,
                taskVersion, principalId);
        require(progress != null, "task progress does not exist");
        return taskView(null, progress, false);
    }

    @Override
    public GamificationView getFragmentBalance(String gameId, String principalId, String fragmentCode) {
        requireId(gameId, "gameId");
        requireId(principalId, "principalId");
        requireGameFragment(fragmentCode);
        FragmentBalance balance = mapper.selectFragmentBalance(TenantContextHolder.getRequiredTenantId(), gameId,
                principalId, fragmentCode);
        require(balance != null, "game fragment balance does not exist");
        return fragmentView(null, balance, false);
    }

    @Override
    public GamificationView getCollectibleOwnership(String gameId, String principalId, String definitionId,
                                                     Long definitionVersion) {
        requireId(gameId, "gameId"); requireId(principalId, "principalId"); requireId(definitionId, "definitionId");
        require(definitionVersion != null && definitionVersion > 0, "definitionVersion must be positive");
        CollectibleOwnership value = mapper.selectCollectibleOwnership(TenantContextHolder.getRequiredTenantId(),
                gameId, principalId, definitionId, definitionVersion);
        require(value != null, "collectible ownership does not exist");
        return collectibleView(null, value);
    }

    @Override
    public GamificationView getRewardClaim(String rewardClaimId) {
        requireId(rewardClaimId, "rewardClaimId");
        RewardClaim value = mapper.selectRewardClaim(TenantContextHolder.getRequiredTenantId(), rewardClaimId);
        require(value != null, "reward claim does not exist"); return claimView(null, value);
    }

    @Override
    public GamificationView getRedemption(String redemptionIntentId) {
        requireId(redemptionIntentId, "redemptionIntentId");
        RedemptionIntent value = mapper.selectRedemption(TenantContextHolder.getRequiredTenantId(), redemptionIntentId);
        require(value != null, "redemption does not exist"); return redemptionView(null, value);
    }

    @Override
    public GamificationView getSeasonSeries(String id, Long version) {
        requireId(id,"seasonSeriesId"); require(version!=null && version>0,"seriesVersion must be positive");
        SeasonSeries value=mapper.selectSeasonSeries(TenantContextHolder.getRequiredTenantId(),id,version);
        require(value!=null,"season-series version does not exist");
        return new GamificationView().setGameId(value.getGameId()).setSeasonSeriesId(id).setSeasonSeriesVersion(version);
    }

    @Override
    public GamificationView getSeason(String id, Long version) {
        requireId(id,"seasonId"); require(version!=null && version>0,"seasonVersion must be positive");
        Season value=mapper.selectSeason(TenantContextHolder.getRequiredTenantId(),id,version);
        require(value!=null,"season version does not exist");
        return new GamificationView().setGameId(value.getGameId()).setSeasonId(id).setSeasonVersion(version)
                .setSeasonSeriesId(value.getSeasonSeriesId()).setSeasonSeriesVersion(value.getSeriesVersion());
    }

    @Override
    public GamificationView getCollectibleDefinition(String id, Long version) {
        requireId(id,"collectibleDefinitionId"); require(version!=null && version>0,"collectibleVersion must be positive");
        CollectibleDefinition value=mapper.selectCollectibleDefinition(TenantContextHolder.getRequiredTenantId(),id,version);
        require(value!=null,"collectible definition version does not exist");
        return new GamificationView().setGameId(value.getGameId()).setCollectibleDefinitionId(id)
                .setCollectibleVersion(version);
    }

    private GamificationView createGame(Long tenantId, Long operationId, GamificationCommand command,
                                        Instant occurredAt, LocalDateTime now) {
        requireCode(command.getGameCode(), "gameCode");
        requireText(command.getGameName(), "gameName", 128);
        require(command.getGameId() == null, "CREATE_GAME does not accept gameId");
        Game game = new Game().setGameId(UUID.randomUUID().toString()).setTenantId(tenantId)
                .setGameCode(command.getGameCode()).setGameName(command.getGameName()).setStatus("DRAFT")
                .setCurrentVersion(0L).setCreatedAt(now).setUpdatedAt(now);
        require(mapper.insertGame(game) == 1, "failed to create game");
        eventService.appendGameCreated(operationId, game, command, occurredAt, now);
        return gameView(operationId, game, false);
    }

    private GamificationView publishGameVersion(Long tenantId, Long operationId, GamificationCommand command,
                                                Instant occurredAt, LocalDateTime now) {
        requireId(command.getGameId(), "gameId");
        requireExpectedVersion(command);
        requireGameCurrency(command.getVirtualCurrencyCode());
        require(command.getAssistDailyLimit() != null && command.getAssistDailyLimit() >= 0
                && command.getAssistDailyLimit() <= 100, "assistDailyLimit must be between 0 and 100");
        require(command.getMaxRoundsPerSession() != null && command.getMaxRoundsPerSession() >= 1
                && command.getMaxRoundsPerSession() <= 1000, "maxRoundsPerSession must be between 1 and 1000");
        require(command.getSessionTtlSeconds() != null && command.getSessionTtlSeconds() >= 60
                && command.getSessionTtlSeconds() <= 86_400, "sessionTtlSeconds must be between 60 and 86400");
        Game game = requireGameForUpdate(tenantId, command.getGameId());
        require(Objects.equals(game.getCurrentVersion(), command.getExpectedVersion()), "game version conflict");
        if (game.getCurrentVersion() > 0) {
            require(Objects.equals(game.getVirtualCurrencyCode(), command.getVirtualCurrencyCode()),
                    "published game currency is immutable; create a new game for a different asset");
        }
        long nextVersion = game.getCurrentVersion() + 1;
        String definitionHash = DigestUtil.sha256Hex(game.getGameCode() + "|" + nextVersion + "|"
                + command.getVirtualCurrencyCode() + "|" + command.getAssistDailyLimit() + "|"
                + command.getMaxRoundsPerSession() + "|" + command.getSessionTtlSeconds());
        GameVersion version = new GameVersion().setGameVersionId(UUID.randomUUID().toString()).setTenantId(tenantId)
                .setGameId(game.getGameId()).setGameVersion(nextVersion).setDefinitionSha256(definitionHash)
                .setVirtualCurrencyCode(command.getVirtualCurrencyCode())
                .setAssistDailyLimit(command.getAssistDailyLimit())
                .setMaxRoundsPerSession(command.getMaxRoundsPerSession())
                .setSessionTtlSeconds(command.getSessionTtlSeconds()).setPublishedAt(now);
        require(mapper.insertGameVersion(version) == 1, "failed to persist immutable game version");
        require(mapper.publishGame(tenantId, game.getGameId(), game.getCurrentVersion(),
                command.getVirtualCurrencyCode(), command.getAssistDailyLimit(), command.getMaxRoundsPerSession(),
                command.getSessionTtlSeconds(), now) == 1, "game publish conflict");
        if (game.getCurrentVersion() == 0) {
            Account treasury = account(tenantId, game.getGameId(), "TREASURY", game.getGameId(),
                    command.getVirtualCurrencyCode(), 0L, now);
            require(mapper.insertAccount(treasury) == 1, "failed to create game treasury account");
        }
        String previous = game.getStatus();
        game.setStatus("PUBLISHED").setCurrentVersion(nextVersion)
                .setVirtualCurrencyCode(command.getVirtualCurrencyCode())
                .setAssistDailyLimit(command.getAssistDailyLimit())
                .setMaxRoundsPerSession(command.getMaxRoundsPerSession())
                .setSessionTtlSeconds(command.getSessionTtlSeconds()).setUpdatedAt(now);
        eventService.appendGame(operationId, game, version, previous, command, occurredAt, now);
        return gameView(operationId, game, false);
    }

    private GamificationView openPlayerAccount(Long tenantId, Long operationId, GamificationCommand command,
                                               Instant occurredAt, LocalDateTime now) {
        Game game = requirePublishedGame(tenantId, command.getGameId());
        requireId(command.getPrincipalId(), "principalId");
        require(mapper.selectAccountByOwner(tenantId, game.getGameId(), "PLAYER", command.getPrincipalId()) == null,
                "player already owns an account for this game currency");
        Account account = account(tenantId, game.getGameId(), "PLAYER", command.getPrincipalId(),
                game.getVirtualCurrencyCode(), 0L, now);
        require(mapper.insertAccount(account) == 1, "failed to open player game-currency account");
        eventService.appendAccountOpened(account, command, occurredAt);
        return accountView(operationId, account, false);
    }

    private GamificationView openSession(Long tenantId, Long operationId, GamificationCommand command,
                                         Instant occurredAt, LocalDateTime now) {
        Game game = requirePublishedGame(tenantId, command.getGameId());
        requireId(command.getPrincipalId(), "principalId");
        require(mapper.selectAccountByOwner(tenantId, game.getGameId(), "PLAYER", command.getPrincipalId()) != null,
                "game session requires an opened player game-currency account");
        Session session = new Session().setSessionId(UUID.randomUUID().toString()).setTenantId(tenantId)
                .setGameId(game.getGameId()).setGameVersion(game.getCurrentVersion())
                .setPrincipalId(command.getPrincipalId()).setStatus("OPEN").setRoundCount(0).setVersion(1L)
                .setExpiresAt(now.plusSeconds(game.getSessionTtlSeconds())).setCreatedAt(now).setUpdatedAt(now);
        require(mapper.insertSession(session) == 1, "failed to open game session");
        eventService.appendSession(operationId, session, null, command, occurredAt, now);
        return sessionView(operationId, session, false);
    }

    private GamificationView startRound(Long tenantId, Long operationId, GamificationCommand command,
                                        Instant occurredAt, LocalDateTime now) {
        requireId(command.getSessionId(), "sessionId");
        requireExpectedVersion(command);
        Session session = mapper.selectSessionForUpdate(tenantId, command.getSessionId());
        require(session != null, "game session does not exist");
        require(Objects.equals(session.getVersion(), command.getExpectedVersion()), "session version conflict");
        Game game = requirePublishedGame(tenantId, session.getGameId());
        require(session.getGameVersion().equals(game.getCurrentVersion()),
                "session game version is no longer current; open a new session");
        require(mapper.addSessionRound(tenantId, session.getSessionId(), session.getVersion(),
                game.getMaxRoundsPerSession(), now) == 1, "session cannot start another round");
        Round round = new Round().setRoundId(UUID.randomUUID().toString()).setTenantId(tenantId)
                .setSessionId(session.getSessionId()).setGameId(session.getGameId())
                .setGameVersion(session.getGameVersion()).setPrincipalId(session.getPrincipalId())
                .setRoundNumber(session.getRoundCount() + 1).setStatus("ACTIVE").setVersion(1L)
                .setStartedAt(now).setUpdatedAt(now);
        require(mapper.insertRound(round) == 1, "failed to start game round");
        eventService.appendRound(operationId, round, null, command, occurredAt, now);
        return roundView(operationId, round, false);
    }

    private GamificationView completeRound(Long tenantId, Long operationId, GamificationCommand command,
                                           Instant occurredAt, LocalDateTime now) {
        requireId(command.getRoundId(), "roundId");
        requireExpectedVersion(command);
        require(ROUND_OUTCOMES.contains(command.getRoundOutcome()), "unsupported roundOutcome");
        require(command.getScore() != null && command.getScore() >= 0, "score must be nonnegative");
        Round round = mapper.selectRoundForUpdate(tenantId, command.getRoundId());
        require(round != null, "game round does not exist");
        require(Objects.equals(round.getVersion(), command.getExpectedVersion()), "round version conflict");
        require(mapper.completeRound(tenantId, round.getRoundId(), round.getVersion(), command.getRoundOutcome(),
                command.getScore(), now) == 1, "round completion conflict");
        String previous = round.getStatus();
        round.setStatus("COMPLETED").setOutcome(command.getRoundOutcome()).setScore(command.getScore())
                .setVersion(round.getVersion() + 1).setCompletedAt(now).setUpdatedAt(now);
        eventService.appendRound(operationId, round, previous, command, occurredAt, now);
        return roundView(operationId, round, false);
    }

    private GamificationView defineReward(Long tenantId, Long operationId, GamificationCommand command,
                                          Instant occurredAt, LocalDateTime now) {
        Game game = requirePublishedGame(tenantId, command.getGameId());
        requireCode(command.getRewardCode(), "rewardCode");
        require(command.getRewardVersion() != null && command.getRewardVersion() > 0,
                "rewardVersion must be positive");
        require(Set.of("CURRENCY", "FRAGMENT").contains(command.getRewardKind()),
                "rewardKind must be CURRENCY or FRAGMENT");
        String id = command.getRewardDefinitionId() == null ? UUID.randomUUID().toString()
                : command.getRewardDefinitionId();
        requireId(id, "rewardDefinitionId");
        String assetClass;
        Long currencyAmount = null;
        Long fragmentQuantity = null;
        if ("CURRENCY".equals(command.getRewardKind())) {
            require(Objects.equals(game.getVirtualCurrencyCode(), command.getAssetCode()),
                    "currency reward must use this game's immutable virtual currency");
            requireGameCurrency(command.getAssetCode());
            require(command.getAssetAmountMicrounits() != null && command.getAssetAmountMicrounits() > 0,
                    "currency reward amountMicrounits must be positive");
            require(command.getRewardFragmentQuantity() == null,
                    "currency reward cannot carry fragment quantity");
            assetClass = GAME_CURRENCY_ASSET_CLASS;
            currencyAmount = command.getAssetAmountMicrounits();
        } else {
            requireGameFragment(command.getAssetCode());
            require(command.getRewardFragmentQuantity() != null && command.getRewardFragmentQuantity() > 0,
                    "fragment reward quantity must be positive");
            require(command.getAssetAmountMicrounits() == null,
                    "fragment reward cannot carry currency amountMicrounits");
            assetClass = GAME_FRAGMENT_ASSET_CLASS;
            fragmentQuantity = command.getRewardFragmentQuantity();
        }
        String definitionHash = DigestUtil.sha256Hex(game.getGameId() + "|" + command.getRewardCode() + "|"
                + command.getRewardVersion() + "|" + command.getRewardKind() + "|" + command.getAssetCode() + "|"
                + currencyAmount + "|" + fragmentQuantity);
        RewardDefinition definition = new RewardDefinition().setRewardDefinitionId(id).setTenantId(tenantId)
                .setGameId(game.getGameId()).setRewardCode(command.getRewardCode())
                .setRewardVersion(command.getRewardVersion()).setRewardKind(command.getRewardKind())
                .setAssetClass(assetClass).setAssetCode(command.getAssetCode())
                .setCurrencyAmountMicrounits(currencyAmount).setFragmentQuantity(fragmentQuantity)
                .setDefinitionSha256(definitionHash).setPublishedAt(now);
        require(mapper.insertRewardDefinition(definition) == 1, "failed to publish immutable reward definition");
        eventService.appendRewardDefinition(definition, command, occurredAt);
        return new GamificationView().setOperationId(operationId).setDuplicate(false).setGameId(game.getGameId())
                .setRewardDefinitionId(id).setRewardVersion(command.getRewardVersion());
    }

    private GamificationView defineDrawPool(Long tenantId, Long operationId, GamificationCommand command,
                                            Instant occurredAt, LocalDateTime now) {
        Game game = requirePublishedGame(tenantId, command.getGameId());
        requireCode(command.getDrawPoolCode(), "drawPoolCode");
        require(command.getDrawPoolVersion() != null && command.getDrawPoolVersion() > 0,
                "drawPoolVersion must be positive");
        require(command.getDrawPriceMicrounits() != null && command.getDrawPriceMicrounits() > 0,
                "drawPriceMicrounits must be positive");
        require(command.getDrawItems() != null && !command.getDrawItems().isEmpty()
                && command.getDrawItems().size() <= 100, "drawItems must contain between 1 and 100 rewards");
        String poolId = command.getDrawPoolId() == null ? UUID.randomUUID().toString() : command.getDrawPoolId();
        requireId(poolId, "drawPoolId");
        int total = 0;
        Set<String> definitions = new HashSet<>();
        for (GamificationCommand.DrawPoolItem item : command.getDrawItems()) {
            require(item != null, "draw pool item is required");
            requireId(item.getRewardDefinitionId(), "draw rewardDefinitionId");
            require(item.getRewardVersion() != null && item.getRewardVersion() > 0,
                    "draw rewardVersion must be positive");
            require(item.getWeight() != null && item.getWeight() > 0 && item.getWeight() <= 1_000_000,
                    "draw weight must be between 1 and 1000000");
            RewardDefinition reward = requireReward(tenantId, item.getRewardDefinitionId(), item.getRewardVersion());
            require(game.getGameId().equals(reward.getGameId()), "draw reward belongs to another game");
            require(definitions.add(item.getRewardDefinitionId() + "|" + item.getRewardVersion()),
                    "draw pool cannot contain a duplicate reward version");
            total = Math.addExact(total, item.getWeight());
        }
        require(total <= 1_000_000, "draw total weight must not exceed 1000000");
        String definitionHash = DigestUtil.sha256Hex(JsonUtils.toJsonString(command.getDrawItems()) + "|"
                + command.getDrawPriceMicrounits());
        DrawPool pool = new DrawPool().setDrawPoolId(poolId).setTenantId(tenantId).setGameId(game.getGameId())
                .setDrawPoolCode(command.getDrawPoolCode()).setPoolVersion(command.getDrawPoolVersion())
                .setStatus("PUBLISHED").setPriceMicrounits(command.getDrawPriceMicrounits())
                .setTotalWeight(total).setDefinitionSha256(definitionHash).setPublishedAt(now);
        require(mapper.insertDrawPool(pool) == 1, "failed to publish immutable draw pool version");
        int sequence = 0;
        int cumulative = 0;
        for (GamificationCommand.DrawPoolItem item : command.getDrawItems()) {
            cumulative = Math.addExact(cumulative, item.getWeight());
            require(mapper.insertDrawPoolItem(new DrawPoolItem().setDrawPoolItemId(UUID.randomUUID().toString())
                    .setTenantId(tenantId).setDrawPoolId(poolId).setPoolVersion(command.getDrawPoolVersion())
                    .setItemSequence(++sequence).setRewardDefinitionId(item.getRewardDefinitionId())
                    .setRewardVersion(item.getRewardVersion()).setWeight(item.getWeight())
                    .setCumulativeWeight(cumulative)) == 1, "failed to persist draw pool item");
        }
        eventService.appendDrawPool(pool, command, occurredAt);
        return new GamificationView().setOperationId(operationId).setDuplicate(false).setGameId(game.getGameId())
                .setDrawPoolId(poolId).setDrawPoolVersion(command.getDrawPoolVersion());
    }

    private GamificationView grantReward(Long tenantId, Long operationId, GamificationCommand command,
                                         Instant occurredAt, LocalDateTime now) {
        require("ADMIN_ADJUSTMENT".equals(command.getRewardSourceType()),
                "public reward grant requires ADMIN_ADJUSTMENT sourceType");
        requireId(command.getRewardSourceId(), "rewardSourceId");
        require(command.getEvidenceRef() != null && EVIDENCE.matcher(command.getEvidenceRef()).matches(),
                "admin reward grant requires sha256 evidenceRef");
        GrantOutcome outcome = applyReward(tenantId, command.getGameId(), command.getPrincipalId(),
                command.getRewardDefinitionId(), command.getRewardVersion(), command.getRewardSourceType(),
                command.getRewardSourceId(), operationId, command, occurredAt, now);
        return rewardView(operationId, outcome, false);
    }

    private GamificationView draw(Long tenantId, Long operationId, GamificationCommand command,
                                  Instant occurredAt, LocalDateTime now) {
        requireId(command.getPrincipalId(), "principalId");
        requireId(command.getDrawPoolId(), "drawPoolId");
        require(command.getDrawPoolVersion() != null && command.getDrawPoolVersion() > 0,
                "drawPoolVersion must be positive");
        DrawPool pool = mapper.selectDrawPool(tenantId, command.getDrawPoolId(), command.getDrawPoolVersion());
        require(pool != null && "PUBLISHED".equals(pool.getStatus()), "published draw pool version does not exist");
        Game game = requirePublishedGame(tenantId, pool.getGameId());
        require(command.getGameId() == null || game.getGameId().equals(command.getGameId()),
                "draw game does not match pool game");
        String requestId = UUID.randomUUID().toString();
        TransferOutcome charge = postCurrencyTransfer(tenantId, game, command.getPrincipalId(), "PLAYER",
                game.getGameId(), "TREASURY", pool.getPriceMicrounits(), "DRAW_CHARGE", requestId,
                command, occurredAt, now);
        DrawRequest request = new DrawRequest().setDrawRequestId(requestId).setTenantId(tenantId)
                .setGameId(game.getGameId()).setPrincipalId(command.getPrincipalId())
                .setDrawPoolId(pool.getDrawPoolId()).setPoolVersion(pool.getPoolVersion())
                .setChargeTransactionId(charge.transaction().getLedgerTransactionId())
                .setPriceMicrounits(pool.getPriceMicrounits()).setStatus("COMPLETED")
                .setOccurredAt(LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC)).setCreatedAt(now);
        require(mapper.insertDrawRequest(request) == 1, "failed to persist draw request");
        List<DrawPoolItem> items = mapper.selectDrawPoolItems(tenantId, pool.getDrawPoolId(), pool.getPoolVersion());
        require(!items.isEmpty() && items.get(items.size() - 1).getCumulativeWeight().equals(pool.getTotalWeight()),
                "draw pool cumulative weight is inconsistent");
        GamificationRandomSource.Selection selection = randomSource.select(pool.getTotalWeight());
        require(selection.ticket() >= 1 && selection.ticket() <= pool.getTotalWeight(),
                "random source returned an invalid draw ticket");
        DrawPoolItem selected = items.stream().filter(item -> selection.ticket() <= item.getCumulativeWeight())
                .findFirst().orElseThrow(() -> new IllegalStateException("draw selection did not resolve a reward"));
        GrantOutcome reward = applyReward(tenantId, game.getGameId(), command.getPrincipalId(),
                selected.getRewardDefinitionId(), selected.getRewardVersion(), "DRAW", requestId, operationId,
                command, occurredAt, now);
        DrawResult result = new DrawResult().setDrawResultId(UUID.randomUUID().toString()).setTenantId(tenantId)
                .setDrawRequestId(requestId).setSelectedTicket(selection.ticket()).setTotalWeight(pool.getTotalWeight())
                .setEntropySha256(selection.entropySha256()).setDrawPoolItemId(selected.getDrawPoolItemId())
                .setRewardGrantId(reward.grant().getRewardGrantId()).setCreatedAt(now);
        require(mapper.insertDrawResult(result) == 1, "failed to persist draw result");
        eventService.appendDraw(request, result, selected, command, occurredAt);
        return rewardView(operationId, reward, false).setDrawPoolId(pool.getDrawPoolId())
                .setDrawPoolVersion(pool.getPoolVersion()).setDrawRequestId(requestId)
                .setDrawResultId(result.getDrawResultId());
    }

    private GamificationView recordAssist(Long tenantId, Long operationId, GamificationCommand command,
                                          Instant occurredAt, LocalDateTime now) {
        Game game = requirePublishedGame(tenantId, command.getGameId());
        requireId(command.getHelperPrincipalId(), "helperPrincipalId");
        requireId(command.getBeneficiaryPrincipalId(), "beneficiaryPrincipalId");
        require(!command.getHelperPrincipalId().equals(command.getBeneficiaryPrincipalId()),
                "self-assist is forbidden");
        require(game.getAssistDailyLimit() > 0, "assist is disabled for this game version");
        LocalDate quotaDate = LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC).toLocalDate();
        AssistQuota quota = mapper.selectAssistQuotaForUpdate(tenantId, game.getGameId(),
                command.getBeneficiaryPrincipalId(), quotaDate);
        if (quota == null) {
            quota = new AssistQuota().setAssistQuotaId(UUID.randomUUID().toString()).setTenantId(tenantId)
                    .setGameId(game.getGameId()).setBeneficiaryPrincipalId(command.getBeneficiaryPrincipalId())
                    .setQuotaDate(quotaDate).setAssistLimit(game.getAssistDailyLimit()).setAssistsUsed(0)
                    .setVersion(1L).setCreatedAt(now).setUpdatedAt(now);
            require(mapper.insertAssistQuota(quota) == 1, "failed to create assist quota");
        }
        require(quota.getAssistLimit().equals(game.getAssistDailyLimit()),
                "assist quota policy changed; use a new UTC quota date");
        require(mapper.consumeAssistQuota(tenantId, quota.getAssistQuotaId(), quota.getVersion(), now) == 1,
                "assist quota exhausted or version conflict");
        quota.setAssistsUsed(quota.getAssistsUsed() + 1).setVersion(quota.getVersion() + 1).setUpdatedAt(now);
        AssistRecord record = new AssistRecord().setAssistRecordId(UUID.randomUUID().toString()).setTenantId(tenantId)
                .setGameId(game.getGameId()).setHelperPrincipalId(command.getHelperPrincipalId())
                .setBeneficiaryPrincipalId(command.getBeneficiaryPrincipalId()).setQuotaDate(quotaDate)
                .setOrdinal(quota.getAssistsUsed()).setOccurredAt(LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC))
                .setCreatedAt(now);
        require(mapper.insertAssistRecord(record) == 1,
                "helper already assisted this beneficiary for this game and UTC date");
        eventService.appendAssist(record, quota, command, occurredAt);
        return new GamificationView().setOperationId(operationId).setDuplicate(false).setGameId(game.getGameId())
                .setAssistRecordId(record.getAssistRecordId()).setAssistsUsed(quota.getAssistsUsed())
                .setAssistLimit(quota.getAssistLimit());
    }

    private GamificationView defineTask(Long tenantId, Long operationId, GamificationCommand command,
                                        Instant occurredAt, LocalDateTime now) {
        Game game = requirePublishedGame(tenantId, command.getGameId());
        requireCode(command.getTaskCode(), "taskCode");
        require(command.getTaskVersion() != null && command.getTaskVersion() > 0,
                "taskVersion must be positive");
        require(command.getTargetUnits() != null && command.getTargetUnits() > 0,
                "targetUnits must be positive");
        RewardDefinition reward = requireReward(tenantId, command.getRewardDefinitionId(), command.getRewardVersion());
        require(game.getGameId().equals(reward.getGameId()), "task reward belongs to another game");
        String id = command.getTaskDefinitionId() == null ? UUID.randomUUID().toString()
                : command.getTaskDefinitionId();
        requireId(id, "taskDefinitionId");
        String definitionHash = DigestUtil.sha256Hex(game.getGameId() + "|" + command.getTaskCode() + "|"
                + command.getTaskVersion() + "|" + command.getTargetUnits() + "|"
                + reward.getRewardDefinitionId() + "|" + reward.getRewardVersion());
        TaskDefinition task = new TaskDefinition().setTaskDefinitionId(id).setTenantId(tenantId)
                .setGameId(game.getGameId()).setTaskCode(command.getTaskCode()).setTaskVersion(command.getTaskVersion())
                .setTargetUnits(command.getTargetUnits()).setRewardDefinitionId(reward.getRewardDefinitionId())
                .setRewardVersion(reward.getRewardVersion()).setDefinitionSha256(definitionHash).setPublishedAt(now);
        require(mapper.insertTaskDefinition(task) == 1, "failed to publish immutable task definition");
        eventService.appendTaskDefinition(task, command, occurredAt);
        return new GamificationView().setOperationId(operationId).setDuplicate(false).setGameId(game.getGameId())
                .setTaskDefinitionId(id).setTaskVersion(task.getTaskVersion()).setTargetUnits(task.getTargetUnits());
    }

    private GamificationView advanceTask(Long tenantId, Long operationId, GamificationCommand command,
                                         Instant occurredAt, LocalDateTime now) {
        requireId(command.getPrincipalId(), "principalId");
        requireId(command.getTaskDefinitionId(), "taskDefinitionId");
        require(command.getTaskVersion() != null && command.getTaskVersion() > 0,
                "taskVersion must be positive");
        require(command.getProgressDelta() != null && command.getProgressDelta() > 0,
                "progressDelta must be positive");
        TaskDefinition task = mapper.selectTaskDefinition(tenantId, command.getTaskDefinitionId(),
                command.getTaskVersion());
        require(task != null, "published task definition does not exist");
        requirePublishedGame(tenantId, task.getGameId());
        TaskProgress progress = mapper.selectTaskProgressForUpdate(tenantId, task.getTaskDefinitionId(),
                task.getTaskVersion(), command.getPrincipalId());
        String previous = null;
        long previousVersion = 0;
        if (progress == null) {
            progress = new TaskProgress().setTaskProgressId(UUID.randomUUID().toString()).setTenantId(tenantId)
                    .setTaskDefinitionId(task.getTaskDefinitionId()).setTaskVersion(task.getTaskVersion())
                    .setGameId(task.getGameId()).setPrincipalId(command.getPrincipalId()).setCompletedUnits(0L)
                    .setTargetUnits(task.getTargetUnits()).setStatus("IN_PROGRESS").setVersion(1L)
                    .setCreatedAt(now).setUpdatedAt(now);
        } else {
            require("IN_PROGRESS".equals(progress.getStatus()), "completed task progress is immutable");
            requireExpectedVersion(command);
            require(Objects.equals(progress.getVersion(), command.getExpectedVersion()), "task progress version conflict");
            previous = progress.getStatus();
            previousVersion = progress.getVersion();
        }
        long completed = Math.min(progress.getTargetUnits(), Math.addExact(progress.getCompletedUnits(),
                command.getProgressDelta()));
        String status = completed == progress.getTargetUnits() ? "COMPLETED" : "IN_PROGRESS";
        String rewardGrantId = null;
        if ("COMPLETED".equals(status)) {
            GrantOutcome reward = applyReward(tenantId, task.getGameId(), command.getPrincipalId(),
                    task.getRewardDefinitionId(), task.getRewardVersion(), "TASK_PROGRESS", progress.getTaskProgressId(),
                    operationId, command, occurredAt, now);
            rewardGrantId = reward.grant().getRewardGrantId();
        }
        if (previousVersion == 0) {
            progress.setCompletedUnits(completed).setStatus(status).setRewardGrantId(rewardGrantId);
            require(mapper.insertTaskProgress(progress) == 1, "failed to create task progress");
        } else {
            require(mapper.updateTaskProgress(tenantId, progress.getTaskProgressId(), previousVersion, completed,
                    status, rewardGrantId, now) == 1, "task progress update conflict");
            progress.setCompletedUnits(completed).setStatus(status).setRewardGrantId(rewardGrantId)
                    .setVersion(previousVersion + 1).setUpdatedAt(now);
        }
        eventService.appendTask(operationId, progress, previous, command, occurredAt, now);
        return taskView(operationId, progress, false);
    }

    private GamificationView transferGift(Long tenantId, Long operationId, GamificationCommand command,
                                          Instant occurredAt, LocalDateTime now) {
        Game game = requirePublishedGame(tenantId, command.getGameId());
        requireId(command.getFromPrincipalId(), "fromPrincipalId");
        requireId(command.getToPrincipalId(), "toPrincipalId");
        require(!command.getFromPrincipalId().equals(command.getToPrincipalId()), "self-gift is forbidden");
        require(command.getGiftAmountMicrounits() != null && command.getGiftAmountMicrounits() > 0,
                "giftAmountMicrounits must be positive");
        requireCode(command.getReasonCode(), "reasonCode");
        String giftId = UUID.randomUUID().toString();
        TransferOutcome transfer = postCurrencyTransfer(tenantId, game, command.getFromPrincipalId(), "PLAYER",
                command.getToPrincipalId(), "PLAYER", command.getGiftAmountMicrounits(), "GIFT_TRANSFER", giftId,
                command, occurredAt, now);
        GiftTransfer gift = new GiftTransfer().setGiftTransferId(giftId).setTenantId(tenantId)
                .setGameId(game.getGameId()).setCurrencyCode(game.getVirtualCurrencyCode())
                .setFromPrincipalId(command.getFromPrincipalId()).setToPrincipalId(command.getToPrincipalId())
                .setAmountMicrounits(command.getGiftAmountMicrounits())
                .setLedgerTransactionId(transfer.transaction().getLedgerTransactionId())
                .setReasonCode(command.getReasonCode()).setOccurredAt(LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC))
                .setCreatedAt(now);
        require(mapper.insertGiftTransfer(gift) == 1, "failed to persist gift transfer");
        eventService.appendGift(gift, command, occurredAt);
        return new GamificationView().setOperationId(operationId).setDuplicate(false).setGameId(game.getGameId())
                .setGiftTransferId(giftId).setLedgerTransactionId(gift.getLedgerTransactionId());
    }

    private GamificationView defineSeasonSeries(Long tenantId, Long operationId, GamificationCommand command,
                                                Instant occurredAt, LocalDateTime now) {
        Game game = requirePublishedGame(tenantId, command.getGameId());
        requireCode(command.getSeasonSeriesCode(), "seasonSeriesCode");
        requireText(command.getSeasonSeriesName(), "seasonSeriesName", 128);
        require(command.getSeasonSeriesVersion() != null && command.getSeasonSeriesVersion() > 0,
                "seasonSeriesVersion must be positive");
        String id = command.getSeasonSeriesId() == null ? UUID.randomUUID().toString() : command.getSeasonSeriesId();
        requireId(id, "seasonSeriesId");
        SeasonSeries value = new SeasonSeries().setSeasonSeriesId(id).setTenantId(tenantId).setGameId(game.getGameId())
                .setSeriesCode(command.getSeasonSeriesCode()).setSeriesVersion(command.getSeasonSeriesVersion())
                .setSeriesName(command.getSeasonSeriesName()).setDefinitionSha256(DigestUtil.sha256Hex(game.getGameId()
                        + "|" + command.getSeasonSeriesCode() + "|" + command.getSeasonSeriesVersion() + "|"
                        + command.getSeasonSeriesName())).setPublishedAt(now);
        require(mapper.insertSeasonSeries(value) == 1, "failed to publish immutable season-series version");
        eventService.appendSeasonSeries(value, command, occurredAt);
        return new GamificationView().setOperationId(operationId).setDuplicate(false).setGameId(game.getGameId())
                .setSeasonSeriesId(id).setSeasonSeriesVersion(value.getSeriesVersion());
    }

    private GamificationView defineSeason(Long tenantId, Long operationId, GamificationCommand command,
                                          Instant occurredAt, LocalDateTime now) {
        requireId(command.getSeasonSeriesId(), "seasonSeriesId");
        require(command.getSeasonSeriesVersion() != null && command.getSeasonSeriesVersion() > 0,
                "seasonSeriesVersion must be positive");
        SeasonSeries series = mapper.selectSeasonSeries(tenantId, command.getSeasonSeriesId(),
                command.getSeasonSeriesVersion());
        require(series != null, "season-series version does not exist");
        requireCode(command.getSeasonCode(), "seasonCode"); requireText(command.getSeasonName(), "seasonName", 128);
        require(command.getSeasonVersion() != null && command.getSeasonVersion() > 0,
                "seasonVersion must be positive");
        require(command.getSeasonStartsAt() != null && command.getSeasonEndsAt() != null
                && command.getSeasonEndsAt().isAfter(command.getSeasonStartsAt()), "season interval is invalid");
        String id = command.getSeasonId() == null ? UUID.randomUUID().toString() : command.getSeasonId();
        requireId(id, "seasonId");
        Season value = new Season().setSeasonId(id).setTenantId(tenantId).setGameId(series.getGameId())
                .setSeasonSeriesId(series.getSeasonSeriesId()).setSeriesVersion(series.getSeriesVersion())
                .setSeasonCode(command.getSeasonCode()).setSeasonVersion(command.getSeasonVersion())
                .setSeasonName(command.getSeasonName()).setStatus("PUBLISHED")
                .setStartsAt(LocalDateTime.ofInstant(command.getSeasonStartsAt(), ZoneOffset.UTC))
                .setEndsAt(LocalDateTime.ofInstant(command.getSeasonEndsAt(), ZoneOffset.UTC))
                .setDefinitionSha256(DigestUtil.sha256Hex(series.getSeasonSeriesId() + "|" + series.getSeriesVersion()
                        + "|" + command.getSeasonCode() + "|" + command.getSeasonVersion() + "|"
                        + command.getSeasonStartsAt() + "|" + command.getSeasonEndsAt())).setPublishedAt(now);
        require(mapper.insertSeason(value) == 1, "failed to publish immutable season version");
        eventService.appendSeason(value, command, occurredAt);
        return new GamificationView().setOperationId(operationId).setDuplicate(false).setGameId(value.getGameId())
                .setSeasonId(id).setSeasonVersion(value.getSeasonVersion()).setSeasonSeriesId(value.getSeasonSeriesId())
                .setSeasonSeriesVersion(value.getSeriesVersion());
    }

    private GamificationView defineCollectible(Long tenantId, Long operationId, GamificationCommand command,
                                               Instant occurredAt, LocalDateTime now) {
        Game game = requirePublishedGame(tenantId, command.getGameId()); requireCode(command.getCollectibleCode(), "collectibleCode");
        requireText(command.getCollectibleName(), "collectibleName", 128);
        require(Set.of("GK", "FIGURE", "COLLECTIBLE").contains(command.getCollectibleKind()), "unsupported collectibleKind");
        require(command.getCollectibleVersion() != null && command.getCollectibleVersion() > 0,
                "collectibleVersion must be positive");
        String id = command.getCollectibleDefinitionId() == null ? UUID.randomUUID().toString() : command.getCollectibleDefinitionId();
        requireId(id, "collectibleDefinitionId");
        CollectibleDefinition value = new CollectibleDefinition().setCollectibleDefinitionId(id).setTenantId(tenantId)
                .setGameId(game.getGameId()).setCollectibleCode(command.getCollectibleCode())
                .setCollectibleVersion(command.getCollectibleVersion()).setCollectibleKind(command.getCollectibleKind())
                .setCollectibleName(command.getCollectibleName()).setDefinitionSha256(DigestUtil.sha256Hex(game.getGameId()
                        + "|" + command.getCollectibleCode() + "|" + command.getCollectibleVersion() + "|"
                        + command.getCollectibleKind())).setPublishedAt(now);
        require(mapper.insertCollectibleDefinition(value) == 1, "failed to publish immutable collectible version");
        eventService.appendCollectibleDefinition(value, command, occurredAt);
        return new GamificationView().setOperationId(operationId).setDuplicate(false).setGameId(game.getGameId())
                .setCollectibleDefinitionId(id).setCollectibleVersion(value.getCollectibleVersion());
    }

    private GamificationView grantCollectible(Long tenantId, Long operationId, GamificationCommand command,
                                              Instant occurredAt, LocalDateTime now) {
        require("ADMIN_ADJUSTMENT".equals(command.getRewardSourceType()), "collectible grant requires ADMIN_ADJUSTMENT");
        require(command.getEvidenceRef() != null && EVIDENCE.matcher(command.getEvidenceRef()).matches(), "collectible grant requires sha256 evidenceRef");
        return collectibleView(operationId, applyCollectible(tenantId, command.getPrincipalId(),
                command.getCollectibleDefinitionId(), command.getCollectibleVersion(), command.getCollectibleQuantity(),
                "ADMIN_ADJUSTMENT", command.getRewardSourceId(), command, occurredAt, now));
    }

    private CollectibleOwnership applyCollectible(Long tenantId, String principalId, String definitionId,
            Long definitionVersion, Long quantity, String sourceType, String sourceId, GamificationCommand command,
            Instant occurredAt, LocalDateTime now) {
        requireId(principalId, "principalId"); requireId(sourceId, "sourceId");
        require(quantity != null && quantity != 0, "collectibleQuantity delta must be non-zero");
        CollectibleDefinition definition = mapper.selectCollectibleDefinition(tenantId, definitionId, definitionVersion);
        require(definition != null, "collectible definition version does not exist");
        CollectibleOwnership ownership = mapper.selectCollectibleOwnershipForUpdate(tenantId, definition.getGameId(),
                principalId, definitionId, definitionVersion);
        if (ownership == null) {
            require(quantity > 0, "collectible debit requires an existing sufficient ownership");
            ownership = new CollectibleOwnership().setOwnershipId(UUID.randomUUID().toString()).setTenantId(tenantId)
                    .setGameId(definition.getGameId()).setPrincipalId(principalId).setCollectibleDefinitionId(definitionId)
                    .setCollectibleVersion(definitionVersion).setQuantity(quantity).setVersion(1L).setCreatedAt(now).setUpdatedAt(now);
            require(mapper.insertCollectibleOwnership(ownership) == 1, "failed to create collectible ownership");
        } else {
            require(mapper.applyCollectibleDelta(tenantId, ownership.getOwnershipId(), ownership.getVersion(), quantity, now) == 1,
                    "collectible ownership version conflict");
            ownership.setQuantity(Math.addExact(ownership.getQuantity(), quantity)).setVersion(ownership.getVersion()+1).setUpdatedAt(now);
        }
        CollectibleLedgerEntry entry = new CollectibleLedgerEntry().setCollectibleEntryId(UUID.randomUUID().toString())
                .setTenantId(tenantId).setOwnershipId(ownership.getOwnershipId()).setSourceType(sourceType).setSourceId(sourceId)
                .setDeltaQuantity(quantity).setBalanceAfterQuantity(ownership.getQuantity())
                .setOccurredAt(LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC)).setCreatedAt(now);
        require(mapper.insertCollectibleEntry(entry) == 1, "duplicate collectible source");
        eventService.appendCollectible(entry, ownership, command, occurredAt); return ownership;
    }

    private GamificationView createRedemption(Long tenantId, Long operationId, GamificationCommand command,
                                              Instant occurredAt, LocalDateTime now) {
        requireId(command.getPrincipalId(), "principalId");
        CollectibleDefinition definition = mapper.selectCollectibleDefinition(tenantId,
                command.getCollectibleDefinitionId(), command.getCollectibleVersion());
        require(definition != null, "collectible definition version does not exist");
        require(command.getCollectibleQuantity() != null && command.getCollectibleQuantity() > 0, "collectibleQuantity must be positive");
        require("MALL_REDEMPTION_V1".equals(command.getAdapterCode()), "only governed mall redemption adapter is supported");
        requireId(command.getExternalIntentRef(), "externalIntentRef");
        require(command.getAssetAmountMicrounits() == null && command.getGiftAmountMicrounits() == null
                && command.getVirtualCurrencyCode() == null, "redemption stores adapter references, never external balances or money");
        RedemptionIntent value = new RedemptionIntent().setRedemptionIntentId(UUID.randomUUID().toString())
                .setTenantId(tenantId).setGameId(definition.getGameId()).setPrincipalId(command.getPrincipalId())
                .setCollectibleDefinitionId(definition.getCollectibleDefinitionId()).setCollectibleVersion(definition.getCollectibleVersion())
                .setQuantity(command.getCollectibleQuantity()).setAdapterCode(command.getAdapterCode())
                .setExternalIntentRef(command.getExternalIntentRef()).setStatus("PENDING").setVersion(1L)
                .setOccurredAt(LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC)).setCreatedAt(now).setUpdatedAt(now);
        require(mapper.insertRedemptionIntent(value) == 1, "duplicate external redemption intent");
        eventService.appendRedemption(operationId, value, null, command, occurredAt, now); return redemptionView(operationId, value);
    }

    private GamificationView recordRedemptionResult(Long tenantId, Long operationId, GamificationCommand command,
                                                    Instant occurredAt, LocalDateTime now) {
        requireId(command.getRedemptionIntentId(), "redemptionIntentId"); requireExpectedVersion(command);
        require(Set.of("SUCCEEDED", "REJECTED").contains(command.getRedemptionOutcome()), "invalid redemptionOutcome");
        requireId(command.getExternalResultRef(), "externalResultRef");
        RedemptionIntent value = mapper.selectRedemptionForUpdate(tenantId, command.getRedemptionIntentId());
        require(value != null && "PENDING".equals(value.getStatus()), "redemption is not pending");
        require(value.getVersion().equals(command.getExpectedVersion()), "redemption version conflict");
        if ("SUCCEEDED".equals(command.getRedemptionOutcome())) {
            applyCollectible(tenantId, value.getPrincipalId(), value.getCollectibleDefinitionId(), value.getCollectibleVersion(),
                    Math.negateExact(value.getQuantity()), "REDEMPTION", value.getRedemptionIntentId(), command,
                    occurredAt, now);
        }
        require(mapper.completeRedemption(tenantId, value.getRedemptionIntentId(), value.getVersion(),
                command.getExternalResultRef(), command.getRedemptionOutcome(), now) == 1, "redemption transition conflict");
        String previous=value.getStatus(); value.setExternalResultRef(command.getExternalResultRef()).setStatus(command.getRedemptionOutcome())
                .setVersion(value.getVersion()+1).setUpdatedAt(now);
        eventService.appendRedemption(operationId, value, previous, command, occurredAt, now); return redemptionView(operationId, value);
    }

    private GamificationView createRewardClaim(Long tenantId, Long operationId, GamificationCommand command,
                                               Instant occurredAt, LocalDateTime now) {
        Game game = requirePublishedGame(tenantId, command.getGameId()); requireId(command.getPrincipalId(), "principalId");
        RewardDefinition reward = requireReward(tenantId, command.getRewardDefinitionId(), command.getRewardVersion());
        require(game.getGameId().equals(reward.getGameId()), "claim reward belongs to another game");
        requireCode(command.getRewardSourceType(), "rewardSourceType"); requireId(command.getRewardSourceId(), "rewardSourceId");
        require(command.getClaimExpiresAt()!=null && command.getClaimExpiresAt().isAfter(occurredAt)
                && command.getClaimExpiresAt().isAfter(now.toInstant(ZoneOffset.UTC)),
                "claimExpiresAt must be in the future and after occurredAt");
        RewardClaim value = new RewardClaim().setRewardClaimId(UUID.randomUUID().toString()).setTenantId(tenantId)
                .setGameId(game.getGameId()).setPrincipalId(command.getPrincipalId()).setRewardDefinitionId(reward.getRewardDefinitionId())
                .setRewardVersion(reward.getRewardVersion()).setSourceType(command.getRewardSourceType()).setSourceId(command.getRewardSourceId())
                .setStatus("CLAIMABLE").setVersion(1L).setClaimExpiresAt(LocalDateTime.ofInstant(command.getClaimExpiresAt(), ZoneOffset.UTC))
                .setCreatedAt(now).setUpdatedAt(now);
        require(mapper.insertRewardClaim(value)==1, "duplicate reward claim source");
        eventService.appendClaim(operationId,value,null,command,occurredAt,now); return claimView(operationId,value);
    }

    private GamificationView claimReward(Long tenantId, Long operationId, GamificationCommand command,
                                         Instant occurredAt, LocalDateTime now) {
        requireId(command.getRewardClaimId(), "rewardClaimId"); requireExpectedVersion(command);
        RewardClaim value=mapper.selectRewardClaimForUpdate(tenantId,command.getRewardClaimId());
        require(value!=null && "CLAIMABLE".equals(value.getStatus()), "reward claim is not claimable");
        require(value.getVersion().equals(command.getExpectedVersion()), "reward claim version conflict");
        require(value.getClaimExpiresAt().isAfter(now), "reward claim is expired");
        GrantOutcome grant=applyReward(tenantId,value.getGameId(),value.getPrincipalId(),value.getRewardDefinitionId(),
                value.getRewardVersion(),"REWARD_CLAIM",value.getRewardClaimId(),operationId,command,occurredAt,now);
        require(mapper.transitionRewardClaim(tenantId,value.getRewardClaimId(),value.getVersion(),"CLAIMED",
                grant.grant().getRewardGrantId(),now,now)==1,"reward claim transition conflict");
        String previous=value.getStatus(); value.setStatus("CLAIMED").setRewardGrantId(grant.grant().getRewardGrantId())
                .setClaimedAt(now).setVersion(value.getVersion()+1).setUpdatedAt(now);
        eventService.appendClaim(operationId,value,previous,command,occurredAt,now); return claimView(operationId,value);
    }

    private GamificationView expireRewardClaim(Long tenantId, Long operationId, GamificationCommand command,
                                               Instant occurredAt, LocalDateTime now) {
        requireId(command.getRewardClaimId(), "rewardClaimId"); requireExpectedVersion(command);
        RewardClaim value=mapper.selectRewardClaimForUpdate(tenantId,command.getRewardClaimId());
        require(value!=null && "CLAIMABLE".equals(value.getStatus()),"reward claim is not claimable");
        require(value.getVersion().equals(command.getExpectedVersion()),"reward claim version conflict");
        require(!value.getClaimExpiresAt().isAfter(now),"reward claim has not expired");
        require(mapper.transitionRewardClaim(tenantId,value.getRewardClaimId(),value.getVersion(),"EXPIRED",null,null,now)==1,
                "reward claim expiry conflict");
        String previous=value.getStatus(); value.setStatus("EXPIRED").setVersion(value.getVersion()+1).setUpdatedAt(now);
        eventService.appendClaim(operationId,value,previous,command,occurredAt,now); return claimView(operationId,value);
    }

    private GrantOutcome applyReward(Long tenantId, String gameId, String principalId, String definitionId,
                                     Long rewardVersion, String sourceType, String sourceId, Long operationId,
                                     GamificationCommand command, Instant occurredAt, LocalDateTime now) {
        Game game = requirePublishedGame(tenantId, gameId);
        requireId(principalId, "principalId");
        requireId(sourceId, "rewardSourceId");
        RewardDefinition definition = requireReward(tenantId, definitionId, rewardVersion);
        require(game.getGameId().equals(definition.getGameId()), "reward belongs to another game");
        RewardGrant grant = new RewardGrant().setRewardGrantId(UUID.randomUUID().toString()).setTenantId(tenantId)
                .setGameId(game.getGameId()).setPrincipalId(principalId)
                .setRewardDefinitionId(definition.getRewardDefinitionId()).setRewardVersion(definition.getRewardVersion())
                .setSourceType(sourceType).setSourceId(sourceId)
                .setOccurredAt(LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC)).setCreatedAt(now);
        FragmentBalance fragmentBalance = null;
        if (GAME_CURRENCY_ASSET_CLASS.equals(definition.getAssetClass())) {
            grant.setGrantedCurrencyMicrounits(definition.getCurrencyAmountMicrounits())
                    .setGrantedFragmentQuantity(null);
            require(mapper.insertRewardGrant(grant) == 1, "duplicate reward source or failed reward grant");
            TransferOutcome transfer = postCurrencyTransfer(tenantId, game, game.getGameId(), "TREASURY",
                    principalId, "PLAYER", definition.getCurrencyAmountMicrounits(), "REWARD_GRANT",
                    grant.getRewardGrantId(), command, occurredAt, now);
            grant.setLedgerTransactionId(transfer.transaction().getLedgerTransactionId())
                    .setGrantedCurrencyMicrounits(definition.getCurrencyAmountMicrounits());
            require(mapper.attachRewardGrantLedger(tenantId, grant.getRewardGrantId(),
                    grant.getLedgerTransactionId()) == 1, "failed to attach reward currency ledger");
        } else if (GAME_FRAGMENT_ASSET_CLASS.equals(definition.getAssetClass())) {
            grant.setGrantedCurrencyMicrounits(null).setGrantedFragmentQuantity(definition.getFragmentQuantity());
            require(mapper.insertRewardGrant(grant) == 1, "duplicate reward source or failed reward grant");
            fragmentBalance = applyFragmentReward(tenantId, game, principalId, definition, grant, command,
                    occurredAt, now);
        } else {
            throw new IllegalStateException("unsupported reward asset class");
        }
        eventService.appendReward(grant, definition, command, occurredAt);
        return new GrantOutcome(grant, definition, fragmentBalance);
    }

    private FragmentBalance applyFragmentReward(Long tenantId, Game game, String principalId,
                                                RewardDefinition definition, RewardGrant grant,
                                                GamificationCommand command, Instant occurredAt, LocalDateTime now) {
        FragmentBalance balance = mapper.selectFragmentBalanceForUpdate(tenantId, game.getGameId(), principalId,
                definition.getAssetCode());
        if (balance == null) {
            balance = new FragmentBalance().setFragmentBalanceId(UUID.randomUUID().toString()).setTenantId(tenantId)
                    .setGameId(game.getGameId()).setPrincipalId(principalId).setAssetClass(GAME_FRAGMENT_ASSET_CLASS)
                    .setFragmentCode(definition.getAssetCode()).setQuantity(definition.getFragmentQuantity())
                    .setVersion(1L).setCreatedAt(now).setUpdatedAt(now);
            require(mapper.insertFragmentBalance(balance) == 1, "failed to create fragment balance");
        } else {
            require(mapper.applyFragmentDelta(tenantId, balance.getFragmentBalanceId(), balance.getVersion(),
                    definition.getFragmentQuantity(), now) == 1, "fragment balance version conflict");
            balance.setQuantity(Math.addExact(balance.getQuantity(), definition.getFragmentQuantity()))
                    .setVersion(balance.getVersion() + 1).setUpdatedAt(now);
        }
        FragmentEntry entry = new FragmentEntry().setFragmentEntryId(UUID.randomUUID().toString())
                .setTenantId(tenantId).setFragmentBalanceId(balance.getFragmentBalanceId())
                .setRewardGrantId(grant.getRewardGrantId()).setDeltaQuantity(definition.getFragmentQuantity())
                .setBalanceAfterQuantity(balance.getQuantity())
                .setOccurredAt(LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC)).setCreatedAt(now);
        require(mapper.insertFragmentEntry(entry) == 1, "failed to append fragment ledger entry");
        eventService.appendFragment(entry, balance, command, occurredAt);
        return balance;
    }

    private TransferOutcome postCurrencyTransfer(Long tenantId, Game game, String fromOwnerRef, String fromOwnerType,
                                                 String toOwnerRef, String toOwnerType, Long amount, String businessType,
                                                 String businessId, GamificationCommand command, Instant occurredAt,
                                                 LocalDateTime now) {
        require(amount != null && amount > 0, "currency transfer amount must be positive");
        Account fromCandidate = mapper.selectAccountByOwner(tenantId, game.getGameId(), fromOwnerType, fromOwnerRef);
        Account toCandidate = mapper.selectAccountByOwner(tenantId, game.getGameId(), toOwnerType, toOwnerRef);
        require(fromCandidate != null && toCandidate != null, "both game-currency accounts must exist");
        require(!fromCandidate.getAccountId().equals(toCandidate.getAccountId()), "currency transfer accounts must differ");
        List<String> lockOrder = new ArrayList<>(List.of(fromCandidate.getAccountId(), toCandidate.getAccountId()));
        Collections.sort(lockOrder);
        Account first = mapper.selectAccountByIdForUpdate(tenantId, lockOrder.get(0));
        Account second = mapper.selectAccountByIdForUpdate(tenantId, lockOrder.get(1));
        require(first != null && second != null, "currency account disappeared while locking");
        Account from = first.getAccountId().equals(fromCandidate.getAccountId()) ? first : second;
        Account to = first.getAccountId().equals(toCandidate.getAccountId()) ? first : second;
        require("ACTIVE".equals(from.getStatus()) && "ACTIVE".equals(to.getStatus()),
                "currency transfer requires active accounts");
        require(GAME_CURRENCY_ASSET_CLASS.equals(from.getAssetClass())
                && GAME_CURRENCY_ASSET_CLASS.equals(to.getAssetClass()),
                "non-game asset account cannot enter the game ledger");
        require(game.getVirtualCurrencyCode().equals(from.getCurrencyCode())
                && game.getVirtualCurrencyCode().equals(to.getCurrencyCode()), "currency account asset mismatch");
        if ("PLAYER".equals(from.getOwnerType())) {
            require(from.getBalanceMicrounits() >= amount, "insufficient game virtual currency");
        }
        long fromAfter = Math.subtractExact(from.getBalanceMicrounits(), amount);
        long toAfter = Math.addExact(to.getBalanceMicrounits(), amount);
        require(mapper.applyAccountDelta(tenantId, from.getAccountId(), from.getVersion(), -amount, now) == 1,
                "debit account balance/version conflict");
        require(mapper.applyAccountDelta(tenantId, to.getAccountId(), to.getVersion(), amount, now) == 1,
                "credit account balance/version conflict");
        CurrencyTransaction transaction = new CurrencyTransaction().setLedgerTransactionId(UUID.randomUUID().toString())
                .setTenantId(tenantId).setGameId(game.getGameId()).setCurrencyCode(game.getVirtualCurrencyCode())
                .setBusinessType(businessType).setBusinessId(businessId).setAmountMicrounits(amount)
                .setOccurredAt(LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC)).setCreatedAt(now);
        require(mapper.insertCurrencyTransaction(transaction) == 1, "duplicate currency business effect");
        CurrencyEntry debit = new CurrencyEntry().setLedgerEntryId(UUID.randomUUID().toString()).setTenantId(tenantId)
                .setLedgerTransactionId(transaction.getLedgerTransactionId()).setEntrySequence(1)
                .setAccountId(from.getAccountId()).setDeltaMicrounits(-amount).setBalanceAfterMicrounits(fromAfter)
                .setCreatedAt(now);
        CurrencyEntry credit = new CurrencyEntry().setLedgerEntryId(UUID.randomUUID().toString()).setTenantId(tenantId)
                .setLedgerTransactionId(transaction.getLedgerTransactionId()).setEntrySequence(2)
                .setAccountId(to.getAccountId()).setDeltaMicrounits(amount).setBalanceAfterMicrounits(toAfter)
                .setCreatedAt(now);
        require(Math.addExact(debit.getDeltaMicrounits(), credit.getDeltaMicrounits()) == 0,
                "game currency ledger must balance to zero");
        require(mapper.insertCurrencyEntry(debit) == 1 && mapper.insertCurrencyEntry(credit) == 1,
                "failed to append balanced currency entries");
        from.setBalanceMicrounits(fromAfter).setVersion(from.getVersion() + 1).setUpdatedAt(now);
        to.setBalanceMicrounits(toAfter).setVersion(to.getVersion() + 1).setUpdatedAt(now);
        eventService.appendCurrencyLedger(transaction, from, to, command, occurredAt);
        return new TransferOutcome(transaction, from, to);
    }

    private Game requirePublishedGame(Long tenantId, String gameId) {
        requireId(gameId, "gameId");
        Game game = mapper.selectGame(tenantId, gameId);
        require(game != null && "PUBLISHED".equals(game.getStatus()) && game.getCurrentVersion() > 0,
                "published game does not exist");
        requireGameCurrency(game.getVirtualCurrencyCode());
        return game;
    }

    private Game requireGameForUpdate(Long tenantId, String gameId) {
        Game game = mapper.selectGameForUpdate(tenantId, gameId);
        require(game != null, "game does not exist");
        return game;
    }

    private RewardDefinition requireReward(Long tenantId, String definitionId, Long version) {
        requireId(definitionId, "rewardDefinitionId");
        require(version != null && version > 0, "rewardVersion must be positive");
        RewardDefinition definition = mapper.selectRewardDefinition(tenantId, definitionId, version);
        require(definition != null, "published reward definition does not exist");
        return definition;
    }

    private static Account account(Long tenantId, String gameId, String ownerType, String ownerRef,
                                   String currencyCode, Long balance, LocalDateTime now) {
        return new Account().setAccountId(UUID.randomUUID().toString()).setTenantId(tenantId).setGameId(gameId)
                .setOwnerType(ownerType).setOwnerRef(ownerRef).setAssetClass(GAME_CURRENCY_ASSET_CLASS)
                .setCurrencyCode(currencyCode).setBalanceMicrounits(balance).setStatus("ACTIVE").setVersion(1L)
                .setCreatedAt(now).setUpdatedAt(now);
    }

    private static GamificationView gameView(Long operationId, Game game, boolean duplicate) {
        return new GamificationView().setOperationId(operationId).setDuplicate(duplicate).setGameId(game.getGameId())
                .setGameVersion(game.getCurrentVersion()).setGameStatus(game.getStatus())
                .setVirtualCurrencyCode(game.getVirtualCurrencyCode());
    }

    private static GamificationView accountView(Long operationId, Account account, boolean duplicate) {
        return new GamificationView().setOperationId(operationId).setDuplicate(duplicate).setGameId(account.getGameId())
                .setVirtualCurrencyCode(account.getCurrencyCode()).setAccountId(account.getAccountId())
                .setAccountVersion(account.getVersion()).setBalanceMicrounits(account.getBalanceMicrounits());
    }

    private static GamificationView sessionView(Long operationId, Session session, boolean duplicate) {
        return new GamificationView().setOperationId(operationId).setDuplicate(duplicate).setGameId(session.getGameId())
                .setGameVersion(session.getGameVersion()).setSessionId(session.getSessionId())
                .setSessionVersion(session.getVersion()).setSessionStatus(session.getStatus());
    }

    private static GamificationView roundView(Long operationId, Round round, boolean duplicate) {
        return new GamificationView().setOperationId(operationId).setDuplicate(duplicate).setGameId(round.getGameId())
                .setGameVersion(round.getGameVersion()).setSessionId(round.getSessionId()).setRoundId(round.getRoundId())
                .setRoundVersion(round.getVersion()).setRoundStatus(round.getStatus());
    }

    private static GamificationView rewardView(Long operationId, GrantOutcome outcome, boolean duplicate) {
        GamificationView view = new GamificationView().setOperationId(operationId).setDuplicate(duplicate)
                .setGameId(outcome.grant().getGameId()).setRewardDefinitionId(outcome.grant().getRewardDefinitionId())
                .setRewardVersion(outcome.grant().getRewardVersion()).setRewardGrantId(outcome.grant().getRewardGrantId())
                .setLedgerTransactionId(outcome.grant().getLedgerTransactionId());
        if (outcome.fragmentBalance() != null) {
            view.setFragmentCode(outcome.fragmentBalance().getFragmentCode())
                    .setFragmentQuantity(outcome.fragmentBalance().getQuantity());
        }
        return view;
    }

    private static GamificationView taskView(Long operationId, TaskProgress progress, boolean duplicate) {
        return new GamificationView().setOperationId(operationId).setDuplicate(duplicate).setGameId(progress.getGameId())
                .setTaskDefinitionId(progress.getTaskDefinitionId()).setTaskVersion(progress.getTaskVersion())
                .setTaskProgressId(progress.getTaskProgressId()).setCompletedUnits(progress.getCompletedUnits())
                .setTargetUnits(progress.getTargetUnits()).setTaskStatus(progress.getStatus())
                .setRewardGrantId(progress.getRewardGrantId());
    }

    private static GamificationView fragmentView(Long operationId, FragmentBalance balance, boolean duplicate) {
        return new GamificationView().setOperationId(operationId).setDuplicate(duplicate).setGameId(balance.getGameId())
                .setFragmentCode(balance.getFragmentCode()).setFragmentQuantity(balance.getQuantity());
    }

    private static GamificationView collectibleView(Long operationId, CollectibleOwnership value) {
        return new GamificationView().setOperationId(operationId).setDuplicate(false).setGameId(value.getGameId())
                .setCollectibleDefinitionId(value.getCollectibleDefinitionId())
                .setCollectibleVersion(value.getCollectibleVersion()).setCollectibleQuantity(value.getQuantity())
                .setVersion(value.getVersion());
    }

    private static GamificationView redemptionView(Long operationId, RedemptionIntent value) {
        return new GamificationView().setOperationId(operationId).setDuplicate(false).setGameId(value.getGameId())
                .setRedemptionIntentId(value.getRedemptionIntentId()).setRedemptionStatus(value.getStatus())
                .setCollectibleDefinitionId(value.getCollectibleDefinitionId())
                .setCollectibleVersion(value.getCollectibleVersion()).setVersion(value.getVersion());
    }

    private static GamificationView claimView(Long operationId, RewardClaim value) {
        return new GamificationView().setOperationId(operationId).setDuplicate(false).setGameId(value.getGameId())
                .setRewardClaimId(value.getRewardClaimId()).setRewardClaimStatus(value.getStatus())
                .setRewardDefinitionId(value.getRewardDefinitionId()).setRewardVersion(value.getRewardVersion())
                .setRewardGrantId(value.getRewardGrantId()).setVersion(value.getVersion());
    }

    private static void validateCommon(GamificationCommand command) {
        require(command != null && command.getOperation() != null, "gamification operation is required");
        requireText(command.getIdempotencyKey(), "idempotencyKey", 128);
        require(command.getIdempotencyKey().length() >= 8, "idempotencyKey is too short");
        requireText(command.getRunId(), "runId", 64);
        requireText(command.getCorrelationId(), "correlationId", 128);
    }

    private static void requireExpectedVersion(GamificationCommand command) {
        require(command.getExpectedVersion() != null && command.getExpectedVersion() >= 0,
                "expectedVersion is required and must be nonnegative");
    }

    private static void requireGameCurrency(String code) {
        require(code != null && GAME_CURRENCY.matcher(code).matches(),
                "virtual currency code must use GAME_COIN_* namespace");
        String upper = code.toUpperCase(Locale.ROOT);
        require(!upper.contains("TOKEN") && !upper.contains("COUPON") && !upper.contains("POINT")
                && !upper.contains("CNY") && !upper.contains("RMB") && !upper.contains("USD")
                && !upper.contains("MONEY"), "game currency must not alias Token, coupon, points, or money");
    }

    private static void requireGameFragment(String code) {
        require(code != null && GAME_FRAGMENT.matcher(code).matches(),
                "fragment code must use GAME_FRAGMENT_* namespace");
    }

    private static void requireCode(String value, String field) {
        require(value != null && CODE.matcher(value).matches(), field + " must be an uppercase business code");
    }

    private static void requireId(String value, String field) {
        require(value != null && ID.matcher(value).matches(), field + " is required and malformed");
    }

    private static void requireText(String value, String field, int max) {
        require(value != null && !value.isBlank() && value.length() <= max, field + " is required");
    }

    private static String firstNonNull(String... values) {
        for (String value : values) if (value != null) return value;
        throw new IllegalStateException("gamification command produced no aggregate identity");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private record TransferOutcome(CurrencyTransaction transaction, Account from, Account to) {
    }

    private record GrantOutcome(RewardGrant grant, RewardDefinition definition, FragmentBalance fragmentBalance) {
    }
}
