package io.github.gyai.projects.beta;

import io.github.gyai.projects.crafting.OutputProposal;
import io.github.gyai.projects.feature.FeatureFlagService;
import io.github.gyai.projects.feature.FeatureKey;
import io.github.gyai.projects.participation.EncounterId;
import io.github.gyai.projects.participation.ExperienceParticipationInput;
import io.github.gyai.projects.participation.NearbyExperienceBoundary;
import io.github.gyai.projects.participation.NearbyExperiencePolicy;
import io.github.gyai.projects.participation.ParticipationEvent;
import io.github.gyai.projects.participation.ParticipationKey;
import io.github.gyai.projects.participation.ParticipationLedger;
import io.github.gyai.projects.participation.ParticipationResult;
import io.github.gyai.projects.party.PartyCommandResult;
import io.github.gyai.projects.party.PartyHealthSummary;
import io.github.gyai.projects.party.PartyId;
import io.github.gyai.projects.party.PartyPolicy;
import io.github.gyai.projects.party.PartyService;
import io.github.gyai.projects.player.progress.PlayerProgressBuilder;
import io.github.gyai.projects.player.progress.QuestProgressState;
import io.github.gyai.projects.quest.PlayerProgressQuestView;
import io.github.gyai.projects.quest.QuestDefinitionRef;
import io.github.gyai.projects.quest.QuestProgressCommand;
import io.github.gyai.projects.quest.QuestProgressResult;
import io.github.gyai.projects.quest.QuestProgressService;
import io.github.gyai.projects.quest.QuestProgressSnapshot;
import io.github.gyai.projects.reward.RewardClaimKey;
import io.github.gyai.projects.reward.RewardClaimRequest;
import io.github.gyai.projects.reward.RewardClaimResult;
import io.github.gyai.projects.reward.RewardClaimService;
import io.github.gyai.projects.reward.RewardClaimStore;
import io.github.gyai.projects.reward.RewardDeliveryReceipt;
import io.github.gyai.projects.reward.RewardTransactionIdentity;
import io.github.gyai.projects.reward.TransactionRewardDeliveryPort;
import io.github.gyai.projects.reward.UnlockProposal;
import io.github.gyai.projects.transaction.InventoryCapacityProposal;
import io.github.gyai.projects.transaction.ReservationToken;
import io.github.gyai.projects.transaction.TransactionAuditResult;
import io.github.gyai.projects.transaction.TransactionEngine;
import io.github.gyai.projects.transaction.TransactionParticipant;
import io.github.gyai.projects.transaction.TransactionRequest;
import io.github.gyai.projects.transaction.TransactionStage;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public final class TrackFPartyQuestRewardFoundationTest {
    private static final Instant START = Instant.parse("2026-08-05T00:00:00Z");

    public static void main(String[] args) throws Exception {
        partyLifecycleIsBoundedDeterministicAndTemporary();
        partyExpiryRateReconnectAndCloseAreSafe();
        participationDeduplicatesOrdersAndCloses();
        questCommandsAreImmutableRevisionedAndIsolated();
        rewardClaimsAreDurableConcurrentAndPolicyDriven();
        rewardDeliveryUsesStableTrackDTransactions();
        nearbyExperienceAndUnlockRemainPolicyBoundaries();
        trackFFlagsStayDisabledAndPureApisStayBukkitFree();
    }

    private static void partyLifecycleIsBoundedDeterministicAndTemporary() {
        MutableClock clock = new MutableClock(START);
        PartyService service = new PartyService(policy(3, 2, 8, 8), clock);
        PartyId partyId = new PartyId(uuid(1));
        UUID leader = uuid(10);
        UUID oldest = uuid(11);
        UUID newest = uuid(12);

        assert service.create(partyId, leader).status() == PartyCommandResult.Status.CREATED;
        UUID oldestInvite = uuid(101);
        assert service.invite(oldestInvite, partyId, leader, oldest).status()
                == PartyCommandResult.Status.INVITED;
        PartyCommandResult accepted = service.accept(oldestInvite);
        assert accepted.status() == PartyCommandResult.Status.ACCEPTED;
        assert accepted.party().orElseThrow().members().size() == 2;
        assert service.accept(oldestInvite).replayed();
        assert service.party(partyId).orElseThrow().members().size() == 2
                : "Duplicate accept must not duplicate membership";

        PartyId secondParty = new PartyId(uuid(2));
        assert service.create(secondParty, oldest).status()
                == PartyCommandResult.Status.REJECTED : "One party per player";
        assert service.create(secondParty, uuid(20)).status()
                == PartyCommandResult.Status.CREATED;
        assert service.create(new PartyId(uuid(22)), uuid(21)).reason()
                .equals("party-capacity");

        UUID newestInvite = uuid(102);
        service.invite(newestInvite, partyId, leader, newest);
        service.accept(newestInvite);
        assert service.invite(oldestInvite, partyId, leader, uuid(77)).reason()
                .equals("invite-id-conflict");
        assert service.invite(uuid(103), partyId, leader, uuid(13)).reason()
                .equals("party-full");

        PartyCommandResult leaderLeft = service.leave(leader);
        assert leaderLeft.status() == PartyCommandResult.Status.LEFT;
        assert leaderLeft.party().orElseThrow().leaderId().equals(oldest)
                : "Oldest join sequence must become leader";
        assert service.kick(newest, oldest).status() == PartyCommandResult.Status.REJECTED;
        assert service.kick(oldest, newest).status() == PartyCommandResult.Status.KICKED;
        assert service.leave(oldest).status() == PartyCommandResult.Status.DISBANDED;
        assert service.party(partyId).isEmpty();

        UUID solo = uuid(20);
        assert service.leave(solo).status() == PartyCommandResult.Status.DISBANDED;
        assert service.partyCount() == 0;
        service.close();
    }

    private static void partyExpiryRateReconnectAndCloseAreSafe() {
        MutableClock clock = new MutableClock(START);
        PartyService service = new PartyService(policy(4, 2, 2, 2), clock);
        PartyId id = new PartyId(uuid(3));
        UUID leader = uuid(30);
        UUID member = uuid(31);
        service.create(id, leader);

        UUID expiring = uuid(301);
        service.invite(expiring, id, leader, member);
        clock.advance(Duration.ofSeconds(31));
        assert service.accept(expiring).status() == PartyCommandResult.Status.EXPIRED;
        assert service.accept(expiring).replayed() : "Invite terminal transition is one-shot";

        UUID decline = uuid(302);
        service.invite(decline, id, leader, member);
        assert service.decline(decline).status() == PartyCommandResult.Status.DECLINED;
        assert service.decline(decline).replayed();
        assert service.invite(uuid(303), id, leader, member).reason()
                .equals("invite-capacity") : "Invite state is bounded";
        service.close();

        PartyService rate = new PartyService(policy(4, 2, 10, 1), clock);
        PartyId rateId = new PartyId(uuid(4));
        rate.create(rateId, leader);
        assert rate.invite(uuid(401), rateId, leader, member).status()
                == PartyCommandResult.Status.INVITED;
        assert rate.invite(uuid(402), rateId, leader, uuid(32)).reason()
                .equals("invite-rate-limit");
        clock.advance(Duration.ofMinutes(2));
        assert rate.invite(uuid(403), rateId, leader, member).status()
                == PartyCommandResult.Status.INVITED;
        rate.accept(uuid(403));

        assert rate.disconnect(member).status() == PartyCommandResult.Status.DISCONNECTED;
        clock.advance(Duration.ofSeconds(10));
        assert rate.reconnect(member).status() == PartyCommandResult.Status.RECONNECTED;
        rate.disconnect(member);
        clock.advance(Duration.ofSeconds(21));
        assert rate.expireDisconnectedMembers() == 1;
        assert rate.party(rateId).orElseThrow().members().size() == 1;

        var health = rate.healthSummary(rateId, List.of(
                new PartyHealthSummary.MemberHealth(leader, 8.0, 10.0),
                new PartyHealthSummary.MemberHealth(uuid(999), 1.0, 2.0)));
        assert health.members().size() == 1;
        assert rate.chatRecipients(rateId, leader).equals(List.of(leader));
        rate.clear();
        assert rate.partyCount() == 0;
        rate.close();
        rate.close();
        assert rate.closed();
    }

    private static void participationDeduplicatesOrdersAndCloses() throws Exception {
        MutableClock clock = new MutableClock(START);
        ParticipationLedger ledger = new ParticipationLedger(
                2, 16, event -> io.github.gyai.projects.participation.ParticipationPolicy
                        .Decision.credit(event.reportedContribution()), clock);
        EncounterId encounter = new EncounterId(uuid(500));
        ParticipationEvent first = event(encounter, uuid(501), "projects:spin-slash", 2,
                4.5, ParticipationEvent.ContributionSemantics.DELTA);
        assert ledger.record(first).status() == ParticipationResult.Status.RECORDED;
        assert ledger.record(first).status() == ParticipationResult.Status.DUPLICATE;
        assert ledger.record(event(encounter, uuid(501), "projects:spin-slash", 1,
                9.0, ParticipationEvent.ContributionSemantics.ABSOLUTE)).status()
                == ParticipationResult.Status.STALE;
        assert ledger.recordCount(encounter) == 1;

        var executor = Executors.newFixedThreadPool(4);
        try {
            List<java.util.concurrent.Future<ParticipationResult>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                int seed = i;
                futures.add(executor.submit(() -> ledger.record(event(
                        encounter, uuid(600 + seed), "projects:boss-hit", 1,
                        seed, ParticipationEvent.ContributionSemantics.ABSOLUTE))));
            }
            for (var future : futures) {
                assert future.get(5, TimeUnit.SECONDS).status()
                        == ParticipationResult.Status.RECORDED;
            }
        } finally {
            executor.shutdownNow();
        }
        assert ledger.recordCount(encounter) == 9;
        EncounterId secondEncounter = new EncounterId(uuid(701));
        assert ledger.record(event(secondEncounter, uuid(702), "projects:other", 1,
                1.0, ParticipationEvent.ContributionSemantics.DELTA)).status()
                == ParticipationResult.Status.RECORDED;
        assert ledger.record(event(new EncounterId(uuid(703)), uuid(704),
                "projects:capacity", 1, 1.0,
                ParticipationEvent.ContributionSemantics.DELTA)).status()
                == ParticipationResult.Status.CAPACITY_REACHED;
        assert ledger.closeEncounter(encounter);
        assert !ledger.closeEncounter(encounter);
        assert ledger.record(event(encounter, uuid(700), "projects:late", 1,
                1.0, ParticipationEvent.ContributionSemantics.DELTA)).status()
                == ParticipationResult.Status.CLOSED;

        ParticipationLedger rejecting = new ParticipationLedger(1, 1,
                event -> io.github.gyai.projects.participation.ParticipationPolicy
                        .Decision.reject("policy-input-rejected"), clock);
        assert rejecting.record(first).status() == ParticipationResult.Status.INELIGIBLE;
        expectIllegal(() -> event(encounter, uuid(1), "projects:bad", 1,
                Double.NaN, ParticipationEvent.ContributionSemantics.DELTA));
        ledger.close();
        ledger.close();
        assert ledger.encounterCount() == 0;
    }

    private static void questCommandsAreImmutableRevisionedAndIsolated() {
        QuestDefinitionRef definition = new QuestDefinitionRef("projects:quest/test", 7);
        QuestProgressService service = new QuestProgressService(definition::equals);
        UUID player = uuid(800);
        QuestProgressResult unknown = service.propose(Optional.empty(), command(
                player, new QuestDefinitionRef("projects:quest/unknown", 1),
                QuestProgressCommand.Type.START, 0, Optional.empty(), 0, 1));
        assert unknown.status() == QuestProgressResult.Status.UNKNOWN_QUEST;
        assert unknown.proposal().isEmpty() : "Unknown quest must be isolated";

        QuestProgressResult start = service.propose(Optional.empty(), command(
                player, definition, QuestProgressCommand.Type.START,
                0, Optional.empty(), 0, 2));
        QuestProgressSnapshot current = start.proposal().orElseThrow();
        assert start.status() == QuestProgressResult.Status.STARTED;
        QuestProgressResult increment = service.propose(Optional.of(current), command(
                player, definition, QuestProgressCommand.Type.INCREMENT_COUNTER,
                1, Optional.of("projects:counter/kill"), 3, 3));
        current = increment.proposal().orElseThrow();
        assert current.counters().get("projects:counter/kill") == 3;
        Map<String, Long> immutableCounters = current.counters();
        expectUnsupported(immutableCounters::clear);
        assert service.propose(Optional.of(current), command(
                player, definition, QuestProgressCommand.Type.SET_MARKER,
                1, Optional.of("projects:marker/seen"), 0, 4)).status()
                == QuestProgressResult.Status.STALE;

        QuestProgressResult marker = service.propose(Optional.of(current), command(
                player, definition, QuestProgressCommand.Type.SET_MARKER,
                2, Optional.of("projects:marker/seen"), 0, 5));
        current = marker.proposal().orElseThrow();
        QuestProgressResult complete = service.propose(Optional.of(current), command(
                player, definition, QuestProgressCommand.Type.COMPLETE,
                3, Optional.empty(), 0, 6));
        current = complete.proposal().orElseThrow();
        assert current.completionMarked();
        QuestProgressResult claimed = service.propose(Optional.of(current), command(
                player, definition, QuestProgressCommand.Type.MARK_CLAIMED,
                4, Optional.empty(), 0, 7));
        assert claimed.proposal().orElseThrow().claimedMarked();

        QuestProgressSnapshot exhausted = new QuestProgressSnapshot(
                player, definition, QuestProgressSnapshot.State.ACTIVE,
                Map.of(), Set.of(), false, false, Long.MAX_VALUE);
        QuestProgressResult overflow = service.propose(Optional.of(exhausted), command(
                player, definition, QuestProgressCommand.Type.SET_MARKER,
                Long.MAX_VALUE, Optional.of("projects:marker/overflow"), 0, 8));
        assert overflow.status() == QuestProgressResult.Status.REJECTED;
        assert overflow.reason().equals("progress-revision-exhausted");
        assert overflow.proposal().isEmpty();
        assert exhausted.definition().questRevision() == 7
                : "Definition revision remains distinct from progress revision";

        QuestProgressState legacy = new QuestProgressState(
                "active", Map.of("kill", 2L), Set.of("seen"));
        var playerSnapshot = new PlayerProgressBuilder(player)
                .questStates(Map.of("projects:quest.test", legacy)).build();
        var view = new PlayerProgressQuestView().inspect(
                playerSnapshot, "projects:quest.test");
        assert view.legacyState().orElseThrow().equals(legacy);
        assert view.mappingStatus() == PlayerProgressQuestView.MappingStatus
                .PERSISTENCE_MAPPING_REQUIRES_OWNER_DECISION
                : "Do not invent a Track A V1 write mapping";
        assert playerSnapshot.questStates().get("projects:quest.test").equals(legacy);
    }

    private static void rewardClaimsAreDurableConcurrentAndPolicyDriven() throws Exception {
        MutableClock clock = new MutableClock(START);
        DurableFakeClaimStore store = new DurableFakeClaimStore(16);
        AtomicInteger deliveries = new AtomicInteger();
        RewardClaimService service = new RewardClaimService(store, request -> {
            deliveries.incrementAndGet();
            return new RewardDeliveryReceipt(
                    RewardDeliveryReceipt.Status.DELIVERED, "", true);
        }, receipt -> true, clock);
        RewardClaimRequest request = claim(900);
        RewardClaimResult first = service.claim(request);
        assert first.status() == RewardClaimResult.Status.DELIVERED;
        assert service.claim(claimWithNewPacket(request, 901)).replayed();
        assert deliveries.get() == 1;

        var executor = Executors.newFixedThreadPool(8);
        try {
            List<java.util.concurrent.Future<RewardClaimResult>> futures = new ArrayList<>();
            for (int i = 0; i < 16; i++) futures.add(executor.submit(() -> service.claim(request)));
            for (var future : futures) assert future.get(5, TimeUnit.SECONDS).status()
                    == RewardClaimResult.Status.DELIVERED;
        } finally {
            executor.shutdownNow();
        }
        assert deliveries.get() == 1 : "Concurrent claim delivered more than once";

        RewardClaimService restarted = new RewardClaimService(store, ignored -> {
            throw new AssertionError("Restart replay reached delivery");
        }, receipt -> true, clock);
        assert restarted.claim(request).replayed() : "Durable store must replay after restart";

        DurableFakeClaimStore crossInstanceStore = new DurableFakeClaimStore(4);
        AtomicInteger crossInstanceDeliveries = new AtomicInteger();
        java.util.function.Function<RewardClaimRequest, RewardClaimResult> crossClaim = value -> {
            RewardClaimService instance = new RewardClaimService(
                    crossInstanceStore, ignored -> {
                        crossInstanceDeliveries.incrementAndGet();
                        try {
                            Thread.sleep(20);
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(exception);
                        }
                        return new RewardDeliveryReceipt(
                                RewardDeliveryReceipt.Status.DELIVERED, "", true);
                    }, receipt -> true, clock);
            return instance.claim(value);
        };
        var crossExecutor = Executors.newFixedThreadPool(2);
        try {
            RewardClaimRequest crossRequest = claim(905);
            var left = crossExecutor.submit(() -> crossClaim.apply(crossRequest));
            var right = crossExecutor.submit(() -> crossClaim.apply(
                    claimWithNewPacket(crossRequest, 906)));
            assert left.get(5, TimeUnit.SECONDS).status()
                    == RewardClaimResult.Status.DELIVERED;
            assert right.get(5, TimeUnit.SECONDS).status()
                    == RewardClaimResult.Status.DELIVERED;
            assert crossInstanceDeliveries.get() == 1
                    : "Durable store admission must coordinate service instances";
        } finally {
            crossExecutor.shutdownNow();
        }

        assertTerminalFailure(RewardDeliveryReceipt.Status.FULL_INVENTORY,
                RewardClaimResult.Status.FULL_INVENTORY, 910, clock);
        assertTerminalFailure(RewardDeliveryReceipt.Status.PERSIST_FAILURE,
                RewardClaimResult.Status.PERSIST_FAILURE, 920, clock);
        assertTerminalFailure(RewardDeliveryReceipt.Status.COMMIT_UNCERTAIN,
                RewardClaimResult.Status.COMMIT_UNCERTAIN, 930, clock);

        AtomicInteger retryableCalls = new AtomicInteger();
        RewardClaimService retryableFull = new RewardClaimService(
                new DurableFakeClaimStore(4), ignored -> {
                    retryableCalls.incrementAndGet();
                    return new RewardDeliveryReceipt(
                            RewardDeliveryReceipt.Status.FULL_INVENTORY,
                            "inventory-full", true);
                }, receipt -> receipt.status() != RewardDeliveryReceipt.Status.FULL_INVENTORY,
                clock);
        RewardClaimRequest retryRequest = claim(940);
        assert !retryableFull.claim(retryRequest).terminal();
        assert !retryableFull.claim(retryRequest).replayed();
        assert retryableCalls.get() == 2
                : "Full-inventory retry semantics must come from policy, not Track F defaults";

        AtomicInteger throwingDeliveryCalls = new AtomicInteger();
        RewardClaimService deliveryFailure = new RewardClaimService(
                new DurableFakeClaimStore(4), ignored -> {
                    throwingDeliveryCalls.incrementAndGet();
                    throw new IllegalStateException("delivery-port-failure");
                }, receipt -> true, clock);
        RewardClaimRequest throwingRequest = claim(950);
        RewardClaimResult deliveryFailureResult = deliveryFailure.claim(throwingRequest);
        assert deliveryFailureResult.status() == RewardClaimResult.Status.COMMIT_UNCERTAIN;
        assert deliveryFailureResult.terminal();
        assert deliveryFailure.claim(throwingRequest).replayed();
        assert throwingDeliveryCalls.get() == 1
                : "Unknown delivery boundary must never be retried as safe";

        RewardClaimStore failingLookup = new RewardClaimStore() {
            @Override
            public Optional<RewardClaimResult> findTerminal(RewardClaimKey key) {
                throw new IllegalStateException("claim-store-unavailable");
            }

            @Override
            public RewardClaimResult executeExclusive(
                    RewardClaimKey key, UUID attemptId,
                    Supplier<RewardClaimResult> operation
            ) {
                throw new AssertionError("Admission must not run after lookup failure");
            }
        };
        RewardClaimResult storeFailure = new RewardClaimService(
                failingLookup, ignored -> {
                    throw new AssertionError("Delivery must not run after store failure");
                }, receipt -> true, clock).claim(claim(951));
        assert storeFailure.status() == RewardClaimResult.Status.CLAIM_STORE_FAILURE;
        assert !storeFailure.terminal();

        assertMandatoryTerminalIgnoresHostileRetryPolicy(
                RewardDeliveryReceipt.Status.DELIVERED, 952, clock);
        assertMandatoryTerminalIgnoresHostileRetryPolicy(
                RewardDeliveryReceipt.Status.COMMIT_UNCERTAIN, 953, clock);
    }

    private static void rewardDeliveryUsesStableTrackDTransactions() {
        MutableClock clock = new MutableClock(START);
        RewardClaimRequest claim = claim(1_000);
        UUID stableId = RewardTransactionIdentity.requestId(claim.key());
        assert stableId.equals(RewardTransactionIdentity.requestId(claim.key()));
        FakeRewardParticipant participant = new FakeRewardParticipant();
        TransactionEngine engine = new TransactionEngine(2, 8, clock);
        TransactionRewardDeliveryPort port = new TransactionRewardDeliveryPort(
                engine,
                request -> transactionRequest(request, stableId),
                request -> participant);
        assert port.deliver(claim).status() == RewardDeliveryReceipt.Status.DELIVERED;
        assert participant.calls.equals(List.of(TransactionStage.values()));
        assert port.deliver(claim).status() == RewardDeliveryReceipt.Status.DELIVERED;
        assert participant.calls.equals(List.of(TransactionStage.values()))
                : "Track D terminal replay must not deliver twice";

        TransactionEngine restarted = new TransactionEngine(2, 8, clock);
        TransactionRewardDeliveryPort restartedPort = new TransactionRewardDeliveryPort(
                restarted, request -> transactionRequest(request, stableId),
                request -> participant);
        assert restartedPort.deliver(claim).status() == RewardDeliveryReceipt.Status.DELIVERED;
        assert participant.calls.equals(List.of(TransactionStage.values()))
                : "Participant durable terminal closes restart replay window";

        TransactionRewardDeliveryPort unstable = new TransactionRewardDeliveryPort(
                engine, request -> transactionRequest(request, uuid(999_999)),
                request -> participant);
        assert unstable.deliver(claim).status() == RewardDeliveryReceipt.Status.REJECTED;
        engine.close();
        restarted.close();
    }

    private static void nearbyExperienceAndUnlockRemainPolicyBoundaries() {
        EncounterId encounter = new EncounterId(uuid(1_100));
        UUID player = uuid(1_101);
        ExperienceParticipationInput input = new ExperienceParticipationInput(
                encounter, player, true, 4.0, 2.0);
        NearbyExperiencePolicy fixture = (value, source) -> {
            boolean eligible = value.sameWorld() && value.distance() <= 5.0;
            return new NearbyExperiencePolicy.Decision(
                    eligible, eligible ? 0.25 : 0.0, "fixture:v1");
        };
        var proposals = new NearbyExperienceBoundary().propose(100,
                List.of(input, new ExperienceParticipationInput(
                        encounter, uuid(1_102), false, 1.0, 1.0)), fixture);
        assert proposals.size() == 1;
        assert proposals.getFirst().proposedExperience() == 25.0;
        assert proposals.getFirst().policyRevision().equals("fixture:v1");
        UnlockProposal unlock = new UnlockProposal(
                player, "projects:unlock/test-fixture", 4, START);
        assert unlock.unlockId().equals("projects:unlock/test-fixture")
                : "Track F must not invent a concrete endgame unlock ID";
    }

    private static void trackFFlagsStayDisabledAndPureApisStayBukkitFree() {
        FeatureFlagService flags = new FeatureFlagService();
        assert !flags.isEnabled(FeatureKey.PARTY);
        assert !flags.isEnabled(FeatureKey.QUESTS);
        assert !flags.isEnabled(FeatureKey.REWARD_V2);
        Set<String> playerFields = java.util.Arrays.stream(
                        io.github.gyai.projects.player.progress.PlayerProgressSnapshot.class
                                .getRecordComponents())
                .map(RecordComponent::getName).collect(java.util.stream.Collectors.toSet());
        assert !playerFields.contains("partyId") && !playerFields.contains("leaderId")
                && !playerFields.contains("invite") && !playerFields.contains("reconnectTimer")
                : "Temporary party state must not enter PlayerData";

        List<Class<?>> publicTypes = List.of(
                PartyId.class, io.github.gyai.projects.party.PartyMember.class,
                io.github.gyai.projects.party.PartySnapshot.class, PartyPolicy.class,
                io.github.gyai.projects.party.PartyInvite.class, PartyService.class,
                ParticipationEvent.class, ParticipationLedger.class,
                QuestProgressSnapshot.class, QuestProgressCommand.class,
                RewardClaimKey.class, RewardClaimService.class,
                TransactionRewardDeliveryPort.class, UnlockProposal.class);
        publicTypes.forEach(TrackFPartyQuestRewardFoundationTest::assertNoBukkitTypes);
    }

    private static void assertTerminalFailure(
            RewardDeliveryReceipt.Status deliveryStatus,
            RewardClaimResult.Status expected,
            int seed,
            Clock clock
    ) {
        AtomicInteger calls = new AtomicInteger();
        RewardClaimService service = new RewardClaimService(
                new DurableFakeClaimStore(4), request -> {
                    calls.incrementAndGet();
                    return new RewardDeliveryReceipt(deliveryStatus,
                            deliveryStatus.name().toLowerCase(), true);
                }, receipt -> true, clock);
        RewardClaimRequest request = claim(seed);
        assert service.claim(request).status() == expected;
        assert service.claim(request).replayed();
        assert calls.get() == 1;
    }

    private static void assertMandatoryTerminalIgnoresHostileRetryPolicy(
            RewardDeliveryReceipt.Status deliveryStatus,
            int seed,
            Clock clock
    ) {
        AtomicInteger calls = new AtomicInteger();
        RewardClaimService service = new RewardClaimService(
                new DurableFakeClaimStore(4), ignored -> {
                    calls.incrementAndGet();
                    return new RewardDeliveryReceipt(deliveryStatus, "fixture", true);
                }, receipt -> false, clock);
        RewardClaimRequest request = claim(seed);
        assert service.claim(request).terminal();
        assert service.claim(request).replayed();
        assert calls.get() == 1
                : "Caller policy cannot make delivered/uncertain claims retryable";
    }

    private static PartyPolicy policy(int size, int parties, int invites, int rate) {
        return new PartyPolicy(size, parties, invites, rate,
                Duration.ofMinutes(1), Duration.ofSeconds(30), Duration.ofSeconds(20));
    }

    private static ParticipationEvent event(
            EncounterId encounter, UUID player, String source, long revision,
            double contribution, ParticipationEvent.ContributionSemantics semantics
    ) {
        return new ParticipationEvent(new ParticipationKey(
                encounter, player, source, revision), contribution, semantics, START);
    }

    private static QuestProgressCommand command(
            UUID player, QuestDefinitionRef definition, QuestProgressCommand.Type type,
            long expected, Optional<String> target, long amount, int seed
    ) {
        return new QuestProgressCommand(uuid(2_000 + seed), player, definition,
                type, expected, target, amount);
    }

    private static RewardClaimRequest claim(int seed) {
        return new RewardClaimRequest(uuid(3_000 + seed), new RewardClaimKey(
                uuid(4_000 + seed), "projects:source/quest", uuid(5_000 + seed),
                "projects:reward/fixture", 1), START);
    }

    private static RewardClaimRequest claimWithNewPacket(
            RewardClaimRequest original, int packetSeed
    ) {
        return new RewardClaimRequest(uuid(6_000 + packetSeed), original.key(), START);
    }

    private static TransactionRequest transactionRequest(
            RewardClaimRequest claim, UUID requestId
    ) {
        return new TransactionRequest(requestId, claim.key().playerId(),
                "projects:operation/reward-claim", claim.key().rewardDefinitionId(),
                claim.key().rewardRevision(), 1,
                List.of(new TransactionRequest.InputRevision(
                        "projects:reward-source/" + claim.key().rewardSourceInstanceId(),
                        claim.key().rewardRevision())));
    }

    private static UUID uuid(long seed) { return new UUID(0, seed); }

    private static void expectIllegal(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static void expectUnsupported(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }

    private static void assertNoBukkitTypes(Class<?> type) {
        assert !type.getName().startsWith("org.bukkit.") : type;
        for (RecordComponent component : type.getRecordComponents() == null
                ? new RecordComponent[0] : type.getRecordComponents()) {
            assert !component.getGenericType().getTypeName().contains("org.bukkit.") : component;
        }
        for (Constructor<?> constructor : type.getConstructors()) {
            assert !constructor.toGenericString().contains("org.bukkit.") : constructor;
        }
        for (Method method : type.getMethods()) {
            if (method.getDeclaringClass() == Object.class) continue;
            assert !method.toGenericString().contains("org.bukkit.") : method;
        }
        for (Field field : type.getFields()) {
            assert !field.getGenericType().getTypeName().contains("org.bukkit.") : field;
        }
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) { this.current = current; }
        private void advance(Duration duration) { current = current.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return current; }
    }

    private static final class DurableFakeClaimStore implements RewardClaimStore {
        private final int maximum;
        private final ConcurrentHashMap<RewardClaimKey, RewardClaimResult> terminals =
                new ConcurrentHashMap<>();

        private DurableFakeClaimStore(int maximum) { this.maximum = maximum; }

        @Override
        public Optional<RewardClaimResult> findTerminal(RewardClaimKey key) {
            return Optional.ofNullable(terminals.get(key));
        }

        @Override
        public synchronized RewardClaimResult executeExclusive(
                RewardClaimKey key,
                UUID attemptId,
                Supplier<RewardClaimResult> operation
        ) {
            RewardClaimResult existing = terminals.get(key);
            if (existing != null) return existing.asReplay();
            if (terminals.size() >= maximum) {
                throw new IllegalStateException("claim-store-capacity");
            }
            RewardClaimResult candidate = operation.get();
            if (candidate.terminal()) terminals.put(key, candidate);
            return candidate;
        }
    }

    private static final class FakeRewardParticipant implements TransactionParticipant {
        private final List<TransactionStage> calls = new ArrayList<>();
        private final Map<UUID, TransactionAuditResult> terminals = new ConcurrentHashMap<>();

        @Override
        public Optional<TransactionAuditResult> findTerminal(TransactionRequest request) {
            return Optional.ofNullable(terminals.get(request.requestId()));
        }

        @Override
        public Validation validate(TransactionRequest request) {
            calls.add(TransactionStage.VALIDATE);
            return Validation.allow(InventoryCapacityProposal.reservedInventory(1));
        }

        @Override
        public ReservationToken reserve(
                TransactionRequest request, InventoryCapacityProposal capacityProposal
        ) {
            calls.add(TransactionStage.RESERVE);
            return new ReservationToken("reward-reservation");
        }

        @Override
        public void consume(TransactionRequest request, ReservationToken token) {
            calls.add(TransactionStage.CONSUME);
        }

        @Override
        public OutputProposal produce(TransactionRequest request, ReservationToken token) {
            calls.add(TransactionStage.PRODUCE);
            return new OutputProposal("projects:reward/proposal", 1, false);
        }

        @Override
        public void persist(
                TransactionRequest request, ReservationToken token, OutputProposal output
        ) {
            calls.add(TransactionStage.PERSIST);
        }

        @Override
        public TransactionAuditResult commit(
                TransactionRequest request, ReservationToken token, OutputProposal output,
                TransactionAuditResult proposedCommittedResult
        ) {
            calls.add(TransactionStage.COMMIT);
            terminals.put(request.requestId(), proposedCommittedResult);
            return proposedCommittedResult;
        }

        @Override
        public void recordTerminal(TransactionAuditResult terminalResult) {
            terminals.putIfAbsent(terminalResult.requestId(), terminalResult);
        }

        @Override
        public void rollback(
                TransactionRequest request, ReservationToken token,
                TransactionStage lastCompletedStage, OutputProposal output
        ) { }
    }
}
