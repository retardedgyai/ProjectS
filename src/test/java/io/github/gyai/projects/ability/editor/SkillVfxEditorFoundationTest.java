package io.github.gyai.projects.ability.editor;

import io.github.gyai.projects.ability.*;
import io.github.gyai.projects.combat.damage.*;
import io.github.gyai.projects.network.SkillVfxEditorChannel;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Protocol and immutable authoring contract checks; intentionally Bukkit-free. */
public final class SkillVfxEditorFoundationTest {
    private static final UUID SESSION=UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String GOLDEN_BYTES_SHA="7178538049f2a7ac6807f1a3190e6840ebb0c8b6690c42a56090612383a4fdd4";
    private static final String GOLDEN_TEXT_SHA="c9f44b45e7a30a54479b0f7488ebc52cf969431a79285a059d8bdef9cce7ddab";

    public static void main(String[] ignored) throws Exception {
        SkillVfxEditorService service=service();
        catalogAndAuthoring(service);
        visualContract();
        roundTripsAndGolden(service);
        malformedPackets(service);
        encodeBounds(service);
        decodeBounds();
        operationShapes(service);
        authorizationAndMetadata();
        channelPermissionBoundary();
        System.out.println("skill editor protocol assertions=115");
    }

    private static SkillVfxEditorService service() {
        AbilityRegistry abilities=new AbilityRegistry(AbilityRuntime.standardActions());
        abilities.register(DevAbilityDefinitions.sharedArcaneBurst());
        abilities.register(new AbilityDefinition(1,"projects:unbound","Unbound",List.of(new AbilityDefinition.Wait(1))));
        AbilityVisualRegistry visuals=new AbilityVisualRegistry(); DevAbilityVisuals.registerInto(visuals);
        return new SkillVfxEditorService(abilities,visuals);
    }

    private static void catalogAndAuthoring(SkillVfxEditorService service) {
        List<SkillVfxEditorService.CatalogItem> catalog=service.catalog();
        check(catalog.size()==2,"catalog size");
        check(catalog.get(0).abilityId().equals(DevAbilityDefinitions.SHARED_ARCANE_BURST_ID),"lexical catalog first");
        check(catalog.get(1).abilityId().equals("projects:unbound")&&!catalog.get(1).hasVisual(),"unbound row");
        expect(SkillVfxEditorService.NotFound.class,()->service.snapshot("projects:unbound"));

        AbilityDefinition ability=DevAbilityDefinitions.sharedArcaneBurst();
        check(ability.id().equals(DevAbilityDefinitions.SHARED_ARCANE_BURST_ID)&&ability.steps().size()==3,"arcane identity/order");
        check(ability.steps().get(0) instanceof AbilityDefinition.CircleTelegraph,"circle first");
        AbilityDefinition.CircleTelegraph telegraph=(AbilityDefinition.CircleTelegraph)ability.steps().get(0);
        check(telegraph.target()==TargetSelector.PRIMARY_TARGET&&telegraph.origin()==TargetSelector.PRIMARY_TARGET&&telegraph.radius()==3&&telegraph.durationTicks()==20&&telegraph.lockAtCreation(),"exact telegraph");
        check(ability.steps().get(1).equals(new AbilityDefinition.Wait(20)),"wait 20");
        AbilityDefinition.Damage damage=(AbilityDefinition.Damage)ability.steps().get(2);
        check(damage.target()==TargetSelector.PRIMARY_TARGET&&damage.damageType()==DamageType.MAGICAL&&damage.damageKind()==DamageKind.DIRECT_SKILL,"damage target/type/kind");
        check(damage.fixedDamage()==12&&damage.coefficient()==.5&&damage.criticalAllowed(),"damage values");
        check(damage.metadata().tags().equals(EnumSet.of(AttackTag.MAGIC,AttackTag.SKILL)),"damage tags");
        check(damage.metadata().elements().equals(ElementProfile.EMPTY),"damage elements");
    }

    private static void visualContract() {
        AbilityVisualDefinition visual=DevAbilityVisuals.arcaneBurst();
        check(visual.id().equals(DevAbilityVisuals.ARCANE_BURST_VISUAL_ID)&&visual.bindings().size()==5,"visual identity/hooks");
        List<AbilityLifecycleEvent.Hook> hooks=visual.bindings().stream().map(AbilityVisualDefinition.HookBinding::hook).toList();
        check(hooks.equals(List.of(AbilityLifecycleEvent.Hook.CAST,AbilityLifecycleEvent.Hook.TELEGRAPH,AbilityLifecycleEvent.Hook.HIT,AbilityLifecycleEvent.Hook.EXPIRE,AbilityLifecycleEvent.Hook.CANCEL)),"exact hook order");
        List<AbilityVisualDefinition.Emission> emissions=visual.bindings().stream().flatMap(x->x.emissions().stream()).toList();
        check(emissions.stream().map(AbilityVisualDefinition.Emission::id).toList().equals(List.of("cast","telegraph","hit","expire","cancel")),"emission ids/order");
        check(emissions.stream().map(AbilityVisualDefinition.Emission::actionIndex).toList().equals(List.of(-1,0,2,-1,-1)),"emission action indexes");
        AbilityVisualDefinition.PrimitiveSpec telegraph=emissions.get(1).primitives().getFirst();
        check(telegraph.type()==AbilityVisualDefinition.PrimitiveType.CIRCLE&&telegraph.radius()==AbilityVisualDefinition.ActionField.RADIUS,"RADIUS scalar binding");
    }

    private static void roundTripsAndGolden(SkillVfxEditorService service) throws Exception {
        AbilityVisualDefinition visual=service.snapshot(DevAbilityDefinitions.SHARED_ARCANE_BURST_ID).effective();
        for (SkillVfxEditorProtocol.Request request : List.of(
                new SkillVfxEditorProtocol.Request(SkillVfxEditorProtocol.Operation.CATALOG,1,SESSION,"",0,"","",null),
                new SkillVfxEditorProtocol.Request(SkillVfxEditorProtocol.Operation.FETCH,2,SESSION,DevAbilityDefinitions.SHARED_ARCANE_BURST_ID,0,"","",null),
                new SkillVfxEditorProtocol.Request(SkillVfxEditorProtocol.Operation.APPLY_VISUAL_SESSION,3,SESSION,DevAbilityDefinitions.SHARED_ARCANE_BURST_ID,0,"base","effective",visual),
                new SkillVfxEditorProtocol.Request(SkillVfxEditorProtocol.Operation.REVERT_VISUAL_SESSION,4,SESSION,DevAbilityDefinitions.SHARED_ARCANE_BURST_ID,1,"base","effective",null))) {
            check(SkillVfxEditorProtocol.decodeRequest(SkillVfxEditorProtocol.encodeRequest(request)).equals(request),"request roundtrip "+request.operation());
        }
        SkillVfxEditorService.Snapshot snapshot=service.snapshot(DevAbilityDefinitions.SHARED_ARCANE_BURST_ID);
        SkillVfxEditorProtocol.State state=new SkillVfxEditorProtocol.State(SkillVfxEditorProtocol.Status.OK,7,SESSION,service.catalog(),snapshot,true,"golden");
        byte[] golden=SkillVfxEditorProtocol.encodeState(state);
        check(SkillVfxEditorProtocol.decodeState(golden).equals(state),"full state roundtrip");
        String hex=HexFormat.of().formatHex(golden);
        check(hex.equals(Files.readString(Path.of("src/test/resources/protocol/skill-vfx-editor-v1-golden.hex"),StandardCharsets.UTF_8).trim()),"golden exact bytes");
        check(sha(golden).equals(GOLDEN_BYTES_SHA),"golden decoded SHA");
        check(sha((hex+"\n").getBytes(StandardCharsets.UTF_8)).equals(GOLDEN_TEXT_SHA),"golden LF text SHA");
    }

    private static void malformedPackets(SkillVfxEditorService service) {
        byte[] request=SkillVfxEditorProtocol.encodeRequest(new SkillVfxEditorProtocol.Request(SkillVfxEditorProtocol.Operation.CATALOG,1,SESSION,"",0,"","",null));
        expect(IllegalArgumentException.class,()->SkillVfxEditorProtocol.decodeRequest(null));
        expect(IllegalArgumentException.class,()->SkillVfxEditorProtocol.decodeRequest(new byte[0]));
        expect(IllegalArgumentException.class,()->SkillVfxEditorProtocol.decodeRequest(Arrays.copyOf(request,4)));
        expect(IllegalArgumentException.class,()->SkillVfxEditorProtocol.decodeRequest(Arrays.copyOf(request,SkillVfxEditorProtocol.MAX_PACKET+1)));
        byte[] wrongVersion=request.clone(); wrongVersion[0]=2; expect(IllegalArgumentException.class,()->SkillVfxEditorProtocol.decodeRequest(wrongVersion));
        byte[] invalidEnum=request.clone(); invalidEnum[1]=127; expect(IllegalArgumentException.class,()->SkillVfxEditorProtocol.decodeRequest(invalidEnum));
        byte[] trailing=Arrays.copyOf(request,request.length+1); expect(IllegalArgumentException.class,()->SkillVfxEditorProtocol.decodeRequest(trailing));
        expect(IllegalArgumentException.class,()->SkillVfxEditorProtocol.encodeRequest(new SkillVfxEditorProtocol.Request(SkillVfxEditorProtocol.Operation.CATALOG,0,SESSION,"",0,"","",null)));
        expect(IllegalArgumentException.class,()->SkillVfxEditorProtocol.decodeRequest(requestWithIdBytes(new byte[]{(byte)0xc3,0x28})));
        expect(IllegalArgumentException.class,()->SkillVfxEditorProtocol.encodeState(new SkillVfxEditorProtocol.State(SkillVfxEditorProtocol.Status.OK,0,SESSION,List.of(),null,false,"")));
        expect(IllegalArgumentException.class,()->SkillVfxEditorProtocol.decodeState(stateWire(0,List.of())));
        check(service.snapshot(DevAbilityDefinitions.SHARED_ARCANE_BURST_ID).revision()==0,"malformed packets do not alter state");
    }

    private static void encodeBounds(SkillVfxEditorService service) {
        check(SkillVfxEditorProtocol.encodeState(new SkillVfxEditorProtocol.State(SkillVfxEditorProtocol.Status.OK,1,SESSION,List.of(),null,false,repeat('x',SkillVfxEditorProtocol.MAX_STRING))).length>0,"string exact bound");
        expect(IllegalArgumentException.class,()->SkillVfxEditorProtocol.encodeState(new SkillVfxEditorProtocol.State(SkillVfxEditorProtocol.Status.OK,1,SESSION,List.of(),null,false,repeat('x',SkillVfxEditorProtocol.MAX_STRING+1))));
        List<AbilityDefinition.ActionSpec> actions=new ArrayList<>(); for(int i=0;i<=SkillVfxEditorProtocol.MAX_ACTIONS;i++)actions.add(new AbilityDefinition.Wait(1));
        AbilityDefinition manyActions=new AbilityDefinition(1,"projects:many-actions","Many",actions);
        SkillVfxEditorService.Snapshot tooManyActions=new SkillVfxEditorService.Snapshot(manyActions,"projects:vfx/x",simpleVisual("projects:vfx/x",3),simpleVisual("projects:vfx/x",3),0,"a","a",false);
        expect(IllegalArgumentException.class,()->SkillVfxEditorProtocol.encodeState(new SkillVfxEditorProtocol.State(SkillVfxEditorProtocol.Status.OK,1,SESSION,List.of(),tooManyActions,false,"")));
        List<AbilityVisualDefinition.HookBinding> hooks=new ArrayList<>(); for(AbilityLifecycleEvent.Hook hook:AbilityLifecycleEvent.Hook.values())if(hook!=AbilityLifecycleEvent.Hook.TRAVEL)hooks.add(new AbilityVisualDefinition.HookBinding(hook,List.of(new AbilityVisualDefinition.Emission(hook.name(),-1,List.of(circle("p"+hook,3))))));
        check(SkillVfxEditorProtocol.encodeVisual(new AbilityVisualDefinition(1,"projects:vfx/max-hooks",hooks)).length>0,"hook exact bound");
        List<SkillVfxEditorService.CatalogItem> duplicate=List.of(new SkillVfxEditorService.CatalogItem("projects:a","A","",false,0,"","",false),new SkillVfxEditorService.CatalogItem("projects:a","B","",false,0,"","",false));
        expect(IllegalArgumentException.class,()->SkillVfxEditorProtocol.encodeState(new SkillVfxEditorProtocol.State(SkillVfxEditorProtocol.Status.OK,1,SESSION,duplicate,null,false,"")));
        List<SkillVfxEditorService.CatalogItem> unsorted=List.of(new SkillVfxEditorService.CatalogItem("projects:z","Z","",false,0,"","",false),new SkillVfxEditorService.CatalogItem("projects:a","A","",false,0,"","",false));
        expect(IllegalArgumentException.class,()->SkillVfxEditorProtocol.encodeState(new SkillVfxEditorProtocol.State(SkillVfxEditorProtocol.Status.OK,1,SESSION,unsorted,null,false,"")));
        SkillVfxEditorService.CatalogItem malformed=new SkillVfxEditorService.CatalogItem("projects:a","A","projects:vfx/a",false,0,"","",false);
        expect(IllegalArgumentException.class,()->SkillVfxEditorProtocol.encodeState(new SkillVfxEditorProtocol.State(SkillVfxEditorProtocol.Status.OK,1,SESSION,List.of(malformed),null,false,"")));
        check(service.catalog().size()==2,"encode bounds preserves fixture service");
    }

    private static void decodeBounds() {
        expect(IllegalArgumentException.class,()->SkillVfxEditorProtocol.decodeState(stateHeader(SkillVfxEditorProtocol.MAX_ABILITIES+1)));
        List<SkillVfxEditorService.CatalogItem> duplicate=List.of(row("projects:a",false),row("projects:a",false));
        expect(IllegalArgumentException.class,()->SkillVfxEditorProtocol.decodeState(stateWire(1,duplicate)));
        List<SkillVfxEditorService.CatalogItem> unsorted=List.of(row("projects:z",false),row("projects:a",false));
        expect(IllegalArgumentException.class,()->SkillVfxEditorProtocol.decodeState(stateWire(1,unsorted)));
        expect(IllegalArgumentException.class,()->SkillVfxEditorProtocol.decodeState(stateWire(1,List.of(new SkillVfxEditorService.CatalogItem("projects:a","A","projects:vfx/a",false,0,"","",false)))));
        expect(IllegalArgumentException.class,()->SkillVfxEditorProtocol.decodeState(stateWire(1,List.of(new SkillVfxEditorService.CatalogItem("projects:a","A","",true,0,"","",false)))));
        expect(IllegalArgumentException.class,()->SkillVfxEditorProtocol.decodeRequest(requestWithVisualCount(6,0,0,0)));
        expect(IllegalArgumentException.class,()->SkillVfxEditorProtocol.decodeRequest(requestWithVisualCount(1,17,0,0)));
        expect(IllegalArgumentException.class,()->SkillVfxEditorProtocol.decodeRequest(requestWithVisualCount(1,1,17,0)));
        expect(IllegalArgumentException.class,()->SkillVfxEditorProtocol.decodeRequest(requestWithVisualCount(1,1,1,9)));
    }

    private static void operationShapes(SkillVfxEditorService service) {
        AbilityVisualDefinition visual=service.snapshot(DevAbilityDefinitions.SHARED_ARCANE_BURST_ID).effective();
        expect(IllegalArgumentException.class,()->SkillVfxEditorProtocol.encodeRequest(new SkillVfxEditorProtocol.Request(SkillVfxEditorProtocol.Operation.CATALOG,1,SESSION,"x",0,"","",null)));
        expect(IllegalArgumentException.class,()->SkillVfxEditorProtocol.encodeRequest(new SkillVfxEditorProtocol.Request(SkillVfxEditorProtocol.Operation.FETCH,1,SESSION,"",0,"","",null)));
        expect(IllegalArgumentException.class,()->SkillVfxEditorProtocol.encodeRequest(new SkillVfxEditorProtocol.Request(SkillVfxEditorProtocol.Operation.FETCH,1,SESSION,"projects:a",1,"","",null)));
        expect(IllegalArgumentException.class,()->SkillVfxEditorProtocol.encodeRequest(new SkillVfxEditorProtocol.Request(SkillVfxEditorProtocol.Operation.FETCH,1,SESSION,"projects:a",0,"x","",null)));
        expect(IllegalArgumentException.class,()->SkillVfxEditorProtocol.encodeRequest(new SkillVfxEditorProtocol.Request(SkillVfxEditorProtocol.Operation.APPLY_VISUAL_SESSION,1,SESSION,"projects:a",0,"b","e",null)));
        expect(IllegalArgumentException.class,()->SkillVfxEditorProtocol.encodeRequest(new SkillVfxEditorProtocol.Request(SkillVfxEditorProtocol.Operation.APPLY_VISUAL_SESSION,1,SESSION,"projects:a",-1,"b","e",visual)));
        expect(IllegalArgumentException.class,()->SkillVfxEditorProtocol.encodeRequest(new SkillVfxEditorProtocol.Request(SkillVfxEditorProtocol.Operation.REVERT_VISUAL_SESSION,1,SESSION,"projects:a",0,"b","e",visual)));
        expect(IllegalArgumentException.class,()->SkillVfxEditorProtocol.decodeRequest(requestWithRawFetchFields(1,"b","")));
    }

    private static void authorizationAndMetadata() throws IOException {
        check(SkillVfxEditorAuthorization.decide(SkillVfxEditorProtocol.Operation.CATALOG,true,false,false).allowed(),"open catalog");
        check(SkillVfxEditorAuthorization.decide(SkillVfxEditorProtocol.Operation.APPLY_VISUAL_SESSION,true,true,true).allowed(),"apply authority");
        check(!SkillVfxEditorAuthorization.decide(SkillVfxEditorProtocol.Operation.REVERT_VISUAL_SESSION,true,true,false).allowed(),"revert denied without apply");
        check(!SkillVfxEditorAuthorization.decide(SkillVfxEditorProtocol.Operation.FETCH,false,true,true).allowed()&&SkillVfxEditorAuthorization.decide(SkillVfxEditorProtocol.Operation.FETCH,false,true,true).previewAllowed(),"preview capability only");
        check(SkillVfxEditorChannel.REQUEST_CHANNEL.equals("projects:skill_editor_req_v1")&&SkillVfxEditorChannel.STATE_CHANNEL.equals("projects:skill_editor_state_v1"),"channel ids");
        String plugin=Files.readString(Path.of("src/main/resources/plugin.yml"));
        for(String permission:List.of(SkillVfxEditorChannel.OPEN_PERMISSION,SkillVfxEditorChannel.PREVIEW_PERMISSION,SkillVfxEditorChannel.APPLY_PERMISSION))check(plugin.contains("  "+permission+":")&&plugin.contains("    default: op"),"plugin permission "+permission);
    }

    private static void channelPermissionBoundary() {
        RecordingEditorService editor=new RecordingEditorService(service().snapshot(DevAbilityDefinitions.SHARED_ARCANE_BURST_ID));
        RecordingSender authorized=new RecordingSender(SkillVfxEditorChannel.OPEN_PERMISSION,SkillVfxEditorChannel.PREVIEW_PERMISSION,SkillVfxEditorChannel.APPLY_PERMISSION);
        SkillVfxEditorChannel.dispatch(SkillVfxEditorProtocol.encodeRequest(new SkillVfxEditorProtocol.Request(SkillVfxEditorProtocol.Operation.CATALOG,17,SESSION,"",0,"","",null)),authorized,editor);
        check(authorized.states.size()==1&&SkillVfxEditorProtocol.decodeState(authorized.states.getFirst()).status()==SkillVfxEditorProtocol.Status.OK,"authorized request returns state");
        check(editor.catalogCalls==1&&editor.snapshotCalls==0,"authorized catalog uses catalog only");

        RecordingEditorService deniedEditor=new RecordingEditorService(editor.snapshot);
        RecordingSender denied=new RecordingSender();
        SkillVfxEditorChannel.dispatch(SkillVfxEditorProtocol.encodeRequest(new SkillVfxEditorProtocol.Request(SkillVfxEditorProtocol.Operation.FETCH,18,SESSION,DevAbilityDefinitions.SHARED_ARCANE_BURST_ID,0,"","",null)),denied,deniedEditor);
        check(denied.states.isEmpty()&&deniedEditor.catalogCalls==0&&deniedEditor.snapshotCalls==0,"unauthorized sends no state or provider requests");
        SkillVfxEditorChannel.dispatch(new byte[]{2,127},denied,deniedEditor);
        SkillVfxEditorChannel.dispatch(null,denied,deniedEditor);
        check(denied.states.isEmpty()&&deniedEditor.catalogCalls==0&&deniedEditor.snapshotCalls==0,"malformed unauthorized input leaks nothing");

        RecordingEditorService applyDeniedEditor=new RecordingEditorService(editor.snapshot);
        RecordingSender openWithoutApply=new RecordingSender(SkillVfxEditorChannel.OPEN_PERMISSION);
        AbilityVisualDefinition visual=simpleVisual(editor.snapshot.visualId(),4);
        SkillVfxEditorChannel.dispatch(SkillVfxEditorProtocol.encodeRequest(new SkillVfxEditorProtocol.Request(SkillVfxEditorProtocol.Operation.APPLY_VISUAL_SESSION,19,SESSION,DevAbilityDefinitions.SHARED_ARCANE_BURST_ID,0,"base","effective",visual)),openWithoutApply,applyDeniedEditor);
        checkPermissionDenied(openWithoutApply,applyDeniedEditor,19,"open-only apply returns bounded denial");

        RecordingEditorService revertDeniedEditor=new RecordingEditorService(editor.snapshot);
        RecordingSender openWithoutApplyForRevert=new RecordingSender(SkillVfxEditorChannel.OPEN_PERMISSION);
        SkillVfxEditorChannel.dispatch(SkillVfxEditorProtocol.encodeRequest(new SkillVfxEditorProtocol.Request(SkillVfxEditorProtocol.Operation.REVERT_VISUAL_SESSION,20,SESSION,DevAbilityDefinitions.SHARED_ARCANE_BURST_ID,0,"base","effective",null)),openWithoutApplyForRevert,revertDeniedEditor);
        checkPermissionDenied(openWithoutApplyForRevert,revertDeniedEditor,20,"open-only revert returns bounded denial");
    }

    private static void checkPermissionDenied(RecordingSender sender,RecordingEditorService editor,long correlation,String message) {
        check(sender.states.size()==1,message+" state count");
        SkillVfxEditorProtocol.State state=SkillVfxEditorProtocol.decodeState(sender.states.getFirst());
        check(state.status()==SkillVfxEditorProtocol.Status.PERMISSION_DENIED&&state.correlation()==correlation&&state.session().equals(SESSION),message+" request identity");
        check(state.catalog().isEmpty()&&state.snapshot()==null,message+" redacted state");
        check(editor.serverSessionCalls==0&&editor.catalogCalls==0&&editor.snapshotCalls==0&&editor.applyCalls==0&&editor.revertCalls==0,message+" no service access");
    }

    private static final class RecordingSender implements SkillVfxEditorChannel.Sender {
        private final Set<String> permissions;
        private final List<byte[]> states=new ArrayList<>();
        private RecordingSender(String... permissions) { this.permissions=Set.of(permissions); }
        @Override public boolean hasPermission(String permission) { return permissions.contains(permission); }
        @Override public void send(byte[] payload) { states.add(payload); }
    }

    private static final class RecordingEditorService implements SkillVfxEditorServiceAccess {
        private final UUID session=SESSION;
        private final SkillVfxEditorService.Snapshot snapshot;
        private int serverSessionCalls, catalogCalls, snapshotCalls, applyCalls, revertCalls;
        private RecordingEditorService(SkillVfxEditorService.Snapshot snapshot) { this.snapshot=snapshot; }
        @Override public UUID serverSession() { serverSessionCalls++; return session; }
        @Override public List<SkillVfxEditorService.CatalogItem> catalog() { catalogCalls++; return List.of(new SkillVfxEditorService.CatalogItem(snapshot.ability().id(),snapshot.ability().displayName(),snapshot.visualId(),true,snapshot.revision(),snapshot.baseFingerprint(),snapshot.effectiveFingerprint(),snapshot.sessionOverride())); }
        @Override public SkillVfxEditorService.Snapshot snapshot(String abilityId) { snapshotCalls++; return snapshot; }
        @Override public SkillVfxEditorService.Snapshot apply(UUID session,String abilityId,long revision,String base,String effective,AbilityVisualDefinition visual) { applyCalls++; return snapshot; }
        @Override public SkillVfxEditorService.Snapshot revert(UUID session,String abilityId,long revision,String base,String effective) { revertCalls++; return snapshot; }
    }

    private static AbilityVisualDefinition simpleVisual(String id,double radius) { return new AbilityVisualDefinition(1,id,List.of(new AbilityVisualDefinition.HookBinding(AbilityLifecycleEvent.Hook.TELEGRAPH,List.of(new AbilityVisualDefinition.Emission("telegraph",0,List.of(circle("circle",radius))))))); }
    private static AbilityVisualDefinition.PrimitiveSpec circle(String id,double radius) { return new AbilityVisualDefinition.PrimitiveSpec(id,AbilityVisualDefinition.PrimitiveType.CIRCLE,0,1,1,1,1,1,new AbilityVisualDefinition.Vec(0,0,0),0,null,new AbilityVisualDefinition.Literal(radius),null,null,null,null,null,null,0,List.of()); }
    private static SkillVfxEditorService.CatalogItem row(String id,boolean bound) { return bound?new SkillVfxEditorService.CatalogItem(id,"A","projects:vfx/a",true,0,"b","e",false):new SkillVfxEditorService.CatalogItem(id,"A","",false,0,"","",false); }
    private static byte[] requestWithIdBytes(byte[] id) { return raw(out->{out.writeByte(1);out.writeByte(0);out.writeLong(1);uuid(out);out.writeShort(id.length);out.write(id);out.writeLong(0);str(out,"");str(out,"");out.writeBoolean(false);}); }
    private static byte[] requestWithRawFetchFields(long revision,String base,String effective) { return raw(out->{out.writeByte(1);out.writeByte(1);out.writeLong(1);uuid(out);str(out,"projects:a");out.writeLong(revision);str(out,base);str(out,effective);out.writeBoolean(false);}); }
    private static byte[] requestWithVisualCount(int hooks,int emissions,int primitives,int points) { return raw(out->{out.writeByte(1);out.writeByte(2);out.writeLong(1);uuid(out);str(out,"projects:a");out.writeLong(0);str(out,"b");str(out,"e");out.writeBoolean(true);out.writeByte(1);str(out,"projects:vfx/a");out.writeByte(hooks);if(hooks<=5){out.writeByte(0);out.writeByte(emissions);if(emissions<=16){str(out,"e");out.writeInt(0);out.writeByte(primitives);if(primitives<=16){str(out,"p");out.writeByte(3);out.writeInt(0);out.writeInt(1);out.writeInt(1);out.writeDouble(1);out.writeInt(1);out.writeLong(1);vec(out);out.writeDouble(0);for(int i=0;i<8;i++)out.writeByte(0);out.writeInt(0);out.writeByte(points);}}}}); }
    private static byte[] stateHeader(int count) { return raw(out->{out.writeByte(1);out.writeByte(0);out.writeLong(1);uuid(out);out.writeBoolean(false);str(out,"");out.writeShort(count);}); }
    private static byte[] stateWire(long correlation,List<SkillVfxEditorService.CatalogItem> catalog) { return raw(out->{out.writeByte(1);out.writeByte(0);out.writeLong(correlation);uuid(out);out.writeBoolean(false);str(out,"");out.writeShort(catalog.size());for(var row:catalog){str(out,row.abilityId());str(out,row.displayName());out.writeBoolean(row.hasVisual());str(out,row.visualId());out.writeLong(row.revision());str(out,row.baseFingerprint());str(out,row.effectiveFingerprint());out.writeBoolean(row.sessionOverride());}out.writeBoolean(false);}); }
    private interface Write { void accept(DataOutputStream out)throws IOException; }
    private static byte[] raw(Write write) { try {ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(DataOutputStream out=new DataOutputStream(bytes)){write.accept(out);}return bytes.toByteArray();}catch(IOException e){throw new AssertionError(e);} }
    private static void uuid(DataOutputStream out)throws IOException {out.writeLong(SESSION.getMostSignificantBits());out.writeLong(SESSION.getLeastSignificantBits());}
    private static void str(DataOutputStream out,String value)throws IOException {byte[] bytes=value.getBytes(StandardCharsets.UTF_8);out.writeShort(bytes.length);out.write(bytes);}
    private static void vec(DataOutputStream out)throws IOException {out.writeDouble(0);out.writeDouble(0);out.writeDouble(0);}
    private static String repeat(char c,int n) { return String.valueOf(c).repeat(n); }
    private static String sha(byte[] value)throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}
    private static void check(boolean value,String message) {if(!value)throw new AssertionError(message);}
    private static void expect(Class<? extends Throwable> type,Runnable action) {try{action.run();throw new AssertionError("expected "+type.getSimpleName());}catch(Throwable error){if(!type.isInstance(error))throw new AssertionError("expected "+type.getSimpleName()+", got "+error,error);}}
}
