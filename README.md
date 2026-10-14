# AlchyFarm

A farming mod for NeoForge (Minecraft 26.1.2), built on [AlchyX](https://github.com/JustAlex14/AlchyX). Set a post in the ground, give it a stock and a basket, and a lost soul comes to work it: sowing, herding, fishing, mining. Then seal it with violet stamps, and the soul starts working by magic.

- **Workstations**: a post, a Stock (what it uses) and a Basket (what it makes). Fence a field of any shape up to 9 x 9, or leave it open for a 5 x 5 around the post
- **Lost souls**: each workstation has its own worker, who walks to the job, kneels, casts, and carries the harvest back
- **Ten workstations**:
  - **Crop Plot** (Carved Stake): sows, waters and harvests, with a seed chosen per square, glow berries on trellises and a small apple tree
  - **Ranch** (Hitching Post): animals led in give their drops unharmed, are milked into casks, and can be bred or freed
  - **Orchard** (Woodsman's Post): plants saplings and fells grown trees, and grows cactus, bamboo and sweet berries
  - **Pond** (Angler's Post): fishes with or without bait (better bait, more treasure), and grows sugar cane on its banks
  - **Flower Garden** (Gardener's Post): blooms every flower kind, or turns them into dye
  - **Apiary** (Beekeeper's Post): fills hives with no bees, into bottles, comb or casks of honey
  - **Gilded Garden** (Gilder's Post): golden carrots, golden apples, glistering melons and even enchanted golden apples, grown as crops
  - **Mushroom Hollow** (Forager's Post): mushrooms, fungi and nether wart on spore loam
  - **Mine** (Miner's Post): ores show on the walls and are dug, with a Pump for lava
  - **Nether Mine** (Nether Miner's Post): quartz, nether gold, glowstone and ancient debris, in any dimension
- **Choice screens**: pick what each workstation keeps, with the odds of every drop shown under the mouse
- **Stamps**: Haste, Bounty and Thrift in four tiers, Quality Work (never short of bone meal, bait or feed) and Void (keeps the basket clean)
- **The rift**: violet stamps (tiers III and IV) make the soul cast from where it stands, and part of what it brings back comes out **rifted**: stronger food, deeper potions, rifted honey, milk and stew in three tiers
- **Casks**: hold lava, honey, milk and mushroom stew, and give them to pipes and buckets
- **Surveyor's Cord**: link a post to stores and fields placed away from it, at the door of your building for instance
- **The Estate Ledger**: an in-game journal (Patchouli) kept by the nobleman sent to this land, whose paper changes as the story turns

## Requirements

- NeoForge 26.1.2
- [AlchyX](https://github.com/JustAlex14/AlchyX) 0.2.1 or newer
- Optional: Patchouli (the ledger), JEI (recipes and workstation pages), Jade

## Configuration

`config/alchyfarm-common.toml` sets the speed of every workstation, field sizes, what each one gives and how often, the stamps' strength, the rift's chances, the cask size and what the pump and apiary pour.

## Building

AlchyX must sit next to this folder (`../AlchyX`): Gradle builds it together with this project.

```
./gradlew build
```

The jar ends up in `build/libs`.

## License

MIT, see [LICENSE](LICENSE).

A few textures are recolored from Minecraft and Patchouli textures; those stay under their original owners' terms.
