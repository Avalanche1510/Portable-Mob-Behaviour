# Special traversal (`skill.conditional.traversal`)

Pathfinding proposes an edge. `traversal` tries wind charge and pearl methods in `methods` order. Both share one `traversal` strategy priority while retaining independent switches, item sources, and cooldowns. The parent is disabled by default; methods are enabled by default.

```text
traversal:{enable:0b,methods:["wind_charge","ender_pearl"],wind_charge:{enable:1b,FetchSource:["offhand","mainhand"],preferredHand:"off",doConsume:0b,cooldownTicks:40,randomCooldownBias:20,maxConnectionDistance:16.0f,minHorizontalConnectionDistance:0.0f,maxConnectionHeight:8.0f,minSavedTicks:10},ender_pearl:{enable:1b,FetchSource:["offhand","mainhand"],preferredHand:"off",doConsume:0b,doTeleportDamage:0b,cooldownTicks:40,randomCooldownBias:20,maxConnectionDistance:64.0f,maxConnectionHeight:64.0f,throwAngle:30.0f,maxThrowPower:1.0f,throwAccuracy:1.0f}}
```

```mcfunction
/pmb skill conditional @e[type=minecraft:zombie,limit=1] traversal append [enable=1b,methods=["wind_charge","ender_pearl"]]
/pmb skill conditional @e[type=minecraft:zombie,limit=1] traversal.wind_charge append [doConsume=1b]
/pmb skill conditional @e[type=minecraft:zombie,limit=1] traversal.ender_pearl append [maxThrowPower=1.5f]
```

The `traversal` parent command accepts only `enable` and `methods`; the dotted IDs accept their respective method fields. A method command may create its disabled parent. `delete []` removes the selected node; `delete *` clears that node's explicit fields. Old `wind_traversal` and `pearl_traversal` nodes and priority IDs are not read.

<details><summary>Planning and execution</summary>

`methods` accepts `wind_charge` and `ender_pearl` once each, in configurable order, or an empty list. Pathfinding first checks the vanilla route. `fallback` considers special edges only without a complete vanilla path. `efficient` considers wind shortcuts across locally impassable obstacles and slow water/cobweb routes; non-immune lava crossing is treated as safety rather than time saving.

Pearl is never used solely to travel faster; when vanilla cannot reach the destination, pearl and wind are checked in `methods` order. Both methods require loaded chunks, a viable trajectory, and safe landing. Items are consumed only after successful release.

With positive wind `postJumpTicks`, cooldown begins when the pre-jump is committed and is retained if preparation is cancelled or release fails. Wind with `postJumpTicks:0` and pearl begin cooldown on successful release. An unconfigured or disabled traversal scene does not run its extra vanilla path or trajectory checks. The parent must occur in the current strategy priority list.

</details>

<details>
<summary>details of parameters</summary>

```text
enable
Meaning: Whether the special-traversal scene is enabled; method switches still require this parent
Type: bool
Range: 0b, 1b
Default: 0b

methods
Meaning: Check order for wind and pearl traversal methods
Type: list of strings
Range: wind_charge and ender_pearl, each at most once; an empty list is allowed
Default: ["wind_charge","ender_pearl"]
Note: Wind is first by default; placing ender_pearl first checks it first
```

</details>

- [Wind traversal parameters and examples](wind_traversal.md)
- [Pearl traversal parameters and examples](pearl_traversal.md)

[Back to conditional skills](README.md)
