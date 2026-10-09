# Pearl traversal (`skill.conditional.traversal.ender_pearl`)

When the vanilla route is unreachable, executes a pathfinding-proposed pearl edge in the parent's `methods` order. Wind comes first by default; moving pearl first gives it the first check. Pearls are never used merely as efficiency shortcuts.

```text
traversal:{enable:0b,methods:["wind_charge","ender_pearl"],ender_pearl:{FetchSource:["offhand","mainhand"],preferredHand:"off",enable:1b,doConsume:0b,doTeleportDamage:0b,cooldownTicks:40,randomCooldownBias:20,maxConnectionDistance:64.0f,maxConnectionHeight:64.0f,throwAngle:30.0f,maxThrowPower:1.0f,throwAccuracy:1.0f}}
```

<details><summary>Parameters and lifecycle</summary>

Connection distance/height `0..256`; cooldown `0..72000`; bias `0..1200`; angle `1..89`; power `0.1..10`; accuracy `0..1`. The solver simulates gravity `0.03`, air/water drag `0.99/0.8`, and first block collision in loaded chunks. It requires collision-clear teleport volume and safe support; entities are deliberately not prechecked. No complete solution means no edge. An early real entity hit accepts the actual teleport and discards/replans the route. `doTeleportDamage` follows the active pearl meaning.

</details>

```mcfunction
/pmb skill conditional @e[type=minecraft:zombie,limit=1] traversal append [enable=1b]
/pmb skill conditional @e[type=minecraft:zombie,limit=1] traversal.ender_pearl append [enable=1b]
```

<details>
<summary>details of parameters</summary>

```text
enable
Meaning: Whether the pearl traversal method is enabled; the traversal parent must be enabled and in the current strategy priority list
Type: bool
Range: 0b, 1b
Default: 1b

FetchSource
Meaning: Ordered locations allowed to provide an ender pearl
Type: list of strings
Range: mainhand, offhand, inventory, inventory:1 through inventory:256, or inclusive ranges such as inventory:1..9
Default: omitted; offhand then mainhand
Note: Exchanges only when the destination hand lacks a pearl; overlapping ranges are allowed, and an empty list does not disable an item already in that hand

preferredHand
Meaning: Preferred hand for pearl traversal
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
Meaning: Whether successful teleport applies 5 damage through the vanilla pearl damage source
Type: bool
Range: 0b, 1b
Default: 0b
Note: Fall protection may reduce this damage

cooldownTicks
Meaning: Base cooldown in game ticks after actual release
Type: integer
Range: [0, 72000]
Default: 40

randomCooldownBias
Meaning: Maximum uniform random extra ticks added to each cooldown
Type: integer
Range: [0, 1200]
Default: 20

maxConnectionDistance
Meaning: Maximum pearl connection distance; does not guarantee physical reach
Type: float
Range: [0.0f, 256.0f]
Default: 64.0f

maxConnectionHeight
Meaning: Maximum connection height difference; actual trajectory is still checked
Type: float
Range: [0.0f, 256.0f]
Default: 64.0f

throwAngle
Meaning: Fixed launch angle above horizontal, in degrees
Type: float
Range: [1.0f, 89.0f]
Default: 30.0f

maxThrowPower
Meaning: Speed cap relative to the vanilla stationary player speed of 1.5 blocks per tick
Type: float
Range: [0.1f, 10.0f]
Default: 1.0f

throwAccuracy
Meaning: Launch accuracy; lower values may miss even a feasible plan
Type: float
Range: [0.0f, 1.0f]
Default: 1.0f
Note: 1.0f adds no random spread
```

</details>

<details><summary>Full configuration and scenario examples</summary>

```mcfunction
/summon minecraft:zombie ~ ~ ~ {Tags:["pmb_pearl_traversal"],equipment:{offhand:{id:"minecraft:ender_pearl",count:64}},PmbAi:{skill:{conditional:{traversal:{enable:1b,methods:["ender_pearl"],ender_pearl:{FetchSource:["offhand","mainhand"],preferredHand:"off",enable:1b,doConsume:0b,doTeleportDamage:0b,cooldownTicks:40,randomCooldownBias:20,maxConnectionDistance:64.0f,maxConnectionHeight:64.0f,throwAngle:30.0f,maxThrowPower:1.0f,throwAccuracy:1.0f}}}},pathfinding:{strategies:{combat:{routeMode:"fallback",dangerousFallPolicy:"conditional_required",requireEyeSight:0b}}}}}
/pmb skill conditional @e[tag=pmb_pearl_traversal] traversal.ender_pearl modify [enable=0b]
```

Use a safe platform unreachable by vanilla. Wind is attempted first by default; change the parent's `methods` to reorder them. Traversal pearls are not efficiency shortcuts and do not fire without a complete trajectory. Lower maxThrowPower to verify rejection of an unreachable edge.

</details>

[Back to special traversal](traversal.md)
