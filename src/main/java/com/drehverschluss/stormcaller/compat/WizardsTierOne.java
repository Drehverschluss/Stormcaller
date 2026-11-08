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
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.spell_power.api.SpellSchools;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Gives the tier 1 equipment of Wizards (Wizard Robe, Wizard Staff) Lightning Spell Power.
 * <p>
 * Uses {@code addModifier} with the modifier IDs of Spell Engine ({@code minecraft:armor.<slot>} for armor,
 * {@code minecraft:equipment_bonus} for weapons). If a pack already adds {@code spell_power:lightning} through
 * {@code config/wizards/equipment_v2.json}, that entry has the same ID, {@code addModifier} returns false and the
 * pack value wins.
 */
public final class WizardsTierOne {
    private static final String WIZARDS = "wizards";
    private static final ResourceLocation STAFF_ID = ResourceLocation.fromNamespaceAndPath(WIZARDS, "staff_wizard");
    private static final ResourceLocation EQUIPMENT_BONUS_ID = ResourceLocation.withDefaultNamespace("equipment_bonus");

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
                event.addModifier(lightning, new AttributeModifier(id, value, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
                        EquipmentSlotGroup.bySlot(type.getSlot()));
            }
        } else if (item == staff && staff != null) {
            double value = StormcallerConfig.get(StormcallerConfig.WIZARD_STAFF_LIGHTNING);
            if (value != 0.0) {
                event.addModifier(lightning, new AttributeModifier(EQUIPMENT_BONUS_ID, value, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND);
            }
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
