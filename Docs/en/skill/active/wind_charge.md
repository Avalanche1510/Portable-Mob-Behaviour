# wind charge (`skill.active.wind_charge`)

Complete default configuration (optional source lists are explained below):

```text
wind_charge:{FetchSource:["mainhand","offhand"],preferredHand:"off",enable:0b,throwRange:16.0f,throwChance:0.35f,throwCooldownTicks:40,throwAccuracy:0.9f,bounceRange:4.0f,bounceChance:0.35f,bounceCooldownTicks:40,bouncePostJumpTicks:2,randomCooldownBias:20,doConsume:0b}
```

The wind-charge AI gives mobs holding a vanilla wind charge two modes: throwing and self-bouncing. Bounce is preferred at close range; throw is used at range. `pathfinding.air_tracking` handles its post-bounce movement.

<details>
<summary>activation, behaviour, and compatibility</summary>

Conditions for wind-charge use:

1. The entity is a mob and holds a vanilla wind charge in either hand.
2. enable is true.
3. The mob has a living hostile target, or it is currently fleeing through Faction passively_evasive/actively_evasive with a valid threat target.
4. The mob does not have NoAI enabled and is not in shield-break vulnerability.
5. The distance, cooldown, and chance checks for the corresponding mode succeed.

Bounce mode takes priority when both modes can be checked. When the mob is on the ground and its target is within bounceRange, it checks bounceChance every bounceCooldownTicks game ticks. On success, the default `bouncePostJumpTicks:2` starts a ground jump, then releases beneath the mob's current feet two ticks later. `0` retains the former same-tick jump and release. Preparation holds the chosen hand(s) and LOOK; an invalid target/item, early landing, NoAI, death, Weakness, or a successful higher-priority action cancels release without refunding the attempt cooldown. Item exchange and consumption occur only at release. An already-airborne mob does not start or cache a bounce.

When a visible target is within throwRange, throw mode checks throwChance every throwCooldownTicks game ticks. On success, the mob swings the hand holding the wind charge and throws at a lead point calculated from the target's position and velocity. throwAccuracy controls vanilla projectile spread; 1.0 means no random spread.

During Faction evasion, wind-charge AI uses the current Faction avoid target as its wind-charge target without restoring it as an attack target. On release, throw mode briefly turns the view, head, and body toward the pursuing threat while leaving escape navigation intact, then resumes fleeing. Bounce mode still uses bounceRange, bounceChance, and bounceCooldownTicks for its downward launch, but airborne steering and body facing point away from the threat. Both modes continue to obey all existing range, chance, cooldown, accuracy, and doConsume settings; evasion never bypasses those checks.

doConsume controls consumption for both throw and bounce mode. By default, the held wind charge only acts as the skill's required equipment and is not consumed. When enabled, every successful throw or bounce consumes one wind charge from the hand that actually performed the action.

The downward bounce uses the vanilla living-entity impulse fall-protection context. Returning to the height where the wind-charge impulse occurred does not deal fall damage from that launch. If the mob lands below the launch point, only the additional drop below that height can still deal normal fall damage. Once the mob has actually left the ground after this bounce and lands again, the server plays the vanilla player's no-damage landing sound once. Never leaving the ground, dying, or using throw mode alone does not trigger it.

Airborne follow-up after a wind bounce is controlled by `pathfinding.air_tracking`, not wind-charge fields. Consuming the final wind charge does not cancel an already-latched session.

The PMB impulse session owns LOOK for the landing tick and releases it next tick. Protection is registered only after the actual self-bounce explosion affects the mob, then cleared on landing; an externally replaced impulse context is preserved.

</details>

<details>
<summary>parameters structure</summary>

```text
wind_charge:
    {
        enable: <bool>,
        FetchSource: [<string>, ...],
        preferredHand: <string>,
        doConsume: <bool>,
        throwRange: <float>,
        throwChance: <float>,
        throwCooldownTicks: <int>,
        throwAccuracy: <float>,
        bounceRange: <float>,
        bounceChance: <float>,
        bounceCooldownTicks: <int>,
        bouncePostJumpTicks: <int>,
        randomCooldownBias: <int>
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

doConsume
Meaning: Whether a successful throw or downward self-bounce consumes one wind charge
Type: bool
Range: 0b, 1b
Default: 0b
Note: Controls both modes and consumes from the hand actually holding the wind charge when the action triggers

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

bouncePostJumpTicks
Meaning: Ticks between a successful ground jump and downward bounce release; 0 jumps and fires in the same tick
Type: integer
Range: [0, 10]
Default: 2
Note: This does not delay long-range throw mode

randomCooldownBias
Meaning: Inclusive uniform random extra game ticks added whenever either mode starts its check cooldown
Type: integer
Range: [0, 1200]
Default: 20
Note: throw and bounce draw independently; 0 makes both cooldowns fixed

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
Summon a zombie holding a wind charge in its off hand and using all default wind-charge parameters.
summon zombie ~ ~ ~ {PmbAi:{skill:{active:{wind_charge:{enable:1b}}}}, equipment:{mainhand:{id:iron_sword}, offhand:{id:wind_charge}}}

Summon a zombie that throws perfectly accurate wind charges and always uses a wind-charge bounce to pursue nearby targets. Each successful use consumes one wind charge.
summon zombie ~ ~ ~ {PmbAi:{skill:{active:{wind_charge:{enable:1b, doConsume:1b, throwRange:24.0f, throwChance:1.0f, throwCooldownTicks:40, throwAccuracy:1.0f, bounceRange:6.0f, bounceChance:1.0f, bounceCooldownTicks:60}}},pathfinding:{air_tracking:{trackAcceleration:0.012f,trackMaxHorizontalSpeed:0.3f,trackDurationTicks:-1}}}, equipment:{mainhand:{id:wind_charge,count:64}}}

Disable wind-charge AI on the nearest zombie without removing its other configured parameters.
data merge entity @e[type=zombie,sort=nearest,limit=1] {PmbAi:{skill:{active:{wind_charge:{enable:0b}}}}}
```

</details>


<details><summary>Quick enable with the modular command</summary>

```mcfunction
/pmb skill active @n[type=minecraft:zombie] wind_charge append [enable=1b]
/pmb skill active @n[type=minecraft:zombie] wind_charge get
/pmb skill active @n[type=minecraft:zombie] wind_charge modify [enable=0b]
```

Append requires an absent skill node. For an existing node use modify when enable is explicit, or insert when it has not been explicitly written.

</details>

[Back to skills](../README.md)
