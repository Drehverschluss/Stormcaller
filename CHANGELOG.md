# Changelog

## 0.4.1

- **Fix: Lightning Bolt and Chain Lightning lost jumps in tight groups.** Against 3 enemies standing close together, Lightning Bolt hit only 2 of them, also with Arc Jump or a full Stored Charge. The cause is in Spell Engine: after a jump, its hit check stops at the nearest enemy on the way, even one the bolt has already hit. That enemy hid the next target, and the fast bolt flew past it. The ricochet fix now leaves the enemies that were already hit out of that check. In my tests:
  - Lightning Bolt against 3 close targets: 3 hits instead of 2, about +50 % damage on every tier.
  - With Arc Jump or a full Stored Charge every jump lands, also against 4 and 5 close targets.
  - Chain Lightning: 3 instead of 2 hits against 3 close targets, 5 instead of 4 against 5.
  - With the full lightning rotation against 3 close targets about +30 to +50 %, against single targets and 10 spread-out targets no change.
  - Static Burst still gives about +35 to +40 % against 3 close targets, Arc Jump about +35 to +45 % against 10 spread-out targets.
  - Spark and the Storm Elemental's bolt use the same fix. With `fix_ricochet_all_spells = true` it applies to the projectiles of other mods too.
- **Config:** the comment of `fix_ricochet_all_spells` now says the right version: the key was called `fix_all_spells` up to 0.2.0 (it was renamed in 0.3.0).

## 0.4.0

- **Skill Tree (RPG Series): Stormcaller is now a class in the Class Skills tab.** With Skill Tree, the Stormcaller
  branch is added to the class circle of the Class Skills tab when the tab loads, as the ninth class next to the eight
  classes of the RPG Series. You pick one class there, like with the others, and the points of that tab are used. The
  branch is the same as before: 28 nodes, the same values, at most 13 points. By default all branches of the circle are
  spread out evenly again and Stormcaller sits between Archer and Arcane; only positions change, no ids.
- **Lightning Bolt nodes in the Weapon Skills tab**, like the staves of the other schools (one fixed node and a choice of
  two, with the points of that tab):
  - **Lightning Staff Specialisation:** Lightning Bolt deals 5 % more damage.
  - **Arc Jump:** Lightning Bolt jumps to 1 more target. Best against spread-out groups (in my tests about +33 to +43 %
    damage against 10 spread-out targets).
  - **Static Burst:** every Lightning Bolt hit discharges into the other enemies within 1.5 blocks, for 0.15 ×
    Lightning Spell Power each (the same up to the edge). Best against tight groups (about +38 % against 3 close
    targets). It has no shock chance of its own.
  - They work with every staff that casts Lightning Bolt, not with the wands. They sit above the Fire and Frost staves.
- **More RPG Classes – Skill Tree:** by default Stormcaller now joins its class circle (20 branches) and its Weapon Skills
  tab too. If you want the separate Stormcaller tab there, set `mode = "auto"` or `"own_tab"`.
- **Samurai RPG Class** works too: Stormcaller is added to its versions of the Class Skills and Weapon Skills tabs.
- **New config options** in `[skill_tree]` of `stormcaller-server.toml`:
  - `mode = "class_tree"` (default): Class Skills tab, also with More RPG Classes. `"auto"`: like `class_tree`, but with
    More RPG Classes the separate Stormcaller tab. `"own_tab"`: always the separate tab, like in 0.3.0.
  - `class_tree_layout = "respace"` (default) or `"gap"`: spread the branches out evenly again, or leave them where they
    are and put the Stormcaller branch further out between two of them.
  - `weapon_skills = true`: the Lightning Bolt nodes. With More RPG Classes only with `mode = "class_tree"`.
  - `class_lock` stays and now only matters for the separate tab (in the Class Skills tab you can only take one class
    anyway).
- **What existing worlds and configs get on the first start:**
  - Configs from 0.3.0 get the new keys with their defaults (so `mode = "class_tree"`). A config in which `mode` is already
    set keeps its value.
  - The Stormcaller branch moves into the Class Skills tab, also with More RPG Classes. Nodes taken in the old Stormcaller
    tab are reset and their points can be spent again; a chat message says how many. The experience of the old tab stays
    saved.
  - When you later change `mode` or add or remove More RPG Classes, the branch moves again: nodes in the old place are
    reset with a chat message. On a switch to the separate tab, its experience is raised once to that of the Class Skills
    tab (it never adds up). If the Lightning Bolt nodes are switched off on purpose, they are removed from the Weapon
    Skills tab the same way.
  - Changes apply after `/reload` or a restart.
- **If something goes wrong** (another pack uses the same ids, broken data, no room in the circle), the tab loads without
  the Stormcaller nodes, or the separate Stormcaller tab comes back for the class branch, with a line in the log. Nodes
  you took stay saved and are back once the problem is fixed. If you took another class in the meantime, your Stormcaller
  class nodes are reset then (one class only).
- **For modpacks:** the place and the nodes are data, `data/stormcaller/stormcaller_skill_tree/class_branch.json` and
  `weapon_branch.json` (built-in datapack, only with Skill Tree), and can be replaced with a datapack or KubeJS. The values
  of the Lightning Bolt nodes are in `data/stormcaller/spell/weapon_lightning_*.json`. No files of Skill Tree, More RPG
  Classes, Samurai RPG Class or Pufferfish's Skills are copied or bundled; Stormcaller adds its nodes while the tabs load.

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
