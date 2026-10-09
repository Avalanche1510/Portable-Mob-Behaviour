# Wind traversal (`skill.conditional.traversal.wind_charge`)

Executes a pathfinding-proposed wind edge. It is deterministic: pathfinding plans; this conditional owns item feasibility and release.

```text
traversal:{enable:0b,methods:["wind_charge","ender_pearl"],wind_charge:{FetchSource:["offhand","mainhand"],preferredHand:"off",enable:1b,doConsume:0b,cooldownTicks:40,randomCooldownBias:20,maxConnectionDistance:16.0f,minHorizontalConnectionDistance:0.0f,maxConnectionHeight:8.0f,minSavedTicks:10,postJumpTicks:2}}
```

<details><summary>Parameters and lifecycle</summary>

Before jumping, the mob approaches the planned takeoff in all three dimensions, then holds movement input while existing horizontal inertia naturally settles. The flight is checked from the actual departure position before starting the jump and cooldown. Preparation continues to hold movement input; gravity and external knockback are not cleared.

The final release check reports its rejection reason in the same tick's K snapshot, including changed state, horizontal motion, obstruction, missed contact, or unsafe descent. Equivalent local path results are reused only within one planning round, with the existing search budgets unchanged. A visible candidate arc alone does not guarantee release.

Connection distance/height `0..256`; minimum horizontal span defaults to `0.0f`; cooldown `0..72000`; bias `0..1200`; saved ticks `0..72000`. The height limit measures takeoff-to-landing difference, not bounce apex.

`fallback` keeps a complete vanilla route. With a complete global route, `efficient` needs a collision-checked connection to a suffix node and at least `minSavedTicks`. Without a complete global route, a complete local route still checks savings, including small obstacles such as lanterns.

If the executed local search returns a partial or null path, a physically safe edge may proceed when ordinary local movement is obstructed and the endpoint is strictly closer to the objective in three dimensions. A check skipped for lack of budget does not qualify. No closed-component or isolated-pillar proof is required.

The existing `efficient` flowing-water exception still requires an actual dry-to-dry crossing and demonstrated onward movement. Still water, water beside the connection, and airborne contact do not receive that exception.

The planner requires a reachable takeoff, a swept collision-clear flight, loaded chunks, and a viable landing. Water and cobwebs may be landings; lava anywhere inside the mob's body requires fire immunity. Ordinary one-block jumps with headroom are kept as walking rather than consuming wind candidates. Beyond direct probes, enabled and ready wind traversal scans eight directions at radii 1 through 6 around the target and may use the end of an incomplete vanilla route as a bounded reachable-edge anchor. This remains local, not global pathfinding; the fast and rim stages retain separate candidate-check quotas and reserve a fast check for air contact. Airborne contact is a distinct endpoint for an attackable target behind a blocked local ground approach, including same-height contact or an occupied narrow ledge. Its trajectory must clear blocks, touch the target, and then reach a safe post-contact descent; it creates no new generic melee ability.

For a wall between higher ground and a lower target, the direct search also tries a bounded set of takeoff backtracks and up to nine lower landing positions near the target. A connection is rejected when its expected landing or post-contact descent would cause unprotected fall damage; a vehicle carrying the target is also treated as a real collision obstacle. These local checks do not guarantee a route through every wall, canopy, or trench. `efficient` uses the simulated flight duration instead of a fixed minimum of 35 ticks when comparing ordinary routes. K diagnostics identify the last candidate's endpoints and a rejection stage, such as approach path, trajectory budget, block/vehicle collision, missed contact, unsafe descent, unknown local path, or insufficient savings; this is diagnostic evidence, not proof of the in-game cause.

While approaching a takeoff, navigation is resubmitted at most every 10 ticks. If it ends while still far from the takeoff, or makes no meaningful progress for 30 ticks, that point is abandoned and suppressed for 60 ticks. A pursuing mob with enabled wind traversal also stops and replans a stale vanilla route after 30 ticks without horizontal progress, at most once per 60 ticks. At the anchor the event waits up to `conditionalWaitTicks`. The default `postJumpTicks:2` starts a ground jump after admission and releases from the actual feet two ticks later; `0` retains immediate ground release. Preparation reserves the chosen hand(s) and LOOK. Early landing, collision/failed launch recheck, invalid target/item, Weakness, NoAI, death, or higher-priority preemption cancels release while retaining the cooldown charged at the jump; exchange/consumption occurs only on release. An already-airborne mob never starts or caches this jump. The 26.2 flight check now estimates actual distance-, exposure-, and resistance-dependent wind impulse from the predicted jump/floor hit and checks the live launch again before release; a configured height limit is not a promise of physical reach. A successful release faces the contact or landing endpoint. Air contact tracks a fixed, prevalidated safe-descent waypoint using the same horizontal steering rule as the feasibility simulation, rather than dynamically chasing the target after contact; other landings track their own endpoints while airborne when LOOK is available.

The launch proof advances the projectile and mob tick by tick to the floor hit, samples explosion exposure from the predicted mob box at impact rather than the release box, and rejects unknown or obstructed sweeps. Pre-jump waiting and pre-impact ticks count toward route time. PMB queues steering at Mob tick tail and applies it only at the following `serverAiStep`, so the first post-impact displacement remains unsteered even at delay 0; delay 1 leaves two such displacements. Planning and runtime use this same boundary. The live launch is checked again before release.

</details>

```mcfunction
/pmb skill conditional @e[type=minecraft:zombie,limit=1] traversal append [enable=1b]
/pmb skill conditional @e[type=minecraft:zombie,limit=1] traversal.wind_charge append [enable=1b]
```

<details>
<summary>details of parameters</summary>

```text
enable
Meaning: Whether the wind traversal method is enabled; the traversal parent must be enabled and in the current strategy priority list
Type: bool
Range: 0b, 1b
Default: 1b

FetchSource
Meaning: Ordered locations allowed to provide a wind charge
Type: list of strings
Range: mainhand, offhand, inventory, inventory:1 through inventory:256, or inclusive ranges such as inventory:1..9
Default: omitted; offhand then mainhand
Note: Exchanges only when the destination hand lacks a charge; overlapping ranges are allowed, and an empty list does not disable an item already in that hand

preferredHand
Meaning: Preferred hand for wind traversal
Type: string
Range: main, off, main-enforce, off-enforce
Default: off
Note: Plain preference falls back only when PMB occupies the preferred hand; enforce never falls back

doConsume
Meaning: Whether a charge is consumed on successful release
Type: bool
Range: 0b, 1b
Default: 0b

cooldownTicks
Meaning: Base cooldown in game ticks after actual commit
Type: integer
Range: [0, 72000]
Default: 40
Note: 0 permits another check next tick

randomCooldownBias
Meaning: Maximum uniform random extra ticks added to each cooldown
Type: integer
Range: [0, 1200]
Default: 20
Note: The inclusive offset is sampled from 0 through the configured value

maxConnectionDistance
Meaning: Maximum wind connection distance; does not guarantee physical reach
Type: float
Range: [0.0f, 256.0f]
Default: 16.0f

minHorizontalConnectionDistance
Meaning: Minimum horizontal span from takeoff to landing or airborne contact; 0 permits a nearby vertical launch
Type: float
Range: [0.0f, 256.0f]
Default: 0.0f

maxConnectionHeight
Meaning: Maximum connection height difference; actual trajectory is still checked
Type: float
Range: [0.0f, 256.0f]
Default: 8.0f

minSavedTicks
Meaning: Minimum estimated ticks saved over vanilla in efficient mode
Type: integer
Range: [0, 72000]
Default: 10

postJumpTicks
Meaning: Ground-jump preparation ticks before releasing a wind traversal charge; 0 preserves immediate ground release
Type: integer
Range: [0, 10]
Default: 2
```

</details>

<details><summary>Full configuration and scenario examples</summary>

```mcfunction
/summon minecraft:zombie ~ ~ ~ {Tags:["pmb_wind_traversal"],equipment:{offhand:{id:"minecraft:wind_charge",count:64}},PmbAi:{skill:{conditional:{traversal:{enable:1b,methods:["wind_charge"],wind_charge:{FetchSource:["offhand","mainhand"],preferredHand:"off",enable:1b,doConsume:0b,cooldownTicks:40,randomCooldownBias:20,maxConnectionDistance:16.0f,minHorizontalConnectionDistance:0.0f,maxConnectionHeight:8.0f,minSavedTicks:10,postJumpTicks:2}}}},pathfinding:{strategies:{combat:{routeMode:"fallback",dangerousFallPolicy:"conditional_required",requireEyeSight:0b}}}}}
/pmb skill conditional @e[tag=pmb_wind_traversal] traversal.wind_charge modify [enable=0b]
```

Put the target on a canopy or behind a short wall. Fallback considers a jump only without a complete vanilla path; use efficient to test a glass-maze shortcut. Release happens at the takeoff anchor. High walls, low ceilings and unsafe landing positions must reject the edge.

</details>

[Back to special traversal](traversal.md)
