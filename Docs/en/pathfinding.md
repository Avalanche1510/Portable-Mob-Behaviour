# Pathfinding

Pathfinding owns movement policy. Skills request actions; Faction supplies threats; neither writes competing navigation directly.

```text
MovementIntent -> RoutePlanner -> MovementController -> ConditionalEvent
RETREAT > ACTIVE_TACTICAL > PURSUIT > IDLE
```

<details><summary>Persisted structure</summary>

```text
pathfinding:{
 planner:{planningHorizon:32.0f,repathIntervalTicks:10,objectiveMemoryTicks:40,targetMoveThreshold:2.0f,conditionalWaitTicks:40,debugPathParticles:0b},
 strategies:{
  combat:{routeMode:"fallback",dangerousFallPolicy:"conditional_required",requireEyeSight:0b},
  retreat:{routeMode:"fallback",dangerousFallPolicy:"conditional_required",requireEyeSight:0b},
  idle:{routeMode:"vanilla_only",dangerousFallPolicy:"disable",requireEyeSight:0b}
 },
 air_tracking:{trackAcceleration:0.012f,trackMaxHorizontalSpeed:0.3f,trackDurationTicks:-1},
 retreat:{speedMultiplier:1.25f,searchHorizontalRange:16,searchVerticalRange:7,repathIntervalTicks:10}
}
```

</details>

<details><summary>Route and fall policies</summary>

`vanilla_only` uses only vanilla routes. `fallback` tries a special local connection when a complete vanilla path is unavailable. `efficient` may use wind traversal when it saves at least `minSavedTicks`. Traversal checks wind before pearl by default, but `traversal.methods` can change that order; pearl is not used for efficiency shortcuts.

`disable` rejects a planned dangerous drop. `enable` permits a supported edge without rescue. `conditional_required` requires an accepted `fall_rescue` promise before the edge is authorized.

`requireEyeSight` gates creation/replanning of special routes. For an already acquired target, enabled traversal methods, and a combat route other than `vanilla_only`, disabling this setting also lets the vanilla TargetGoal retain the target through temporary occlusion. It does not discover unseen targets through walls. FOLLOW_RANGE governs entity-objective validity, not the local planning radius.

</details>

<details><summary>Planning lifecycle and limits</summary>

Special checks advance through `VANILLA`, `WIND`, and `PEARL` under a fair server budget; exhaustion postpones work rather than declaring the route unreachable. One mob performs at most one planning stage per tick. Planned drops use `APPROACH -> ARMED -> AIRBORNE`; a pending conditional expires after `conditionalWaitTicks`.

Wind search examines at most six direct and four rim candidates per round. Normal retries continue from independent cursors rather than always retrying the first candidates; terrain and trajectories are recalculated. Significant movement, a new objective or strategy, disabling traversal, or a successful release resets this progress.

Wind traversal no longer requires a closed landing-side component. When no complete vanilla route exists, a computed partial or null local path can permit a physically safe wind edge if ordinary local movement is obstructed and its endpoint is strictly closer to the objective in three dimensions. An unexecuted check due to budget exhaustion does not qualify.

A complete global vanilla route remains authoritative: `fallback` keeps it, while `efficient` requires a safe connection to its suffix and the configured `minSavedTicks`. Without a complete global route, a complete local route around a lantern or other small obstacle still needs the same savings. The existing dry-to-dry flowing-water exception is unchanged. Air contact still requires collision-checked contact and safe descent.

Only loaded chunks, collision-clear volume, support, world border, and the entity's path types are considered. Runtime objectives, search cursors, proposals, promises, routes, and air-tracking contexts are not persisted. The current planner builds conservative local edges to the current objective, not a persistent arbitrary-terrain graph.

Air tracking is not a skill. A committed action installs an entity or position context; only another committed action replaces it. It preserves vertical velocity and applies bounded horizontal acceleration until landing, invalidation, or timeout.

A game-master can aim at a configured PMB mob and press K to add it to a persistent observation list; pressing K on the same mob removes it. Each administrator may observe up to four mobs. A fifth selection reports that the list is full. Chat only confirms list changes. A full K snapshot is written to the server terminal and `latest.log` every tick for each selected mob, including position, velocity, target, and sight. Sending `1` in chat can mark a moment in the log for later comparison. Continuous snapshots may produce large logs, so observe only while debugging.

Only the observer receives highlighting particles. With `debugPathParticles:true`, cached vanilla comparisons and wind candidates are also drawn without replanning. A comparison has at most 64 nodes; up to two physically simulated but later rejected wind routes and the accepted route have up to 80 flight samples each. Colors stay distinct within a plan, and the adopted route is larger. A flight arc clears after landing or 80 ticks. Highlighting and routes share a cap of 144 particles per administrator per tick. Turning the route flag off does not stop K logs or highlighting.

Death, unload, dimension or permission changes, and disconnect stop the affected observation; temporary loss of sight does not. If no valid trajectory exists, the continuous log still exposes the planning and rejection state.

`WIND_PLANNER` records the effective sight rule, current objective, distance/height limits, and pre-planning stops. For each observed planning round it retains up to ten checked candidates with exact takeoff, landing and descent coordinates, the failing phase, and the rejection reason. Budget totals, unchecked counts and next cursors are separate; they do not replace collision or policy failures. `roundTick` and `roundAge` identify retained results, while global planning-budget waits and ordinary replan waits are reported separately. These terminal details also work with route particles disabled.

Admission logs distinguish `partial_route_obstacle_progress`, `no_local_obstacle`, `no_target_progress`, local-path budget exhaustion, and full/local-route savings failures. Local path results are reused within the round; the removed closed-component proof no longer produces rejection reasons.

</details>

<details><summary>Commands</summary>

```mcfunction
/pmb pathfinding planner <targets> get
/pmb pathfinding planner <targets> set planningHorizon 32.0
/pmb pathfinding planner <targets> set debugPathParticles true
/pmb pathfinding strategy <targets> get
/pmb pathfinding strategy <targets> set combat routeMode fallback
/pmb pathfinding strategy <targets> set combat dangerousFallPolicy conditional_required
/pmb pathfinding strategy <targets> set combat requireEyeSight false
/pmb pathfinding air_tracking <targets> get
/pmb pathfinding air_tracking <targets> set trackDurationTicks -1
/pmb pathfinding retreat <targets> get
/pmb pathfinding retreat <targets> set speedMultiplier 1.25
```

</details>


<details>
<summary>details of parameters</summary>

```text
planner.planningHorizon
Meaning: Local planning radius; distant objectives use rolling replanning, independently of FOLLOW_RANGE
Type: float
Range: [1.0f, 256.0f]
Default: 32.0f

planner.repathIntervalTicks
Meaning: Ordinary replanning interval; invalidation or significant target movement may trigger earlier
Type: integer
Range: [1, 1200]
Default: 10

planner.objectiveMemoryTicks
Meaning: Minimum replan delay after a planning round ends without a pending proposal, combined with repathIntervalTicks; does not retain unseen targets or bypass requireEyeSight
Type: integer
Range: [0, 72000]
Default: 40

planner.targetMoveThreshold
Meaning: Objective displacement in blocks that requests replanning
Type: float
Range: [0.0f, 64.0f]
Default: 2.0f

planner.conditionalWaitTicks
Meaning: Maximum wait at the anchor for a conditional commit before replanning
Type: integer
Range: [1, 72000]
Default: 40

planner.debugPathParticles
Meaning: Show cached path particles to a game-master already observing this PMB mob with K; does not control K logs or highlighting
Type: boolean
Range: true, false
Default: false

strategies.*.routeMode
Meaning: Vanilla routes only, special fallback, or efficient wind shortcuts
Type: string
Range: vanilla_only, fallback, efficient
Default: combat/retreat fallback; idle vanilla_only
Note: The default traversal method order can be changed through traversal.methods

strategies.*.dangerousFallPolicy
Meaning: Allow dangerous descent, reject it, or require a feasible rescue promise; also applies to bow movement
Type: string
Range: enable, disable, conditional_required
Default: combat/retreat conditional_required; idle disable

strategies.*.requireEyeSight
Meaning: Require sight to create or replan special routes; does not undo committed actions
Type: boolean
Range: 0b, 1b
Default: 0b

air_tracking.trackAcceleration
Meaning: Horizontal tracking acceleration per tick; 0 adds no acceleration
Type: float
Range: [0.0f, 1.0f]
Default: 0.012f

air_tracking.trackMaxHorizontalSpeed
Meaning: Horizontal tracking speed cap in blocks per tick; does not truncate vertical velocity
Type: float
Range: [0.0f, 3.0f]
Default: 0.3f

air_tracking.trackDurationTicks
Meaning: Context duration; -1 until landing, invalidation, or replacement; 0 does not sustain tracking
Type: integer
Range: [-1, 72000]
Default: -1

retreat.speedMultiplier
Meaning: Retreat movement-speed multiplier replacing the former Faction speed field
Type: float
Range: [0.0f, 100.0f]
Default: 1.25f

retreat.searchHorizontalRange
Meaning: Horizontal search radius for retreat destinations
Type: integer
Range: [1, 256]
Default: 16

retreat.searchVerticalRange
Meaning: Vertical search radius for retreat destinations
Type: integer
Range: [1, 256]
Default: 7

retreat.repathIntervalTicks
Meaning: Interval to select a new retreat destination; changed or moved threats can refresh sooner
Type: integer
Range: [1, 1200]
Default: 10
```

</details>

<details><summary>Scenarios and complete configuration examples</summary>

This complete entity enables wind wall traversal and rescue-backed descents. Put its target behind a short wall; use vanilla_only as the control case. Feasibility still depends on collision, landing support, equipment and cooldown.

```mcfunction
/summon minecraft:zombie ~ ~ ~ {Tags:["pmb_path_test"],equipment:{offhand:{id:"minecraft:wind_charge",count:64}},PmbAi:{skill:{conditional:{traversal:{enable:1b,methods:["wind_charge"],wind_charge:{enable:1b,doConsume:1b,cooldownTicks:40,randomCooldownBias:0,maxConnectionDistance:16.0f,maxConnectionHeight:8.0f,minSavedTicks:10}},fall_rescue:{enable:1b,methods:["wind_charge"]}}},pathfinding:{planner:{planningHorizon:32.0f,repathIntervalTicks:10,objectiveMemoryTicks:40,targetMoveThreshold:2.0f,conditionalWaitTicks:40},strategies:{combat:{routeMode:"efficient",dangerousFallPolicy:"conditional_required",requireEyeSight:0b}},air_tracking:{trackAcceleration:0.012f,trackMaxHorizontalSpeed:0.3f,trackDurationTicks:-1},retreat:{speedMultiplier:1.25f,searchHorizontalRange:16,searchVerticalRange:7,repathIntervalTicks:10}}}}
/pmb pathfinding strategy @e[tag=pmb_path_test] set combat routeMode vanilla_only
/pmb pathfinding strategy @e[tag=pmb_path_test] set combat routeMode efficient
```

Retreat uses the same route policies with a Faction threat objective. Committed wind/mace actions update airborne tracking for their combo; no air_tracking skill needs enabling.

</details>

[Back to documentation](../docs.md)
