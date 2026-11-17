package com.drehverschluss.stormcaller.compat;

import com.drehverschluss.stormcaller.config.StormcallerConfig;
import com.drehverschluss.stormcaller.item.StormcallerItems;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.common.BasicItemListing;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraftforge.event.village.VillagerTradesEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Lightning offers of the Wizard Merchant of Wizards ({@code wizards:wizard_merchant}), made like the Frost offers:
 * Lightning Rune on level 1, Lightning Wand on level 2, an enchanted Lightning Staff on level 5. They are appended to the
 * pools like Elemental Wizards does it; vanilla picks 2 random offers per level.
 * <p>
 * The event fires when the server data loads, before the server config is loaded. So the offers are always added and the
 * switch {@code [trades] wizard_merchant} is read when an offer is rolled: switched off, the offer returns {@code null},
 * vanilla then draws another one, and the merchant ends up exactly as without these offers.
 */
public final class WizardMerchantTrades {
    private static final ResourceLocation WIZARD_MERCHANT = new ResourceLocation("wizards", "wizard_merchant");
    private static final ResourceLocation LIGHTNING_RUNE = new ResourceLocation("runes", "lightning_stone");

    private WizardMerchantTrades() {}

    public static void onVillagerTrades(VillagerTradesEvent event) {
        if (!WIZARD_MERCHANT.equals(BuiltInRegistries.VILLAGER_PROFESSION.getKey(event.getType()))) {
            return;
        }
        // Same numbers as the Frost offers of Wizards
        add(event, 1, () -> runeItem() == null ? null
                : new BasicItemListing(2, new ItemStack(runeItem(), 8), 128, 3, 0.1F));
        add(event, 2, () -> new BasicItemListing(18, new ItemStack(StormcallerItems.wandLightning.item(), 1), 12, 18, 0.05F));
        add(event, 5, () -> enchanted(StormcallerItems.staffLightning.item(), 40, 3, 30, 0.0F));
    }

    private static void add(VillagerTradesEvent event, int level, Supplier<VillagerTrades.ItemListing> listing) {
        List<VillagerTrades.ItemListing> pool = event.getTrades().get(level);
        if (pool != null) {
            pool.add(new Switched(listing));
        }
    }

    /**
     * Forge 1.20.1: the vanilla classes ItemsForEmeralds and EnchantedItemForEmeralds are not public here. Plain offers use
     * BasicItemListing of Forge, the enchanted staff is built like vanilla EnchantedItemForEmeralds (level 5 to 19, the
     * emerald cost grows with the level, at most 64).
     */
    private static VillagerTrades.ItemListing enchanted(Item item, int baseEmeralds, int maxUses, int villagerXp, float priceMultiplier) {
        return (trader, random) -> {
            int level = 5 + random.nextInt(15);
            ItemStack stack = EnchantmentHelper.enchantItem(random, new ItemStack(item), level, false);
            int cost = Math.min(baseEmeralds + level, 64);
            return new MerchantOffer(new ItemStack(Items.EMERALD, cost), stack, maxUses, villagerXp, priceMultiplier);
        };
    }

    @Nullable
    private static Item runeItem() {
        Item item = BuiltInRegistries.ITEM.get(LIGHTNING_RUNE);
        return item == Items.AIR ? null : item;
    }

    /** Builds the real offer only while {@code [trades] wizard_merchant} is on; otherwise no offer (vanilla draws another). */
    private record Switched(Supplier<VillagerTrades.ItemListing> listing) implements VillagerTrades.ItemListing {
        @Nullable
        @Override
        public MerchantOffer getOffer(Entity trader, RandomSource random) {
            if (!StormcallerConfig.get(StormcallerConfig.TRADES_WIZARD_MERCHANT)) {
                return null;
            }
            VillagerTrades.ItemListing real = listing.get();
            return real == null ? null : real.getOffer(trader, random);
        }
    }
}
