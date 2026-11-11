package com.drehverschluss.stormcaller.compat.curios;

import com.drehverschluss.stormcaller.item.StormcallerItems;
import com.drehverschluss.stormcaller.trinket.StormcallerTrinkets;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.spell_engine.PlatformEvents;
import net.spell_engine.api.spell.SpellDataComponents;
import net.spell_engine.api.spell.container.SpellContainers;

import java.util.ArrayList;
import java.util.List;

/**
 * Registers the trinkets of {@link StormcallerTrinkets#ITEMS} as Curios items. Curios itself attaches its item capability
 * to every {@code ICurioItem}; the Accessories compatibility layer turns them into accessories the same way.
 */
final class CuriosItems {
    private CuriosItems() {}

    static void register() {
        List<Item> items = new ArrayList<>();
        for (StormcallerTrinkets.Def def : StormcallerTrinkets.ITEMS) {
            Item.Properties properties = new Item.Properties().stacksTo(1).rarity(def.rarity());
            if (def.fireResistant()) {
                properties = properties.fireResistant();
            }
            if (def.spell() != null) {
                properties = properties.component(SpellDataComponents.SPELL_CONTAINER, SpellContainers.forRelic(def.spell()));
            }
            Item item = Registry.register(BuiltInRegistries.ITEM, def.id(), new StormcallerCurioItem(properties, def));
            items.add(item);
        }
        // last in the Stormcaller tab
        PlatformEvents.onItemGroupModify(StormcallerItems.TAB_KEY, (content, context) -> items.forEach(content::accept));
    }
}
