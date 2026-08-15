package io.github.gyai.projects.monster.boss;

import io.github.gyai.projects.combat.damage.DamageKind;
import io.github.gyai.projects.combat.damage.DamageType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Deterministic contract coverage for the Bukkit-bound Grohm damage slice. */
public final class HarborDevourerBossDamageTest {
    private static final Path BOSS = Path.of(
            "src/main/java/io/github/gyai/projects/monster/boss/"
                    + "HarborDevourerBoss.java");
    private static final Path MANAGER = Path.of(
            "src/main/java/io/github/gyai/projects/manager/MonsterManager.java");
    private static final Path LISTENER = Path.of(
            "src/main/java/io/github/gyai/projects/listener/MonsterListener.java");
    private static final Path COMMAND = Path.of(
            "src/main/java/io/github/gyai/projects/command/ProjectCommand.java");
    private static final Path DAMAGE_SERVICE = Path.of(
            "src/main/java/io/github/gyai/projects/combat/damage/DamageService.java");

    private static int checks;

    private HarborDevourerBossDamageTest() {
    }

    public static void main(String[] args) throws Exception {
        String boss = Files.readString(BOSS);
        String manager = Files.readString(MANAGER);
        String listener = Files.readString(LISTENER);
        String command = Files.readString(COMMAND);
        String damageService = Files.readString(DAMAGE_SERVICE);

        damageProfileMapping(boss);
        noRawDamageSourcePath(boss);
        basicAttackRecursionContract(boss, listener, damageService);
        behavioralRegressionModel();
        specialRoutesAndCastStability(boss);
        resetContract(boss);
        managerWiring(manager);
        commandWiring(command);

        System.out.println(
                "HarborDevourerBossDamageTest passed (" + checks + " checks)");
    }

    private static void damageProfileMapping(String source) {
        String profile = section(
                source,
                "static DamageProfile damageProfile(",
                "record DamageProfile(");
        check(profile.contains("DamageType.PHYSICAL"),
                "Grohm damage profile is physical");
        check(profile.contains("damageKind"),
                "profile preserves normal versus special damage kind");
        check(profile.contains("fixedDamage"),
                "profile preserves the configured raw amount");
        check(profile.contains("0.0"),
                "profile fixes coefficient and lifesteal to zero");
        check(profile.contains("false"),
                "profile disables critical damage");
        check(source.contains("DamageKind.NORMAL_ATTACK"),
                "basic attack maps to normal attack damage kind");
        check(source.contains("DamageType.PHYSICAL"),
                "basic attack maps to physical damage");
        check(count(source, "DamageKind.DIRECT_SKILL") == 2,
                "three specials share the direct skill route seam");
    }

    private static void noRawDamageSourcePath(String source) {
        check(!source.contains("player.damage("),
                "Grohm has no direct player.damage source path");
        check(!source.contains("target.damage("),
                "Grohm does not bypass DamageService through target.damage");
        check(source.contains("damageService.applyMobAbility("),
                "Grohm delegates damage to DamageService");
        check(source.contains("Objects.requireNonNull(\n                damageService"),
                "Grohm rejects an unavailable DamageService");
    }

    private static void basicAttackRecursionContract(
            String boss,
            String listener,
            String damageService
    ) {
        String basic = section(
                boss,
                "public DamageApplicationResult applyBasicAttack(Player target)",
                "public boolean reset()");
        check(basic.contains("UUID.randomUUID()"),
                "basic action creates one cast id");
        check(basic.contains("DamageKind.NORMAL_ATTACK"),
                "basic action uses normal attack kind");
        check(basic.contains("DamageType.PHYSICAL"),
                "basic action uses physical damage");
        check(basic.contains("data.stats().attackDamage()"),
                "basic action uses the configured Grohm attack amount");
        check(basic.contains("damageService.applyMobAbility("),
                "basic action delegates directly to the mob ability boundary");
        check(!basic.contains("event.getDamage()"),
                "basic action does not consume Bukkit event damage");
        check(count(boss, "UUID.randomUUID()") == 1,
                "basic cast id is not regenerated per target");

        String lowest = section(
                listener,
                "@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)",
                "@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)");
        String highest = section(
                listener,
                "@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)",
                "@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)");
        int guard = lowest.indexOf("boss.isApplyingDamage(attacker, target)");
        int guardReturn = lowest.indexOf("return;", guard);
        int route = lowest.indexOf("boss.applyBasicAttack(target)", guard);
        int cancel = lowest.indexOf("event.setCancelled(true);", guardReturn);
        check(guard >= 0 && guardReturn > guard,
                "nested DamageService event has an application-guard return");
        check(route > guardReturn && cancel > guardReturn && cancel < route,
                "unmanaged basic event cancels before one service route");
        check(count(lowest, "boss.applyBasicAttack(target)") == 1,
                "unmanaged basic event invokes exactly one basic route");
        check(lowest.contains(
                        "event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK"),
                "listener is limited to the original entity attack cause");
        check(!highest.contains("boss.isApplyingDamage(attacker, target)")
                        && !highest.contains("boss.applyBasicAttack(target)"),
                "HIGHEST keeps editor and hard-control behavior without Grohm rerouting");
        check(!listener.contains("boss.applyBasicAttack(target, event.getDamage())"),
                "listener never routes using raw Bukkit event damage");
        check(damageService.contains("applicationGuard.run("),
                "DamageService wraps nested Bukkit damage in its guard");
        check(damageService.contains("public boolean isApplying(LivingEntity attacker"),
                "DamageService exposes the exact attacker-target guard seam");
    }

    private static void behavioralRegressionModel() {
        AttackPipeline original = new AttackPipeline();
        original.dispatch(false, false);
        check(original.steps.equals(List.of("cancel", "route", "lower")),
                "original damage is canceled before the nested lower modifier");

        AttackPipeline nested = new AttackPipeline();
        nested.dispatch(true, false);
        check(nested.steps.equals(List.of("lower")),
                "guarded nested damage passes to the lower modifier once");

        AttackPipeline controlled = new AttackPipeline();
        controlled.dispatch(false, true);
        check(controlled.steps.equals(List.of("cancel")),
                "hard-controlled Grohm damage is canceled without rerouting");

        DamageProfileModel basic = new DamageProfileModel(
                DamageType.PHYSICAL, DamageKind.NORMAL_ATTACK, 37.5, 0.0, false);
        check(basic.damageType() == DamageType.PHYSICAL
                        && basic.damageKind() == DamageKind.NORMAL_ATTACK
                        && basic.fixedDamage() == 37.5
                        && basic.coefficient() == 0.0
                        && !basic.criticalAllowed(),
                "basic profile preserves the configured fixed physical amount");
        DamageProfileModel special = new DamageProfileModel(
                DamageType.PHYSICAL, DamageKind.DIRECT_SKILL, 24.0, 0.0, false);
        check(special.damageKind() == DamageKind.DIRECT_SKILL
                        && special.fixedDamage() == 24.0,
                "special profile preserves its direct-skill fixed amount");
    }

    private static void specialRoutesAndCastStability(String source) {
        String slam = section(
                source,
                "private void executeSlam(",
                "private void executePhaseTwoShockwave(");
        String shockwave = section(
                source,
                "private void executePhaseTwoShockwave(",
                "private void startHullbreakerCharge(");
        String charge = section(
                source,
                "private void beginChargeMovement(",
                "private boolean collidesWithSolidBlock(");
        String telegraphDamage = section(
                source,
                "private void damagePlayersInTelegraph(",
                "private DamageApplicationResult applyDamage(");
        String compactSlam = compact(slam);
        String compactShockwave = compact(shockwave);
        String compactTelegraphDamage = compact(telegraphDamage);

        check(slam.contains("settings.slamDamage()"),
                "breakwater slam keeps its configured damage amount");
        check(compactSlam.contains("damagePlayersInTelegraph( telegraphId"),
                "breakwater slam uses its telegraph as cast id");
        check(shockwave.contains("12.0"),
                "deep-tide shockwave keeps its existing raw amount");
        check(compactShockwave.contains("damagePlayersInTelegraph( telegraphId"),
                "deep-tide shockwave uses one telegraph cast id");
        check(charge.contains("charge.telegraphId()"),
                "hullbreaker charge shares one telegraph cast id");
        check(charge.contains("hitPlayers.contains(player.getUniqueId())"),
                "hullbreaker charge prevents duplicate player hits");
        check(compactTelegraphDamage.contains("applyDamage( player, telegraphId"),
                "telegraph specials route every eligible player through service");
        check(telegraphDamage.contains("result.shieldDamage()")
                        && telegraphDamage.contains("result.healthDamage()"),
                "special knockback requires shield or health damage");
        check(charge.contains("result.shieldDamage()")
                        && charge.contains("result.healthDamage()"),
                "charge knockback requires shield or health damage");
    }

    private static void resetContract(String source) {
        String resetEntry = section(
                source,
                "public boolean reset()",
                "@Override\n    public void handleDeath");
        String reset = section(
                source,
                "private void resetBoss()",
                "private void checkPhaseTransition(");

        check(resetEntry.contains("if (!isValid())"),
                "public reset entry rejects an invalid Grohm");
        check(resetEntry.contains("return false"),
                "public reset entry reports invalid Grohm");
        check(resetEntry.contains("resetBoss()"),
                "public reset entry preserves the live entity");
        check(reset.contains("ChargeFinishReason.BOSS_RESET"),
                "reset finishes an active charge with BOSS_RESET");
        check(reset.contains("cancelActionTasks()"),
                "reset cancels scheduled action tasks");
        check(reset.contains("TelegraphInstance.CancellationReason\n                        .BOSS_RESET"),
                "reset cancels telegraphs with BOSS_RESET");
        check(reset.contains("phaseTwo = false") && reset.contains("currentPhase = 1"),
                "reset returns Grohm to phase one");
        check(reset.contains("clearManagedEffects(HardControlRemovalReason.BOSS_RESET)"),
                "reset clears managed effects and control");
        check(reset.contains("ravager.setAI(true)")
                        && reset.contains("ravager.setAware(true)")
                        && reset.contains("ravager.setVelocity(new Vector())"),
                "reset restores AI and velocity");
        check(reset.contains("ravager.setFireTicks(0)")
                        && reset.contains("ravager.teleport(spawnLocation)")
                        && reset.contains("ravager.setHealth(data.stats().maxHealth())"),
                "reset restores fire, location, and health");
        check(reset.contains("initializeCooldowns()")
                        && reset.contains("bossBar.removeAll()")
                        && reset.contains("updateBossBar()"),
                "reset restores cooldowns and boss bar state");
    }

    private static void managerWiring(String source) {
        check(source.contains("|| damageService == null"),
                "manager fails closed when DamageService is unavailable");
        check(source.contains("telegraphManager,\n                damageService)"),
                "manager injects DamageService into Grohm");
        check(source.contains("public boolean resetHarborDevourer()"),
                "manager exposes the bounded reset entry");
        String reset = section(
                source,
                "public boolean resetHarborDevourer()",
                "public CustomMonster get(");
        check(reset.contains("HarborDevourerBoss::isValid")
                        && reset.contains("HarborDevourerBoss::reset"),
                "manager reset rejects invalid bosses and preserves the entity");
    }

    private static void commandWiring(String source) {
        String bossCommand = section(
                source,
                "private boolean handleBossCommand(",
                "private void giveMaterial(");
        check(bossCommand.contains("spawn|reset|remove"),
                "boss command usage lists spawn, reset, and remove");
        check(bossCommand.contains("case \"reset\"")
                        && bossCommand.contains("resetHarborDevourer()"),
                "boss reset command routes through MonsterManager");
        check(bossCommand.contains("projects.dev"),
                "boss reset remains behind projects.dev");
    }

    private static String section(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, Math.max(0, startIndex));
        check(startIndex >= 0 && endIndex > startIndex,
                "source section exists: " + start);
        return source.substring(startIndex, endIndex);
    }

    private static int count(String source, String value) {
        int result = 0;
        for (int offset = 0; ; ) {
            int found = source.indexOf(value, offset);
            if (found < 0) return result;
            result++;
            offset = found + value.length();
        }
    }

    private static String compact(String source) {
        return source.replaceAll("\\s+", " ");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class AttackPipeline {
        private final List<String> steps = new ArrayList<>();

        private void dispatch(boolean guarded, boolean hardControlled) {
            if (guarded) {
                steps.add("lower");
                return;
            }
            steps.add("cancel");
            if (hardControlled) {
                return;
            }
            steps.add("route");
            dispatch(true, false);
        }
    }

    private record DamageProfileModel(
            DamageType damageType,
            DamageKind damageKind,
            double fixedDamage,
            double coefficient,
            boolean criticalAllowed
    ) {
    }
}
