package io.github.gyai.projects.ability;

import io.github.gyai.projects.manager.MonsterManager;
import io.github.gyai.projects.monster.editor.MobDefinition;
import io.github.gyai.projects.monster.editor.MobStatsDefinition;
import io.github.gyai.projects.ability.editor.SkillVfxEditorService;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Objects;

/** Dev-only entry point; both commands deliberately resolve one registry object. */
public final class DevAbilityService implements AutoCloseable {
    private final AbilityRegistry registry;
    private final MobAbilityAssignmentPolicy assignments;
    private final BukkitAbilityRuntime runtime;
    private final MonsterManager monsters;
    private final SkillVfxEditorService skillVfxEditor;
    public DevAbilityService(BukkitAbilityRuntime runtime, MonsterManager monsters) {
        this.runtime = runtime; this.monsters = monsters;
        registry = new AbilityRegistry(runtime.runtime().actionRegistry());
        registry.register(DevAbilityDefinitions.sharedArcaneBurst());
        assignments = new MobAbilityAssignmentPolicy(registry);
        skillVfxEditor = new SkillVfxEditorService(registry, runtime.visualRegistry());
        runtime.setVisualResolver(skillVfxEditor);
        monsters.configureEditorAbilityCaster(this::autoCastAssignedMob);
    }
    public AbilityRegistry registry() { return registry; }
    public SkillVfxEditorService skillVfxEditor() { return skillVfxEditor; }
    public Result castPlayer(Player player) {
        Entity sight = player.getTargetEntity(48);
        if (!(sight instanceof LivingEntity target) || target == player) return Result.failure("生体ターゲットを見てください。");
        AbilityDefinition definition = registry.find(DevAbilityDefinitions.SHARED_ARCANE_BURST_ID).orElseThrow();
        runtime.runtime().cast(definition, runtime.playerContext(player, target, definition.id()));
        return Result.success("共有アビリティをプレイヤーから発動しました。");
    }
    public Result castMob(Player player) {
        Entity sight = player.getTargetEntity(48);
        if (!(sight instanceof LivingEntity source) || !monsters.isEditorMonster(source)
                || monsters.editorStats(source) == null) return Result.failure("Editor Mobを見てください。");
        AbilityDefinition definition = registry.find(DevAbilityDefinitions.SHARED_ARCANE_BURST_ID).orElseThrow();
        runtime.runtime().cast(definition, runtime.mobContext(source, monsters.editorStats(source), player, definition.id()));
        return Result.success("共有アビリティをEditor Mobから発動しました。");
    }
    /** Dev-only explicit assignment path; it never selects an implicit ability. */
    public Result castMobAssigned(Player player, String abilityId) {
        Entity sight = player.getTargetEntity(48);
        if (!(sight instanceof LivingEntity source) || !monsters.isEditorMonster(source)) {
            return Result.failure("Editor Mobを見てください。");
        }
        MobDefinition definition = monsters.editorDefinition(source);
        MobAbilityAssignmentPolicy.Resolution resolved = assignments.resolve(
                definition, abilityId);
        if (!resolved.resolved()) {
            return Result.failure(switch (resolved.status()) {
                case MALFORMED -> "Ability IDが不正です。";
                case UNASSIGNED -> "そのAbilityはMobへ未割り当てです。";
                case ASSIGNED_BUT_UNKNOWN -> "割り当て済みAbilityがレジストリにありません。";
                case RESOLVED -> throw new IllegalStateException("resolved state handled");
            });
        }
        MobStatsDefinition stats = monsters.editorStats(source);
        if (stats == null) {
            return Result.failure("Editor Mobの状態を取得できません。");
        }
        AbilityDefinition ability = resolved.definition();
        runtime.runtime().cast(ability,
                runtime.mobContext(source, stats, player, ability.id()));
        return Result.success("割り当て済みAbilityをEditor Mobから発動しました。");
    }

    private AbilityRuntime.Cast autoCastAssignedMob(
            LivingEntity source,
            MobDefinition definition,
            Player target
    ) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(target, "target");
        MobAbilityAssignmentPolicy.Resolution resolved =
                assignments.resolveFirst(definition);
        if (!resolved.resolved()) return null;
        AbilityDefinition ability = resolved.definition();
        return runtime.runtime().cast(ability,
                runtime.mobContext(source, definition.stats(), target, ability.id()));
    }

    @Override public void close() { runtime.close(); }
    public record Result(boolean success, String message) { static Result success(String value) { return new Result(true, value); } static Result failure(String value) { return new Result(false, value); } }
}
