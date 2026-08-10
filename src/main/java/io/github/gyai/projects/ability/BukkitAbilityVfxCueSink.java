package io.github.gyai.projects.ability;

import io.github.gyai.projects.network.AbilityVfxPacket;
import io.github.gyai.projects.network.AbilityVfxPacketV2;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Bounded snapshot broadcaster; listening support is optional and has no gameplay meaning. */
final class BukkitAbilityVfxCueSink implements AbilityVisualAdapter.CueSink {
    private static final double MAX_DISTANCE_SQUARED=128*128; private final JavaPlugin plugin;
    BukkitAbilityVfxCueSink(JavaPlugin plugin){this.plugin=plugin;plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin,AbilityVfxPacketV2.CHANNEL);}
    @Override public void send(AbilityVfxPacket.Cue cue) {
        try { byte[] motionBytes=AbilityVfxPacketV2.encode(cue); java.util.Optional<AbilityVfxPacket.Cue> legacy=AbilityVfxPacket.legacyOnly(cue); for(Player p:plugin.getServer().getOnlinePlayers()) try {
            if(!p.getWorld().getUID().equals(cue.anchor().worldId()) || p.getLocation().distanceSquared(new org.bukkit.Location(p.getWorld(),cue.anchor().x(),cue.anchor().y(),cue.anchor().z()))>MAX_DISTANCE_SQUARED) continue;
            if(p.getListeningPluginChannels().contains(AbilityVfxPacketV2.CHANNEL)) { p.sendPluginMessage(plugin,AbilityVfxPacketV2.CHANNEL,motionBytes); continue; }
            if(legacy.isPresent() && p.getListeningPluginChannels().contains(AbilityVfxPacket.CHANNEL)) p.sendPluginMessage(plugin,AbilityVfxPacket.CHANNEL,AbilityVfxPacket.encode(legacy.get()));
        } catch(RuntimeException ignored) { } } catch(RuntimeException ignored) { }
    }
}
