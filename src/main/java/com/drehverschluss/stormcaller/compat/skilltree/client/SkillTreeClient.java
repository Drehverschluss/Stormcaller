package com.drehverschluss.stormcaller.compat.skilltree.client;

import com.drehverschluss.stormcaller.Stormcaller;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
import net.neoforged.fml.ModList;
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

    /** Spells of the nodes; the definition id is {@code stormcaller_<spell>} (definitions.json). */
    private static final List<String> SPELL_NODES = List.of(
            // passive 1, tier 2 (phase 5A)
            "forked_spark", "galvanic_rush",
            "improved_chain_lightning", "long_arc", "conductive_chain",
            "improved_lightning_nova", "thunderclap", "quick_discharge",
            // passive 2, tier 3, passive 3, tier 4 (phase 5B)
            "static_trap", "charged_roll",
            "improved_static_shield", "lasting_charge", "arc_retort",
            "improved_lightning_strike", "double_strike", "charged_ground",
            "eye_of_the_storm", "storm_ward",
            "improved_thunderstorm", "static_rain", "rolling_thunder",
            "improved_storm_elemental", "twin_storms", "chain_conductor");
    /** Passives with the ROLL trigger: they only work with Combat Roll. */
    private static final Set<String> ROLL_NODES = Set.of("static_trap", "charged_roll");
    private static final String COMBAT_ROLL = "combat_roll";

    private SkillTreeClient() {}

    public static void init() {
        String prefix = Stormcaller.MODID + "_";
        TranslationUtil.resolvers.put(prefix + "root", () -> root());
        TranslationUtil.resolvers.put(prefix + "boost", () -> attributeLines());
        boolean combatRoll = ModList.get().isLoaded(COMBAT_ROLL);
        for (String spell : SPELL_NODES) {
            ResourceLocation spellId = Stormcaller.id(spell);
            if (ROLL_NODES.contains(spell) && !combatRoll) {
                TranslationUtil.resolvers.put(prefix + spell, () -> {
                    List<Component> lines = new ArrayList<>(TranslationUtil.resolveSpellDetails(spellId));
                    lines.add(Component.translatable("skill." + Stormcaller.MODID + ".requires_combat_roll")
                            .withStyle(ChatFormatting.RED));
                    return lines;
                });
            } else {
                TranslationUtil.resolvers.put(prefix + spell, () -> TranslationUtil.resolveSpellDetails(spellId));
            }
        }
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
