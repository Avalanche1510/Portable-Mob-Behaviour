# bow (`skill.active.bow`)

Complete default configuration (optional source lists are explained below):

```text
bow:{FetchSource:["mainhand"],preferredHand:"main",enable:0b,doConsume:0b,requireEyeSight:1b,modePriority:"line",mobileWhileShooting:1b,lineMinRange:0.0f,lineMaxRange:16.0f,lineSafeDistance:6.0f,lineCooldownTicks:40,lineShootChance:0.8f,lineShootAccuracy:0.9f,lineChargeTicks:20,linePower:1.0f,arcMinRange:16.0f,arcMaxRange:48.0f,arcSafeDistance:12.0f,arcCooldownTicks:60,randomCooldownBias:20,arcShootChance:0.8f,arcShootAccuracy:0.9f,arcChargeTicks:20,arcAngle:42.0f,arcMaxPower:1.0f}
```

Bow AI provides low-trajectory `line` and high-angle `arc` modes. It selects its use hand through preferredHand, defaulting to main; range controls check eligibility while power and vanilla physics determine actual reach.

<details>
<summary>activation, ammunition, movement, and rendering details</summary>

Both modes require a living, attackable target. With requireEyeSight 1b, that target must also be visible; 0b skips only this visibility requirement. For an original vanilla TargetGoal only, 0b may also retain its already acquired mob.getTarget() through obstruction when it is alive, in the same level, inside FOLLOW_RANGE and an enabled line or arc shooting range, and a bow is available under the destination-hand and FetchSource rules. It never changes initial target discovery.

This TargetGoal-only rule does not affect Faction or Brain targeting, any evasive path including pearl evasion, general melee, or other skills. line can be checked from lineMinRange through lineMaxRange. arc can be checked from arcMinRange through arcMaxRange. Setting either ShootChance to 0.0 completely disables that mode.

When both modes satisfy their distance, ammunition, and cooldown conditions, modePriority selects line or arc. A failed chance check by the priority mode does not fall back to the other mode during the same tick. If the priority mode is still cooling down or otherwise not ready, the other mode may be checked. Only after priority and resource admission does the selected mode roll chance and start CooldownTicks, whether the roll succeeds or fails. A suppressed candidate does not spend this cooldown.

After a successful chance check, the mob draws the resolved bow from its current hand for the corresponding ChargeTicks. A value of 0 releases during the same tick. While drawing, its head and both arms continuously follow the actual calculated launch vector, so line displays low-trajectory aiming and arc visibly raises the bow to its designed angle.

Charging is cancelled while preserving the cooldown if the target dies, becomes unattackable, leaves the current mode's range, loses line of sight while requireEyeSight is 1b, the bow leaves its current use hand, required ammunition becomes unavailable, NoAI is enabled, or shield-break vulnerability begins.

linePower and arcMaxPower are multipliers of the vanilla player's fully drawn bow initial speed of 3.0, not absolute initial speeds. line uses 3.0 times linePower as its fixed initial speed and calculates a low trajectory from the target's latest position, velocity, and light gravity compensation.

arc uses arcAngle as a fixed angle above horizontal, numerically simulates vanilla arrow drag of 0.99 and gravity of 0.05, binary-searches the required initial speed, and iteratively leads a moving target. If the required speed exceeds 3.0 times arcMaxPower or no complete solution exists, it still fires at that speed limit and may miss because of insufficient physical reach. Accuracy 1.0 adds no random spread.

`AmmoSource` independently limits where bow AI reads real supported arrows. It uses the same ordered source syntax as FetchSource, but never moves or exchanges ammunition. When omitted, supported arrows in either hand are preferred, followed by enabled PmbInventory slots from low to high. An explicit list scans only its written locations in order; a present empty or invalid list has no real ammunition source.

When a bow exchange displaces a previously selected hand arrow, that arrow follows its stack into the other hand or inventory slot for this action only. Subsequent actions scan AmmoSource again at the actual new layout. No separate ammunition exchange or long-lived source exemption is created.

With doConsume 0b, ammunition is not consumed and no real arrow falls back to unlimited normal arrows. With doConsume 1b, a real supported arrow is required and one is consumed from its actual source only after release. The bow does not take additional durability damage. FetchSource only resolves the bow itself and never restricts this ammunition lookup.

Ordinary melee is suppressed by a lightweight live predicate only when bow is enabled; the current target is non-null, alive, in the same level and attackable; a bow is already held in an allowed hand; the target distance is inside at least one line or arc closed interval whose ShootChance is greater than zero; and, when doConsume is 1b, AmmoSource still resolves a supported arrow.

Soft main/off preferences accept a bow already held in either hand, while main-enforce and off-enforce require their named hand. Inventory bows, FetchSource availability and action bindings do not count as held bows. Cooldowns, the current random roll, resource claims, line of sight/requireEyeSight, modePriority, SafeDistance, mobileWhileShooting and FOLLOW_RANGE do not participate in this predicate.

When any required live condition becomes false, vanilla melee Goal navigation and ordinary direct melee damage are immediately available again. FetchSource remains responsible for starting the skill and exchanging the bow; it does not independently suppress melee. The K-key report uses this same predicate for its `bow` suppression reason.

A scheduler-selected PMB mace smash remains allowed, so a valid bow suppression state does not cancel a legal falling-mace combination. Bow movement takes control only after a successful check has acquired the required action resources, bound the bow and begun drawing.

During that draw, entering the corresponding SafeDistance makes the mob retreat while aiming regardless of mobileWhileShooting. Outside SafeDistance but still inside the mode range, mobileWhileShooting 1b enables skeleton-like randomized forward, backward, and sideways strafing; 0b keeps the shooter stationary.

Cooldown, a failed chance check, missing ammunition or an action-resource conflict submits no bow locomotion and no longer intercepts vanilla navigation or movement control. Whether the mob actually pursues still depends on an applicable vanilla Goal or Brain behavior remaining able to run. Vanilla RangedBowAttackGoal suppression remains broader and unchanged so skeletons cannot bypass PMB firing rules.

Vanilla RangedBowAttackGoal draw, fire, and strafe writes are suppressed so skeletons cannot bypass the configured modes, chances, cooldowns, or mobileWhileShooting; PMB's own retreat and randomized movement commands remain permitted. The bow pose is forcibly reapplied at the end of model animation, preventing zombie attack animations, illager crossed arms, or other model-specific animations from replacing it. The special held-item layers used by vindicators and evokers also render the bow and arms.

</details>

<details>
<summary>parameters structure</summary>

```text
bow:
    {
        enable: <bool>,
        FetchSource: [<string>, ...],
        preferredHand: <string>,
        AmmoSource: [<string>, ...],
        doConsume: <bool>,
        requireEyeSight: <bool>,
        modePriority: <string>,
        mobileWhileShooting: <bool>,
        lineMinRange: <float>,
        lineMaxRange: <float>,
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
        arcMaxPower: <float>,
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

AmmoSource
Meaning: Ordered locations from which bow AI may read real supported arrows
Type: list of strings
Range: mainhand, offhand, inventory, inventory:1 through inventory:256, or inclusive ranges such as inventory:1..9
Default: omitted; supported arrows in either hand first, then enabled PmbInventory slots from low to high
Note: It only scans ammunition and never exchanges it. An explicit empty or invalid list supplies no real arrow; FetchSource does not constrain ammunition

doConsume
Meaning: Whether releasing an arrow consumes one supported arrow from its real hand or PmbInventory source
Type: bool
Range: 0b, 1b
Default: 0b

requireEyeSight
Meaning: Whether bow checks and an active bow charge require line of sight to the target
Type: bool
Range: 0b, 1b
Default: 1b
Note: 0b skips only these line-of-sight gates; target validation, ranges, cooldowns, chance checks, items, and all other behavior remain unchanged

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

lineMinRange
Meaning: Minimum target distance at which line mode can begin a shooting check
Type: float
Range: [0.0f, 256.0f]
Default: 0.0f

lineMaxRange
Meaning: Maximum target distance at which line mode can begin a shooting check
Type: float
Range: [lineMinRange, 256.0f]
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
Meaning: Fixed line initial-speed multiplier relative to the vanilla player's fully drawn bow speed of 3.0
Type: float
Range: [0.1f, 10.0f]
Default: 1.0f
Note: 1.0 is 100% vanilla full-draw speed, producing an actual initial speed of 3.0; the range represents 10% through 1000%

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
Meaning: Arc trajectory-solver speed-limit multiplier relative to the vanilla player's fully drawn bow speed of 3.0
Type: float
Range: [0.1f, 10.0f]
Default: 1.0f
Note: 1.0 gives a speed limit of 3.0; if this limit cannot reach the target, the arrow is still fired at the limit

randomCooldownBias
Meaning: Inclusive uniform random extra game ticks added whenever either mode starts its check cooldown
Type: integer
Range: [0, 1200]
Default: 20
Note: line and arc draw independently; 0 makes both cooldowns fixed

preferredHand
Type: string
Range: main, off, main-enforce, off-enforce
Default: "main"
Meaning: Selects the use hand at action startup; plain values fall back only for ongoing PMB hand ownership, enforce values never fall back
```

Values outside these ranges are automatically clamped when entity data is read.
One second is normally equal to 20 game ticks.

</details>

<details>
<summary>command examples</summary>

```text
Summon a zombie holding a bow in its main hand and using default bow AI without requiring arrows.
summon zombie ~ ~ ~ {PmbAi:{skill:{active:{bow:{enable:1b}}}}, equipment:{mainhand:{id:bow}}}

Summon a skeleton with 64 off-hand arrows, real arrow consumption, and customized line and arc modes.
summon skeleton ~ ~ ~ {PmbAi:{skill:{active:{bow:{enable:1b, doConsume:1b, modePriority:"arc", mobileWhileShooting:1b, lineMinRange:0.0f, lineMaxRange:14.0f, lineSafeDistance:6.0f, lineCooldownTicks:30, lineShootChance:0.8f, lineShootAccuracy:1.0f, lineChargeTicks:10, linePower:0.8f, arcMinRange:12.0f, arcMaxRange:64.0f, arcSafeDistance:10.0f, arcShootChance:0.6f, arcShootAccuracy:0.95f, arcChargeTicks:20, arcAngle:60.0f, arcMaxPower:1.25f}}}}, equipment:{mainhand:{id:bow}, offhand:{id:arrow,count:64}}}

Disable bow AI on the nearest skeleton without removing its other configured parameters.
data merge entity @e[type=skeleton,sort=nearest,limit=1] {PmbAi:{skill:{active:{bow:{enable:0b}}}}}
```

</details>


`AmmoSource` is optional: omission searches `["mainhand","offhand","inventory"]`; explicit `[]` supplies no real ammunition, while `doConsume:0b` may still use unlimited normal arrows. It is independent of `FetchSource` and never exchanges ammunition.


<details><summary>Quick enable with the modular command</summary>

```mcfunction
/pmb skill active @n[type=minecraft:zombie] bow append [enable=1b]
/pmb skill active @n[type=minecraft:zombie] bow get
/pmb skill active @n[type=minecraft:zombie] bow modify [enable=0b]
```

Append requires an absent skill node. For an existing node use modify when enable is explicit, or insert when it has not been explicitly written.

</details>

[Back to skills](../README.md)
