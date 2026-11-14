package com.drehverschluss.stormcaller.item;

import com.drehverschluss.stormcaller.Stormcaller;
import com.drehverschluss.stormcaller.attribute.StormcallerAttributes;
import com.drehverschluss.stormcaller.trinket.StormcallerTrinkets;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SmithingTemplateItem;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.spell_engine.PlatformEvents;
import net.spell_engine.api.spell.SpellDataComponents;
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
 * Lightning robes, wands and staves (tier 2 to 4), registered through the RPG Series helpers of Spell Engine,
 * the same way Wizards registers its school equipment.
 * <p>
 * Tier 4 robes (like Armory): two sets with set bonuses (Fulgurite, Stormweave; {@code data/stormcaller/equipment_set/*.json},
 * linked through the data component {@code spell_engine:equipment_set}), an own smithing template and two upgrade crystals.
 * <p>
 * Tier 4 weapons (like Wizards and Arsenal, phase 6A.1): the Amber Lightning Staff (only with Better End, like the tier 4
 * staves of Wizards; no tier 4 wand) and the Stormbringer Staff, a unique staff with a passive that only drops as loot.
 */
public final class StormcallerItems {
    public static final ResourceLocation TAB_ID = Stormcaller.id("general");
    public static final ResourceKey<CreativeModeTab> TAB_KEY = ResourceKey.create(Registries.CREATIVE_MODE_TAB, TAB_ID);

    /** {@code config/stormcaller/tweaks.json}, read once at startup (before the items are registered). */
    public static final ConfigManager<Tweaks> TWEAKS_CONFIG = new ConfigManager<>("tweaks", new Tweaks())
            .builder()
            .setDirectory(Stormcaller.MODID)
            .sanitize(true)
            .build();

    /** Startup switches, like {@code config/wizards/tweaks.json}. */
    public static class Tweaks {
        /** Written into the file, explains the option (JSON has no comments). */
        public String _comment = "ignore_items_required_mods: true registers items that need another mod even without that"
                + " mod (Amber Lightning Staff: Better End). Default false. Server and client must use the same value."
                + " Needs a restart.";
        /** Registers items that need another mod (Amber Lightning Staff: Better End) even without that mod. */
        public boolean ignore_items_required_mods = false;
    }

    /** {@code config/stormcaller/equipment.json}, same format as the equipment configs of Wizards. */
    public static final ConfigManager<ConfigFile.Equipment> EQUIPMENT_CONFIG = new ConfigManager<>("equipment", new ConfigFile.Equipment())
            .builder()
            .setDirectory(Stormcaller.MODID)
            .sanitize(true)
            .build();

    // Spell IDs of the weapon containers. The spells themselves follow in phase 3.
    public static final ResourceLocation SPARK = Stormcaller.id("spark");
    public static final ResourceLocation LIGHTNING_BOLT = Stormcaller.id("lightning_bolt");
    // Spells of the Stormbringer Staff (besides Lightning Bolt): active Ball Lightning (phase 10), passive Skyfall
    public static final ResourceLocation BALL_LIGHTNING_STAFF = Stormcaller.id("ball_lightning_staff");
    public static final ResourceLocation SKYFALL = Stormcaller.id("skyfall");
    // Passive of the Amber Lightning Staff (phase 10)
    public static final ResourceLocation STORED_CHARGE = Stormcaller.id("stored_charge");
    /** Lightning Spell Power of the Stormbringer Staff (default of equipment.json; tier 5 gives +8). */
    private static final float STORMBRINGER_LIGHTNING = 10.0F;

    // Tier 4 sets (equipment_set ids) and smithing items
    public static final ResourceLocation FULGURITE_SET = Stormcaller.id("fulgurite");
    public static final ResourceLocation STORMWEAVE_SET = Stormcaller.id("stormweave");
    public static final ResourceLocation TEMPLATE_ID = Stormcaller.id("stormbringer_upgrade_smithing_template");
    public static final ResourceLocation LOST_CRYSTAL_ID = Stormcaller.id("stormbringers_lost_crystal");
    public static final ResourceLocation FORGOTTEN_CRYSTAL_ID = Stormcaller.id("stormbringers_forgotten_crystal");

    // Values per robe piece (defaults of equipment.json)
    private static final float LIGHTNING_T2 = 0.25F;
    private static final float LIGHTNING_T3 = 0.3F;
    private static final float SHOCK_CHANCE_T2 = 0.02F;
    private static final float SHOCK_CHANCE_T3 = 0.03F;
    private static final float LIGHTNING_T4 = 0.35F;      // like the Armory robes
    private static final float SHOCK_CHANCE_T4 = 0.04F;
    private static final String BETTER_END = "betterend";

    // Armor values like the Frost Robe (tier 2) and Netherite Frost Robe (tier 3) of Wizards
    private static final int ARMOR_HEAD = 1;
    private static final int ARMOR_CHEST = 3;
    private static final int ARMOR_LEGS = 2;
    private static final int ARMOR_FEET = 1;
    private static final int DURABILITY_T2 = 20;
    private static final int DURABILITY_T3 = 30;
    private static final int ENCHANTABILITY_T2 = 10;
    private static final int ENCHANTABILITY_T3 = 15;
    private static final int DURABILITY_T4 = 40;          // like the Armory robes
    private static final int ENCHANTABILITY_T4 = 18;

    private static final ResourceLocation ROBE_EQUIP_SOUND = ResourceLocation.fromNamespaceAndPath("wizards", "wizard_robes_equip");

    public static Armor.Set<?> lightningRobe;
    public static Armor.Set<?> netheriteLightningRobe;
    public static Weapon.Entry wandLightning;
    public static Weapon.Entry staffLightning;
    public static Weapon.Entry wandNetheriteLightning;
    public static Weapon.Entry staffNetheriteLightning;
    public static Armor.Set<?> fulguriteRobe;
    public static Armor.Set<?> stormweaveRobe;
    /** Null without Better End (unless {@link Tweaks#ignore_items_required_mods}). */
    public static Weapon.Entry staffAmberLightning;
    public static Weapon.Entry staffStormbringer;
    public static Item stormbringerTemplate;
    public static Item lostCrystal;
    public static Item forgottenCrystal;

    private StormcallerItems() {}

    /** Loads tweaks.json and equipment.json. Called from the mod constructor, before registration. */
    public static void init() {
        TWEAKS_CONFIG.refresh();
        TWEAKS_CONFIG.save();
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

        // Weapons first: the creative tab lists them in this order (wands, staves), then the robes, then the tier 4
        // smithing items.
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
        List<Weapon.Entry> weapons = new ArrayList<>(List.of(wandLightning, wandNetheriteLightning, staffLightning, staffNetheriteLightning));
        // Tier 4 like staff_crystal_arcane of Wizards: only with Better End, +8, same spell as the other staves; repaired
        // with amber (netherite without Better End). Crafted from Better End items (recipe condition). Since phase 10 with
        // the passive Stored Charge (spell.StoredCharge).
        boolean betterEnd = ModList.get().isLoaded(BETTER_END);
        if (betterEnd || TWEAKS_CONFIG.value.ignore_items_required_mods) {
            Supplier<Ingredient> amber = betterEnd
                    ? () -> Ingredient.of(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(BETTER_END, "amber_gem")))
                    : netherite;
            staffAmberLightning = Weapons.damageStaff(Stormcaller.MODID, "staff_amber_lightning", Equipment.Tier.TIER_4, amber, lightning)
                    .spellContainer(SpellContainers.forMagicWeapon().withSpellId(LIGHTNING_BOLT))
                    .withAdditionalSpell(STORED_CHARGE.toString());
            weapons.add(staffAmberLightning);
        }
        // Unique staff like the unique staves of Arsenal: tier 5 (loot tier 5 via tag), rare, no recipe. Since phase 10 +10
        // instead of +8 and a second active spell: Lightning Bolt, Ball Lightning (in the spell hotbar), passive Skyfall.
        staffStormbringer = Weapons.damageStaff(Stormcaller.MODID, "staff_stormbringer", Equipment.Tier.TIER_5, netherite, lightning)
                .spellContainer(SpellContainers.forMagicWeapon().withSpellId(LIGHTNING_BOLT))
                .withAdditionalSpell(BALL_LIGHTNING_STAFF.toString())
                .withAdditionalSpell(SKYFALL.toString());
        // only the default: an equipment.json that already has the staff keeps its value (Spell Engine reads the file first)
        for (AttributeModifier attribute : staffStormbringer.defaults().attributes) {
            if (SpellSchools.LIGHTNING.id.toString().equals(attribute.attribute)) {
                attribute.value = STORMBRINGER_LIGHTNING;
            }
        }
        staffStormbringer.rarity = Rarity.RARE;
        weapons.add(staffStormbringer);
        Weapon.register(config.weapons, weapons, TAB_KEY);

        Holder<SoundEvent> equipSound = BuiltInRegistries.SOUND_EVENT.getHolder(ROBE_EQUIP_SOUND)
                .<Holder<SoundEvent>>map(h -> h)
                .orElse(SoundEvents.ARMOR_EQUIP_LEATHER);
        Holder<ArmorMaterial> materialT2 = material("lightning_robe", ENCHANTABILITY_T2, equipSound, copper);
        Holder<ArmorMaterial> materialT3 = material("netherite_lightning_robe", ENCHANTABILITY_T3, equipSound, netherite);

        Armor.Entry robeT2 = Armor.Entry.create(materialT2, Stormcaller.id("lightning_robe"), DURABILITY_T2, Armor.CustomItem::new,
                robeDefaults(LIGHTNING_T2, SHOCK_CHANCE_T2), Equipment.LootProperties.of(2));
        Armor.Entry robeT3 = Armor.Entry.create(materialT3, Stormcaller.id("netherite_lightning_robe"), DURABILITY_T3, Armor.CustomItem::new,
                robeDefaults(LIGHTNING_T3, SHOCK_CHANCE_T3), Equipment.LootProperties.of(3));
        // Tier 4 like the Armory robes: rare, loot tier 5 (only makes them fire resistant; the robes are in no loot
        // tag, only template and crystals are), set through the equipment_set component
        Holder<ArmorMaterial> materialFulgurite = material("fulgurite_robe", ENCHANTABILITY_T4, equipSound, netherite);
        Holder<ArmorMaterial> materialStormweave = material("stormweave_robe", ENCHANTABILITY_T4, equipSound, netherite);
        Armor.Entry robeFulgurite = Armor.Entry.create(materialFulgurite, Stormcaller.id("fulgurite_robe"), DURABILITY_T4, Armor.CustomItem::new,
                robeDefaults(LIGHTNING_T4, SHOCK_CHANCE_T4), Equipment.LootProperties.of(5), setSettings(FULGURITE_SET));
        Armor.Entry robeStormweave = Armor.Entry.create(materialStormweave, Stormcaller.id("stormweave_robe"), DURABILITY_T4, Armor.CustomItem::new,
                robeDefaults(LIGHTNING_T4, SHOCK_CHANCE_T4), Equipment.LootProperties.of(5), setSettings(STORMWEAVE_SET));

        Armor.register(config.armor_sets, new ArrayList<>(List.of(robeT2, robeT3, robeFulgurite, robeStormweave)), TAB_KEY);
        lightningRobe = robeT2.armorSet();
        netheriteLightningRobe = robeT3.armorSet();
        fulguriteRobe = robeFulgurite.armorSet();
        stormweaveRobe = robeStormweave.armorSet();

        registerSmithingItems();
        // rings, amulets and relics, only with Curios (phase 6B)
        StormcallerTrinkets.registerItems();

        EQUIPMENT_CONFIG.save();
    }

    private static Armor.ItemSettingsTweaker setSettings(ResourceLocation equipmentSet) {
        return Armor.ItemSettingsTweaker.standard(settings -> settings
                .component(SpellDataComponents.EQUIPMENT_SET, equipmentSet)
                .component(DataComponents.RARITY, Rarity.RARE));
    }

    /** Smithing template and the two Stormbringer crystals for the tier 4 robes (like Armory: crystals epic and fire resistant). */
    private static void registerSmithingItems() {
        stormbringerTemplate = Registry.register(BuiltInRegistries.ITEM, TEMPLATE_ID, new SmithingTemplateItem(
                templateText("applies_to", ChatFormatting.BLUE),
                templateText("ingredients", ChatFormatting.BLUE),
                Component.translatable(Util.makeDescriptionId("upgrade", Stormcaller.id("stormbringer_upgrade"))).withStyle(ChatFormatting.GRAY),
                templateText("base_slot_description", null),
                templateText("additions_slot_description", null),
                List.of(
                        ResourceLocation.withDefaultNamespace("item/empty_armor_slot_helmet"),
                        ResourceLocation.withDefaultNamespace("item/empty_armor_slot_chestplate"),
                        ResourceLocation.withDefaultNamespace("item/empty_armor_slot_leggings"),
                        ResourceLocation.withDefaultNamespace("item/empty_armor_slot_boots")),
                List.of(ResourceLocation.withDefaultNamespace("item/empty_slot_amethyst_shard"))));
        lostCrystal = Registry.register(BuiltInRegistries.ITEM, LOST_CRYSTAL_ID,
                new UpgradeCrystalItem(new Item.Properties().rarity(Rarity.EPIC).fireResistant(), "lost"));
        forgottenCrystal = Registry.register(BuiltInRegistries.ITEM, FORGOTTEN_CRYSTAL_ID,
                new UpgradeCrystalItem(new Item.Properties().rarity(Rarity.EPIC).fireResistant(), "forgotten"));
        // after the robes in the creative tab
        PlatformEvents.onItemGroupModify(TAB_KEY, (content, context) -> {
            content.accept(stormbringerTemplate);
            content.accept(lostCrystal);
            content.accept(forgottenCrystal);
        });
    }

    /** {@code item.stormcaller.smithing_template.stormbringer_upgrade.<part>}, like the Vanilla netherite template. */
    private static Component templateText(String part, ChatFormatting style) {
        Component text = Component.translatable(Util.makeDescriptionId("item", Stormcaller.id("smithing_template.stormbringer_upgrade." + part)));
        return style == null ? text : text.copy().withStyle(style);
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
