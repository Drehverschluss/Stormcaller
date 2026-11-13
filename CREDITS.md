# Credits

## Stormcaller

Stormcaller (RPG Series Plus) by Drehverschluss, licensed under the [MIT License](LICENSE). The license text is also in
the root of the mod jar.

Stormcaller is an unofficial addon for **Wizards (RPG Series) by Daedelus**. It is not made by or affiliated with the
original author.

## Assets

All textures, models and icons in the jar are my own work: the robe models and textures of all four robes, the item
icons, the Stormbringer Staff model, the spell, effect and skill icons, the skill tab background and the mod logo. The storm cloud, the Storm Elemental and the lightning effects are drawn by code and have no textures from other
mods.

Models, textures and sounds of other mods are **only referenced by their id and not bundled**:

- `wizards:item/medium_staff`: parent model of the four staff item models (the flat staff in the hand, like the Wizards
  staves). The texture is my own.
- `wizards:wizard_robes_equip`: equip sound of the robes (falls back to the leather sound without it).
- Spell Engine: particles (`spell_engine:magic_spark`, `spell_engine:lightning_arc_a`, …), sounds
  (`spell_engine:generic_lightning_casting`, `spell_engine:generic_lightning_release`) and cast animations, used by id
  in the spell files.
- `runes:lightning_stone`: the Lightning Rune of Runes, used as the spell cost and as the result of the rune recipes.
- Vanilla Minecraft sounds (thunder, lightning impact) and the vanilla smithing slot icons.

The spell files are my own, written in the format of Spell Engine. No code, models, textures, sounds or data files of
the RPG Series mods are copied.

## Ideas

- The layout of the skill tab (28 nodes, 13 points, one path, two modifiers per spell) follows the class branches of
  More RPG Classes – Skill Tree by Fichte. Only structure and scale were used, no files.
- The Wizard Merchant offers are added to the merchant's pools the way Elemental Wizards (RPG Series Plus) by Fichte
  does it. No code of it is used.

## Mods Stormcaller works with (not bundled)

Stormcaller contains no code, textures, models, sounds or translations of these mods. It is compiled against the APIs
listed below and otherwise only refers to their ids (items, spells, tags, sounds, models, `neoforge:mod_loaded`
conditions).

- [Wizards (RPG Series)](https://www.curseforge.com/minecraft/mc-mods/wizards) by Daedelus (All Rights Reserved):
  required. Its items (Wizard Robe, Wizard Staff), the Wizard Merchant, tags, a model and a sound are used by id. Not
  bundled.
- [Spell Engine](https://www.curseforge.com/minecraft/mc-mods/spell-engine) by Daedelus (GPL-3.0): required. Compiled
  against its API; spells, items, equipment sets and loot run on it. Not bundled.
- Tiny Config by Daedelus (CC0-1.0): comes with Spell Engine; the JSON configs use it. Not bundled.
- [Spell Power Attributes](https://www.curseforge.com/minecraft/mc-mods/spell-power) by Daedelus (GPL-3.0): required.
  The lightning school, its attribute and damage type. Compiled against it, not bundled.
- [Armor Model API](https://www.curseforge.com/minecraft/mc-mods/armor-model-api) by Daedelus (MIT): required. Draws the
  robe models. Compiled against it, not bundled.
- Runes by Daedelus (All Rights Reserved): comes with Wizards. The Lightning Rune and the rune crafting recipe type are
  used by id. Not bundled.
- [Skill Tree (RPG Series)](https://www.curseforge.com/minecraft/mc-mods/skill-tree) by Daedelus (All Rights Reserved):
  optional. Compiled against it for the respec. Not bundled.
- Pufferfish's Skills by Pufferfish (All Rights Reserved according to its mod file): optional, comes with Skill Tree.
  The skill tab is a datapack for it; compiled against its API for the class lock. Not bundled.
- [Curios API](https://www.curseforge.com/minecraft/mc-mods/curios) by C4 (LGPL-3.0-or-later): optional. Compiled
  against its API for the jewelry and relics. Not bundled.
- Accessories by Blodhgarm, chyzman, Dr.Zeal and its Curios compatibility layer (MIT): optional, no direct use. Not
  bundled.
- [Combat Roll](https://www.curseforge.com/minecraft/mc-mods/combat-roll) by Daedelus (GPL-3.0): optional. Two passives
  of the skill tab use Spell Engine's roll trigger, which Combat Roll fires. Not bundled.
- [Better End](https://www.curseforge.com/minecraft/mc-mods/betterend-neoforge) by quiqueck, paulevs and others (MIT,
  CC BY-NC-SA 4.0 for some assets): optional. Its Amber Gem and Aeternium Ingot are used by id in one recipe, the Amber Gem
  also as repair material. Not bundled.
- [Better Combat](https://www.curseforge.com/minecraft/mc-mods/better-combat-by-daedelus) by Daedelus (All Rights
  Reserved): recommended. The weapon files refer to its presets `bettercombat:wand` and `bettercombat:staff`. Not
  bundled.
- [RunelessRPG](https://www.curseforge.com/minecraft/mc-mods/runelessrpg) by Drehverschluss (MIT): recommended. No
  direct use; it gives the lightning spells mana costs by their tier. Not bundled.
- Other ids that are only used in optional tag entries: `#c:bosses`, `#mrpg_lib:stun_immune` (More RPG Library),
  `#wizards:armor_type/generic_robes`, `#rpg_series:*` loot tags.

## License

Stormcaller is licensed under the [MIT License](LICENSE).
