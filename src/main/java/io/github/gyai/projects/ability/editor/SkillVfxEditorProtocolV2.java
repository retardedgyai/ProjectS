package io.github.gyai.projects.ability.editor;

import io.github.gyai.projects.ability.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Appearance-aware editor transport.  A v2 frame is {@code u8(2), u16(v1-body-length),
 * v1-body, u8(visual-table-count), visual-table...}.  The v1 body deliberately remains
 * canonical for every non-appearance field; each table is {@code u16(count), [primitive-id,
 * appearance-kind, appearance-id]*}.  This keeps the v2 delta bounded and deterministic.
 */
public final class SkillVfxEditorProtocolV2 {
    public static final int VERSION=2, MAX_PACKET=SkillVfxEditorProtocol.MAX_PACKET, MAX_STRING=SkillVfxEditorProtocol.MAX_STRING;
    private SkillVfxEditorProtocolV2() { }
    public static byte[] encodeRequest(SkillVfxEditorProtocol.Request request) { return frame(SkillVfxEditorProtocol.encodeRequest(request), request.visual()==null?List.of():List.of(request.visual())); }
    public static SkillVfxEditorProtocol.Request decodeRequest(byte[] bytes) { Frame f=unframe(bytes); if(f.tables().size()!=(SkillVfxEditorProtocol.decodeRequest(f.body()).visual()==null?0:1)) throw bad("request tables"); SkillVfxEditorProtocol.Request r=SkillVfxEditorProtocol.decodeRequest(f.body()); return new SkillVfxEditorProtocol.Request(r.operation(),r.correlation(),r.session(),r.abilityId(),r.revision(),r.baseFingerprint(),r.effectiveFingerprint(),r.visual()==null?null:patch(r.visual(),f.tables().getFirst())); }
    public static byte[] encodeState(SkillVfxEditorProtocol.State state) { List<AbilityVisualDefinition> visuals=state.snapshot()==null?List.of():List.of(state.snapshot().base(),state.snapshot().effective()); return frame(SkillVfxEditorProtocol.encodeState(state),visuals); }
    public static SkillVfxEditorProtocol.State decodeState(byte[] bytes) { Frame f=unframe(bytes); SkillVfxEditorProtocol.State s=SkillVfxEditorProtocol.decodeState(f.body()); if(s.snapshot()==null) { if(!f.tables().isEmpty()) throw bad("state tables"); return s; } if(f.tables().size()!=2) throw bad("state tables"); var old=s.snapshot(); var snapshot=new SkillVfxEditorService.Snapshot(old.ability(),old.visualId(),patch(old.base(),f.tables().get(0)),patch(old.effective(),f.tables().get(1)),old.revision(),old.baseFingerprint(),old.effectiveFingerprint(),old.sessionOverride()); return new SkillVfxEditorProtocol.State(s.status(),s.correlation(),s.session(),s.catalog(),snapshot,s.previewAllowed(),s.message()); }
    /** Stand-alone fixture-friendly visual codec using the same bounded table layout. */
    public static byte[] encodeVisual(AbilityVisualDefinition visual) { return frame(SkillVfxEditorProtocol.encodeVisual(visual),List.of(visual)); }
    public static AbilityVisualDefinition decodeVisual(byte[] bytes) { Frame f=unframe(bytes); if(f.tables().size()!=1) throw bad("visual tables"); return patch(decodeBaseVisual(f.body()),f.tables().getFirst()); }

    private record Frame(byte[] body,List<Map<String,AbilityVisualDefinition.Appearance>> tables) { }
    private static byte[] frame(byte[] body,List<AbilityVisualDefinition> visuals) {
        if(body.length>65535) throw bad("body");
        try { ByteArrayOutputStream raw=new ByteArrayOutputStream(); DataOutputStream o=new DataOutputStream(raw); o.writeByte(VERSION);o.writeShort(body.length);o.write(body);o.writeByte(visuals.size());for(var v:visuals)table(o,v);o.flush();if(raw.size()>MAX_PACKET)throw bad("packet");return raw.toByteArray(); } catch(IOException e){throw new IllegalStateException(e);}
    }
    private static Frame unframe(byte[] bytes) {
        if(bytes==null||bytes.length>MAX_PACKET)throw bad("packet");
        try { DataInputStream i=new DataInputStream(new ByteArrayInputStream(bytes));if(i.readUnsignedByte()!=VERSION)throw new IOException("version");int n=i.readUnsignedShort();byte[] body=i.readNBytes(n);if(body.length!=n)throw new EOFException();int tables=i.readUnsignedByte();if(tables>2)throw new IOException("tables");List<Map<String,AbilityVisualDefinition.Appearance>> result=new ArrayList<>();for(int x=0;x<tables;x++)result.add(readTable(i));if(i.available()!=0)throw new IOException("trailing");return new Frame(body,List.copyOf(result)); }catch(Exception e){throw bad("malformed",e);}
    }
    private static AbilityVisualDefinition decodeBaseVisual(byte[] body) {
        /* A synthetic v1 APPLY packet gives the v1 codec ownership of all scalar/shape parsing. */
        try { DataInputStream i=new DataInputStream(new ByteArrayInputStream(body)); if(i.readUnsignedByte()!=1)throw new IOException("visual"); int first=i.readUnsignedByte();
            if(first==1) { /* standalone visual starts schema version 1, not request operation */ }
        } catch(IOException e) { throw bad("visual",e); }
        // Stand-alone visuals are decoded by a small request envelope generated only for this purpose.
        try { ByteArrayOutputStream raw=new ByteArrayOutputStream();DataOutputStream o=new DataOutputStream(raw);o.writeByte(1);o.writeByte(SkillVfxEditorProtocol.Operation.APPLY_VISUAL_SESSION.ordinal());o.writeLong(1);o.writeLong(0);o.writeLong(0);str(o,"projects:a");o.writeLong(0);str(o,"b");str(o,"e");o.writeBoolean(true);o.write(body);o.flush();return SkillVfxEditorProtocol.decodeRequest(raw.toByteArray()).visual(); }catch(IOException e){throw bad("visual",e);}
    }
    private static void table(DataOutputStream o,AbilityVisualDefinition visual)throws IOException { List<AbilityVisualDefinition.PrimitiveSpec> ps=primitives(visual);o.writeShort(ps.size());for(var p:ps){str(o,p.id());o.writeByte(p.appearance().kind().ordinal());str(o,p.appearance().id());} }
    private static Map<String,AbilityVisualDefinition.Appearance> readTable(DataInputStream i)throws IOException {int count=i.readUnsignedShort();if(count>SkillVfxEditorProtocol.MAX_HOOKS*SkillVfxEditorProtocol.MAX_EMISSIONS*SkillVfxEditorProtocol.MAX_PRIMITIVES)throw new IOException("appearance count");Map<String,AbilityVisualDefinition.Appearance> appearances=new HashMap<>();for(int x=0;x<count;x++){String id=str(i);var kind=enumValue(AbilityVisualDefinition.AppearanceKind.values(),i.readUnsignedByte());var appearance=new AbilityVisualDefinition.Appearance(kind,str(i));if(appearances.put(id,appearance)!=null)throw new IOException("duplicate primitive");}return Map.copyOf(appearances);}
    private static AbilityVisualDefinition patch(AbilityVisualDefinition base,Map<String,AbilityVisualDefinition.Appearance> appearances) { List<AbilityVisualDefinition.PrimitiveSpec> all=primitives(base);if(appearances.size()!=all.size()||!appearances.keySet().equals(all.stream().map(AbilityVisualDefinition.PrimitiveSpec::id).collect(java.util.stream.Collectors.toSet())))throw bad("primitive ids");List<AbilityVisualDefinition.HookBinding> bindings=new ArrayList<>();for(var h:base.bindings()){List<AbilityVisualDefinition.Emission> emissions=new ArrayList<>();for(var e:h.emissions()){List<AbilityVisualDefinition.PrimitiveSpec> ps=new ArrayList<>();for(var p:e.primitives())ps.add(copy(p,appearances.get(p.id())));emissions.add(new AbilityVisualDefinition.Emission(e.id(),e.actionIndex(),ps));}bindings.add(new AbilityVisualDefinition.HookBinding(h.hook(),emissions));}return new AbilityVisualDefinition(base.schemaVersion(),base.id(),bindings); }
    private static AbilityVisualDefinition.PrimitiveSpec copy(AbilityVisualDefinition.PrimitiveSpec p,AbilityVisualDefinition.Appearance a){return new AbilityVisualDefinition.PrimitiveSpec(p.id(),p.type(),p.delayTicks(),p.durationTicks(),p.argb(),p.width(),p.density(),p.seed(),p.localOffset(),p.yawRadians(),p.size(),p.radius(),p.length(),p.height(),p.angle(),p.startAngle(),p.sweepAngle(),p.turns(),p.count(),p.controlPoints(),a);}
    private static List<AbilityVisualDefinition.PrimitiveSpec> primitives(AbilityVisualDefinition v){List<AbilityVisualDefinition.PrimitiveSpec> result=new ArrayList<>();for(var h:v.bindings())for(var e:h.emissions())result.addAll(e.primitives());return result;}
    private static void str(DataOutputStream o,String s)throws IOException{byte[] b=s.getBytes(StandardCharsets.UTF_8);if(b.length>MAX_STRING)throw new IOException("string");o.writeShort(b.length);o.write(b);}private static String str(DataInputStream i)throws IOException{int n=i.readUnsignedShort();if(n>MAX_STRING)throw new IOException("string");byte[] b=i.readNBytes(n);if(b.length!=n)throw new EOFException();String s=new String(b,StandardCharsets.UTF_8);if(!Arrays.equals(b,s.getBytes(StandardCharsets.UTF_8)))throw new IOException("utf8");return s;}private static <T>T enumValue(T[] values,int ordinal)throws IOException{if(ordinal<0||ordinal>=values.length)throw new IOException("enum");return values[ordinal];}private static IllegalArgumentException bad(String message){return new IllegalArgumentException(message);}private static IllegalArgumentException bad(String message,Exception cause){return new IllegalArgumentException(message,cause);}
}
