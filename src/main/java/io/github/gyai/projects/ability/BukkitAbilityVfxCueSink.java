package io.github.gyai.projects.ability;

import io.github.gyai.projects.network.AbilityVfxPacket;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Bounded snapshot broadcaster; listening support is optional and has no gameplay meaning. */
final class BukkitAbilityVfxCueSink implements AbilityVisualAdapter.CueSink {
    private static final double MAX_DISTANCE_SQUARED=128*128; private final JavaPlugin plugin;
    BukkitAbilityVfxCueSink(JavaPlugin plugin){this.plugin=plugin;}
    @Override public void send(AbilityVfxPacket.Cue cue) {
        try { byte[] bytes=AbilityVfxPacket.encode(cue); for(Player p:plugin.getServer().getOnlinePlayers()) try {
            if(!p.getWorld().getUID().equals(cue.anchor().worldId()) || p.getLocation().distanceSquared(new org.bukkit.Location(p.getWorld(),cue.anchor().x(),cue.anchor().y(),cue.anchor().z()))>MAX_DISTANCE_SQUARED || !p.getListeningPluginChannels().contains(AbilityVfxPacket.CHANNEL)) continue;
            p.sendPluginMessage(plugin,AbilityVfxPacket.CHANNEL,bytes);
        } catch(RuntimeException ignored) { } } catch(RuntimeException ignored) { }
    }
}
