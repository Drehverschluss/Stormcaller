package com.drehverschluss.stormcaller.compat;

import com.drehverschluss.stormcaller.Stormcaller;
import com.drehverschluss.stormcaller.config.StormcallerConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.spell_power.api.SpellSchools;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Gives the tier 1 equipment of Wizards (Wizard Robe, Wizard Staff) Lightning Spell Power.
 * <p>
 * If a pack already adds {@code spell_power:lightning} to the item in that slot (for example through
 * {@code config/wizards/equipment_v2.json}), that entry wins and nothing counts twice.
 * <p>
 * Forge 1.20.1: the event runs once per equipment slot and modifiers are identified by UUID (1.21.1: by id, with a slot
 * group). The modifiers use fixed UUIDs ({@link Stormcaller#uuid}). Unlike 1.21.1 the line cannot be sorted in right
 * under Frost Spell Power: the modifier map of the event has no order that the tooltip keeps.
 * <p>
 * Listener priority HIGHEST: at that point the map only holds the item's own modifiers (Wizards config). Listeners of
 * other mods (Apotheosis affixes and gems, enchantments) run later.
 */
public final class WizardsTierOne {
    private static final String WIZARDS = "wizards";
    private static final ResourceLocation STAFF_ID = new ResourceLocation(WIZARDS, "staff_wizard");

    /** Resolved lazily, after all items are registered. */
    private static volatile Map<Item, ArmorItem.Type> robePieces;
    private static volatile Item staff;
    private static volatile Attribute lightning;

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
            if (event.getSlotType() != type.getSlot()) {
                return;
            }
            double value = StormcallerConfig.get(StormcallerConfig.WIZARD_ROBE_LIGHTNING);
            if (value != 0.0) {
                add(event, new AttributeModifier(Stormcaller.uuid("wizards_tier_one/armor." + type.getName()),
                        "Wizard robe lightning", value, AttributeModifier.Operation.MULTIPLY_BASE));
            }
        } else if (item == staff && staff != null && event.getSlotType() == EquipmentSlot.MAINHAND) {
            double value = StormcallerConfig.get(StormcallerConfig.WIZARD_STAFF_LIGHTNING);
            if (value != 0.0) {
                add(event, new AttributeModifier(Stormcaller.uuid("wizards_tier_one/equipment_bonus"),
                        "Wizard staff lightning", value, AttributeModifier.Operation.ADDITION));
            }
        }
    }

    /** Adds the lightning modifier unless the item already has lightning in this slot (pack entry wins). */
    private static void add(ItemAttributeModifierEvent event, AttributeModifier modifier) {
        if (event.getModifiers().containsKey(lightning)) {
            return;
        }
        event.addModifier(lightning, modifier);
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
        lightning = BuiltInRegistries.ATTRIBUTE.getOptional(SpellSchools.LIGHTNING.id).orElse(null);
        robePieces = pieces;
    }
}
