package com.drehverschluss.stormcaller.compat;

import com.drehverschluss.stormcaller.config.StormcallerConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.spell_power.api.SpellSchools;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Gives the tier 1 equipment of Wizards (Wizard Robe, Wizard Staff) Lightning Spell Power.
 * <p>
 * Uses the modifier IDs of Spell Engine ({@code minecraft:armor.<slot>} for armor, {@code minecraft:equipment_bonus}
 * for weapons). If a pack already adds {@code spell_power:lightning} through {@code config/wizards/equipment_v2.json},
 * that entry has the same ID: it wins, stays where the config puts it, and nothing counts twice.
 * <p>
 * Order (phase 10): the lightning modifier goes right behind the {@code spell_power:frost} modifier of the same slot,
 * so the tooltip line is always right under Frost Spell Power, also when a pack adds more lines (mana) to the Wizards
 * config. Without Frost it goes behind the last spell school modifier of that slot, without one at the end. All other
 * modifiers keep their order: the list is copied, cleared and added again in the same order.
 * <p>
 * Listener priority HIGHEST: at that point the list only holds the item's own modifiers (Wizards config). Listeners of
 * other mods (Apotheosis affixes and gems, enchantments) run later and append behind, untouched by this class.
 */
public final class WizardsTierOne {
    private static final String WIZARDS = "wizards";
    private static final ResourceLocation STAFF_ID = ResourceLocation.fromNamespaceAndPath(WIZARDS, "staff_wizard");
    private static final ResourceLocation EQUIPMENT_BONUS_ID = ResourceLocation.withDefaultNamespace("equipment_bonus");
    private static final ResourceLocation FROST_ID = ResourceLocation.fromNamespaceAndPath("spell_power", "frost");
    /** Spell school attributes of Spell Power (not haste or crit). */
    private static final Set<String> SCHOOLS = Set.of("arcane", "fire", "frost", "healing", "lightning", "soul");

    /** Resolved lazily, after all items are registered. */
    private static volatile Map<Item, ArmorItem.Type> robePieces;
    private static volatile Item staff;
    private static volatile Holder<Attribute> lightning;

    private WizardsTierOne() {}

    public static void onItemAttributeModifiers(ItemAttributeModifierEvent event) {
        if (!StormcallerConfig.get(StormcallerConfig.WIZARDS_TIER_ONE_ENABLED)) {
            return;
        }
        resolve();
        if (lightning == null) {
            return;
        }
        Item item = event.getItemStack().getItem();
        ArmorItem.Type type = robePieces.get(item);
        if (type != null) {
            double value = StormcallerConfig.get(StormcallerConfig.WIZARD_ROBE_LIGHTNING);
            if (value != 0.0) {
                ResourceLocation id = ResourceLocation.withDefaultNamespace("armor." + type.getName());
                insert(event, new AttributeModifier(id, value, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
                        EquipmentSlotGroup.bySlot(type.getSlot()));
            }
        } else if (item == staff && staff != null) {
            double value = StormcallerConfig.get(StormcallerConfig.WIZARD_STAFF_LIGHTNING);
            if (value != 0.0) {
                insert(event, new AttributeModifier(EQUIPMENT_BONUS_ID, value, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND);
            }
        }
    }

    /**
     * Adds the lightning modifier behind Frost of the same slot (see class comment). Does nothing if a modifier with
     * the same ID for lightning is already there (pack entry in the Wizards config).
     */
    private static void insert(ItemAttributeModifierEvent event, AttributeModifier modifier, EquipmentSlotGroup slot) {
        List<ItemAttributeModifiers.Entry> entries = new ArrayList<>(event.getModifiers());
        int frost = -1;
        int school = -1;
        for (int i = 0; i < entries.size(); i++) {
            ItemAttributeModifiers.Entry entry = entries.get(i);
            if (entry.attribute().is(lightning.unwrapKey().orElseThrow()) && entry.modifier().id().equals(modifier.id())) {
                return; // the config entry wins and keeps its place
            }
            if (entry.slot() != slot) {
                continue;
            }
            ResourceLocation attribute = entry.attribute().unwrapKey().map(key -> key.location()).orElse(null);
            if (attribute == null) {
                continue;
            }
            if (attribute.equals(FROST_ID)) {
                frost = i;
            }
            if (attribute.getNamespace().equals("spell_power") && SCHOOLS.contains(attribute.getPath())) {
                school = i;
            }
        }
        int at = frost >= 0 ? frost + 1 : school >= 0 ? school + 1 : entries.size();
        if (at >= entries.size()) {
            event.addModifier(lightning, modifier, slot);
            return;
        }
        entries.add(at, new ItemAttributeModifiers.Entry(lightning, modifier, slot));
        event.clearModifiers();
        for (ItemAttributeModifiers.Entry entry : entries) {
            event.addModifier(entry.attribute(), entry.modifier(), entry.slot());
        }
    }

    private static void resolve() {
        if (robePieces != null) {
            return;
        }
        Map<Item, ArmorItem.Type> pieces = new IdentityHashMap<>();
        for (String slot : new String[]{"head", "chest", "legs", "feet"}) {
            Optional<Item> item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath(WIZARDS, "wizard_robe_" + slot));
            item.ifPresent(i -> {
                if (i instanceof ArmorItem armor) {
                    pieces.put(i, armor.getType());
                }
            });
        }
        staff = BuiltInRegistries.ITEM.getOptional(STAFF_ID).orElse(null);
        lightning = BuiltInRegistries.ATTRIBUTE.getHolder(SpellSchools.LIGHTNING.id).orElse(null);
        robePieces = pieces;
    }
}
