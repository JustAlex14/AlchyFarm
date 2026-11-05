# Configuration

`config/alchyfarm-common.toml` sets almost every number in the mod. On a server it is sent to the players, so screens and tooltips show the server's numbers.

| Section | What it sets |
|---|---|
| `souls` | how long a worker takes to arrive, how long it idles, the open-ground radius without a fence |
| `cropPlot` | largest field, work and walking speed, crops per bone meal, harvests |
| `ground` | which ground each workstation needs |
| `ranch` | pen size, animals per ranch, time per drop, drops, feed per drop and per breeding, milking time, breeding rest, growing up, freeing |
| `pond` | time per catch, least water, loot table, rift potions and their chance, catches, baits |
| `orchard` | harvests |
| `mushroomHollow` | time per mushroom, harvests |
| `mine` | ore veins for the mine and the nether mine, lava springs, torch blocks and their bonus, time per vein |
| `stamps` | Haste, Bounty and Thrift per tier, the rift's growth bonus, rifted chance per tier, rifted food effects |
| `fluids` | cask size in buckets |
| `apiary` | time per honey level, the flower bonus, most flowers, what honey, comb and fluid it gives |
| `flowerGarden` | time per bloom, harvests |
| `gildedGarden` | seeds and harvests |

Each setting has a comment in the file that explains it.

## Datapacks

Every workstation is an AlchyX multiblock (`data/alchyfarm/multiblock/`), which is where the fixed shapes of the Mine, Nether Mine and Mushroom Hollow live, and each one has its animation file in `data/alchyfarm/machine_animation/`. See the [AlchyX wiki](https://github.com/JustAlex14/AlchyX/wiki).
