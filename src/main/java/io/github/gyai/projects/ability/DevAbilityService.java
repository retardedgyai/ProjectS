package io.github.gyai.projects.ability;

import io.github.gyai.projects.manager.MonsterManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/** Dev-only entry point; both commands deliberately resolve one registry object. */
public final class DevAbilityService implements AutoCloseable {
    private final AbilityRegistry registry;
    private final BukkitAbilityRuntime runtime;
    private final MonsterManager monsters;
    public DevAbilityService(BukkitAbilityRuntime runtime, MonsterManager monsters) {
        this.runtime = runtime; this.monsters = monsters;
        registry = new AbilityRegistry(runtime.runtime().actionRegistry());
        registry.register(DevAbilityDefinitions.sharedArcaneBurst());
    }
    public AbilityRegistry registry() { return registry; }
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
    @Override public void close() { runtime.close(); }
    public record Result(boolean success, String message) { static Result success(String value) { return new Result(true, value); } static Result failure(String value) { return new Result(false, value); } }
}
