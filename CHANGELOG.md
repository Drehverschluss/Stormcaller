# Changelog

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
