# Modpack Guide

What a modpack maker can change in AlchyFarm without code: recipes, which plants and blocks each workstation accepts, what workers bring back, and the stamps. For the general methods (replacing a datapack file, removing a recipe, tags), see the [AlchyX Modpack Guide](https://github.com/JustAlex14/AlchyX/wiki/Modpack-Guide).

AlchyFarm is built to take other mods' crops, animals, ores and fluids. Most of the time adding a mod's content is one tag entry or one config line.

## Where each thing lives

| What | Where |
|---|---|
| Crafting recipes (45) | `data/alchyfarm/recipe/<item>.json` |
| What each workstation accepts (seeds, saplings, flowers, fences, beds) | tags in `data/alchyfarm/tags/item/` and `tags/block/` |
| What workers bring back, ore veins, baits, ground rules | `config/alchyfarm-common.toml` |
| Stamp strengths | `config/alchyfarm-common.toml`, section `stamps` |
| Workstation shapes | `data/alchyfarm/multiblock/<workstation>.json` |

Datapack files go in a datapack or in `kubejs/data/`. Run `/reload` after editing them.

## Recipes

Every item has an ordinary crafting recipe named after it: the posts (`carved_stake`, `hitching_post`, `woodsman_post`, `angler_post`, `gardener_post`, `beekeeper_post`, `gilder_post`, `forager_post`, `miner_post`, `nether_miner_post`), the stamps (`haste_stamp_1` to `_4`, `bounty_stamp_1` to `_4`, `thrift_stamp_1` to `_4`, `quality_stamp_1`, `void_stamp_1` and `_2`), the parts (`iron_fitting`, `crimson_seal`, `violet_seal`, `estate_timber`...) and the rest. Put a file with the same path in your datapack to replace one.

The stamps are where progression lives. A tier 1 Haste stamp:

```json
{
  "type": "minecraft:crafting_shapeless",
  "category": "misc",
  "ingredients": [ "alchyfarm:iron_fitting", "alchyfarm:crimson_seal", "minecraft:sugar" ],
  "result": { "id": "alchyfarm:haste_stamp_1" }
}
```

The tier 4 stamps also ask for a rifted item of level 3, with AlchyX's rifted ingredient:

```json
{ "neoforge:ingredient_type": "alchyx:rifted", "level": 3 }
```

Raise the level (up to 6) to make tier 4 harder, or swap it for an item from another mod to tie AlchyFarm into the pack's progression. To remove a recipe, replace the file with `{"neoforge:conditions": [{"type": "neoforge:false"}]}`, or use KubeJS: `event.remove({ id: 'alchyfarm:void_stamp_2' })`.

## Adding another mod's plants and blocks

Each workstation reads a tag to know what it accepts. Add to the tag and the workstation takes it. Use `"replace": false` so the default entries stay, and `"required": false` for mods that may be missing.

| Tag | Used by | Default |
|---|---|---|
| `alchyfarm:plot_seeds` (item) | Crop Plot, what it sows | wheat seeds, carrot, potato, beetroot seeds, pumpkin and melon seeds, glow berries, apple |
| `alchyfarm:plot_fertilizers` (item) | Crop Plot, what it spreads | bone meal |
| `alchyfarm:plot_fruits` (block) | Crop Plot, fruit that grows beside a stem | pumpkin, melon, glistering melon |
| `alchyfarm:orchard_saplings` (item) | Orchard, what it plants | the vanilla saplings, cactus, bamboo, sweet berries |
| `alchyfarm:orchard_stalks` (block) | Orchard, plants cut above the first block | cactus, sugar cane, bamboo |
| `alchyfarm:stalk_tops` (block) | Orchard, what ends a column's growth so it is cut | cactus flower |
| `alchyfarm:orchard_bushes` (block) | Orchard, bushes picked without cutting | sweet berry bush |
| `alchyfarm:pond_plants` (item) | Pond, what it plants on its banks | sugar cane |
| `alchyfarm:trellis_plants` (item) | Trellis | glow berries |
| `alchyfarm:garden_flowers` (item) | Flower Garden | small flowers and the tall ones |
| `alchyfarm:mushroom_spawn` (item) | Mushroom Hollow, what it grows | mushrooms, fungi, nether wart, `#c:mushrooms` |
| `alchyfarm:mushroom_beds` (block) | Mushroom Hollow, its floor | mycelium, podzol, nylium, soul soil and sand, spore loam |
| `alchyfarm:plot_fences` (block) | every fenced workstation, what closes the field | fences, fence gates, walls |
| `alchyfarm:plot_borders` (block) | the same, quieter borders | leaves, trapdoors, carpets, bars, glass panes |
| `alchyfarm:hollow_stone` (block) | Mine and Mushroom Hollow walls | cobblestone, stone, deepslate, tuff, moss... |
| `alchyfarm:nether_stone` (block) | Nether Mine walls | netherrack, blackstone, basalt, nether bricks... |

Example, a modded crop and its farmland:

`data/alchyfarm/tags/item/plot_seeds.json`

```json
{
  "replace": false,
  "values": [ { "id": "somemod:rice_seeds", "required": false } ]
}
```

and in the config, under `ground`:

```toml
grounds = ["minecraft:cactus=#minecraft:sand", "minecraft:sugar_cane=#minecraft:dirt, #minecraft:sand, minecraft:farmland", "minecraft:bamboo=#minecraft:dirt, #minecraft:sand, minecraft:gravel", "#alchyfarm:orchard_saplings=#minecraft:dirt", "somemod:rice_seeds=somemod:paddy", "#alchyfarm:plot_seeds=minecraft:farmland, somemod:rich_soil"]
```

The first entry that names a plant decides for it, so put single items before the tags they belong to.

## What workers bring back

By default a worker gets what the block or animal drops in the game (its loot table), whatever mod it comes from. The config lists override that for the entries they name. They all use the same writing:

`id=item, item*count, item*least-most, item%chance`

| Setting | Key is | Example |
|---|---|---|
| `ranch.drops` | an entity | `"minecraft:sheep=minecraft:white_wool, minecraft:mutton*1-2"` |
| `cropPlot.harvest` | the crop block | `"minecraft:wheat=minecraft:wheat*1-2, minecraft:wheat_seeds*0-3"` |
| `orchard.harvest` | the block felled or picked | `"minecraft:oak_log=minecraft:oak_log, minecraft:stick%20"` |
| `mushroomHollow.harvest` | the mushroom block | `"minecraft:red_mushroom=minecraft:red_mushroom*1-2"` |
| `flowerGarden.harvest` | the flower block | `"minecraft:poppy=minecraft:poppy*1-2"` |
| `gildedGarden.harvest` | the gilded crop block | `"alchyfarm:gilded_carrots=minecraft:golden_carrot*2"` |
| `apiary.honey`, `apiary.comb` | (none, just items) | `"minecraft:honeycomb*3"` |

An entry with nothing after `=` gives nothing.

### Mines

`mine.veins` and `mine.netherVeins` list what a miner can bring up, as `block=weight,lowest,highest`. Each swing picks one entry by weight among those whose height range holds the post's height. The block is then broken with the stock's pickaxe, so Fortune and Silk Touch work. Any mod's ore can be listed:

```toml
veins = ["minecraft:stone=560,0,320", "minecraft:deepslate=560,-64,-1", "minecraft:iron_ore=45,0,72", "somemod:tin_ore=40,0,96", "somemod:deepslate_tin_ore=20,-64,-1"]
```

Put plain rock first. A block that does not exist is skipped with a line in the log, so one list can cover several mods. `mine.springs` and `mine.netherSprings` work the same way for fluids: `fluid=mB each second,lowest,highest`.

### Ponds

- `pond.catches`: empty uses the fishing loot table (`pond.lootTable`). Otherwise one entry is caught each time, by weight: `"minecraft:cod%60"`, `"minecraft:salmon*1-2%25"`.
- `pond.baits`: what counts as bait, as `item=wait,treasure`. `wait` is in hundredths of the catch time, `treasure` is how many casts in a hundred bring treasure. Add a mod's bait here.

### Gilded Garden

`gildedGarden.seeds` maps an item to the crop it grows, as `seed=block`. Extra items before the `=` are taken with the seed, to make sowing cost more:

```toml
seeds = ["minecraft:carrot, minecraft:gold_nugget*8=alchyfarm:gilded_carrots", "minecraft:golden_apple=alchyfarm:gilded_apple_tree"]
```

### Apiary fluid

`apiary.fluid` is the fluid a hive gives into a cask: a fluid id or a `#` fluid tag. Set it to another mod's honey to share one honey across the pack. AlchyFarm's honey is in `#c:honey`, so mods that read that tag already accept it.

## Stamps

`stamps.hasteTiers`, `bountyTiers` and `thriftTiers` each list four numbers, one per tier, in percent:

```toml
hasteTiers = [15, 30, 55, 80]
bountyTiers = [25, 50, 120, 200]
thriftTiers = [20, 40, 65, 90]
```

Haste is capped at 95, Thrift at 100, Bounty at 1000. The rest of the section sets what the rift does at each violet tier; each line has a comment in the file.

## Workstation shapes

The Mine, Nether Mine and Mushroom Hollow have fixed shapes in `data/alchyfarm/multiblock/`. Their walls use the `hollow_stone` and `nether_stone` tags, so the easy change is to add blocks to those tags. To change a shape, copy the file to your pack and edit it, keeping the `core` and the `anchors` (stock, basket, floor). The fenced workstations have a one-block pattern (the post) and their field is whatever the fence closes.

## Shipping it

- Recipes, tags and shapes are server data: ship them in KubeJS `data` or a global datapack.
- Put the edited `config/alchyfarm-common.toml` in the pack's `config` folder. It is sent to players when they join.
