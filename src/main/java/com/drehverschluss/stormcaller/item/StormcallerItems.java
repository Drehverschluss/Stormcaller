package com.drehverschluss.stormcaller.item;

import com.drehverschluss.stormcaller.Stormcaller;
import com.drehverschluss.stormcaller.attribute.StormcallerAttributes;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.spell_engine.api.spell.container.SpellContainers;
import net.spell_engine.rpg_series.config.ArmorSetConfig;
import net.spell_engine.rpg_series.config.AttributeModifier;
import net.spell_engine.rpg_series.config.ConfigFile;
import net.spell_engine.rpg_series.item.Armor;
import net.spell_engine.rpg_series.item.Equipment;
import net.spell_engine.rpg_series.item.Weapon;
import net.spell_engine.rpg_series.item.Weapons;
import net.spell_power.api.SpellSchools;
import net.tiny_config.ConfigManager;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Lightning robes, wands and staves (tier 2 and 3), registered through the RPG Series helpers of Spell Engine,
 * the same way Wizards registers its school equipment.
 */
public final class StormcallerItems {
    public static final ResourceLocation TAB_ID = Stormcaller.id("general");
    public static final ResourceKey<CreativeModeTab> TAB_KEY = ResourceKey.create(Registries.CREATIVE_MODE_TAB, TAB_ID);

    /** {@code config/stormcaller/equipment.json}, same format as the equipment configs of Wizards. */
    public static final ConfigManager<ConfigFile.Equipment> EQUIPMENT_CONFIG = new ConfigManager<>("equipment", new ConfigFile.Equipment())
            .builder()
            .setDirectory(Stormcaller.MODID)
            .sanitize(true)
            .build();

    // Spell IDs of the weapon containers. The spells themselves follow in phase 3.
    public static final ResourceLocation SPARK = Stormcaller.id("spark");
    public static final ResourceLocation LIGHTNING_BOLT = Stormcaller.id("lightning_bolt");

    // Values per robe piece (defaults of equipment.json)
    private static final float LIGHTNING_T2 = 0.25F;
    private static final float LIGHTNING_T3 = 0.3F;
    private static final float SHOCK_CHANCE_T2 = 0.02F;
    private static final float SHOCK_CHANCE_T3 = 0.03F;

    // Armor values like the Frost Robe (tier 2) and Netherite Frost Robe (tier 3) of Wizards
    private static final int ARMOR_HEAD = 1;
    private static final int ARMOR_CHEST = 3;
    private static final int ARMOR_LEGS = 2;
    private static final int ARMOR_FEET = 1;
    private static final int DURABILITY_T2 = 20;
    private static final int DURABILITY_T3 = 30;
    private static final int ENCHANTABILITY_T2 = 10;
    private static final int ENCHANTABILITY_T3 = 15;

    private static final ResourceLocation ROBE_EQUIP_SOUND = ResourceLocation.fromNamespaceAndPath("wizards", "wizard_robes_equip");

    public static Armor.Set<?> lightningRobe;
    public static Armor.Set<?> netheriteLightningRobe;
    public static Weapon.Entry wandLightning;
    public static Weapon.Entry staffLightning;
    public static Weapon.Entry wandNetheriteLightning;
    public static Weapon.Entry staffNetheriteLightning;

    private StormcallerItems() {}

    /** Loads equipment.json. Called from the mod constructor, before registration. */
    public static void init() {
        EQUIPMENT_CONFIG.refresh();
    }

    public static void onRegister(RegisterEvent event) {
        event.register(Registries.CREATIVE_MODE_TAB, helper -> helper.register(TAB_ID, CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.stormcaller.general"))
                .icon(() -> new ItemStack(staffLightning != null && staffLightning.item() != null ? staffLightning.item() : Items.LIGHTNING_ROD))
                .build()));
        event.register(Registries.ITEM, helper -> registerItems());
    }

    @SuppressWarnings({"unchecked", "rawtypes"}) // Armor.Set.ItemFactory is generic in Spell Engine
    private static void registerItems() {
        ConfigFile.Equipment config = EQUIPMENT_CONFIG.value;

        // Weapons first: the creative tab lists them in this order (wands, staves), then the robes.
        Supplier<Ingredient> copper = () -> Ingredient.of(Items.COPPER_INGOT);
        Supplier<Ingredient> netherite = () -> Ingredient.of(Items.NETHERITE_INGOT);
        List<ResourceLocation> lightning = List.of(SpellSchools.LIGHTNING.id);
        wandLightning = Weapons.damageWand(Stormcaller.MODID, "wand_lightning", Equipment.Tier.TIER_2, copper, lightning)
                .spellContainer(SpellContainers.forMagicWeapon().withSpellId(SPARK));
        wandNetheriteLightning = Weapons.damageWand(Stormcaller.MODID, "wand_netherite_lightning", Equipment.Tier.TIER_3, netherite, lightning)
                .spellContainer(SpellContainers.forMagicWeapon().withSpellId(SPARK));
        staffLightning = Weapons.damageStaff(Stormcaller.MODID, "staff_lightning", Equipment.Tier.TIER_2, copper, lightning)
                .spellContainer(SpellContainers.forMagicWeapon().withSpellId(LIGHTNING_BOLT));
        staffNetheriteLightning = Weapons.damageStaff(Stormcaller.MODID, "staff_netherite_lightning", Equipment.Tier.TIER_3, netherite, lightning)
                .spellContainer(SpellContainers.forMagicWeapon().withSpellId(LIGHTNING_BOLT));
        Weapon.register(config.weapons, List.of(wandLightning, wandNetheriteLightning, staffLightning, staffNetheriteLightning), TAB_KEY);

        Holder<SoundEvent> equipSound = BuiltInRegistries.SOUND_EVENT.getHolder(ROBE_EQUIP_SOUND)
                .<Holder<SoundEvent>>map(h -> h)
                .orElse(SoundEvents.ARMOR_EQUIP_LEATHER);
        Holder<ArmorMaterial> materialT2 = material("lightning_robe", ENCHANTABILITY_T2, equipSound, copper);
        Holder<ArmorMaterial> materialT3 = material("netherite_lightning_robe", ENCHANTABILITY_T3, equipSound, netherite);

        Armor.Entry robeT2 = Armor.Entry.create(materialT2, Stormcaller.id("lightning_robe"), DURABILITY_T2, Armor.CustomItem::new,
                robeDefaults(LIGHTNING_T2, SHOCK_CHANCE_T2), Equipment.LootProperties.of(2));
        Armor.Entry robeT3 = Armor.Entry.create(materialT3, Stormcaller.id("netherite_lightning_robe"), DURABILITY_T3, Armor.CustomItem::new,
                robeDefaults(LIGHTNING_T3, SHOCK_CHANCE_T3), Equipment.LootProperties.of(3));
        Armor.register(config.armor_sets, new ArrayList<>(List.of(robeT2, robeT3)), TAB_KEY);
        lightningRobe = robeT2.armorSet();
        netheriteLightningRobe = robeT3.armorSet();

        EQUIPMENT_CONFIG.save();
    }

    private static ArmorSetConfig robeDefaults(float lightning, float shockChance) {
        return ArmorSetConfig.with(
                piece(ARMOR_HEAD, lightning, shockChance),
                piece(ARMOR_CHEST, lightning, shockChance),
                piece(ARMOR_LEGS, lightning, shockChance),
                piece(ARMOR_FEET, lightning, shockChance));
    }

    private static ArmorSetConfig.Piece piece(int armor, float lightning, float shockChance) {
        return new ArmorSetConfig.Piece(armor).addAll(List.of(
                AttributeModifier.multiply(SpellSchools.LIGHTNING.id, lightning),
                AttributeModifier.multiply(StormcallerAttributes.SHOCK_CHANCE.getId(), shockChance)));
    }

    private static Holder<ArmorMaterial> material(String name, int enchantability, Holder<SoundEvent> equipSound, Supplier<Ingredient> repair) {
        Map<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
        defense.put(ArmorItem.Type.HELMET, ARMOR_HEAD);
        defense.put(ArmorItem.Type.CHESTPLATE, ARMOR_CHEST);
        defense.put(ArmorItem.Type.LEGGINGS, ARMOR_LEGS);
        defense.put(ArmorItem.Type.BOOTS, ARMOR_FEET);
        ArmorMaterial material = new ArmorMaterial(defense, enchantability, equipSound, repair,
                List.of(new ArmorMaterial.Layer(Stormcaller.id(name))), 0.0F, 0.0F);
        return Registry.registerForHolder(BuiltInRegistries.ARMOR_MATERIAL, Stormcaller.id(name), material);
    }
}
