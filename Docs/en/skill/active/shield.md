# shield (`skill.active.shield`)

Complete default configuration (optional source lists are explained below):

```text
shield:{FetchSource:["offhand"],preferredHand:"off",enable:0b,range:8.0f,shieldChance:0.35f,minUseTicks:80,maxUseTicks:160,cooldownTicks:40,randomCooldownBias:20,blockingAngle:30.0f,axeDisableCooldownTicks:100,shieldToughness:1,critToughnessDamage:1,disableVulnerTicks:40,vulnerDamageMultiplier:2.0f,disableKBMultiplier:1.2f,speedReduction:0.5f}
```

Basic shield usage is currently implemented.
An eligible mob repeatedly checks whether it can raise its shield, with a configurable chance of success.

<details>
<summary>activation, blocking, and shield-break details</summary>

Conditions for raising a shield:

1. The entity is a mob and has a vanilla shield in an allowed activation source. A held shield is used directly in its actual hand; an inventory shield is temporarily placed in the off hand.
2. enable is true.
3. The mob has a living hostile target within range and has line of sight to it.
4. The mob is not in the normal shield cooldown.
5. The mob is not in the forced cooldown caused by shield-disabling attacks.
6. The shieldChance check succeeds.
7. The mob does not have NoAI enabled.

PMB-controlled shields can block from the first shield-use tick and do not use the vanilla player's initial shield activation delay.

For PMB-controlled shields, the blocking angle is resolved from the attacker's entity position when available, rather than only from the raw damage-source position. This avoids close-range or extended-interaction-range source positions flipping across the defender and causing apparent shield penetration.

If the server still sees the mob actively using its shield, PMB can continue recognizing the actual used hand during the final transition tick even if the internal shield-use counter has just reached zero. An ActionBinding validates the actual used-hand item, including authorized shield durability changes or breakage. Ending use releases the binding and retains the final equipment layout.

When the shield is raised, it takes durability damage from blocked attacks.

Attacks capable of disabling shields consume one point of shield toughness.

When the configured number of such attacks is reached during the current shield-use round, shield use stops and the forced cooldown begins.

The counter resets when the normal cooldown ends, before a new round after a forced cooldown, and immediately when the current shield use is interrupted without a true shield break.

Shield recoil, durability loss, and shield toughness consumption occur at most once per normal damage interval. Additional attacks ignored by the entity's damage cooldown do not accumulate these effects.

A fully blocked attack does not produce vanilla critical-hit particles, but the mod captures the player's vanilla critical-hit eligibility before attack charge is reset. A critical shield-disabling attack consumes critToughnessDamage points.

If the shield remains intact, a critical shield-disabling attack plays the zombie wooden-door attack sound and emits 10 oak-door debris particles. A normal shield-disabling attack emits 6 particles; any other blocked attack emits 3 particles.

A true shield break instead plays the zombie wooden-door break sound, emits 24 particles, and applies extra shield-break knockback controlled by disableKBMultiplier.

After a true shield break, the mob enters shield-break vulnerability for disableVulnerTicks game ticks. During this window, the mob cannot attack, keeps clearing its target and navigation, and suppresses AI movement input, while existing velocity from knockback or other external forces is preserved. It renders with the same shaking style used by zombie-villager curing or piglin zombification. This does not enable NoAI, so normal physics such as gravity continue to apply. Damage received during this window is multiplied by vulnerDamageMultiplier. If disableVulnerTicks is 0, this vulnerability window is skipped.

Entities from the Guard Villagers namespace are excluded from this behaviour to avoid conflicting with that mod.

</details>

<details>
<summary>parameters structure</summary>

```text
shield:
    {
        enable: <bool>,
        FetchSource: [<string>, ...],
        preferredHand: <string>,
        range: <float>,
        shieldChance: <float>,
        minUseTicks: <int>,
        maxUseTicks: <int>,
        cooldownTicks: <int>,
        randomCooldownBias: <int>,
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

FetchSource
Meaning: Ordered locations allowed to provide this skill's required item
Type: list of strings
Range: mainhand, offhand, inventory, inventory:1 through inventory:256, or inclusive ranges such as inventory:1..9
Default: omitted; uses this skill's legacy source-hand order
Note: Used only when the destination hand lacks a suitable item; empty/invalid lists supply no fetch location

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
Default: 2.0f
Note: 2.0 means the mob receives double damage during shield-break vulnerability; entities that explicitly saved another value are not migrated to the new default

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

randomCooldownBias
Meaning: Inclusive uniform random extra game ticks added whenever a shield-check cooldown starts
Type: integer
Range: [0, 1200]
Default: 20
Note: Does not affect axeDisableCooldownTicks or disableVulnerTicks; 0 makes check cooldown fixed

preferredHand
Type: string
Range: main, off, main-enforce, off-enforce
Default: "off"
Meaning: Selects the use hand at action startup; plain values fall back only for ongoing PMB hand ownership, enforce values never fall back
```

Values outside these ranges are automatically clamped when the entity data is read.
One second is normally equal to 20 game ticks.

</details>

<details>
<summary>command examples</summary>

```text
Summon a zombie with an iron sword, a shield, and an iron helmet. Shield usage is enabled and all omitted parameters use their default values.
summon zombie ~ ~ ~ {PmbAi:{skill:{active:{shield:{enable:1b}}}}, equipment:{mainhand:{id:iron_sword}, offhand:{id:shield}, head:{id:iron_helmet}}}

Summon a heavily armoured zombie with a diamond sword and shield.
It can always start to raise its shield within 12 blocks, uses it for 4–8 seconds, has a 3-second normal cooldown, can block attacks from any direction, has 3 shield toughness, takes 2 toughness damage from a critical shield-disabling attack, enters 2 seconds of shield-break vulnerability with double incoming damage after a true shield break, applies 1.5x extra shield-break knockback, has a 2.5-second forced cooldown after its shield is disabled, and loses 90% of its movement speed while shielding.
(This command is long; use a command block.)
summon zombie ~ ~ ~ {PmbAi:{skill:{active:{shield:{enable:1b, range:12.0f, shieldChance:1.0f, minUseTicks:80, maxUseTicks:160, cooldownTicks:60, blockingAngle:180.0f, axeDisableCooldownTicks:50, shieldToughness:3, critToughnessDamage:2, disableVulnerTicks:40, vulnerDamageMultiplier:2.0f, disableKBMultiplier:1.5f, speedReduction:0.9f}}}}, equipment:{mainhand:{id:diamond_sword}, offhand:{id:shield}, head:{id:diamond_helmet}, chest:{id:diamond_chestplate}, legs:{id:diamond_leggings}, feet:{id:diamond_boots}}}

Enable shield usage with default parameters on the nearest zombie that already has a shield in its off hand.
data merge entity @e[type=zombie,sort=nearest,limit=1] {PmbAi:{skill:{active:{shield:{enable:1b}}}}}

Disable shield usage without removing its other configured parameters.
data merge entity @e[type=zombie,sort=nearest,limit=1] {PmbAi:{skill:{active:{shield:{enable:0b}}}}}
```

</details>


<details><summary>Quick enable with the modular command</summary>

```mcfunction
/pmb skill active @n[type=minecraft:zombie] shield append [enable=1b]
/pmb skill active @n[type=minecraft:zombie] shield get
/pmb skill active @n[type=minecraft:zombie] shield modify [enable=0b]
```

Append requires an absent skill node. For an existing node use modify when enable is explicit, or insert when it has not been explicitly written.

</details>

[Back to skills](../README.md)
