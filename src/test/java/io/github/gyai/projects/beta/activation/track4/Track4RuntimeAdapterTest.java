package io.github.gyai.projects.beta.activation.track4;

import io.github.gyai.projects.beta.activation.*;
import io.github.gyai.projects.network.beta.*;
import io.github.gyai.projects.party.*;
import io.github.gyai.projects.reward.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class Track4RuntimeAdapterTest {
    private static final Instant NOW = Instant.parse("2026-08-05T00:00:00Z");

    public static void main(String[] args) {
        partyLifecycleAndIsolation();
        questDedupAndRewardExactlyOnce();
        protocolRegistersOnlyWhileRunning();
        defaultsRemainDisconnected();
        sourceBoundariesRemainUntouched();
        System.out.println("Track4RuntimeAdapterTest passed");
    }

    private static void partyLifecycleAndIsolation() {
        UUID leader = UUID.randomUUID(); UUID member = UUID.randomUUID();
        StagingPartyRuntime runtime = new StagingPartyRuntime(new PartyService(
                new PartyPolicy(4, 32, 64, 8, Duration.ofSeconds(10),
                        Duration.ofMinutes(1), Duration.ofMinutes(2)), clock()), policy());
        StagingPartyRuntime.Context leaderContext = context(leader);
        StagingPartyRuntime.Context memberContext = context(member);
        PartyId party = new PartyId(UUID.randomUUID()); UUID invite = UUID.randomUUID();
        assert runtime.create(leaderContext, party, leader).status() == PartyCommandResult.Status.CREATED;
        assert runtime.invite(leaderContext, invite, party, leader, member).status()
                == PartyCommandResult.Status.INVITED;
        assert runtime.accept(memberContext, invite).status() == PartyCommandResult.Status.ACCEPTED;
        assert runtime.chatRecipients(memberContext, party).equals(List.of(leader, member));
        assert runtime.onQuit(memberContext).status() == PartyCommandResult.Status.DISCONNECTED;
        assert runtime.onJoin(memberContext).status() == PartyCommandResult.Status.RECONNECTED;
        assert runtime.leave(memberContext).status() == PartyCommandResult.Status.LEFT;
        runtime.close(); runtime.close();
    }

    private static void questDedupAndRewardExactlyOnce() {
        UUID player = UUID.randomUUID(), encounter = UUID.randomUUID(), target = UUID.randomUUID();
        InMemoryClaimStore claims = new InMemoryClaimStore();
        AtomicInteger deliveries = new AtomicInteger();
        StagingItemDeliveryPort delivery = new StagingItemDeliveryPort() {
            @Override public RewardDeliveryReceipt deliver(RewardClaimRequest claim,
                    String item, int quantity) {
                assert item.equals(Track4StagingIds.TOKEN) && quantity == 1;
                deliveries.incrementAndGet();
                return new RewardDeliveryReceipt(RewardDeliveryReceipt.Status.DELIVERED, "", true);
            }
            @Override public boolean available() { return true; }
        };
        StagingTrainingDummyQuestRuntime runtime = new StagingTrainingDummyQuestRuntime(
                policy(), clock(), null, claims, delivery);
        StagingTrainingDummyQuestRuntime.DirectHit first = hit(encounter, player, target, 1);
        assert runtime.record(first).status() == StagingTrainingDummyQuestRuntime.HitStatus.MEMORY_ONLY;
        assert runtime.record(first).status() == StagingTrainingDummyQuestRuntime.HitStatus.DUPLICATE;
        for (int i = 2; i <= 10; i++) assert runtime.record(
                hit(encounter, player, target, i)).progress().isPresent();
        assert runtime.snapshot(player).orElseThrow().completionMarked();
        var request = new StagingTrainingDummyQuestRuntime.ClaimRequest(
                encounter, player, "world", true, true);
        assert runtime.claim(request).status() == StagingTrainingDummyQuestRuntime.ClaimStatus.DELIVERED;
        assert runtime.claim(request).status() == StagingTrainingDummyQuestRuntime.ClaimStatus.REPLAYED;
        assert deliveries.get() == 1 : "reward delivery must be exactly once";
        assert runtime.trackedHitCount() == 10;
        runtime.close(); runtime.close();
    }

    private static void protocolRegistersOnlyWhileRunning() {
        RecordingChannels channels = new RecordingChannels();
        BetaCapabilitySessionService sessions = new BetaCapabilitySessionService(
                BetaCapabilityPolicy.wave3Defaults(), clock());
        BetaCommandRouter router = new BetaCommandRouter(new BetaRateLimiter(32, clock()),
                (context, envelope) -> BetaCommandAuthorization.Decision.allow(), 32);
        ClientBetaProtocolRuntime protocol = new ClientBetaProtocolRuntime(channels, sessions,
                router, (player, capability) -> true);
        assert protocol.registrationCount() == 0;
        protocol.start(); protocol.start();
        assert protocol.registrationCount() == 4;
        assert channels.active.size() == 4;
        assert Set.copyOf(channels.names).equals(Set.of(BetaChannels.CAPABILITIES,
                BetaChannels.ACKNOWLEDGEMENT, BetaChannels.STATE, BetaChannels.COMMAND));
        protocol.close(); protocol.close();
        assert protocol.registrationCount() == 0 && channels.active.isEmpty();
    }

    private static void defaultsRemainDisconnected() {
        assert BetaActivationPolicy.defaults().audience() == BetaActivationAudience.OFF;
        assert BetaActivationPolicy.defaults().mutationPolicy() == BetaMutationPolicy.READ_ONLY;
        assert StagingMobEditorRuntime.RELATIVE_STAGING_ROOT.toString()
                .replace('\\', '/').equals("plugins/ProjectS/beta-staging/mobs");
    }

    private static void sourceBoundariesRemainUntouched() {
        assert !read("src/main/java/io/github/gyai/projects/ProjectSPlugin.java")
                .contains("Track4RuntimeModuleProvider");
        assert !read("src/main/java/io/github/gyai/projects/command/ProjectCommand.java")
                .contains("beta staging track4");
        assert !read("src/main/resources/config.yml").contains("activation-track-4");
    }

    private static String read(String path) {
        try { return java.nio.file.Files.readString(java.nio.file.Path.of(path)); }
        catch (java.io.IOException e) { throw new AssertionError(e); }
    }

    private static StagingTrainingDummyQuestRuntime.DirectHit hit(
            UUID encounter, UUID player, UUID target, long revision) {
        return new StagingTrainingDummyQuestRuntime.DirectHit(encounter,
                UUID.nameUUIDFromBytes(("hit-" + revision).getBytes()), player, target,
                "world", revision, NOW.plusSeconds(revision), true, true, true, true);
    }

    private static StagingPartyRuntime.Context context(UUID player) {
        return new StagingPartyRuntime.Context(player, "world", true, true);
    }

    private static BetaActivationPolicy policy() {
        return new BetaActivationPolicy(BetaActivationAudience.GLOBAL,
                BetaActivationTargetScope.TRAINING_DUMMY_ONLY,
                BetaMutationPolicy.STAGING_WRITE, Set.of(), Set.of("world"), true, false);
    }

    private static Clock clock() { return Clock.fixed(NOW, ZoneOffset.UTC); }

    private static final class RecordingChannels implements BetaChannelRegistrar {
        final Set<String> active = new LinkedHashSet<>(); final List<String> names = new ArrayList<>();
        @Override public void register(String channel, Direction direction) {
            active.add(channel + ":" + direction); names.add(channel);
        }
        @Override public void unregister(String channel, Direction direction) {
            active.remove(channel + ":" + direction);
        }
    }

    private static final class InMemoryClaimStore implements RewardClaimStore {
        private final Map<RewardClaimKey, RewardClaimResult> values = new HashMap<>();
        @Override public synchronized Optional<RewardClaimResult> findTerminal(RewardClaimKey key) {
            return Optional.ofNullable(values.get(key));
        }
        @Override public synchronized RewardClaimResult executeExclusive(RewardClaimKey key,
                UUID attempt, java.util.function.Supplier<RewardClaimResult> operation) {
            RewardClaimResult existing = values.get(key);
            if (existing != null) return existing.asReplay();
            RewardClaimResult result = operation.get();
            if (result.terminal()) values.put(key, result);
            return result;
        }
    }
}
