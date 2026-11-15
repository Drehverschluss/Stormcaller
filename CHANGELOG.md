# Changelog

## 0.3.0

- **New default look for all four lightning robes**, in the style of the Wizards robes. I made the models and textures
  myself from scratch, no files from other mods are used.
  - Lightning Robe (tier 2) and Netherite Lightning Robe (tier 3): storm violet with a pointed wizard hat and
    yellow-white lightning bolts, tier 3 with netherite grey.
  - Fulgurite Robe: lightning yellow and storm anthracite with a crystal-crowned hat.
  - Stormweave Robe: storm grey with silver trims, a cloud wreath around the hat, a small blue rune and calmer shoulders.
  - New item icons for the robe pieces (Stormweave: hat and top; its bottom and boots stay as they were).
  - Only the glowing bolts, runes and crystals light up at night, tier 4 a little more.
  - Values, recipes, spells, items and item ids are the same, so robes you already wear just change their look.
- **The old look is still there:** the built-in resource pack **Stormcaller_Original_Armor** brings back the robes of
  0.1/0.2 (models, textures, glow and icons). Turn it on in *Options → Resource Packs* (move it to the right, *Done*).
  It works right away without a restart and only on your own client: every player sees all robes in the look they
  picked, and servers need nothing. Pack id for modpacks (`resourcePacks` in `options.txt`):
  `mod/stormcaller:resourcepacks/stormcaller_original_armor`.
- **Shock: stun pause.** After a stun, the same target can't be stunned by shocks again for 2 seconds
  (`[shock] stun_pause_seconds` in `stormcaller-server.toml`, 0 turns it off). Shocks still add stages of Shocked in
  that time. The pause is per target, for all players together. Lightning Nova always stuns and restarts the pause.
  Stuns from other mods are left alone. Before, a single big enemy could be stunned about 40 % of the time on tier 4,
  now at most 20 %.
- **Config:** `[ricochet] fix_all_spells` is now called `fix_ricochet_all_spells`. An existing value is carried over
  automatically on the first start (one line in the log), afterwards only the new name is in the file.

## 0.2.0

- **Stormbringer Staff: Ball Lightning.** The staff gets Ball Lightning as a second active spell (in the spell hotbar,
  tier 3, instant, 30 s cooldown that Spell Haste shortens). The ball drifts where you look, and its hits count as your
  lightning spell hits, so they can trigger Skyfall, the Static Lodestone and the Caged Ball Lightning. Up to 3 staff
  balls at once (`[ball_lightning] max_staff_balls` in `stormcaller-server.toml`); the relic still makes one at most.
- **Stormbringer Staff: +10 Lightning Spell Power** instead of +8. This is the default of
  `config/stormcaller/equipment.json`, so an existing file keeps +8. To get +10, set the value of `staff_stormbringer` to
  10, or delete its entry and it is written again with 10 on the next start.
- **Amber Lightning Staff: Stored Charge.** Lightning spell hits charge the staff, up to 5 charges (new effect with its own
  icon). At full charge your next Lightning Bolt jumps to 2 more targets and shocks every target it hits. The hits of that
  bolt do not charge the staff again. The charge fades 15 s after the last hit. Values in `[stored_charge]` of
  `stormcaller-server.toml`.
- **Wizard Robe and Wizard Staff:** the lightning line now always sits right under Frost Spell Power, also when a pack
  adds more lines (for example mana) in the Wizards config. An entry for `spell_power:lightning` in
  `config/wizards/equipment_v2.json` is no longer needed for that. An existing one still wins and does no harm.

## 0.1.0 – First release

- Lightning as a fourth school of magic for Wizards (RPG Series), on NeoForge 1.21.1.
- 8 lightning spells: Spark (wand), Lightning Bolt (staff), and in the Tome of Lightning Chain Lightning, Lightning
  Nova, Static Shield, Lightning Strike, Thunderstorm and Storm Elemental.
- Shock: every lightning hit can shock; stages of Shocked, short stuns, bosses are slowed instead. New attribute Shock
  Chance.
- Robes: Lightning Robe (tier 2), Netherite Lightning Robe (tier 3), Fulgurite Robe and Stormweave Robe (tier 4) with set
  bonuses, each with its own model.
- Weapons: Lightning Wand and Staff, Netherite versions, Amber Lightning Staff (with Better End), unique Stormbringer
  Staff with the passive Skyfall.
- Lightning power for the Wizard Robe and the Wizard Staff of Wizards; Lightning Bolt in the spell choices of the Wizard
  Staff.
- Recipes for the Lightning Rune; Tome of Lightning and Lightning Spell Scrolls.
- With Curios API: Stormheart and Thunderstrike rings and amulets, Static Lodestone, Storm Orb and Caged Ball Lightning.
- With Skill Tree (RPG Series): a Stormcaller tab with 28 nodes, locked against the class tab (configurable).
- Loot through the RPG Series loot tiers, lightning offers of the Wizard Merchant, advancements, recipe book unlocks.
- Configs for equipment, effects, the Storm Elemental, jewelry, shock, trades and visuals; spell values by datapack.
- Works with RunelessRPG (mana costs by tier) and with shader packs (tested with Complementary).
