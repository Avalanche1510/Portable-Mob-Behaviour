# Fall rescue (`skill.conditional.fall_rescue`)

Responds to a predicted harmful impact. The ordered `methods` list checks physically feasible methods that can acquire resources; wind charge precedes ender pearl by default. `singleMethodPerFall:1b` permits only one successfully released rescue per actual fall. A failed chance check remains terminal for that fall; a blocked or physically impossible method does not lock the choice and permits the next method.

Once a physically viable method acquires resources inside its `rescueLeadTicks` window, rescue claims required `LOOK` and aims at the predicted destination before release. After release, wind aims at the explosion point; pearl tracks the pearl entity each tick, then uses its last known or predicted impact point if it disappears.

LOOK lasts through the actual landing completion tick and releases on the next tick. Hands and `USE_ITEM` are not retained. Chance, resource, final trajectory, or spawn failure clears LOOK that tick. A higher-priority action may temporarily take LOOK; rescue resumes when it is free and the fall is still valid.

A wind rebound or airborne teleport does not end it. Disabling the scene or method, removing its priority, death, NoAI, shield vulnerability, and dimension changes clear the state.

The bounded impact prediction follows current horizontal momentum. Water suppresses expected fall damage only if contact with it precedes solid collision; an unknown trajectory does not cancel rescue. Enabled rescue cancels its plan, LOOK, and resources on water or bubble-column contact; the mob no longer keeps looking down while submerged.

```text
fall_rescue:{enable:0b,minRescueDamage:1.0f,singleMethodPerFall:1b,methods:["wind_charge","ender_pearl"],wind_charge:{enable:1b,preferredHand:"off",FetchSource:["offhand","mainhand"],doConsume:0b,successChance:1.0f,cooldownTicks:20,randomCooldownBias:0,rescueLeadTicks:4},ender_pearl:{enable:1b,preferredHand:"off",FetchSource:["offhand","mainhand"],doConsume:0b,doTeleportDamage:0b,successChance:1.0f,cooldownTicks:20,randomCooldownBias:0,rescueLeadTicks:4,maxThrowPower:1.0f,throwAccuracy:1.0f}}
```

The `fall_rescue` parent command accepts only `enable`, `minRescueDamage`, `singleMethodPerFall`, and `methods`. `fall_rescue.wind_charge` and `fall_rescue.ender_pearl` accept their own fields with independent completion. A method node can be created first, while the parent remains disabled by default. The old parent syntax `[wind_charge={...}]` is rejected.

<details><summary>Accidental and planned falls</summary>

For an accidental fall, each enabled physically feasible method rolls at most once per fall. If an earlier method cannot acquire resources, the next feasible method may try. If its chance fails, it consumes its attempt cooldown and blocks all later attempts for that fall.

With the default `singleMethodPerFall:1b`, the first successful release locks all rescue methods until actual landing, even through wind rebound, pearl teleport, or a transition from planned to accidental falling. Disabling this option (`0b`) restores the earlier behavior: if a released method still leaves danger, the next not-yet-submitted method may try.

A later high-priority action pauses the rescue LOOK pose without unlocking the method. Existing wind/mace impulse protection is included in predicted damage.

A planned fall under `dangerousFallPolicy:"conditional_required"` is deterministic: pathfinding asks for a feasible promise and never rolls chance. Before promising wind, it forecasts the falling motion from the takeoff height and requires a timely in-radius explosion within the configured release window; if wind fails this check, it may promise a feasible pearl instead.

Pearl promises also solve from the predicted first release frame inside their lead window; a zero- or one-tick lead cannot promise a pearl that must arrive earlier than its owner. The promise locks method and impact endpoint. `rescueLeadTicks:0` means the last airborne pre-impact tick. Only actual release consumes item and cooldown.

Pearl rescue solves angle and time within max power, accepts a safe earlier block collision, and requires predicted residual fall plus optional protected pearl damage to be strictly safer than the original impact. Entities are not prechecked; an early real hit is accepted and danger is re-evaluated.

</details>

```mcfunction
/pmb skill conditional @e[type=minecraft:zombie,limit=1] fall_rescue append [enable=1b]
/pmb skill conditional @e[type=minecraft:zombie,limit=1] fall_rescue.wind_charge append [rescueLeadTicks=6,successChance=1.0f]
/pmb skill conditional @e[type=minecraft:zombie,limit=1] fall_rescue.ender_pearl append [rescueLeadTicks=10,maxThrowPower=1.0f]
```

<details>
<summary>details of parameters</summary>

```text
enable
Meaning: Whether the fall-rescue scene is enabled; method switches still require this parent
Type: bool
Range: 0b, 1b
Default: 0b

minRescueDamage
Meaning: Minimum predicted harm after impulse and fall protection to attempt rescue
Type: float
Range: [0.0f, 1000.0f]
Default: 1.0f

singleMethodPerFall
Meaning: After the first successful release, block all later rescue methods until actual landing
Type: bool
Range: 0b, 1b
Default: 1b
Note: 0b allows a later unused method if danger remains

methods
Meaning: Check order for wind and pearl rescue methods
Type: list of strings
Range: wind_charge and ender_pearl, each at most once; an empty list is allowed
Default: ["wind_charge","ender_pearl"]
```

</details>

<details><summary>Full configuration and scenario examples</summary>

```mcfunction
/summon minecraft:zombie ~ ~ ~ {Tags:["pmb_fall_rescue"],equipment:{offhand:{id:"minecraft:wind_charge",count:64}},PmbAi:{skill:{conditional:{fall_rescue:{enable:1b,minRescueDamage:1.0f,singleMethodPerFall:1b,methods:["wind_charge","ender_pearl"],wind_charge:{enable:1b,preferredHand:"off",FetchSource:["offhand","mainhand"],doConsume:0b,successChance:1.0f,cooldownTicks:20,randomCooldownBias:0,rescueLeadTicks:4},ender_pearl:{enable:1b,preferredHand:"off",FetchSource:["offhand","mainhand"],doConsume:0b,doTeleportDamage:0b,successChance:1.0f,cooldownTicks:20,randomCooldownBias:0,rescueLeadTicks:4,maxThrowPower:1.0f,throwAccuracy:1.0f}}}},pathfinding:{strategies:{combat:{routeMode:"fallback",dangerousFallPolicy:"conditional_required",requireEyeSight:0b}}}}}
/pmb skill conditional @e[tag=pmb_fall_rescue] fall_rescue modify [enable=0b]
```

For accidental-fall testing, drop the mob from a platform. Chance 1 guarantees only an admitted feasible roll, not projectile arrival before impact; an earlier check or more pearl power can help. Physically infeasible or resource-blocked wind permits pearl fallback; failed wind chance does not.

The default permits one successful rescue release per fall. Set `singleMethodPerFall:0b` to allow a later unused method if danger remains after the first release. Rescue pearls must reach a safe collision at least one tick before the mob; otherwise they are not thrown. Only rescue pearls inherit downward velocity; active and traversal pearls retain their existing velocity rules.

Wind grants protection only after its explosion actually pushes the mob; PMB-owned protection clears on that landing. LOOK is requested from an admitted feasible plan inside the release window through landing, and resumes after a higher-priority interruption. Rejected projectile spawning rolls back equipment and consumes no item; spent attempt cooldown is not refunded.

</details>

- [Wind rescue method and parameters](fall_rescue_wind_charge.md)
- [Pearl rescue method and parameters](fall_rescue_ender_pearl.md)

[Back to conditional skills](README.md)
