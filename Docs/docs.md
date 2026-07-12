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
                    critToughnessDamage: <int>,
                    disableVulnerTicks: <int>,
                    vulnerDamageMultiplier: <float>,
                    disableKBMultiplier: <float>,
                    speedReduction: <float>
                },
            wind_charge:
                {
                    enable: <bool>,
                    doConsume: <bool>,
                    inAirTrackStrength: <float>,
                    throwRange: <float>,
                    throwChance: <float>,
                    throwCooldownTicks: <int>,
                    throwAccuracy: <float>,
                    bounceRange: <float>,
                    bounceChance: <float>,
                    bounceCooldownTicks: <int>
                },
            mace:
                {
                    enable: <bool>,
                    smashRange: <float>,
                    hitChance: <float>,
                    damageReduction: <float>,
                    smashCooldownTicks: <int>
                },
            bow:
                {
                    enable: <bool>,
                    doConsume: <bool>,
                    modePriority: <string>,
                    mobileWhileShooting: <bool>,
                    lineRange: <float>,
                    lineSafeDistance: <float>,
                    lineCooldownTicks: <int>,
                    lineShootChance: <float>,
                    lineShootAccuracy: <float>,
                    lineChargeTicks: <int>,
                    linePower: <float>,
                    arcMinRange: <float>,
                    arcMaxRange: <float>,
                    arcSafeDistance: <float>,
                    arcCooldownTicks: <int>,
                    arcShootChance: <float>,
                    arcShootAccuracy: <float>,
                    arcChargeTicks: <int>,
                    arcAngle: <float>,
                    arcMaxPower: <float>
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

shield, wind_charge, mace, and bow are currently implemented.

</details>

### wind charge

The wind-charge AI gives mobs holding a vanilla wind charge two modes: throwing and self-bouncing. It also provides a shared foundation for wind-charge mace and future wind-charge spear behaviours. The mace skill now supplies a dedicated falling-smash check and can work with post-bounce target retention and airborne movement. Dedicated wind-charge spear attack decisions are not implemented yet.

Conditions for wind-charge use:

1. The entity is a mob and holds a vanilla wind charge in either hand.
2. enable is true.
3. The mob has a living hostile target.
4. The mob does not have NoAI enabled and is not in shield-break vulnerability.
5. The distance, cooldown, and chance checks for the corresponding mode succeed.

Bounce mode takes priority when both modes can be checked. When the mob is on the ground and its target is within bounceRange, it checks bounceChance every bounceCooldownTicks game ticks. On success, it immediately looks down, swings the hand holding the wind charge, jumps, and fires a wind charge beneath its feet so that the explosion produces as much upward launch as possible.

When a visible target is within throwRange, throw mode checks throwChance every throwCooldownTicks game ticks. On success, the mob swings the hand holding the wind charge and throws at a lead point calculated from the target's position and velocity. throwAccuracy controls vanilla projectile spread; 1.0 means no random spread.

doConsume controls consumption for both throw and bounce mode. By default, the held wind charge only acts as the skill's required equipment and is not consumed. When enabled, every successful throw or bounce consumes one wind charge from the hand that actually performed the action.

The downward bounce uses the vanilla living-entity impulse fall-protection context. Returning to the height where the wind-charge impulse occurred does not deal fall damage from that launch. If the mob lands below the launch point, only the additional drop below that height can still deal normal fall damage.

After being launched by its own wind charge, the mob remembers that target for up to 60 game ticks. If vanilla airborne pathfinding temporarily clears the target, the mod restores it and applies a small, limited targetward acceleration controlled by inAirTrackStrength. This preserves gravity, wind-charge launch velocity, and existing horizontal inertia while gradually advancing toward the target instead of replacing movement with conspicuous tracking velocity. A value of 0.0 adds no horizontal tracking acceleration.

After the initial downward bounce pose ends, the mob continuously aligns its view, head, and body with the target while airborne. If it starts a melee attack before that pose ends, the downward pose ends immediately and the mob faces the entity it is actually attacking, preventing hits while visibly facing away. Facing correction is independent of inAirTrackStrength. The tracking state clears when the mob lands, the target becomes invalid, or the time expires.

<details>
<summary>parameters structure</summary>

```text
wind_charge:
    {
        enable: <bool>,
        doConsume: <bool>,
        inAirTrackStrength: <float>,
        throwRange: <float>,
        throwChance: <float>,
        throwCooldownTicks: <int>,
        throwAccuracy: <float>,
        bounceRange: <float>,
        bounceChance: <float>,
        bounceCooldownTicks: <int>
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

doConsume
Meaning: Whether a successful throw or downward self-bounce consumes one wind charge
Type: bool
Range: 0b, 1b
Default: 0b
Note: Controls both modes and consumes from the hand actually holding the wind charge when the action triggers

inAirTrackStrength
Meaning: Maximum horizontal acceleration added toward the target each game tick after a downward self-bounce
Type: float
Range: [0.0f, 1.0f]
Default: 0.012f
Note: Added only while targetward horizontal velocity is below an internal threshold; 0.0 disables horizontal tracking but does not disable facing the target while airborne

throwRange
Meaning: Maximum target distance for throw mode; line of sight is also required
Type: float
Range: [0.0f, 64.0f]
Default: 16.0f

throwChance
Meaning: Chance for each throw check to succeed
Type: float
Range: [0.0f, 1.0f]
Default: 0.35f

throwCooldownTicks
Meaning: Game ticks between throw chance checks
Type: integer
Range: [0, 72000]
Default: 40
Note: 0 allows a check every game tick

throwAccuracy
Meaning: Accuracy of thrown wind charges; higher values produce less random spread
Type: float
Range: [0.0f, 1.0f]
Default: 0.9f
Note: 1.0 means no random spread

bounceRange
Meaning: Maximum target distance for the downward self-bounce mode
Type: float
Range: [0.0f, 64.0f]
Default: 4.0f

bounceChance
Meaning: Chance for each downward self-bounce check to succeed
Type: float
Range: [0.0f, 1.0f]
Default: 0.35f

bounceCooldownTicks
Meaning: Game ticks between downward self-bounce chance checks
Type: integer
Range: [0, 72000]
Default: 40
Note: 0 allows a check every game tick
```

Values outside these ranges are automatically clamped when the entity data is read.
One second is normally equal to 20 game ticks.

</details>

<details>
<summary>command examples</summary>

```text
Summon a zombie holding a wind charge in its off hand and using all default wind-charge parameters.
summon zombie ~ ~ ~ {PmbAi:{wind_charge:{enable:1b}}, equipment:{mainhand:{id:iron_sword}, offhand:{id:wind_charge}}}

Summon a zombie that throws perfectly accurate wind charges and always uses a wind-charge bounce to pursue nearby targets. Each successful use consumes one wind charge.
summon zombie ~ ~ ~ {PmbAi:{wind_charge:{enable:1b, doConsume:1b, inAirTrackStrength:0.012f, throwRange:24.0f, throwChance:1.0f, throwCooldownTicks:40, throwAccuracy:1.0f, bounceRange:6.0f, bounceChance:1.0f, bounceCooldownTicks:60}}, equipment:{mainhand:{id:wind_charge,count:64}}}

Disable wind-charge AI on the nearest zombie without removing its other configured parameters.
data merge entity @e[type=zombie,sort=nearest,limit=1] {PmbAi:{wind_charge:{enable:0b}}}
```

</details>

### mace

Mace AI lets a mob holding a vanilla mace in its main hand actively perform one vanilla mace smash while falling, controlled by chance and cooldown.

Conditions for a smash check:

1. The entity is a mob and holds a vanilla mace in its main hand.
2. enable is true.
3. The mob is airborne, moving downward, and meets the vanilla minimum fall distance for a mace smash.
4. The mob has a living, attackable, visible hostile target within smashRange.
5. The mob is not in the smash-check cooldown defined by smashCooldownTicks.
6. The mob does not have NoAI enabled and is not in shield-break vulnerability.

Once these conditions are met, the mob immediately performs one hitChance check. It enters the smashCooldownTicks cooldown whether that check succeeds or fails. On success, the mob faces the target, swings its main hand, and attacks through the vanilla Mob and mace damage paths. On failure, no attack is performed.

While the skill is enabled and the mob is in a valid falling-mace state, attacks attempted directly by an ordinary melee goal without this skill's check are prevented. This stops vanilla AI from bypassing hitChance, smashCooldownTicks, or damageReduction. Ordinary melee attacks remain unchanged whenever the mob is not in a valid mace-smash fall.

Vanilla first calculates base attack damage, enchantments, and fall-height mace bonus. The final result of the skill-triggered smash is then multiplied by (1-damageReduction). The default damageReduction of 0.5 therefore halves the final mace damage. Vanilla mace sounds, knockback, durability use, and fall-state handling are preserved.

<details>
<summary>parameters structure</summary>

```text
mace:
    {
        enable: <bool>,
        smashRange: <float>,
        hitChance: <float>,
        damageReduction: <float>,
        smashCooldownTicks: <int>
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

smashRange
Meaning: Maximum target distance at which a falling mob can immediately start a smash check; line of sight is also required
Type: float
Range: [0.0f, 64.0f]
Default: 3.0f

hitChance
Meaning: Chance for each smash check to succeed and perform the attack
Type: float
Range: [0.0f, 1.0f]
Default: 0.5f

damageReduction
Meaning: Final damage reduction ratio for a skill-triggered mace smash; actual damage is multiplied by (1-damageReduction)
Type: float
Range: [0.0f, 1.0f]
Default: 0.5f
Note: 0.0 leaves damage unchanged, 0.5 halves final damage, and 1.0 reduces final damage to 0

smashCooldownTicks
Meaning: Game ticks of cooldown after every smash check, whether the check succeeds or fails
Type: integer
Range: [0, 72000]
Default: 100
Note: 0 allows another check on the next game tick
```

Values outside these ranges are automatically clamped when the entity data is read.
One second is normally equal to 20 game ticks.

</details>

<details>
<summary>command examples</summary>

```text
Summon a zombie holding a mace in its main hand and using all default mace parameters.
summon zombie ~ ~ ~ {PmbAi:{mace:{enable:1b}}, equipment:{mainhand:{id:mace}}}

Summon a zombie that always performs a smash check within 4 blocks, deals 75% of normal mace damage, and enters a 3-second cooldown after every check.
summon zombie ~ ~ ~ {PmbAi:{mace:{enable:1b, smashRange:4.0f, hitChance:1.0f, damageReduction:0.25f, smashCooldownTicks:60}}, equipment:{mainhand:{id:mace}}}

Summon a zombie with both downward wind-charge bounce and mace-smash AI enabled.
summon zombie ~ ~ ~ {PmbAi:{wind_charge:{enable:1b, bounceRange:6.0f, bounceChance:1.0f}, mace:{enable:1b}}, equipment:{mainhand:{id:mace}, offhand:{id:wind_charge}}}

Disable mace AI on the nearest zombie without removing its other configured parameters.
data merge entity @e[type=zombie,sort=nearest,limit=1] {PmbAi:{mace:{enable:0b}}}
```

</details>

### bow

Bow AI runs only while a mob holds a vanilla bow in its main hand. It provides a low-trajectory line mode and a fixed high-angle arc mode. Range parameters only control whether shooting behaviour can start; actual arrow reach is determined by power and vanilla projectile physics.

Both modes require a living, attackable, visible target. line can be checked at distances no greater than lineRange. arc can be checked from arcMinRange through arcMaxRange. Setting either ShootChance to 0.0 completely disables that mode.

When both modes satisfy their distance, ammunition, and cooldown conditions, modePriority selects line or arc. A failed chance check by the priority mode does not fall back to the other mode during the same tick. If the priority mode is still cooling down or otherwise not ready, the other mode may be checked. Selecting a mode immediately starts its CooldownTicks whether the chance check succeeds or fails.

After a successful chance check, the mob draws its main-hand bow for the corresponding ChargeTicks. A value of 0 releases during the same tick. While drawing, its head and both arms continuously follow the actual calculated launch vector, so line displays low-trajectory aiming and arc visibly raises the bow to its designed angle. Charging is cancelled while preserving the cooldown if the target dies, becomes unattackable, leaves the current mode's range, leaves line of sight, the bow leaves the main hand, required ammunition becomes unavailable, NoAI is enabled, or shield-break vulnerability begins.

line uses linePower as a fixed initial speed and calculates a low trajectory from the target's latest position, velocity, and light gravity compensation. arc uses arcAngle as a fixed angle above horizontal, numerically simulates vanilla arrow drag of 0.99 and gravity of 0.05, binary-searches the required initial speed, and iteratively leads a moving target. If the required speed exceeds arcMaxPower or no complete solution exists, it still fires at arcMaxPower and may miss because of insufficient physical reach. Accuracy 1.0 adds no random spread.

Supported arrows in the off hand are preferred. With doConsume 0b, arrows are not consumed and a mob with no off-hand arrow uses unlimited normal arrows. With doConsume 1b, a supported off-hand arrow is required and one is consumed only after an arrow is actually released. Empty off-hand state is explicitly synchronized after the last arrow. The bow does not take additional durability damage.

While this skill is enabled and the mob keeps a bow in its main hand, all melee attacks are blocked. Inside the current mode's shooting range, melee navigation stops. Inside the corresponding SafeDistance, the mob retreats while aiming regardless of mobileWhileShooting. Arc retreat also applies after a target has crossed inside arcMinRange, allowing an arc-only shooter to try to reopen distance. Outside SafeDistance but still inside the mode range, mobileWhileShooting 1b enables skeleton-like randomized forward, backward, and sideways strafing; 0b keeps the shooter stationary.

Vanilla RangedBowAttackGoal draw, fire, and strafe writes are suppressed so skeletons cannot bypass the configured modes, chances, cooldowns, or mobileWhileShooting; PMB's own retreat and randomized movement commands remain permitted. The bow pose is forcibly reapplied at the end of model animation, preventing zombie attack animations, illager crossed arms, or other model-specific animations from replacing it. The special held-item layers used by vindicators and evokers also render the bow and arms.

<details>
<summary>parameters structure</summary>

```text
bow:
    {
        enable: <bool>,
        doConsume: <bool>,
        modePriority: <string>,
        mobileWhileShooting: <bool>,
        lineRange: <float>,
        lineSafeDistance: <float>,
        lineCooldownTicks: <int>,
        lineShootChance: <float>,
        lineShootAccuracy: <float>,
        lineChargeTicks: <int>,
        linePower: <float>,
        arcMinRange: <float>,
        arcMaxRange: <float>,
        arcSafeDistance: <float>,
        arcCooldownTicks: <int>,
        arcShootChance: <float>,
        arcShootAccuracy: <float>,
        arcChargeTicks: <int>,
        arcAngle: <float>,
        arcMaxPower: <float>
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

doConsume
Meaning: Whether releasing an arrow consumes one supported arrow from the off hand
Type: bool
Range: 0b, 1b
Default: 0b

modePriority
Meaning: Mode selected when line and arc can both be checked
Type: string
Range: "line", "arc"
Default: "line"
Note: Invalid values fall back to "line"

mobileWhileShooting
Meaning: Whether to use randomized forward, backward, and sideways strafing outside the safe distance but inside the current mode's range
Type: bool
Range: 0b, 1b
Default: 1b
Note: A target inside the safe distance always causes retreat regardless of this parameter

lineRange
Meaning: Maximum target distance at which line mode can begin a shooting check
Type: float
Range: [0.0f, 256.0f]
Default: 16.0f

lineSafeDistance
Meaning: Target distance below which line mode forces the shooter to retreat
Type: float
Range: [0.0f, 256.0f]
Default: 6.0f

lineCooldownTicks
Meaning: Cooldown after every line check, including failed checks, in game ticks
Type: integer
Range: [0, 72000]
Default: 40

lineShootChance
Meaning: Chance for a line check to succeed and begin drawing
Type: float
Range: [0.0f, 1.0f]
Default: 0.8f
Note: 0.0 completely disables line mode

lineShootAccuracy
Meaning: Accuracy of line arrows; higher values produce less random spread
Type: float
Range: [0.0f, 1.0f]
Default: 0.9f

lineChargeTicks
Meaning: Bow-drawing game ticks after a successful line check
Type: integer
Range: [0, 72000]
Default: 20

linePower
Meaning: Initial speed of line arrows, not real-world kinetic energy
Type: float
Range: [0.1f, 10.0f]
Default: 1.6f

arcMinRange
Meaning: Minimum target distance at which arc mode can begin a shooting check
Type: float
Range: [0.0f, 256.0f]
Default: 16.0f

arcMaxRange
Meaning: Maximum target distance for arc checks; values below arcMinRange are raised to arcMinRange
Type: float
Range: [arcMinRange, 256.0f]
Default: 48.0f

arcSafeDistance
Meaning: Target distance below which arc mode forces the shooter to retreat
Type: float
Range: [0.0f, 256.0f]
Default: 12.0f

arcCooldownTicks
Meaning: Cooldown after every arc check, including failed checks, in game ticks
Type: integer
Range: [0, 72000]
Default: 60

arcShootChance
Meaning: Chance for an arc check to succeed and begin drawing
Type: float
Range: [0.0f, 1.0f]
Default: 0.80f
Note: 0.0 completely disables arc mode

arcShootAccuracy
Meaning: Accuracy of arc arrows; higher values produce less random spread
Type: float
Range: [0.0f, 1.0f]
Default: 0.9f

arcChargeTicks
Meaning: Bow-drawing game ticks after a successful arc check
Type: integer
Range: [0, 72000]
Default: 20

arcAngle
Meaning: Fixed arc firing angle above horizontal, in degrees
Type: float
Range: [1.0f, 89.0f]
Default: 42.0f

arcMaxPower
Meaning: Maximum arrow initial speed available to the arc trajectory solver
Type: float
Range: [0.1f, 10.0f]
Default: 3.0f
Note: If this limit cannot reach the target, the arrow is still fired at the limit
```

Values outside these ranges are automatically clamped when entity data is read.
One second is normally equal to 20 game ticks.

</details>

<details>
<summary>command examples</summary>

```text
Summon a zombie holding a bow in its main hand and using default bow AI without requiring arrows.
summon zombie ~ ~ ~ {PmbAi:{bow:{enable:1b}}, equipment:{mainhand:{id:bow}}}

Summon a skeleton with 64 off-hand arrows, real arrow consumption, and customized line and arc modes.
summon skeleton ~ ~ ~ {PmbAi:{bow:{enable:1b, doConsume:1b, modePriority:"arc", mobileWhileShooting:1b, lineRange:14.0f, lineSafeDistance:6.0f, lineCooldownTicks:30, lineShootChance:0.8f, lineShootAccuracy:1.0f, lineChargeTicks:10, linePower:2.0f, arcMinRange:12.0f, arcMaxRange:64.0f, arcSafeDistance:10.0f, arcCooldownTicks:50, arcShootChance:0.6f, arcShootAccuracy:0.95f, arcChargeTicks:20, arcAngle:60.0f, arcMaxPower:3.5f}}, equipment:{mainhand:{id:bow}, offhand:{id:arrow,count:64}}}

Disable bow AI on the nearest skeleton without removing its other configured parameters.
data merge entity @e[type=skeleton,sort=nearest,limit=1] {PmbAi:{bow:{enable:0b}}}
```

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

For PMB-controlled shields, the blocking angle is resolved from the attacker's entity position when available, rather than only from the raw damage-source position. This avoids close-range or extended-interaction-range source positions flipping across the defender and causing apparent shield penetration.

If the server still sees the mob actively using its off-hand shield, PMB can continue recognizing the shield during the final transition tick even if the internal shield-use counter has just reached zero.

When the shield is raised, it takes durability damage from blocked attacks.

Attacks capable of disabling shields consume one point of shield toughness.

When the configured number of such attacks is reached during the current shield-use round, shield use stops and the forced cooldown begins.

The counter resets when the normal cooldown ends, before a new round after a forced cooldown, and immediately when the current shield use is interrupted without a true shield break.

Shield recoil, durability loss, and shield toughness consumption occur at most once per normal damage interval. Additional attacks ignored by the entity's damage cooldown do not accumulate these effects.

A fully blocked attack does not produce vanilla critical-hit particles, but the mod captures the player's vanilla critical-hit eligibility before attack charge is reset. A critical shield-disabling attack consumes critToughnessDamage points. If the shield remains intact, a critical shield-disabling attack plays the zombie wooden-door attack sound and emits 10 oak-door debris particles, a normal shield-disabling attack emits 6 particles, and any other blocked attack emits 3 particles. A true shield break instead plays the zombie wooden-door break sound, emits 24 particles, and applies extra shield-break knockback controlled by disableKBMultiplier.

After a true shield break, the mob enters shield-break vulnerability for disableVulnerTicks game ticks. During this window, the mob cannot attack, keeps clearing its target and navigation, and suppresses AI movement input, while existing velocity from knockback or other external forces is preserved. It renders with the same shaking style used by zombie-villager curing or piglin zombification. This does not enable NoAI, so normal physics such as gravity continue to apply. Damage received during this window is multiplied by vulnerDamageMultiplier. If disableVulnerTicks is 0, this vulnerability window is skipped.

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
        critToughnessDamage: <int>,
        disableVulnerTicks: <int>,
        vulnerDamageMultiplier: <float>,
        disableKBMultiplier: <float>,
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
Refresh: Resets after the normal cooldown ends, before a new round after a forced cooldown, and when the current shield use is interrupted without a true shield break

critToughnessDamage
Meaning: Shield toughness consumed by a shield-disabling attack that meets the vanilla player critical-hit conditions
Type: integer
Range: [1, 100]
Default: 1
Note: Normal shield-disabling attacks always consume 1 point; attacks that cannot disable shields consume none

disableVulnerTicks
Meaning: Duration of the shield-break vulnerability window after a true shield break, in game ticks
Type: integer
Range: [0, 72000]
Default: 40
Note: 0 skips the vulnerability window

vulnerDamageMultiplier
Meaning: Damage multiplier applied while the mob is in shield-break vulnerability
Type: float
Range: [1.0f, 100.0f]
Default: 1.5f
Note: 1.5 means the mob receives double damage during shield-break vulnerability

disableKBMultiplier
Meaning: Multiplier for the extra knockback applied by the final hit that truly breaks the shield
Type: float
Range: [0.0f, 100.0f]
Default: 1.2f
Note: 0.0 disables the extra shield-break knockback; the applied base strength is 0.6 multiplied by this value

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
It can always start to raise its shield within 12 blocks, uses it for 4–8 seconds, has a 3-second normal cooldown, can block attacks from any direction, has 3 shield toughness, takes 2 toughness damage from a critical shield-disabling attack, enters 2 seconds of shield-break vulnerability with double incoming damage after a true shield break, applies 1.5x extra shield-break knockback, has a 2.5-second forced cooldown after its shield is disabled, and loses 90% of its movement speed while shielding.
(This command is long; use a command block.)
summon zombie ~ ~ ~ {PmbAi:{shield:{enable:1b, range:12.0f, shieldChance:1.0f, minUseTicks:80, maxUseTicks:160, cooldownTicks:60, blockingAngle:180.0f, axeDisableCooldownTicks:50, shieldToughness:3, critToughnessDamage:2, disableVulnerTicks:40, vulnerDamageMultiplier:2.0f, disableKBMultiplier:1.5f, speedReduction:0.9f}}, equipment:{mainhand:{id:diamond_sword}, offhand:{id:shield}, head:{id:diamond_helmet}, chest:{id:diamond_chestplate}, legs:{id:diamond_leggings}, feet:{id:diamond_boots}}}

Enable shield usage with default parameters on the nearest zombie that already has a shield in its off hand.
data merge entity @e[type=zombie,sort=nearest,limit=1] {PmbAi:{shield:{enable:1b}}}

Disable shield usage without removing its other configured parameters.
data merge entity @e[type=zombie,sort=nearest,limit=1] {PmbAi:{shield:{enable:0b}}}
```

</details>
