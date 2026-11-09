package com.drehverschluss.stormcaller.compat.skilltree.client;

import com.drehverschluss.stormcaller.Stormcaller;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.skill_tree_rpgs.utils.TranslationUtil;
import net.spell_engine.mixin.client.ItemStackTooltipAccessor;

/**
 * Tooltips of the Stormcaller skill nodes. Each definition has the description {@code {"skill_definition_id": id}},
 * Skill Tree resolves it through {@code TranslationUtil.resolvers} on the client:
 * <ul>
 *     <li>passives and spell nodes: description and details of their spell (tokens like {@code {damage}} are filled
 *     in by Spell Engine, the shock line comes from {@code ShockTooltips}),</li>
 *     <li>root and boosts: the attribute bonus; the root also shows the class lock.</li>
 * </ul>
 * Only loaded when Skill Tree is there.
 */
public final class SkillTreeClient {
    /** Bonus of the root and every boost, same as in definitions.json ({@code gen_phase5a_data.py}). */
    private static final double BOOST_VALUE = 0.01;
    private static final ResourceLocation LIGHTNING_POWER = ResourceLocation.fromNamespaceAndPath("spell_power", "lightning");

    /** definition id -> spell id of the node (same names in definitions.json) */
    private static final Map<String, String> SPELL_NODES = Map.of(
            "forked_spark", "forked_spark",
            "galvanic_rush", "galvanic_rush",
            "improved_chain_lightning", "improved_chain_lightning",
            "long_arc", "long_arc",
            "conductive_chain", "conductive_chain",
            "improved_lightning_nova", "improved_lightning_nova",
            "thunderclap", "thunderclap",
            "quick_discharge", "quick_discharge");

    private SkillTreeClient() {}

    public static void init() {
        String prefix = Stormcaller.MODID + "_";
        TranslationUtil.resolvers.put(prefix + "root", () -> root());
        TranslationUtil.resolvers.put(prefix + "boost", () -> attributeLines());
        SPELL_NODES.forEach((definition, spell) -> {
            ResourceLocation spellId = Stormcaller.id(spell);
            TranslationUtil.resolvers.put(prefix + definition, () -> TranslationUtil.resolveSpellDetails(spellId));
        });
    }

    private static List<Component> root() {
        List<Component> lines = new ArrayList<>(attributeLines());
        if (ClassLockClient.enabled()) {
            lines.add(Component.translatable("skill." + Stormcaller.MODID + ".class_lock.info").withStyle(ChatFormatting.GRAY));
            if (ClassLockClient.stormcallerBlocked()) {
                lines.add(Component.translatable("skill." + Stormcaller.MODID + ".class_lock.blocked").withStyle(ChatFormatting.RED));
            }
        }
        return lines;
    }

    /** "+1% Lightning Spell Power", in the same form as on items. */
    private static List<Component> attributeLines() {
        LocalPlayer player = Minecraft.getInstance().player;
        Holder<Attribute> attribute = BuiltInRegistries.ATTRIBUTE.getHolder(LIGHTNING_POWER).map(h -> (Holder<Attribute>) h).orElse(null);
        if (player == null || attribute == null) {
            return List.of();
        }
        List<Component> lines = new ArrayList<>();
        AttributeModifier modifier = new AttributeModifier(Stormcaller.id("skill_boost"), BOOST_VALUE,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        ((ItemStackTooltipAccessor) (Object) ItemStack.EMPTY)
                .spellEngine_appendAttributeModifierTooltip(lines::add, player, attribute, modifier);
        return lines;
    }
}
