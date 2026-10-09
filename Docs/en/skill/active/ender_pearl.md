# ender pearl (`skill.active.ender_pearl`)

Complete default configuration (optional source lists are explained below):

```text
ender_pearl:{FetchSource:["mainhand","offhand"],preferredHand:"off",enable:0b,minThrowRange:4.0f,maxThrowRange:16.0f,throwChance:0.35f,throwCooldownTicks:40,throwAccuracy:0.9f,maxThrowPower:1.0f,throwAngle:30.0f,doConsume:0b,doTeleportDamage:0b,requireEyeSight:1b,randomCooldownBias:20}
```

Ender-pearl AI calculates a fixed-angle throw for a held vanilla pearl. Combat leads the attack target; Faction evasion instead selects an escape destination away from its threat.

<details>
<summary>activation, trajectory, and evasion details</summary>

Conditions for a throw check:

1. The entity is a mob and holds a vanilla ender pearl in either hand.
2. enable is true and throwChance is greater than 0.0.
3. The mob has a living attack target, or it is currently executing Faction evasion with a valid avoid threat.
4. With requireEyeSight 1b, an ordinary attack target must be visible and between minThrowRange and maxThrowRange; a Faction avoid threat must be visible and no farther than maxThrowRange. With 0b, only this visibility requirement is skipped. For an original vanilla TargetGoal only, 0b may also retain its already acquired mob.getTarget() through obstruction when it is alive, in the same level, inside FOLLOW_RANGE and this combat throw range, throwChance is greater than 0.0, and FetchSource can resolve a pearl to either hand. It never changes initial target discovery.
5. The skill is not in the throw-check cooldown defined by throwCooldownTicks.
6. The mob does not have NoAI enabled and is not in shield-break vulnerability.

Every admitted check immediately enters throwCooldownTicks cooldown before rolling throwChance, so a failed chance roll also starts cooldown. A value of 0 game ticks permits another check on the next game tick. On success, the mob faces the actual launch trajectory, swings the hand holding the pearl, and plays the vanilla ender-pearl throw sound.

maxThrowPower is not an absolute initial speed. It limits solved speed as a multiplier of the vanilla stationary-player ender-pearl launch speed of 1.5. The default 1.0 allows speeds up to 1.5, while 2.0 allows up to 3.0. throwAngle fixes the launch angle above horizontal and defaults to 30 degrees.

The solver uses the discrete vanilla air trajectory with drag 0.99 and gravity 0.03, binary-searches the required speed at that fixed angle, and iteratively leads horizontal target movement while preserving its current eye height. If the geometry is valid but 1.5 times maxThrowPower is insufficient, it still launches at that limit and may fall short.

Impossible fixed-angle geometry, near-zero horizontal distance, or an invalid numerical solution skips the throw without consuming a pearl; the normal check cooldown remains. The calculation has no 300-tick trajectory cutoff.

Pearls are not thrown while their launch point (0.1 blocks below eye height) is submerged in water. Shallow water remains usable when that point is above the actual fluid surface. This check applies both before submitting a throw and immediately before release, in combat and evasive mode.

minThrowRange and maxThrowRange define the ordinary combat throw-check interval, preventing repeated pearl use after a target gets too close. They do not guarantee that the configured maxThrowPower and throwAngle physically cover the whole interval.

Faction evasion ignores minThrowRange: with requireEyeSight 1b, a check is allowed whenever the avoid threat is visible and no farther than maxThrowRange; 0b skips only that visibility requirement. The pearl is neither thrown toward that threat nor used to write an attack target.

Its destination prefers the current reachable escape-navigation target that is farther from the threat and is clamped to maxThrowRange; without a valid navigation target, it uses a stable directly-away position. Pearl impact continues through vanilla ender-pearl teleportation, collision, and damage handling.

throwAccuracy 1.0 adds no random spread; lower values add progressively more spread. With doConsume 0b, the held pearl only acts as required equipment and is not consumed. With doConsume 1b, one pearl is consumed from the hand that actually swings after a successful release, and the final pearl explicitly synchronizes an empty hand. Range and chance checks never consume an item early.

</details>

<details>
<summary>parameters structure</summary>

```text
ender_pearl:
    {
        enable: <bool>,
        FetchSource: [<string>, ...],
        preferredHand: <string>,
        minThrowRange: <float>,
        maxThrowRange: <float>,
        throwChance: <float>,
        throwCooldownTicks: <int>,
        throwAccuracy: <float>,
        maxThrowPower: <float>,
        throwAngle: <float>,
        doTeleportDamage: <bool>,
        doConsume: <bool>,
        requireEyeSight: <bool>,
        randomCooldownBias: <int>
    }
```

</details>

<details>
<summary>details of parameters</summary>

```text
doTeleportDamage
Meaning: Apply 5 points of vanilla ender-pearl damage after successful teleportation. Feather Falling and applicable vanilla protection reduce it; a failed teleport causes no teleport damage.
Type: bool
Range: 0b, 1b
Default: 0b

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

minThrowRange
Meaning: Minimum ordinary-combat target distance that can start an ender-pearl throw check
Type: float
Range: [0.0f, 256.0f]
Default: 4.0f
Note: Faction evasive checks ignore this lower bound

maxThrowRange
Meaning: Maximum target or avoid-threat distance that can start a pearl throw check; a Faction escape destination is also limited to this distance
Type: float
Range: [minThrowRange, 256.0f]
Default: 16.0f
Note: Controls AI activation only and does not guarantee sufficient physical range at the current speed; values below minThrowRange are clamped to minThrowRange

throwChance
Meaning: Chance for each throw check to succeed and release a pearl
Type: float
Range: [0.0f, 1.0f]
Default: 0.35f
Note: 0.0 fully disables throw checks

throwCooldownTicks
Meaning: Game ticks of cooldown after every throw check, whether the chance succeeds or fails
Type: integer
Range: [0, 72000]
Default: 40
Note: 0 allows another check on the next game tick

throwAccuracy
Meaning: Ender-pearl throw accuracy; higher values produce less random spread
Type: float
Range: [0.0f, 1.0f]
Default: 0.9f
Note: 1.0 adds no random spread

maxThrowPower
Meaning: Ender-pearl trajectory-solver speed limit as a multiplier of the vanilla stationary-player speed of 1.5
Type: float
Range: [0.1f, 10.0f]
Default: 1.0f
Note: 1.0 means maximum speed 1.5; the solver may use a lower speed up to this limit

throwAngle
Meaning: Fixed ender-pearl launch angle above horizontal
Type: float
Range: [1.0f, 89.0f]
Default: 30.0f
Note: The solver keeps this angle fixed and varies initial speed

doConsume
Meaning: Whether a successful throw consumes one ender pearl
Type: bool
Range: 0b, 1b
Default: 0b
Note: Consumes from the hand that actually swings while holding the pearl

requireEyeSight
Meaning: Whether an ender-pearl throw check requires line of sight to its attack target or Faction avoid threat
Type: bool
Range: 0b, 1b
Default: 1b
Note: 0b skips only this line-of-sight gate; target validation, ranges, cooldown, chance, items, and all other behavior remain unchanged

randomCooldownBias
Meaning: Inclusive uniform random extra game ticks added whenever a throw-check cooldown starts
Type: integer
Range: [0, 1200]
Default: 20
Note: 0 makes throw cooldown fixed

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
Summon a zombie holding an ender pearl in its off hand and using all default ender-pearl parameters.
summon zombie ~ ~ ~ {PmbAi:{skill:{active:{ender_pearl:{enable:1b}}}}, equipment:{mainhand:{id:iron_sword}, offhand:{id:ender_pearl}}}

Summon a zombie that throws accurately from 6 through 32 blocks at a 30-degree angle, allows up to twice vanilla speed, and consumes pearls.
summon zombie ~ ~ ~ {PmbAi:{skill:{active:{ender_pearl:{enable:1b, minThrowRange:6.0f, maxThrowRange:32.0f, throwChance:1.0f, throwCooldownTicks:60, throwAccuracy:1.0f, maxThrowPower:2.0f, throwAngle:30.0f, doConsume:1b}}}}, equipment:{mainhand:{id:ender_pearl,count:16}}}

Summon a vindicator whose pearl skill can be driven by a Faction evasive relationship toward an escape position.
summon vindicator ~ ~ ~ {PmbAi:{skill:{active:{ender_pearl:{enable:1b, minThrowRange:8.0f, maxThrowRange:24.0f, throwChance:1.0f}}}}, equipment:{offhand:{id:ender_pearl}}}

Disable ender-pearl AI on the nearest zombie without removing its other configured parameters.
data merge entity @e[type=zombie,sort=nearest,limit=1] {PmbAi:{skill:{active:{ender_pearl:{enable:0b}}}}}
```

</details>


<details><summary>Quick enable with the modular command</summary>

```mcfunction
/pmb skill active @n[type=minecraft:zombie] ender_pearl append [enable=1b]
/pmb skill active @n[type=minecraft:zombie] ender_pearl get
/pmb skill active @n[type=minecraft:zombie] ender_pearl modify [enable=0b]
```

Append requires an absent skill node. For an existing node use modify when enable is explicit, or insert when it has not been explicitly written.

</details>

[Back to skills](../README.md)
