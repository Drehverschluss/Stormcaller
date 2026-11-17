package com.drehverschluss.stormcaller.compat;

import com.drehverschluss.stormcaller.Stormcaller;
import com.drehverschluss.stormcaller.config.StormcallerConfig;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.spell_engine.api.item.ItemAttributeModifiers;
import net.spell_engine.utils.AttributeModifierUtil;
import net.spell_power.api.SpellSchools;
import org.jetbrains.annotations.Nullable;

/**
 * Gives the tier 1 equipment of Wizards (Wizard Robe, Wizard Staff) Lightning Spell Power.
 * <p>
 * If a pack already adds {@code spell_power:lightning} to the item in that slot (for example through
 * {@code config/wizards/equipment_v2.json}), that entry wins and nothing counts twice.
 * <p>
 * <b>Forge 1.20.1:</b> Spell Engine keeps the attribute modifiers of RPG Series equipment in its own per-item map
 * ({@link AttributeModifierUtil#itemModifiers}). A mixin of Spell Engine on the return of
 * {@code ItemStack.getAttributeModifiers} replaces the result with that map, so whatever {@code ItemAttributeModifierEvent}
 * adds is lost (1.21.1 uses the event). The lightning entry is therefore written into that map:
 * <ul>
 *     <li>at common setup with the default values (main menu, tooltips before joining a world),</li>
 *     <li>whenever the server config loads or reloads (server start, file change while running, and on the client when
 *     the server sends its config on login),</li>
 *     <li>and on a tick after the values changed without a reload event ({@link #onTick}).</li>
 * </ul>
 * Each time our own entry (fixed UUIDs, {@link Stormcaller#uuid}) is first removed and then added again with the current
 * value, or left out when switched off or 0. The line goes right after Frost Spell Power, like on 1.21.1. The map holds
 * items, not data pack content, so {@code /reload} does not touch it. After a change the players get the new value
 * right away ({@link #refreshPlayers}).
 */
public final class WizardsTierOne {
    private static final String WIZARDS = "wizards";
    private static final ResourceLocation STAFF_ID = new ResourceLocation(WIZARDS, "staff_wizard");
    private static final ResourceLocation FROST = new ResourceLocation("spell_power", "frost");
    private static final UUID STAFF_MODIFIER = Stormcaller.uuid("wizards_tier_one/equipment_bonus");

    /** Resolved lazily, after all items are registered. */
    private static volatile Map<Item, ArmorItem.Type> robePieces;
    private static volatile Item staff;
    /** Values written last ({@link #onTick}). */
    private static volatile boolean lastEnabled;
    private static volatile double lastRobe = Double.NaN;
    private static volatile double lastStaff = Double.NaN;

    private WizardsTierOne() {}

    /** Mod bus: server config loaded or reloaded. */
    public static void onConfig(ModConfigEvent event) {
        if (event.getConfig().getType() == ModConfig.Type.SERVER && event.getConfig().getModId().equals(Stormcaller.MODID)) {
            apply();
        }
    }

    /**
     * Server and client tick: if the three values differ from the ones written last, write again. Catches changes that
     * reach the config without a reload event (a mod calling {@code set()}); three cached reads per tick.
     */
    public static void onTick(TickEvent event) {
        if (event.phase == TickEvent.Phase.END && robePieces != null
                && (StormcallerConfig.get(StormcallerConfig.WIZARDS_TIER_ONE_ENABLED) != lastEnabled
                || StormcallerConfig.get(StormcallerConfig.WIZARD_ROBE_LIGHTNING) != lastRobe
                || StormcallerConfig.get(StormcallerConfig.WIZARD_STAFF_LIGHTNING) != lastStaff)) {
            apply();
        }
    }

    /** Writes the lightning entries with the current config values (defaults while the server config is not loaded). */
    public static synchronized void apply() {
        resolve();
        boolean enabled = StormcallerConfig.get(StormcallerConfig.WIZARDS_TIER_ONE_ENABLED);
        double robe = StormcallerConfig.get(StormcallerConfig.WIZARD_ROBE_LIGHTNING);
        double staffValue = StormcallerConfig.get(StormcallerConfig.WIZARD_STAFF_LIGHTNING);
        lastEnabled = enabled;
        lastRobe = robe;
        lastStaff = staffValue;
        robePieces.forEach((item, type) -> update(item, robeModifier(type), type.getSlot(),
                enabled && robe != 0.0 ? new AttributeModifier(robeModifier(type), "Wizard robe lightning", robe,
                        AttributeModifier.Operation.MULTIPLY_BASE) : null));
        if (staff != null) {
            update(staff, STAFF_MODIFIER, EquipmentSlot.MAINHAND,
                    enabled && staffValue != 0.0 ? new AttributeModifier(STAFF_MODIFIER, "Wizard staff lightning", staffValue,
                            AttributeModifier.Operation.ADDITION) : null);
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            server.execute(() -> refreshPlayers(server));
        }
    }

    /**
     * Server thread: puts our modifiers on every player in line with the new values. Vanilla only updates item modifiers
     * on an equipment change and takes the old ones off with the item's <i>current</i> modifiers: a value switched off
     * while a player wears the item would otherwise stay on that player. So our UUIDs are removed from everyone and added
     * again for the items worn right now.
     */
    private static void refreshPlayers(MinecraftServer server) {
        Attribute lightning = BuiltInRegistries.ATTRIBUTE.getOptional(SpellSchools.LIGHTNING.id).orElse(null);
        if (lightning == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            AttributeInstance instance = player.getAttribute(lightning);
            if (instance == null) {
                continue;
            }
            instance.removeModifier(STAFF_MODIFIER);
            for (ArmorItem.Type type : ArmorItem.Type.values()) {
                instance.removeModifier(robeModifier(type));
            }
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack stack = player.getItemBySlot(slot);
                if (stack.isEmpty() || (stack.getItem() != staff && !robePieces.containsKey(stack.getItem()))) {
                    continue;
                }
                for (AttributeModifier modifier : stack.getAttributeModifiers(slot).get(lightning)) {
                    if (isOwn(modifier.getId()) && !instance.hasModifier(modifier)) {
                        instance.addTransientModifier(modifier);
                    }
                }
            }
        }
    }

    private static boolean isOwn(UUID id) {
        if (id.equals(STAFF_MODIFIER)) {
            return true;
        }
        for (ArmorItem.Type type : ArmorItem.Type.values()) {
            if (id.equals(robeModifier(type))) {
                return true;
            }
        }
        return false;
    }

    private static UUID robeModifier(ArmorItem.Type type) {
        return Stormcaller.uuid("wizards_tier_one/armor." + type.getName());
    }

    /**
     * Replaces our entry ({@code ownId}) of the item: removed, then {@code modifier} added unless null or unless another
     * {@code spell_power:lightning} entry already applies to {@code slot} (pack entry wins).
     */
    private static void update(Item item, UUID ownId, EquipmentSlot slot, @Nullable AttributeModifier modifier) {
        ItemAttributeModifiers current = AttributeModifierUtil.itemModifiers(item);
        if (current == null) {
            // Without a Spell Engine entry the vanilla modifiers of the item apply; Wizards 3.1.3 always registers one
            Stormcaller.LOGGER.warn("Wizards tier 1: {} has no Spell Engine attribute modifiers, no Lightning Spell Power added",
                    BuiltInRegistries.ITEM.getKey(item));
            return;
        }
        List<ItemAttributeModifiers.Entry> entries = new ArrayList<>(current.modifiers().size() + 1);
        boolean otherLightning = false;
        int afterFrost = -1;
        for (ItemAttributeModifiers.Entry entry : current.modifiers()) {
            if (entry.modifier().getId().equals(ownId)) {
                continue;
            }
            entries.add(entry);
            if (entry.slot().matches(slot)) {
                if (entry.attributeId().equals(SpellSchools.LIGHTNING.id)) {
                    otherLightning = true;
                } else if (entry.attributeId().equals(FROST)) {
                    afterFrost = entries.size();
                }
            }
        }
        if (modifier != null && !otherLightning) {
            ItemAttributeModifiers.Entry own = new ItemAttributeModifiers.Entry(SpellSchools.LIGHTNING.id, modifier,
                    ItemAttributeModifiers.Slot.forEquipmentSlot(slot));
            entries.add(afterFrost >= 0 ? afterFrost : entries.size(), own);
        }
        // always written: AttributeModifier.equals only compares the UUID, a changed value would look equal
        AttributeModifierUtil.setItemModifiers(item, new ItemAttributeModifiers(List.copyOf(entries), current.showInTooltip()));
    }

    private static void resolve() {
        if (robePieces != null) {
            return;
        }
        Map<Item, ArmorItem.Type> pieces = new IdentityHashMap<>();
        for (String slot : new String[]{"head", "chest", "legs", "feet"}) {
            Optional<Item> item = BuiltInRegistries.ITEM.getOptional(new ResourceLocation(WIZARDS, "wizard_robe_" + slot));
            item.ifPresent(i -> {
                if (i instanceof ArmorItem armor) {
                    pieces.put(i, armor.getType());
                }
            });
        }
        staff = BuiltInRegistries.ITEM.getOptional(STAFF_ID).orElse(null);
        robePieces = pieces;
    }
}
