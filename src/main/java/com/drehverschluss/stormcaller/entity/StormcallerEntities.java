package com.drehverschluss.stormcaller.entity;

import com.drehverschluss.stormcaller.Stormcaller;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.spell_engine.api.spell.summon.SummonedEntities;
import net.spell_engine.api.spell.summon.SummonedEntityConfig;
import net.spell_power.api.SpellSchools;
import net.tiny_config.ConfigManager;

/**
 * Entity types of Stormcaller, registered the way Wizards registers its summons.
 * <ul>
 *     <li>{@code stormcaller:storm_cloud}: the cloud of Thunderstorm ({@link StormCloudEntity}). Spells use it with
 *     {@code "entity_type_id": "stormcaller:storm_cloud"} in a CLOUD delivery.</li>
 *     <li>{@code stormcaller:static_field}: field on the ground of Static Trap and Charged Ground (skill tree,
 *     {@link StaticFieldEntity}), also a CLOUD delivery.</li>
 *     <li>{@code stormcaller:ball_lightning}: the ball of the Caged Ball Lightning relic and of the Stormbringer Staff
 *     ({@link BallLightningEntity}), created by code (custom deliveries in {@code trinket.BallLightning}).</li>
 *     <li>{@code stormcaller:storm_elemental}: the summon of Storm Elemental ({@link StormElementalEntity}). Its base
 *     attributes come from {@code config/stormcaller/summoned_entities.json} (same format as the file of Wizards).</li>
 * </ul>
 */
public final class StormcallerEntities {
    public static final ResourceLocation STORM_CLOUD_ID = Stormcaller.id("storm_cloud");
    public static final ResourceLocation STORM_ELEMENTAL_ID = Stormcaller.id("storm_elemental");
    public static final ResourceLocation STATIC_FIELD_ID = Stormcaller.id("static_field");
    public static final ResourceLocation BALL_LIGHTNING_ID = Stormcaller.id("ball_lightning");

    public static final EntityType<StormCloudEntity> STORM_CLOUD = EntityType.Builder
            .<StormCloudEntity>of(StormCloudEntity::new, MobCategory.MISC)
            .sized(6.0F, 0.5F)
            .fireImmune()
            .clientTrackingRange(128)
            .updateInterval(20)
            .build("storm_cloud");

    public static final EntityType<StaticFieldEntity> STATIC_FIELD = EntityType.Builder
            .<StaticFieldEntity>of(StaticFieldEntity::new, MobCategory.MISC)
            .sized(5.0F, 0.5F)
            .fireImmune()
            .clientTrackingRange(64)
            .updateInterval(20)
            .build("static_field");

    public static final EntityType<BallLightningEntity> BALL_LIGHTNING = EntityType.Builder
            .<BallLightningEntity>of(BallLightningEntity::new, MobCategory.MISC)
            .sized(0.8F, 0.8F)
            .fireImmune()
            .noSave()
            .clientTrackingRange(8)
            .updateInterval(4)
            .build("ball_lightning");

    public static final EntityType<StormElementalEntity> STORM_ELEMENTAL = EntityType.Builder
            .<StormElementalEntity>of(StormElementalEntity::new, MobCategory.MISC)
            .sized(0.8F, 1.9F)
            .clientTrackingRange(64)
            .updateInterval(3)
            .build("storm_elemental");

    /** {@code config/stormcaller/summoned_entities.json}: base attributes of the summons (like Wizards). */
    public static final ConfigManager<SummonedEntityConfig> SUMMON_CONFIG = new ConfigManager<>("summoned_entities", defaults())
            .builder()
            .setDirectory(Stormcaller.MODID)
            .schemaVersion(1)
            .sanitize(true)
            .build();

    private StormcallerEntities() {}

    /** Same base values as the Frost Elemental of Wizards, with Lightning Spell Power. */
    private static SummonedEntityConfig defaults() {
        SummonedEntityConfig config = new SummonedEntityConfig();
        SummonedEntityConfig.Entry entry = new SummonedEntityConfig.Entry();
        entry.common = new SummonedEntityConfig.CommonAttributes(30.0, 0.25, 4.0);
        entry.common.follow_range = 32.0;
        entry.custom.add(new SummonedEntityConfig.CustomAttribute(SpellSchools.LIGHTNING.id.toString(), 1.0));
        config.entries.put(STORM_ELEMENTAL_ID.toString(), entry);
        return config;
    }

    /** Mod bus. */
    public static void onRegister(RegisterEvent event) {
        if (!event.getRegistryKey().equals(Registries.ENTITY_TYPE)) {
            return;
        }
        SUMMON_CONFIG.refresh();
        Registry.register(BuiltInRegistries.ENTITY_TYPE, STORM_CLOUD_ID, STORM_CLOUD);
        Registry.register(BuiltInRegistries.ENTITY_TYPE, STATIC_FIELD_ID, STATIC_FIELD);
        Registry.register(BuiltInRegistries.ENTITY_TYPE, STORM_ELEMENTAL_ID, STORM_ELEMENTAL);
        Registry.register(BuiltInRegistries.ENTITY_TYPE, BALL_LIGHTNING_ID, BALL_LIGHTNING);
        // a pack file without our entry falls back to the defaults instead of failing at startup
        SummonedEntities.registerAttributes(STORM_ELEMENTAL_ID, STORM_ELEMENTAL, id -> {
            SummonedEntityConfig.Entry entry = SUMMON_CONFIG.value.entryFor(id);
            return entry != null ? entry : defaults().entryFor(id);
        });
        SUMMON_CONFIG.save();
    }
}
