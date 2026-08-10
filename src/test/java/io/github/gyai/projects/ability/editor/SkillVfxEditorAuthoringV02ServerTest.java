package io.github.gyai.projects.ability.editor;

import io.github.gyai.projects.ability.*;
import io.github.gyai.projects.network.SkillVfxEditorChannel;
import io.github.gyai.projects.network.SkillVfxEditorChannelV2;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;

/** Executable v0.2 authoring regressions against the immutable server/editor APIs. */
public final class SkillVfxEditorAuthoringV02ServerTest {
    private static final String ABILITY=DevAbilityDefinitions.SHARED_ARCANE_BURST_ID;

    public static void main(String[] ignored) throws Exception {
        appearanceCatalogAndRejection();
        v2StructuralCreateDuplicateDeleteApply();
        nonemptyEmissionApplyAndGlobalIds();
        v1ApplyPreservesStableAppearanceAcrossStructuralAndScalarEdits();
        appearanceFingerprintAndFixturesRemainExact();
        System.out.println("skill vfx authoring v0.2 server assertions=38");
    }

    private static void appearanceCatalogAndRejection() {
        Set<String> particles=AbilityVisualDefinition.Appearance.particleIds();
        check(particles.size()==9,"catalog has exactly nine particles");
        check(particles.contains("minecraft:flame") && !particles.contains("minecraft:dust"),"catalog retains supported IDs and DUST stays deferred");
        check(new AbilityVisualDefinition.Appearance(AbilityVisualDefinition.AppearanceKind.DEBUG_QUAD,"projects:debug_quad").equals(AbilityVisualDefinition.Appearance.DEBUG_QUAD),"debug quad remains the tenth supported appearance");
        expect(IllegalArgumentException.class,()->AbilityVisualDefinition.Appearance.particle("minecraft:dust"));
        expect(IllegalArgumentException.class,()->new AbilityVisualDefinition.Appearance(AbilityVisualDefinition.AppearanceKind.PARTICLE,"minecraft:not_supported"));
    }

    private static void v2StructuralCreateDuplicateDeleteApply() {
        SkillVfxEditorService service=service();
        AbilityVisualDefinition created=visual(boundVisualId(service),List.of(
                emission("telegraph",0,circle("created",3,"minecraft:flame"))));
        SkillVfxEditorService.Snapshot create=applyV2(service,created,11);
        check(create.effective().equals(created) && create.revision()==1,"v2 create applies full structural body with appearance");
        check(primitive(create.effective(),"created").appearance().equals(AbilityVisualDefinition.Appearance.particle("minecraft:flame")),"v2 create retains particle appearance");

        AbilityVisualDefinition duplicated=visual(created.id(),List.of(emission("telegraph",0,
                circle("created",3,"minecraft:flame"),circle("created-copy",3,"minecraft:end_rod"))));
        SkillVfxEditorService.Snapshot duplicate=applyV2(service,duplicated,12);
        check(duplicate.revision()==2 && ids(duplicate.effective()).equals(List.of("created","created-copy")),"v2 duplicate accepts unique ID directly after source");
        check(primitive(duplicate.effective(),"created-copy").appearance().equals(AbilityVisualDefinition.Appearance.particle("minecraft:end_rod")),"v2 duplicate preserves independent appearance");

        AbilityVisualDefinition deleted=visual(created.id(),List.of(emission("telegraph",0,circle("created-copy",3,"minecraft:end_rod"))));
        SkillVfxEditorService.Snapshot remove=applyV2(service,deleted,13);
        check(remove.revision()==3 && ids(remove.effective()).equals(List.of("created-copy")),"v2 delete applies structural removal without altering survivor appearance");
        check(primitive(remove.effective(),"created-copy").appearance().equals(AbilityVisualDefinition.Appearance.particle("minecraft:end_rod")),"v2 delete survivor appearance remains exact");
    }

    private static void nonemptyEmissionApplyAndGlobalIds() {
        SkillVfxEditorService service=service();
        expect(IllegalArgumentException.class,()->new AbilityVisualDefinition.Emission("empty",0,List.of()));
        AbilityVisualDefinition valid=visual(boundVisualId(service),List.of(
                emission("telegraph",0,circle("telegraph-part",3,"minecraft:ash")),
                emission("telegraph-second",0,circle("telegraph-second-part",3,"minecraft:cloud"))));
        SkillVfxEditorService.Snapshot applied=applyV2(service,valid,21);
        check(applied.effective().equals(valid) && applied.effective().bindings().getFirst().emissions().size()==2,"each applied emission has a nonempty valid primitive structure");

        SkillVfxEditorService duplicateService=service();
        AbilityVisualDefinition duplicateIds=visual(boundVisualId(duplicateService),List.of(
                emission("telegraph",0,circle("shared",3,"minecraft:flame")),
                emission("telegraph-second",0,circle("shared",3,"minecraft:soul"))));
        SkillVfxEditorService.Snapshot before=duplicateService.snapshot(ABILITY);
        expect(IllegalArgumentException.class,()->duplicateService.apply(duplicateService.serverSession(),ABILITY,before.revision(),before.baseFingerprint(),before.effectiveFingerprint(),duplicateIds));
        byte[] duplicateWire=SkillVfxEditorProtocolV2.encodeRequest(request(31,duplicateService,before,duplicateIds));
        expect(IllegalArgumentException.class,()->SkillVfxEditorProtocolV2.decodeRequest(duplicateWire));
        check(duplicateService.snapshot(ABILITY).revision()==0,"global duplicate rejection does not mutate session state");
    }

    private static void v1ApplyPreservesStableAppearanceAcrossStructuralAndScalarEdits() {
        SkillVfxEditorService service=service();
        AbilityVisualDefinition seeded=visual(boundVisualId(service),List.of(emission("telegraph",0,
                circle("stable",3,"minecraft:flame"),circle("removed",3,"minecraft:soul"))));
        SkillVfxEditorService.Snapshot seededSnapshot=applyV2(service,seeded,41);
        AbilityVisualDefinition v1Candidate=visual(seeded.id(),List.of(emission("replacement-emission",0,
                circle("stable",4,null),circle("created-by-v1",3,null))));
        RecordingSender sender=new RecordingSender();
        SkillVfxEditorChannel.dispatch(SkillVfxEditorProtocol.encodeRequest(request(42,service,seededSnapshot,v1Candidate)),sender,service);
        SkillVfxEditorProtocol.State state=SkillVfxEditorProtocol.decodeState(sender.states.getFirst());
        check(state.status()==SkillVfxEditorProtocol.Status.OK && state.snapshot().revision()==2,"v1 structural/scalar apply succeeds");
        AbilityVisualDefinition effective=service.snapshot(ABILITY).effective();
        check(ids(effective).equals(List.of("stable","created-by-v1")) && ((AbilityVisualDefinition.Literal)primitive(effective,"stable").radius()).value()==4,"v1 structural deletion/create and scalar edit apply");
        check(primitive(effective,"stable").appearance().equals(AbilityVisualDefinition.Appearance.particle("minecraft:flame")),"v1 preserves hidden appearance by matching stable primitive ID");
        check(primitive(effective,"created-by-v1").appearance().equals(AbilityVisualDefinition.Appearance.DEBUG_QUAD),"v1-created primitive receives only the compatibility default appearance");
    }

    private static void appearanceFingerprintAndFixturesRemainExact() throws Exception {
        AbilityVisualDefinition debug=visual("projects:vfx/appearance",List.of(emission("telegraph",0,circle("circle",3,null))));
        AbilityVisualDefinition particle=visual(debug.id(),List.of(emission("telegraph",0,circle("circle",3,"minecraft:flame"))));
        check(!SkillVfxEditorService.fingerprint(debug).equals(SkillVfxEditorService.fingerprint(particle)),"appearance participates in fingerprint");
        check(SkillVfxEditorService.fingerprint(debug).equals("f315080c4ed96b5c1d0a3278b9dcaa8a94c21a920c88d1720e275166e7c61379"),"all-debug fingerprint remains v1 exact");
        check(SkillVfxEditorService.fingerprint(particle).equals("dc41e54671699b79043b5bc9526d6f705813894693f7cbe506772b6e2c8bfece"),"particle fingerprint remains domain-separated v2 exact");

        SkillVfxEditorService service=service(); SkillVfxEditorService.Snapshot snapshot=service.snapshot(ABILITY);
        SkillVfxEditorProtocol.State state=new SkillVfxEditorProtocol.State(SkillVfxEditorProtocol.Status.OK,7,UUID.fromString("11111111-2222-3333-4444-555555555555"),service.catalog(),snapshot,true,"golden");
        byte[] v1=SkillVfxEditorProtocol.encodeState(state);
        check(HexFormat.of().formatHex(v1).equals(fixture("skill-vfx-editor-v1-golden.hex")),"editor v1 fixture bytes unchanged");
        check(sha(v1).equals("7178538049f2a7ac6807f1a3190e6840ebb0c8b6690c42a56090612383a4fdd4"),"editor v1 decoded fixture hash unchanged");
        check(sha((fixture("skill-vfx-editor-v1-golden.hex")+"\n").getBytes(StandardCharsets.UTF_8)).equals("c9f44b45e7a30a54479b0f7488ebc52cf969431a79285a059d8bdef9cce7ddab"),"editor v1 text fixture hash unchanged");
        byte[] v2=SkillVfxEditorProtocolV2.encodeVisual(particle);
        check(HexFormat.of().formatHex(v2).equals(fixture("skill-vfx-editor-v2-appearance-golden.hex")),"editor v2 fixture bytes unchanged");
        check(sha(v2).equals("40e48c62f77d615776c08446a66384e5fa0d62695e9b6877df08ce181d07683b"),"editor v2 decoded fixture hash unchanged");
    }

    private static SkillVfxEditorService.Snapshot applyV2(SkillVfxEditorService service,AbilityVisualDefinition candidate,long correlation) {
        SkillVfxEditorService.Snapshot before=service.snapshot(ABILITY); RecordingSender sender=new RecordingSender();
        SkillVfxEditorChannelV2.dispatch(SkillVfxEditorProtocolV2.encodeRequest(request(correlation,service,before,candidate)),sender,service);
        check(sender.states.size()==1,"v2 apply produces one state");
        SkillVfxEditorProtocol.State state=SkillVfxEditorProtocolV2.decodeState(sender.states.getFirst());
        check(state.status()==SkillVfxEditorProtocol.Status.OK && state.snapshot().effective().equals(candidate),"v2 apply state carries structural body and appearance table");
        return state.snapshot();
    }

    private static SkillVfxEditorProtocol.Request request(long correlation,SkillVfxEditorService service,SkillVfxEditorService.Snapshot snapshot,AbilityVisualDefinition visual) {
        return new SkillVfxEditorProtocol.Request(SkillVfxEditorProtocol.Operation.APPLY_VISUAL_SESSION,correlation,service.serverSession(),ABILITY,snapshot.revision(),snapshot.baseFingerprint(),snapshot.effectiveFingerprint(),visual);
    }
    private static SkillVfxEditorService service() { AbilityRegistry abilities=new AbilityRegistry(AbilityRuntime.standardActions()); abilities.register(DevAbilityDefinitions.sharedArcaneBurst()); abilities.register(new AbilityDefinition(1,"projects:unbound","Unbound",List.of(new AbilityDefinition.Wait(1)))); AbilityVisualRegistry visuals=new AbilityVisualRegistry(); DevAbilityVisuals.registerInto(visuals); return new SkillVfxEditorService(abilities,visuals); }
    private static String boundVisualId(SkillVfxEditorService service) { return service.snapshot(ABILITY).visualId(); }
    private static AbilityVisualDefinition visual(String id,List<AbilityVisualDefinition.Emission> emissions) { return new AbilityVisualDefinition(1,id,List.of(new AbilityVisualDefinition.HookBinding(AbilityLifecycleEvent.Hook.TELEGRAPH,emissions))); }
    private static AbilityVisualDefinition.Emission emission(String id,int action,AbilityVisualDefinition.PrimitiveSpec... primitives) { return new AbilityVisualDefinition.Emission(id,action,List.of(primitives)); }
    private static AbilityVisualDefinition.PrimitiveSpec circle(String id,double radius,String particle) { return new AbilityVisualDefinition.PrimitiveSpec(id,AbilityVisualDefinition.PrimitiveType.CIRCLE,0,1,1,1,1,1,new AbilityVisualDefinition.Vec(0,0,0),0,null,new AbilityVisualDefinition.Literal(radius),null,null,null,null,null,null,0,List.of(),particle==null?AbilityVisualDefinition.Appearance.DEBUG_QUAD:AbilityVisualDefinition.Appearance.particle(particle)); }
    private static AbilityVisualDefinition.PrimitiveSpec primitive(AbilityVisualDefinition visual,String id) { return visual.bindings().stream().flatMap(h->h.emissions().stream()).flatMap(e->e.primitives().stream()).filter(p->p.id().equals(id)).findFirst().orElseThrow(); }
    private static List<String> ids(AbilityVisualDefinition visual) { return visual.bindings().stream().flatMap(h->h.emissions().stream()).flatMap(e->e.primitives().stream()).map(AbilityVisualDefinition.PrimitiveSpec::id).toList(); }
    private static String fixture(String name) throws Exception { return Files.readString(Path.of("src/test/resources/protocol",name)).replaceAll("\\s",""); }
    private static String sha(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static void check(boolean value,String message) { if(!value) throw new AssertionError(message); }
    private static void expect(Class<? extends Throwable> type,Runnable action) { try { action.run(); throw new AssertionError("expected "+type.getSimpleName()); } catch(Throwable error) { if(!type.isInstance(error)) throw new AssertionError("expected "+type.getSimpleName()+", got "+error,error); } }
    private static final class RecordingSender implements SkillVfxEditorChannel.Sender { private final List<byte[]> states=new ArrayList<>(); @Override public boolean hasPermission(String permission) { return true; } @Override public void send(byte[] payload) { states.add(payload); } }
}
