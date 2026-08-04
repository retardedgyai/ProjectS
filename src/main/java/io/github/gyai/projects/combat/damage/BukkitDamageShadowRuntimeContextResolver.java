package io.github.gyai.projects.combat.damage;

import io.github.gyai.projects.dummy.TrainingDummyManager;
import io.github.gyai.projects.manager.EnhancementManager;
import io.github.gyai.projects.manager.MonsterManager;
import io.github.gyai.projects.monster.CustomMonster;
import io.github.gyai.projects.monster.MonsterRank;
import org.bukkit.entity.Monster;
import org.bukkit.inventory.ItemStack;

import java.time.Clock;
import java.util.Objects;

/** Reads Bukkit state once before legacy application and retains no entities. */
public final class BukkitDamageShadowRuntimeContextResolver
        implements DamageShadowRuntimeContextResolver {
    private final TrainingDummyManager dummyManager;
    private final MonsterManager monsterManager;
    private final EnhancementManager enhancementManager;
    private final Clock clock;

    public BukkitDamageShadowRuntimeContextResolver(
            TrainingDummyManager dummyManager,
            MonsterManager monsterManager,
            EnhancementManager enhancementManager,
            Clock clock
    ) {
        this.dummyManager = Objects.requireNonNull(
                dummyManager, "dummyManager");
        this.monsterManager = Objects.requireNonNull(
                monsterManager, "monsterManager");
        this.enhancementManager = Objects.requireNonNull(
                enhancementManager, "enhancementManager");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public DamageShadowRuntimeContext resolve(DamageRequest request) {
        ItemStack weapon = request.attacker()
                .getInventory().getItemInMainHand();
        return new DamageShadowRuntimeContext(
                clock.instant(),
                request.attacker().getUniqueId(),
                request.target().getUniqueId(),
                targetType(request),
                StarterSwordDamageShadow.ITEM_ID,
                enhancementManager.getLevel(weapon));
    }

    private DamageShadowTargetType targetType(DamageRequest request) {
        if (dummyManager.isTrainingDummy(request.target())) {
            return DamageShadowTargetType.TRAINING_DUMMY;
        }
        CustomMonster custom = monsterManager.get(
                request.target().getUniqueId());
        if (custom != null) {
            MonsterRank rank = custom.getData().rank();
            return switch (rank) {
                case NORMAL -> DamageShadowTargetType.NORMAL_MONSTER;
                case ELITE -> DamageShadowTargetType.ELITE;
                case BOSS -> DamageShadowTargetType.BOSS;
            };
        }
        if (request.target() instanceof Monster) {
            return DamageShadowTargetType.NORMAL_MONSTER;
        }
        return DamageShadowTargetType.OTHER;
    }
}
