# Portable Mob Behaviour documentation

PMB is a two-sided Fabric mod. Install PMB on the server and every client with matching network protocol versions; display versions need not be identical. AI, damage, Faction data, and `PmbAi` configuration are server-authoritative; clients receive only the small visual states required for animation.

> **Breaking migration (`26.10.4-1`)**: back up the world first. The old flat `PmbAi`, old `skillPriorities`, and skill-form `air_tracking` are intentionally not read. Convert summons, data packs, and entity data.

```text
PmbAi:{skill:{active:{...},conditional:{...},priorities:{...}},pathfinding:{planner:{...},strategies:{...},air_tracking:{...},retreat:{...}}}
```

## Guides

- [Pathfinding](en/pathfinding.md)
- [Faction](en/faction.md)
- [Skills](en/skill/README.md)
  - [Active skills](en/skill/active/README.md): [bow](en/skill/active/bow.md), [ender pearl](en/skill/active/ender_pearl.md), [shield](en/skill/active/shield.md), [wind charge](en/skill/active/wind_charge.md)
  - [Conditional skills](en/skill/conditional/README.md): [fall rescue](en/skill/conditional/fall_rescue.md), [mace](en/skill/conditional/mace.md), [special traversal](en/skill/conditional/traversal.md), [pearl method](en/skill/conditional/pearl_traversal.md), [wind method](en/skill/conditional/wind_traversal.md)

These repository-relative links remain valid across branches and tags.

## Command roots

```mcfunction
/pmb skill active ...
/pmb skill conditional ...
/pmb skill priority ...
/pmb pathfinding ...
/pmb faction ...
```

The old `/pmb skills` root is not registered.

## Verification boundary

Builds and automated checks do not prove live movement, projectile timing, animation, networking, or Goal/Brain interaction. Those require in-game testing.


~~p.s. if you don't want to read the docs line by line, just send the repo link to ai and ask ai about the docs details~~
```text
https://github.com/Avalanche1510/Portable-Mob-Behaviour
```