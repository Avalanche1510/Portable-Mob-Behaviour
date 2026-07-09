# Portable Mob Behaviour Docs

## Introduction

This mod introduces a new, highly customizable, and relatively simple data structure for mob AI behaviours.
Players can use commands or data packs to modify the data in game. Changes take effect immediately and can modify or enable specific AI behaviours.
Available parameters and the syntax used to define the data structure are given below.

## Complete data structure

```text
{
    PmbAi:
        {
            shield:
                {
                    enable: <bool>,
                    range: <float>,
                    shieldChance: <float>,
                    minUseTicks: <int>,
                    maxUseTicks: <int>,
                    cooldownTicks: <int>,
                    blockingAngle: <float>,
                    axeDisableCooldownTicks: <int>,
                    shieldToughness: <int>,
                    speedReduction: <float>
                }
        }
}
```

## PmbAi

PmbAi is the root data tag of this mod. It must be written inside a mob's NBT at the same level as other root tags such as Health, Motion, and equipment.

PmbAi is a compound tag. It can contain multiple manually added AI skills, each represented by another compound tag containing its parameters.

By default, mobs do not spawn with the PmbAi root tag.
This means that naturally spawned mobs have no AI behaviour changes caused by this mod.

Currently, players must manually add PmbAi using the data merge command, a summon command, or a data pack function.

<details>
<summary>abstract structure</summary>

```text
PmbAi:
    {
        shield:{<parameters>},
        mace:{<parameters>},
        bow:{<parameters>},
        ...
    }
```

Only shield is currently implemented. mace and bow are examples of possible future skill tags and are not currently available.

</details>

### shield

Basic shield usage is currently implemented.
An eligible mob repeatedly checks whether it can raise its shield, with a configurable chance of success.

Conditions for raising a shield:

1. The entity is a mob and holds a vanilla shield in its off hand.
2. enable is true.
3. The mob has a living hostile target within range and has line of sight to it.
4. The mob is not in the normal shield cooldown.
5. The mob is not in the forced cooldown caused by shield-disabling attacks.
6. The shieldChance check succeeds.
7. The mob does not have NoAI enabled.

PMB-controlled shields can block from the first shield-use tick and do not use the vanilla player's initial shield activation delay.

When the shield is raised, it takes durability damage from blocked attacks.

Attacks capable of disabling shields consume one point of shield toughness.

When the configured number of such attacks is reached during the current shield-use round, shield use stops and the forced cooldown begins.

The counter resets when the normal cooldown ends and before a new round after a forced cooldown.

Shield recoil, durability loss, and shield toughness consumption occur at most once per normal damage interval. Additional attacks ignored by the entity's damage cooldown do not accumulate these effects.

Entities from the Guard Villagers namespace are excluded from this behaviour to avoid conflicting with that mod.

<details>
<summary>parameters structure</summary>

```text
shield:
    {
        enable: <bool>,
        range: <float>,
        shieldChance: <float>,
        minUseTicks: <int>,
        maxUseTicks: <int>,
        cooldownTicks: <int>,
        blockingAngle: <float>,
        axeDisableCooldownTicks: <int>,
        shieldToughness: <int>,
        speedReduction: <float>
    }
```

</details>

<details>
<summary>details of parameters</summary>

```text
enable
Meaning: Whether this AI skill is enabled
Type: bool
Range: 0b, 1b
Default: 0b

range
Meaning: Maximum distance at which the mob can raise its shield; line of sight is also required
Type: float
Range: [0.0f, 64.0f]
Default: 8.0f

shieldChance
Meaning: Chance for each shield-raising check to succeed
Type: float
Range: [0.0f, 1.0f]
Default: 0.35f

minUseTicks
Meaning: Minimum duration of a randomly selected shield use, in game ticks
Type: integer
Range: [1, 72000]
Default: 80

maxUseTicks
Meaning: Maximum duration of a randomly selected shield use, in game ticks; values below minUseTicks are raised to minUseTicks
Type: integer
Range: [minUseTicks, 72000]
Default: 160

cooldownTicks
Meaning: Cooldown after shield use ends or a shieldChance check fails, in game ticks
Type: integer
Range: [0, 400]
Default: 40

blockingAngle
Meaning: Maximum attack angle that the shield can block, in degrees
Type: float
Range: [1.0f, 180.0f]
Default: 30.0f

axeDisableCooldownTicks
Meaning: Forced shield cooldown after an attack disables the shield, in game ticks
Type: integer
Range: [0, 600]
Default: 100

shieldToughness
Meaning: Number of shield-disabling attacks required to break the current shield-use round
Type: integer
Range: [1, 100]
Default: 1
Refresh: Resets after the normal cooldown ends and before a new round after a forced cooldown

speedReduction
Meaning: Movement speed reduction ratio while the shield is raised
Type: float
Range: [0.0f, 1.0f]
Default: 0.5f
Compatibility: Values above 1.0 are treated as percentages before clamping; for example, 50 becomes 0.5
```

Values outside these ranges are automatically clamped when the entity data is read.
One second is normally equal to 20 game ticks.

</details>

<details>
<summary>command examples</summary>

```text
Summon a zombie with an iron sword, a shield, and an iron helmet. Shield usage is enabled and all omitted parameters use their default values.
summon zombie ~ ~ ~ {PmbAi:{shield:{enable:1b}}, equipment:{mainhand:{id:iron_sword}, offhand:{id:shield}, head:{id:iron_helmet}}}

Summon a heavily armoured zombie with a diamond sword and shield.
It can always raise its shield within 12 blocks, uses it for 4–8 seconds, has a 3-second normal cooldown, can block attacks from any direction, requires 3 shield-disabling attacks to break each round, has a 2.5-second forced cooldown after its shield is disabled, and loses 90% of its movement speed while shielding.
(This command is long; use a command block.)
summon zombie ~ ~ ~ {PmbAi:{shield:{enable:1b, range:12.0f, shieldChance:1.0f, minUseTicks:80, maxUseTicks:160, cooldownTicks:60, blockingAngle:180.0f, axeDisableCooldownTicks:50, shieldToughness:3, speedReduction:0.9f}}, equipment:{mainhand:{id:diamond_sword}, offhand:{id:shield}, head:{id:diamond_helmet}, chest:{id:diamond_chestplate}, legs:{id:diamond_leggings}, feet:{id:diamond_boots}}}

Enable shield usage with default parameters on the nearest zombie that already has a shield in its off hand.
data merge entity @e[type=zombie,sort=nearest,limit=1] {PmbAi:{shield:{enable:1b}}}

Disable shield usage without removing its other configured parameters.
data merge entity @e[type=zombie,sort=nearest,limit=1] {PmbAi:{shield:{enable:0b}}}
```

</details>
