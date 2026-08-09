package io.github.gyai.projects.ability;

import java.util.*;
/** The deliberately small v0.1 vertical slice; gameplay values are never duplicated here. */
public final class DevAbilityVisuals {
    public static final String ARCANE_BURST_VISUAL_ID="projects:vfx/dev-arcane-burst";
    private DevAbilityVisuals() { }
    public static void registerInto(AbilityVisualRegistry registry) {
        registry.register(arcaneBurst()); registry.bind(new AbilityVisualBinding(DevAbilityDefinitions.SHARED_ARCANE_BURST_ID,ARCANE_BURST_VISUAL_ID));
    }
    public static AbilityVisualDefinition arcaneBurst() {
        return new AbilityVisualDefinition(1,ARCANE_BURST_VISUAL_ID,List.of(
                h(AbilityLifecycleEvent.Hook.CAST,e("cast",-1,spiral("cast-spiral",2,12,1.2,1,1),sphere("cast-sphere",0,10,.7))),
                h(AbilityLifecycleEvent.Hook.TELEGRAPH,e("telegraph",0,circle("telegraph-circle",0,20,AbilityVisualDefinition.ActionField.RADIUS))),
                h(AbilityLifecycleEvent.Hook.HIT,e("hit",2,burst("hit-burst",0,10,1,12))),
                h(AbilityLifecycleEvent.Hook.EXPIRE,e("expire",-1,sphere("expire-fade",0,8,.5))),
                h(AbilityLifecycleEvent.Hook.CANCEL,e("cancel",-1,burst("cancel-burst",0,6,.4,8)))));
    }
    private static AbilityVisualDefinition.Emission e(String id,int action,AbilityVisualDefinition.PrimitiveSpec...p){return new AbilityVisualDefinition.Emission(id,action,List.of(p));}
    private static AbilityVisualDefinition.HookBinding h(AbilityLifecycleEvent.Hook hook,AbilityVisualDefinition.Emission...e){return new AbilityVisualDefinition.HookBinding(hook,List.of(e));}
    private static AbilityVisualDefinition.PrimitiveSpec circle(String id,int d,int t,AbilityVisualDefinition.Scalar r){return p(id,AbilityVisualDefinition.PrimitiveType.CIRCLE,d,t,null,r,null,null,null,null,null,null,0);}
    private static AbilityVisualDefinition.PrimitiveSpec sphere(String id,int d,int t,double r){return p(id,AbilityVisualDefinition.PrimitiveType.SPHERE,d,t,null,new AbilityVisualDefinition.Literal(r),null,null,null,null,null,null,0);}
    private static AbilityVisualDefinition.PrimitiveSpec burst(String id,int d,int t,double r,int count){return p(id,AbilityVisualDefinition.PrimitiveType.BURST,d,t,null,new AbilityVisualDefinition.Literal(r),null,null,null,null,null,null,count);}
    private static AbilityVisualDefinition.PrimitiveSpec spiral(String id,int d,int t,double r,double height,double turns){return p(id,AbilityVisualDefinition.PrimitiveType.SPIRAL,d,t,null,new AbilityVisualDefinition.Literal(r),null,new AbilityVisualDefinition.Literal(height),null,null,null,new AbilityVisualDefinition.Literal(turns),0);}
    private static AbilityVisualDefinition.PrimitiveSpec p(String id,AbilityVisualDefinition.PrimitiveType type,int d,int t,AbilityVisualDefinition.Scalar size,AbilityVisualDefinition.Scalar radius,AbilityVisualDefinition.Scalar length,AbilityVisualDefinition.Scalar height,AbilityVisualDefinition.Scalar angle,AbilityVisualDefinition.Scalar start,AbilityVisualDefinition.Scalar sweep,AbilityVisualDefinition.Scalar turns,int count){return new AbilityVisualDefinition.PrimitiveSpec(id,type,d,t,0xAAA060FF,.12,8,17,new AbilityVisualDefinition.Vec(0,0,0),0,size,radius,length,height,angle,start,sweep,turns,count,List.of());}
}
