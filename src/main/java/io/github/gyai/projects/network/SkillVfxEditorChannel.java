package io.github.gyai.projects.network;

import io.github.gyai.projects.ability.editor.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;
import java.util.List;

/** Permission-gated transport; all mutation authority remains in SkillVfxEditorService. */
public final class SkillVfxEditorChannel implements PluginMessageListener {
    public static final String REQUEST_CHANNEL="projects:skill_editor_req_v1", STATE_CHANNEL="projects:skill_editor_state_v1";
    public static final String OPEN_PERMISSION="projects.skilleditor.open", PREVIEW_PERMISSION="projects.vfxeditor.preview", APPLY_PERMISSION="projects.vfxeditor.session.apply";

    /** Testable sender boundary; authorization happens before the request is decoded. */
    public interface Sender {
        boolean hasPermission(String permission);
        void send(byte[] payload);
    }

    private final JavaPlugin plugin;
    private final SkillVfxEditorServiceAccess service;

    public SkillVfxEditorChannel(JavaPlugin plugin, SkillVfxEditorServiceAccess service) {
        this.plugin=plugin;
        this.service=service;
    }

    public void register() {
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, REQUEST_CHANNEL, this);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, STATE_CHANNEL);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, SkillVfxEditorChannelV2.REQUEST_CHANNEL, new SkillVfxEditorChannelV2(plugin,service));
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, SkillVfxEditorChannelV2.STATE_CHANNEL);
    }

    @Override public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] payload) {
        if(!REQUEST_CHANNEL.equals(channel)) return;
        dispatch(payload, new Sender() {
            @Override public boolean hasPermission(String permission) { return player.hasPermission(permission); }
            @Override public void send(byte[] state) { player.sendPluginMessage(plugin, STATE_CHANNEL, state); }
        }, service);
    }

    /** No request bytes are decoded until the sender has editor-open authority. */
    public static void dispatch(byte[] payload, Sender sender, SkillVfxEditorServiceAccess service) {
        if(!sender.hasPermission(OPEN_PERMISSION)) return;
        SkillVfxEditorProtocol.Request request=null;
        try {
            request=SkillVfxEditorProtocol.decodeRequest(payload);
            var access=SkillVfxEditorAuthorization.decide(
                    request.operation(), true, sender.hasPermission(PREVIEW_PERMISSION), sender.hasPermission(APPLY_PERMISSION));
            if(!access.allowed()) {
                denied(sender, request, access.previewAllowed());
                return;
            }
            switch(request.operation()) {
                case CATALOG -> send(sender, ok(sender, request, service, service.catalog(), null));
                case FETCH -> send(sender, ok(sender, request, service, List.of(), service.snapshot(request.abilityId())));
                case APPLY_VISUAL_SESSION -> send(sender, ok(sender, request, service, List.of(),
                        service.applyV1(request.session(), request.abilityId(), request.revision(), request.baseFingerprint(), request.effectiveFingerprint(), request.visual())));
                case REVERT_VISUAL_SESSION -> send(sender, ok(sender, request, service, List.of(),
                        service.revert(request.session(), request.abilityId(), request.revision(), request.baseFingerprint(), request.effectiveFingerprint())));
            }
        } catch(RuntimeException error) {
            if(request!=null) reply(sender, request, service, SkillVfxEditorStatusMapper.map(error), "rejected");
        }
    }

    private static SkillVfxEditorProtocol.State ok(Sender sender, SkillVfxEditorProtocol.Request request, SkillVfxEditorServiceAccess service, List<SkillVfxEditorService.CatalogItem> catalog, SkillVfxEditorService.Snapshot snapshot) {
        return new SkillVfxEditorProtocol.State(SkillVfxEditorProtocol.Status.OK, request.correlation(), service.serverSession(), catalog, snapshot, sender.hasPermission(PREVIEW_PERMISSION), "ok");
    }

    /** Operation authorization is terminal but deliberately contains no server-owned editor state. */
    private static void denied(Sender sender, SkillVfxEditorProtocol.Request request, boolean previewAllowed) {
        send(sender, new SkillVfxEditorProtocol.State(SkillVfxEditorProtocol.Status.PERMISSION_DENIED, request.correlation(), request.session(), List.of(), null, previewAllowed, "permission denied"));
    }

    private static SkillVfxEditorService.Snapshot safe(SkillVfxEditorServiceAccess service, String abilityId) {
        try { return abilityId==null || abilityId.isEmpty() ? null : service.snapshot(abilityId); }
        catch(RuntimeException ignored) { return null; }
    }

    private static void reply(Sender sender, SkillVfxEditorProtocol.Request request, SkillVfxEditorServiceAccess service, SkillVfxEditorProtocol.Status status, String message) {
        send(sender, new SkillVfxEditorProtocol.State(status, request.correlation(), service.serverSession(), List.of(), safe(service, request.abilityId()), sender.hasPermission(PREVIEW_PERMISSION), message));
    }

    private static void send(Sender sender, SkillVfxEditorProtocol.State state) {
        try { sender.send(SkillVfxEditorProtocol.encodeState(state)); }
        catch(RuntimeException ignored) { }
    }
}
