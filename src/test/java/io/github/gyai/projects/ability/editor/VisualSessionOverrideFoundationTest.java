package io.github.gyai.projects.ability.editor;

import io.github.gyai.projects.ability.*;
import io.github.gyai.projects.network.AbilityVfxPacket;
import io.github.gyai.projects.network.SkillVfxEditorChannel;
import java.util.*; import java.nio.file.*;

/** Session override, resolver, and presentation-failure contract checks. */
public final class VisualSessionOverrideFoundationTest {
    public static void main(String[] ignored) {
        applyResolveAndAdapter();
        failureMatrixAndValidation();
        globalPrimitiveIdValidation();
        appearanceCodecAndFingerprint();
        tombstonesAndRestart();
        presentationFailures();
        System.out.println("visual session override assertions=55");
    }

    private static void applyResolveAndAdapter() {
        Fixture fixture=fixture(); SkillVfxEditorService service=fixture.service;
        SkillVfxEditorService.Snapshot before=service.snapshot(DevAbilityDefinitions.SHARED_ARCANE_BURST_ID);
        AbilityDefinition immutableAbility=before.ability(); AbilityVisualDefinition immutableBase=before.base();
        AbilityVisualDefinition changed=visual(before.visualId(),4,0,null);
        SkillVfxEditorService.Snapshot applied=service.apply(service.serverSession(),before.ability().id(),0,before.baseFingerprint(),before.effectiveFingerprint(),changed);
        check(applied.revision()==1&&applied.sessionOverride()&&applied.effective().equals(changed),"apply changes effective visual");
        check(applied.base().equals(immutableBase)&&fixture.visuals.find(before.visualId()).orElseThrow().equals(immutableBase),"base immutable");
        check(service.resolve(before.ability().id()).orElseThrow().equals(changed),"resolver returns override");
        check(applied.ability().equals(immutableAbility)&&before.ability().equals(applied.ability()),"ability equality unchanged after apply");

        List<AbilityVfxPacket.Cue> cues=new ArrayList<>(); AbilityVisualAdapter adapter=new AbilityVisualAdapter(service,cues::add,()->1);
        events(SourceKind.PLAYER).forEach(adapter::onLifecycle); events(SourceKind.MOB).forEach(adapter::onLifecycle);
        check(cues.size()==2&&cues.stream().allMatch(c->c.primitives().getFirst().radius()==4),"PLAYER and MOB use one service override");
        SkillVfxEditorService.Snapshot reverted=service.revert(service.serverSession(),before.ability().id(),1,applied.baseFingerprint(),applied.effectiveFingerprint());
        check(reverted.revision()==2&&!reverted.sessionOverride()&&reverted.effective().equals(immutableBase),"revert returns base");
        check(reverted.ability().equals(immutableAbility),"ability equality unchanged after revert");
        cues.clear(); events(SourceKind.PLAYER).forEach(adapter::onLifecycle); events(SourceKind.MOB).forEach(adapter::onLifecycle);
        check(cues.size()==2&&cues.stream().allMatch(c->c.primitives().getFirst().radius()==3),"PLAYER and MOB return to base");
    }

    private static void failureMatrixAndValidation() {
        Fixture fixture=fixture(); SkillVfxEditorService service=fixture.service;
        SkillVfxEditorService.Snapshot before=service.snapshot(DevAbilityDefinitions.SHARED_ARCANE_BURST_ID);
        AbilityVisualDefinition valid=visual(before.visualId(),4,0,AbilityVisualDefinition.ActionField.RADIUS);
        expect(SkillVfxEditorService.StaleSession.class,()->service.apply(UUID.randomUUID(),before.ability().id(),0,before.baseFingerprint(),before.effectiveFingerprint(),valid));
        expect(VisualSessionOverrideStore.StaleRevisionException.class,()->service.apply(service.serverSession(),before.ability().id(),1,before.baseFingerprint(),before.effectiveFingerprint(),valid));
        expect(SkillVfxEditorService.Conflict.class,()->service.apply(service.serverSession(),before.ability().id(),0,"wrong",before.effectiveFingerprint(),valid));
        expect(SkillVfxEditorService.Conflict.class,()->service.apply(service.serverSession(),before.ability().id(),0,before.baseFingerprint(),before.effectiveFingerprint(),visual("projects:vfx/other",4,0,AbilityVisualDefinition.ActionField.RADIUS)));
        expect(SkillVfxEditorService.NotFound.class,()->service.snapshot("projects:unbound"));
        expect(SkillVfxEditorService.NotFound.class,()->service.snapshot("projects:none"));
        expect(SkillVfxEditorService.NotFound.class,()->service.apply(service.serverSession(),"projects:unbound",0,"","",valid));
        check(SkillVfxEditorStatusMapper.map(new SkillVfxEditorService.NotFound("x"))==SkillVfxEditorProtocol.Status.NOT_FOUND,"not found maps");
        check(SkillVfxEditorStatusMapper.map(new SkillVfxEditorService.StaleSession())==SkillVfxEditorProtocol.Status.STALE,"session stale maps");
        check(SkillVfxEditorStatusMapper.map(new VisualSessionOverrideStore.StaleRevisionException())==SkillVfxEditorProtocol.Status.STALE,"revision stale maps");
        check(SkillVfxEditorStatusMapper.map(new SkillVfxEditorService.Conflict())==SkillVfxEditorProtocol.Status.CONFLICT,"conflict maps");
        check(SkillVfxEditorStatusMapper.map(new IllegalArgumentException())==SkillVfxEditorProtocol.Status.MALFORMED,"malformed maps");

        invalidLeavesState(service,before,visual(before.visualId(),4,3,AbilityVisualDefinition.ActionField.RADIUS));
        invalidLeavesState(service,before,visual(before.visualId(),4,0,AbilityVisualDefinition.ActionField.INNER_RADIUS));
        invalidLeavesState(service,before,visual(before.visualId(),4,0,AbilityVisualDefinition.ActionField.WIDTH));
        invalidLeavesState(service,before,visual(before.visualId(),4,0,AbilityVisualDefinition.ActionField.LENGTH));
        invalidLeavesState(service,before,visual(before.visualId(),4,1,AbilityVisualDefinition.ActionField.RADIUS));
        invalidLeavesState(service,before,visualHook(before.visualId(),AbilityLifecycleEvent.Hook.TELEGRAPH,1));
        invalidLeavesState(service,before,visualHook(before.visualId(),AbilityLifecycleEvent.Hook.HIT,0));
        invalidLeavesState(service,before,visualHook(before.visualId(),AbilityLifecycleEvent.Hook.CAST,0));
        AbilityVisualCrossValidator.validate(before.ability(),visualHook(before.visualId(),AbilityLifecycleEvent.Hook.HIT,2));
        SkillVfxEditorService.Snapshot accepted=service.apply(service.serverSession(),before.ability().id(),0,before.baseFingerprint(),before.effectiveFingerprint(),valid);
        check(accepted.revision()==1&&accepted.effective().equals(valid),"CircleTelegraph RADIUS accepted");
    }

    private static void tombstonesAndRestart() {
        VisualSessionOverrideStore store=new VisualSessionOverrideStore(); AbilityVisualDefinition base=visual("projects:vfx/a",3,0,AbilityVisualDefinition.ActionField.RADIUS);
        AbilityVisualDefinition changed=visual("projects:vfx/a",4,0,null);
        check(store.current("projects:a",base,"base").revision()==0&&!store.current("projects:a",base,"base").overridden(),"initial slot");
        check(store.apply("projects:a",0,changed,"changed").revision()==1,"store apply revision");
        VisualSessionOverrideStore.Slot tombstone=store.revert("projects:a",1,"base");
        check(tombstone.revision()==2&&!tombstone.overridden()&&store.current("projects:a",base,"base").revision()==2,"tombstone prevents ABA");
        expect(VisualSessionOverrideStore.StaleRevisionException.class,()->store.revert("projects:a",1,"base"));
        check(store.revert("projects:a",2,"base").revision()==3&&!store.current("projects:a",base,"base").overridden(),"second revert deterministic increments");
        store.clear(); check(store.current("projects:a",base,"base").revision()==0,"clear resets in-memory session state");
        Fixture one=fixture(), two=fixture(); check(!one.service.serverSession().equals(two.service.serverSession()),"new service has new session UUID");
        check(two.service.snapshot(DevAbilityDefinitions.SHARED_ARCANE_BURST_ID).revision()==0&&!two.service.snapshot(DevAbilityDefinitions.SHARED_ARCANE_BURST_ID).sessionOverride(),"new service restart has base only");
    }

    private static void globalPrimitiveIdValidation() {
        Fixture fixture=fixture(); SkillVfxEditorService service=fixture.service;
        SkillVfxEditorService.Snapshot before=service.snapshot(DevAbilityDefinitions.SHARED_ARCANE_BURST_ID);
        expect(IllegalArgumentException.class,()->new AbilityVisualDefinition(1,before.visualId(),List.of(new AbilityVisualDefinition.HookBinding(AbilityLifecycleEvent.Hook.TELEGRAPH,List.of(new AbilityVisualDefinition.Emission("duplicate",0,List.of(circle("same",3,null),circle("same",3,null))))))));
        check(service.snapshot(before.ability().id()).revision()==0,"same-emission duplicate leaves service state unchanged");
        expect(IllegalArgumentException.class,()->twoEmissionVisual(before.visualId(),"shared","shared"));
        check(service.snapshot(before.ability().id()).revision()==0,"cross-hook duplicate leaves service state unchanged");
        AbilityVisualDefinition unique=twoEmissionVisual(before.visualId(),"telegraph-circle","hit-circle");
        SkillVfxEditorService.Snapshot accepted=service.apply(service.serverSession(),before.ability().id(),0,before.baseFingerprint(),before.effectiveFingerprint(),unique);
        check(accepted.revision()==1&&accepted.effective().equals(unique),"unique primitive ids across document accept");
    }

    private static void appearanceCodecAndFingerprint() {
        AbilityVisualDefinition debug=visual("projects:vfx/appearance",3,0,null);
        AbilityVisualDefinition.PrimitiveSpec original=debug.bindings().getFirst().emissions().getFirst().primitives().getFirst();
        AbilityVisualDefinition particle=new AbilityVisualDefinition(1,debug.id(),List.of(new AbilityVisualDefinition.HookBinding(AbilityLifecycleEvent.Hook.TELEGRAPH,List.of(new AbilityVisualDefinition.Emission("telegraph",0,List.of(new AbilityVisualDefinition.PrimitiveSpec(original.id(),original.type(),original.delayTicks(),original.durationTicks(),original.argb(),original.width(),original.density(),original.seed(),original.localOffset(),original.yawRadians(),original.size(),original.radius(),original.length(),original.height(),original.angle(),original.startAngle(),original.sweepAngle(),original.turns(),original.count(),original.controlPoints(),AbilityVisualDefinition.Appearance.particle("minecraft:flame"))))))));
        check(SkillVfxEditorProtocolV2.decodeVisual(SkillVfxEditorProtocolV2.encodeVisual(particle)).equals(particle),"v2 appearance visual roundtrip");
        try { check(HexFormat.of().formatHex(SkillVfxEditorProtocolV2.encodeVisual(particle)).equals(Files.readString(Path.of("src/test/resources/protocol/skill-vfx-editor-v2-appearance-golden.hex")).trim()),"v2 editor golden"); }
        catch(Exception e) { throw new AssertionError(e); }
        check(SkillVfxEditorService.fingerprint(debug).equals("f315080c4ed96b5c1d0a3278b9dcaa8a94c21a920c88d1720e275166e7c61379"),"all-debug legacy fingerprint remains exact");
        check(SkillVfxEditorService.fingerprint(particle).equals("dc41e54671699b79043b5bc9526d6f705813894693f7cbe506772b6e2c8bfece"),"particle fingerprint uses NUL-separated v2 domain");
        AbilityVisualDefinition merged=SkillVfxEditorService.mergeV1Appearance(particle,debug);
        check(merged.bindings().getFirst().emissions().getFirst().primitives().getFirst().appearance().equals(AbilityVisualDefinition.Appearance.particle("minecraft:flame")),"v1 merge retains matching appearance");
        check(SkillVfxEditorService.mergeV1Appearance(particle,visual("projects:vfx/appearance",4,0,null)).bindings().getFirst().emissions().getFirst().primitives().getFirst().appearance().equals(AbilityVisualDefinition.Appearance.particle("minecraft:flame")),"v1 scalar edits retain appearance");
        Fixture fixture=fixture(); SkillVfxEditorService service=fixture.service; SkillVfxEditorService.Snapshot before=service.snapshot(DevAbilityDefinitions.SHARED_ARCANE_BURST_ID);
        AbilityVisualDefinition existing=particleVisual(before.visualId(),"circle",3); SkillVfxEditorService.Snapshot seeded=service.apply(service.serverSession(),before.ability().id(),0,before.baseFingerprint(),before.effectiveFingerprint(),existing);
        AbilityVisualDefinition v1Edit=visual(before.visualId(),4,0,null); List<byte[]> states=new ArrayList<>(); SkillVfxEditorChannel.Sender sender=new SkillVfxEditorChannel.Sender(){public boolean hasPermission(String p){return true;}public void send(byte[] bytes){states.add(bytes);}};
        SkillVfxEditorChannel.dispatch(SkillVfxEditorProtocol.encodeRequest(new SkillVfxEditorProtocol.Request(SkillVfxEditorProtocol.Operation.APPLY_VISUAL_SESSION,77,service.serverSession(),before.ability().id(),seeded.revision(),seeded.baseFingerprint(),seeded.effectiveFingerprint(),v1Edit)),sender,service);
        check(states.size()==1&&SkillVfxEditorProtocol.decodeState(states.getFirst()).status()==SkillVfxEditorProtocol.Status.OK,"v1 apply succeeds");
        check(service.snapshot(before.ability().id()).effective().bindings().getFirst().emissions().getFirst().primitives().getFirst().appearance().equals(AbilityVisualDefinition.Appearance.particle("minecraft:flame")),"v1 APPLY retains hidden appearance by primitive id");
    }

    private static AbilityVisualDefinition particleVisual(String id,String primitiveId,double radius) { AbilityVisualDefinition base=visual(id,radius,0,null); AbilityVisualDefinition.PrimitiveSpec p=base.bindings().getFirst().emissions().getFirst().primitives().getFirst(); return new AbilityVisualDefinition(1,id,List.of(new AbilityVisualDefinition.HookBinding(AbilityLifecycleEvent.Hook.TELEGRAPH,List.of(new AbilityVisualDefinition.Emission("telegraph",0,List.of(new AbilityVisualDefinition.PrimitiveSpec(primitiveId,p.type(),p.delayTicks(),p.durationTicks(),p.argb(),p.width(),p.density(),p.seed(),p.localOffset(),p.yawRadians(),p.size(),p.radius(),p.length(),p.height(),p.angle(),p.startAngle(),p.sweepAngle(),p.turns(),p.count(),p.controlPoints(),AbilityVisualDefinition.Appearance.particle("minecraft:flame")))))))); }

    private static void presentationFailures() {
        AbilityLifecycleEvent event=events(SourceKind.PLAYER).getFirst();
        AbilityVisualAdapter resolverFailure=new AbilityVisualAdapter(id->{throw new IllegalStateException("resolver");},cue->{throw new AssertionError("sink must not run");},()->0);
        resolverFailure.onLifecycle(event);
        AbilityVisualAdapter sinkFailure=new AbilityVisualAdapter(id->Optional.of(DevAbilityVisuals.arcaneBurst()),cue->{throw new IllegalStateException("sink");},()->0);
        sinkFailure.onLifecycle(event);
        check(true,"resolver and sink exceptions do not escape separately");
    }

    private static void invalidLeavesState(SkillVfxEditorService service,SkillVfxEditorService.Snapshot before,AbilityVisualDefinition invalid) {
        expect(IllegalArgumentException.class,()->service.apply(service.serverSession(),before.ability().id(),0,before.baseFingerprint(),before.effectiveFingerprint(),invalid));
        SkillVfxEditorService.Snapshot after=service.snapshot(before.ability().id());
        check(after.revision()==0&&!after.sessionOverride()&&after.effective().equals(before.effective()),"invalid apply has no mutation");
    }

    private static Fixture fixture() {
        AbilityRegistry abilities=new AbilityRegistry(AbilityRuntime.standardActions()); abilities.register(DevAbilityDefinitions.sharedArcaneBurst()); abilities.register(new AbilityDefinition(1,"projects:unbound","U",List.of(new AbilityDefinition.Wait(1))));
        AbilityVisualRegistry visuals=new AbilityVisualRegistry(); DevAbilityVisuals.registerInto(visuals);
        return new Fixture(new SkillVfxEditorService(abilities,visuals),visuals);
    }
    private record Fixture(SkillVfxEditorService service,AbilityVisualRegistry visuals) { }
    private static AbilityVisualDefinition visual(String id,double radius,int actionIndex,AbilityVisualDefinition.ActionField field) { return new AbilityVisualDefinition(1,id,List.of(new AbilityVisualDefinition.HookBinding(AbilityLifecycleEvent.Hook.TELEGRAPH,List.of(new AbilityVisualDefinition.Emission("telegraph",actionIndex,List.of(circle(radius,field))))))); }
    private static AbilityVisualDefinition visualHook(String id,AbilityLifecycleEvent.Hook hook,int actionIndex) { return new AbilityVisualDefinition(1,id,List.of(new AbilityVisualDefinition.HookBinding(hook,List.of(new AbilityVisualDefinition.Emission("hook",actionIndex,List.of(circle(3,null))))))); }
    private static AbilityVisualDefinition twoEmissionVisual(String id,String telegraphPrimitiveId,String hitPrimitiveId) { return new AbilityVisualDefinition(1,id,List.of(new AbilityVisualDefinition.HookBinding(AbilityLifecycleEvent.Hook.TELEGRAPH,List.of(new AbilityVisualDefinition.Emission("telegraph",0,List.of(circle(telegraphPrimitiveId,3,null))))),new AbilityVisualDefinition.HookBinding(AbilityLifecycleEvent.Hook.HIT,List.of(new AbilityVisualDefinition.Emission("hit",2,List.of(circle(hitPrimitiveId,3,null))))))); }
    private static AbilityVisualDefinition.PrimitiveSpec circle(double radius,AbilityVisualDefinition.ActionField field) { return circle("circle",radius,field); }
    private static AbilityVisualDefinition.PrimitiveSpec circle(String id,double radius,AbilityVisualDefinition.ActionField field) { return new AbilityVisualDefinition.PrimitiveSpec(id,AbilityVisualDefinition.PrimitiveType.CIRCLE,0,1,1,1,1,1,new AbilityVisualDefinition.Vec(0,0,0),0,null,field==null?new AbilityVisualDefinition.Literal(radius):field,null,null,null,null,null,null,0,List.of()); }
    private static List<AbilityLifecycleEvent> events(SourceKind kind) { UUID id=UUID.randomUUID(); AbilityCastContext context=new AbilityCastContext(UUID.randomUUID(),DevAbilityDefinitions.SHARED_ARCANE_BURST_ID,new AbilityCastContext.EntityRef(id),kind,new AbilityCastContext.Origin(id,"minecraft:overworld",0,0,0),null,Map.of()); return List.of(new AbilityLifecycleEvent(context,AbilityLifecycleEvent.Hook.TELEGRAPH,0,DevAbilityDefinitions.sharedArcaneBurst().steps().getFirst(),null,null,new AnchorFrame(id,"minecraft:overworld",0,0,0,1,0,0,0,1,0))); }
    private static void check(boolean condition,String message) {if(!condition)throw new AssertionError(message);}
    private static void expect(Class<? extends Throwable> type,Runnable action) {try{action.run();throw new AssertionError("expected "+type.getSimpleName());}catch(Throwable error){if(!type.isInstance(error))throw new AssertionError("expected "+type.getSimpleName()+", got "+error,error);}}
}
