package io.github.gyai.projects.network;

import io.github.gyai.projects.ability.editor.SkillVfxEditorAuthorization;
import io.github.gyai.projects.ability.editor.SkillVfxEditorProtocol;
import io.github.gyai.projects.ability.editor.SkillVfxEditorProtocolV3;
import io.github.gyai.projects.ability.editor.SkillVfxEditorService;
import io.github.gyai.projects.ability.editor.SkillVfxEditorServiceAccess;
import io.github.gyai.projects.ability.editor.SkillVfxEditorStatusMapper;
import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

/** Motion-aware editor transport; v1/v2 remain independently registered. */
public final class SkillVfxEditorChannelV3 implements PluginMessageListener {
    public static final String REQUEST_CHANNEL = "projects:skill_editor_req_v3";
    public static final String STATE_CHANNEL = "projects:skill_editor_state_v3";

    private final JavaPlugin plugin;
    private final SkillVfxEditorServiceAccess service;

    public SkillVfxEditorChannelV3(JavaPlugin plugin, SkillVfxEditorServiceAccess service) {
        this.plugin = plugin;
        this.service = service;
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player,
                                        byte @NotNull [] payload) {
        if (!REQUEST_CHANNEL.equals(channel)) return;
        dispatch(payload, new SkillVfxEditorChannel.Sender() {
            @Override public boolean hasPermission(String permission) { return player.hasPermission(permission); }
            @Override public void send(byte[] state) { player.sendPluginMessage(plugin, STATE_CHANNEL, state); }
        }, service);
    }

    public static void dispatch(byte[] payload, SkillVfxEditorChannel.Sender sender,
                                SkillVfxEditorServiceAccess service) {
        if (!sender.hasPermission(SkillVfxEditorChannel.OPEN_PERMISSION)) return;
        SkillVfxEditorProtocol.Request request = null;
        try {
            request = SkillVfxEditorProtocolV3.decodeRequest(payload);
            var access = SkillVfxEditorAuthorization.decide(request.operation(), true,
                    sender.hasPermission(SkillVfxEditorChannel.PREVIEW_PERMISSION),
                    sender.hasPermission(SkillVfxEditorChannel.APPLY_PERMISSION));
            if (!access.allowed()) {
                send(sender, new SkillVfxEditorProtocol.State(SkillVfxEditorProtocol.Status.PERMISSION_DENIED,
                        request.correlation(), request.session(), List.of(), null, access.previewAllowed(),
                        "permission denied"));
                return;
            }
            boolean preview = sender.hasPermission(SkillVfxEditorChannel.PREVIEW_PERMISSION);
            switch (request.operation()) {
                case CATALOG -> send(sender, state(request, service, List.of(), null, preview));
                case FETCH -> send(sender, state(request, service, List.of(), service.snapshot(request.abilityId()), preview));
                case APPLY_VISUAL_SESSION -> send(sender, state(request, service, List.of(),
                        service.apply(request.session(), request.abilityId(), request.revision(),
                                request.baseFingerprint(), request.effectiveFingerprint(), request.visual()), preview));
                case REVERT_VISUAL_SESSION -> send(sender, state(request, service, List.of(),
                        service.revert(request.session(), request.abilityId(), request.revision(),
                                request.baseFingerprint(), request.effectiveFingerprint()), preview));
            }
        } catch (RuntimeException error) {
            if (request != null) {
                try {
                    send(sender, new SkillVfxEditorProtocol.State(SkillVfxEditorStatusMapper.map(error),
                            request.correlation(), service.serverSession(), List.of(), null,
                            sender.hasPermission(SkillVfxEditorChannel.PREVIEW_PERMISSION), "rejected"));
                } catch (RuntimeException ignored) { }
            }
        }
    }

    private static SkillVfxEditorProtocol.State state(SkillVfxEditorProtocol.Request request,
                                                       SkillVfxEditorServiceAccess service,
                                                       List<SkillVfxEditorService.CatalogItem> catalog,
                                                       SkillVfxEditorService.Snapshot snapshot,
                                                       boolean preview) {
        if (request.operation() == SkillVfxEditorProtocol.Operation.CATALOG) {
            catalog = service.catalog();
        }
        return new SkillVfxEditorProtocol.State(SkillVfxEditorProtocol.Status.OK, request.correlation(),
                service.serverSession(), catalog, snapshot, preview, "ok");
    }

    private static void send(SkillVfxEditorChannel.Sender sender, SkillVfxEditorProtocol.State state) {
        try {
            sender.send(SkillVfxEditorProtocolV3.encodeState(state));
        } catch (RuntimeException ignored) { }
    }
}
