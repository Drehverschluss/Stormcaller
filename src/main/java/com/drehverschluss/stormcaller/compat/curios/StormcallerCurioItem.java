package com.drehverschluss.stormcaller.compat.curios;

import com.drehverschluss.stormcaller.trinket.StormcallerTrinkets;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.internals.casting.SpellCaster;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;

/**
 * A Stormcaller ring, amulet or relic in a Curios slot.
 * <ul>
 *   <li>Attributes come from {@code trinkets.json} and are only given through {@link #getAttributeModifiers} (no vanilla
 *   attribute component, so they never work from the hand). The modifier id contains the slot id, so the same ring in
 *   two ring slots counts twice.</li>
 *   <li>Relics carry their spell in the {@code spell_engine:spell_container} component (set in {@link CuriosItems}) and
 *   cannot be taken off while that spell (or the item) is on cooldown, like the relics of Relics (RPG Series).</li>
 * </ul>
 */
public class StormcallerCurioItem extends Item implements ICurioItem {
    private final StormcallerTrinkets.Def def;

    public StormcallerCurioItem(Properties properties, StormcallerTrinkets.Def def) {
        super(properties);
        this.def = def;
    }

    public StormcallerTrinkets.Def def() {
        return def;
    }

    @Override
    public Multimap<Holder<Attribute>, AttributeModifier> getAttributeModifiers(SlotContext slotContext, ResourceLocation id, ItemStack stack) {
        Multimap<Holder<Attribute>, AttributeModifier> modifiers = LinkedHashMultimap.create();
        modifiers.putAll(ICurioItem.super.getAttributeModifiers(slotContext, id, stack));
        String itemPath = BuiltInRegistries.ITEM.getKey(this).getPath();
        for (net.spell_engine.rpg_series.config.AttributeModifier entry : StormcallerTrinkets.attributes(def)) {
            ResourceLocation attributeId = ResourceLocation.tryParse(entry.attribute);
            if (attributeId == null) {
                continue;
            }
            BuiltInRegistries.ATTRIBUTE.getHolder(attributeId).ifPresent(attribute -> modifiers.put(attribute,
                    new AttributeModifier(id.withSuffix("/" + itemPath + "/" + attributeId.getPath()), entry.value, entry.operation)));
        }
        return modifiers;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        if (def.kind() == StormcallerTrinkets.Kind.JEWELRY) {
            tooltip.add(Component.translatable(Util.makeDescriptionId("item", def.id()) + ".lore")
                    .withStyle(ChatFormatting.ITALIC, ChatFormatting.GOLD));
        }
    }

    @Override
    public ICurio.SoundInfo getEquipSound(SlotContext slotContext, ItemStack stack) {
        return new ICurio.SoundInfo(equipSound(), 1.0F, 1.0F);
    }

    @Override
    public void onEquip(SlotContext slotContext, ItemStack prevStack, ItemStack stack) {
        LivingEntity entity = slotContext.entity();
        if (entity == null) {
            return;
        }
        Level level = entity.level();
        // like Jewelry and Relics: not while the player logs in (Curios equips everything again)
        if (!level.isClientSide() && entity.tickCount > 100 && !prevStack.is(stack.getItem())) {
            level.playSound(null, entity.blockPosition(), equipSound(), entity.getSoundSource(), 1.0F, 1.0F);
        }
    }

    @Override
    public boolean canUnequip(SlotContext slotContext, ItemStack stack) {
        if (def.kind() == StormcallerTrinkets.Kind.RELIC && slotContext.entity() instanceof Player player
                && !player.isCreative() && isOnCooldown(player, stack)) {
            return false;
        }
        return ICurioItem.super.canUnequip(slotContext, stack);
    }

    /** True while the item or its spell is on cooldown. */
    public boolean isOnCooldown(Player player, ItemStack stack) {
        if (player.getCooldowns().isOnCooldown(stack.getItem())) {
            return true;
        }
        if (def.spell() == null || !(player instanceof SpellCaster.Player caster)) {
            return false;
        }
        return SpellRegistry.from(player.level()).getHolder(def.spell())
                .map(spell -> caster.getCooldownManager().isCoolingDown(spell))
                .orElse(false);
    }

    private SoundEvent equipSound() {
        return def.kind() == StormcallerTrinkets.Kind.JEWELRY
                ? SoundEvents.ARMOR_EQUIP_GOLD.value()
                : SoundEvents.ARMOR_EQUIP_GENERIC.value();
    }
}
