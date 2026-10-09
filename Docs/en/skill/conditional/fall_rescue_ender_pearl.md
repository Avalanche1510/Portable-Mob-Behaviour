# Pearl rescue (`skill.conditional.fall_rescue.ender_pearl`)

Complete default method configuration. The `fall_rescue` parent is disabled by default; the pearl method is enabled by default. The parent, method, and current strategy priority must all allow the action.

```text
fall_rescue:{enable:0b,methods:["wind_charge","ender_pearl"],ender_pearl:{enable:1b,FetchSource:["offhand","mainhand"],preferredHand:"off",doConsume:0b,doTeleportDamage:0b,successChance:1.0f,cooldownTicks:20,randomCooldownBias:0,rescueLeadTicks:4,maxThrowPower:1.0f,throwAccuracy:1.0f}}
```

<details>
<summary>trajectory and LOOK details</summary>

`rescueLeadTicks` only opens the check window. Inside it, the pearl solver uses current height, downward velocity, and a safe collision destination to calculate additional throw impulse. Rescue pearls inherit the mob's current downward velocity, never horizontal or upward velocity. `maxThrowPower` limits that additional impulse. The pearl must collide safely at least one tick before the mob; predicted residual fall damage plus optional reducible pearl damage must be strictly lower than the harm without rescue. Intervening entities are not prechecked.

When a physical plan is feasible, the mob claims `LOOK` toward the predicted impact before chance and throw. After successful release, it follows the actual pearl entity each tick, then its last known or predicted landing position until the actual landing completion tick. Spread applies only to additional impulse, followed by inherited downward velocity. The sampled real initial velocity is simulated again. An unsafe or late path, resource block, chance failure, or spawn failure ends LOOK that tick. Spawn failure rolls back the equipment exchange and consumes no pearl; spent attempt cooldown is not refunded.

</details>

<details>
<summary>details of parameters</summary>

```text
enable
Meaning: Whether the pearl rescue method is enabled; the fall_rescue parent must also be enabled
Type: bool
Range: 0b, 1b
Default: 1b

FetchSource
Meaning: Ordered item locations allowed to provide an ender pearl
Type: list of strings
Range: mainhand, offhand, inventory, inventory:1 through inventory:256, or inclusive ranges such as inventory:1..9
Default: omitted; offhand then mainhand
Note: Use a pearl already in the destination hand or exchange from listed sources; overlaps are allowed

preferredHand
Meaning: Preferred hand for pearl rescue
Type: string
Range: main, off, main-enforce, off-enforce
Default: off
Note: Plain preference falls back only when PMB occupies the preferred hand; enforce never falls back

doConsume
Meaning: Whether a pearl is consumed on successful release
Type: bool
Range: 0b, 1b
Default: 0b

doTeleportDamage
Meaning: Whether teleport applies vanilla pearl's reducible 5 damage
Type: bool
Range: 0b, 1b
Default: 0b
Note: The solver includes damage adjusted by fall protection in net harm

successChance
Meaning: Chance of an admitted, physically feasible accidental-fall rescue check succeeding
Type: float
Range: [0.0f, 1.0f]
Default: 1.0f
Note: Planned-fall promises skip chance; chance failure ends further attempts for that fall

cooldownTicks
Meaning: Base cooldown in game ticks after a check
Type: integer
Range: [0, 72000]
Default: 20
Note: Accidental falls spend it on chance check; planned falls spend it only on commit

randomCooldownBias
Meaning: Maximum inclusive uniform random extra ticks added to cooldown
Type: integer
Range: [0, 1200]
Default: 0

rescueLeadTicks
Meaning: Ticks before predicted impact that open the pearl check window
Type: integer
Range: [0, 1200]
Default: 4
Note: A late window may offer no safe earlier collision even with chance 1

maxThrowPower
Meaning: Maximum additional throw impulse relative to vanilla stationary player speed of 1.5 blocks per tick
Type: float
Range: [0.1f, 10.0f]
Default: 1.0f
Note: Only rescue pearls also inherit the mob's downward velocity; active and traversal pearls do not use this rule

throwAccuracy
Meaning: Pearl launch accuracy
Type: float
Range: [0.0f, 1.0f]
Default: 1.0f
Note: 1.0f has no spread; lower settings add horizontal spread, but unsafe sampled paths are rejected before launch
```

</details>

<details>
<summary>Full configuration and test scenario</summary>

```mcfunction
/summon minecraft:zombie ~ ~ ~ {Tags:["pmb_pearl_rescue"],equipment:{offhand:{id:"minecraft:ender_pearl",count:64}},PmbAi:{skill:{conditional:{fall_rescue:{enable:1b,methods:["ender_pearl"],ender_pearl:{enable:1b,FetchSource:["offhand","mainhand"],preferredHand:"off",doConsume:0b,doTeleportDamage:0b,successChance:1.0f,cooldownTicks:20,randomCooldownBias:0,rescueLeadTicks:10,maxThrowPower:1.0f,throwAccuracy:1.0f}}}}}}
/pmb skill conditional @e[tag=pmb_pearl_rescue] fall_rescue.ender_pearl modify [maxThrowPower=1.5f]
```

Drop the mob from a high platform toward a safe flat area. K debug's `no_timely_safe_trajectory` means current timing, velocity, and power cap yield no safe route arriving before the mob. Raise `rescueLeadTicks` or `maxThrowPower` as controls. Lower `throwAccuracy` from 1.0f to observe spread and prelaunch safety rejection; landing positions need not change linearly with the setting.

</details>

[Back to fall rescue](fall_rescue.md)
