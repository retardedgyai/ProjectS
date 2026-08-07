package io.github.gyai.projects.beta.activation.track3.infrastructure;

import io.github.gyai.projects.beta.activation.track3.StagingEconomyCatalog;
import io.github.gyai.projects.beta.activation.track3.StagingEquipmentCodec;
import io.github.gyai.projects.beta.activation.track3.StagingEquipmentInspectionFormatter;
import io.github.gyai.projects.beta.activation.track3.StagingModRollService;
import io.github.gyai.projects.equipment.BaseStatRoll;
import io.github.gyai.projects.equipment.CrafterIdentity;
import io.github.gyai.projects.equipment.EquipmentItemV1;
import io.github.gyai.projects.equipment.EquipmentModSlot;
import io.github.gyai.projects.equipment.EquipmentTier;
import io.github.gyai.projects.mod.UnknownModEntry;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Focused registry-free assertions for inspection text and Bukkit presentation. */
public final class StagingEquipmentInspectionPresentationTest {
    private StagingEquipmentInspectionPresentationTest() { }

    public static void runAll() {
        formatterPreservesKnownAndOpaqueModMetadata();
        loreShowsRequiredReadOnlyDetailsWithoutIdentityAllocation();
    }

    private static void formatterPreservesKnownAndOpaqueModMetadata() {
        EquipmentItemV1 known = knownFixture();
        String formatted = StagingEquipmentInspectionFormatter.format(known);
        for (String field : List.of(
                "EquipmentSchema=1", "ID=" + known.itemId(), "UUID=" + known.instanceId().orElseThrow(),
                "Tier=T1", "ILv=1", "Rarity=COMMON", "Quality=UNSPECIFIED",
                "Category=WEAPON", "Slot=WEAPON", "Enhancement=2", "Broken=false",
                "Binding=UNBOUND", "Trade=DENY_ALL", "MOD slots=1",
                "BaseStats=[projects:physical-attack=12.5]", "CrafterUUID=" + uuid(91),
                "Crafter=Fixture Smith", "MOD slot=0 ID=projects:staging-keen-edge",
                "Rank=RANK_1", "value=1.25", "Display=Keen Edge (Staging)",
                "Schema=1", "DefinitionRevision=1", "SourceCatalog=projects:staging-fixtures",
                "SourceId=projects:staging-craft")) {
            check(formatted.contains(field), "formatter omitted " + field);
        }
        check(formatted.length() < StagingEquipmentInspectionFormatter.MAXIMUM_RENDERED_CHARACTERS,
                "ordinary staging fixture was formatter-truncated");
        check(StagingEquipmentInspectionFormatter.format(known, 256).length() <= 256,
                "bounded formatter exceeded command-sized limit");

        EquipmentItemV1 unknown = unknownFixture();
        UnknownModEntry opaque = (UnknownModEntry) unknown.modSlots().getFirst().entry().orElseThrow();
        String unknownRendered = StagingEquipmentInspectionFormatter.format(unknown);
        check(!opaque.effectEnabled(), "opaque MOD became enabled");
        check(unknownRendered.contains("UnknownMOD slot=0 ID=future:opaque")
                        && unknownRendered.contains("Schema=future-mod/99")
                        && unknownRendered.contains("UNKNOWN / 効果無効"),
                "formatter did not preserve or disable opaque MOD");
    }

    private static void loreShowsRequiredReadOnlyDetailsWithoutIdentityAllocation() {
        AtomicInteger factories = new AtomicInteger();
        BukkitStagingEquipmentItemAdapter adapter = adapter(factories);
        EquipmentItemV1 preview = StagingEconomyCatalog.previewBlade(EquipmentTier.T1);
        check(preview.instanceId().isEmpty(), "fixture preview already has a UUID");
        ItemStack previewStack = adapter.preview(preview);
        check(preview.instanceId().isEmpty() && factories.get() == 1,
                "preview allocated identity instead of only rendering a stack");

        EquipmentItemV1 known = knownFixture();
        ItemStack stack = adapter.committed(new StagingEquipmentCodec().encode(known, 1));
        String lore = lore(stack);
        for (String field : List.of("Equipment schema 1", "Item ID: " + known.itemId(),
                "Instance: " + known.instanceId().orElseThrow(), "Rarity COMMON / Quality UNSPECIFIED",
                "Category WEAPON / Slot weapon", "Enhancement +2 / Broken false",
                "Binding UNBOUND / Trade DENY_ALL", "Crafter UUID: " + uuid(91),
                "Crafter: Fixture Smith", "Base stats: projects:physical-attack=12.5", "MOD slots: 1",
                "MOD projects:staging-keen-edge R1 1.25", "Keen Edge (Staging)")) {
            check(lore.contains(field), "lore omitted " + field);
        }

        EquipmentItemV1 unknown = unknownFixture();
        String opaqueLore = lore(adapter.committed(new StagingEquipmentCodec().encode(unknown, 2)));
        check(opaqueLore.contains("MOD #0 future:opaque (future-mod/99) UNKNOWN / 効果無効"),
                "lore did not preserve disabled opaque MOD");
        check(factories.get() == 3, "inspection/lore rendering called an identity supplier");
    }

    private static EquipmentItemV1 knownFixture() {
        EquipmentItemV1 resolved = new StagingModRollService(
                StagingModRollService.defaultCandidates(), () -> .25)
                .resolve(StagingEconomyCatalog.previewBlade(EquipmentTier.T1));
        return fixture(resolved, resolved.modSlots());
    }

    private static EquipmentItemV1 unknownFixture() {
        EquipmentItemV1 preview = StagingEconomyCatalog.previewBlade(EquipmentTier.T1);
        return fixture(preview, List.of(new EquipmentModSlot(0, Optional.of(new UnknownModEntry(
                0, "future-mod", 99, "future:opaque", new byte[]{1, 2, 3})))));
    }

    private static EquipmentItemV1 fixture(EquipmentItemV1 base, List<EquipmentModSlot> mods) {
        return new EquipmentItemV1(base.schemaVersion(), base.itemId(), base.category(), base.slot(),
                base.tier(), base.itemLevel(), base.rarity(), base.quality(),
                List.of(new BaseStatRoll("projects:physical-attack", 12.5)), mods,
                Optional.of(new CrafterIdentity(uuid(91), "Fixture Smith")), 2, false,
                base.binding(), base.tradePolicy(), Optional.of(uuid(92)));
    }

    private static BukkitStagingEquipmentItemAdapter adapter(AtomicInteger factories) {
        return new BukkitStagingEquipmentItemAdapter(new NamespacedKey("projects", "marker"),
                new NamespacedKey("projects", "payload"), new NamespacedKey("projects", "revision"),
                new NamespacedKey("projects", "item"), () -> {
                    factories.incrementAndGet();
                    return new FixtureStack();
                });
    }

    private static String lore(ItemStack stack) {
        return stack.getItemMeta().lore().stream().map(String::valueOf)
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    private static UUID uuid(long value) { return new UUID(0, value); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    /** Minimal ItemStack/ItemMeta/PDC seam; it deliberately avoids Bukkit registries. */
    private static final class FixtureStack extends ItemStack {
        private MetaState state = new MetaState();
        @Override public Material getType() { return Material.IRON_SWORD; }
        @Override public boolean hasItemMeta() { return true; }
        @Override public ItemMeta getItemMeta() { return meta(state); }
        @Override public boolean setItemMeta(ItemMeta value) {
            if (!Proxy.isProxyClass(value.getClass())) return false;
            InvocationHandler handler = Proxy.getInvocationHandler(value);
            if (!(handler instanceof MetaHandler meta)) return false;
            state = meta.state.copy();
            return true;
        }
    }

    private static ItemMeta meta(MetaState state) {
        return (ItemMeta) Proxy.newProxyInstance(ItemMeta.class.getClassLoader(), new Class[]{ItemMeta.class},
                new MetaHandler(state));
    }

    private static final class MetaHandler implements InvocationHandler {
        private final MetaState state;
        private MetaHandler(MetaState state) { this.state = state; }
        @Override public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] arguments) {
            Object[] args = arguments == null ? new Object[0] : arguments;
            return switch (method.getName()) {
                case "displayName" -> args.length == 0 ? state.display : setDisplay((Component) args[0]);
                case "lore" -> args.length == 0 ? List.copyOf(state.lore) : setLore(args[0]);
                case "getPersistentDataContainer" -> pdc(state);
                case "clone" -> meta(state.copy());
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                default -> primitiveDefault(method.getReturnType());
            };
        }
        private Object setDisplay(Component value) { state.display = value; return null; }
        @SuppressWarnings("unchecked")
        private Object setLore(Object value) { state.lore = List.copyOf((List<Component>) value); return null; }
    }

    private static PersistentDataContainer pdc(MetaState state) {
        return (PersistentDataContainer) Proxy.newProxyInstance(PersistentDataContainer.class.getClassLoader(),
                new Class[]{PersistentDataContainer.class}, (proxy, method, arguments) -> {
                    Object[] args = arguments == null ? new Object[0] : arguments;
                    if (method.getName().equals("set")) {
                        state.values.put((NamespacedKey) args[0], args[2]);
                        return null;
                    }
                    if (method.getName().equals("equals")) return proxy == args[0];
                    if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                    return primitiveDefault(method.getReturnType());
                });
    }

    private static Object primitiveDefault(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        return null;
    }

    private static final class MetaState {
        private Component display;
        private List<Component> lore = new ArrayList<>();
        private final Map<NamespacedKey, Object> values = new HashMap<>();
        private MetaState copy() {
            MetaState copy = new MetaState();
            copy.display = display;
            copy.lore = List.copyOf(lore);
            copy.values.putAll(values);
            return copy;
        }
    }
}
