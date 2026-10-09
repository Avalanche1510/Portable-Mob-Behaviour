# Skills

`skill.active` initiates actions from world state. `skill.conditional` responds to a concrete event produced by combat, a fall, or pathfinding. After admission both kinds use the same priority table, action resources, preemption, and one-irreversible-commit-per-tick rule.

- [Active skills](active/README.md)
- [Conditional skills](conditional/README.md)

<details><summary>Priorities and lifecycle</summary>

```text
combat:["mace","fall_rescue","ender_pearl","wind_charge","traversal","bow","shield","vanilla"]
retreat:["fall_rescue","ender_pearl","wind_charge","traversal","vanilla"]
idle:["fall_rescue","traversal","vanilla"]
```

A nested list is an equal tier. Equal candidates use the scheduler's stable arbitration; only a strictly higher conflicting skill preempts a running phase. Omitted skills cannot start in that strategy. `vanilla` represents competing vanilla irreversible actions, not navigation. A skill needs both `enable:1b` and an entry in the current strategy.

Priority and resource admission precede chance and its attempt cooldown. A passed or failed roll consumes the attempt cooldown; suppression or missing feasibility does not. Deterministic conditionals consume commit cooldown only after a real release/commit.

</details>

<details><summary>Items, ammunition, and inventory</summary>

`FetchSource` accepts `mainhand`, `offhand`, `inventory`, or `inventory:n`/`inventory:a..b` for slots 1–256. It defines where a skill may obtain and exchange its item. `preferredHand` accepts `main`, `off`, `main-enforce`, or `off-enforce`; enforced values do not fall back. Bow uses a separate `AmmoSource`. Existing suitable hand items are checked before fetching. Exchanges are permanent; actions do not automatically restore the previous layout.

`PmbInventory` is an entity-root sibling of `PmbAi`: `PmbInventory:{Slots:0,DropChance:0.5f,ScatterDrops:0b,Items:[]}`. `Slots` is `0..256`, `DropChance` is `0..1`, and item slots are one-based. Storage is allocated to the configured size.

With vanilla `CanPickUpLoot` and `mobGriefing`, a remainder not equipped or consumed by species-specific pickup logic may enter this inventory; pickup overflow stays on the ground. Explicit capacity reduction drops remainders that cannot fit.

Every stored stack rolls `DropChance` independently on death. `ScatterDrops:1b` gives successful intact stacks player-like outward item velocity and the vanilla pickup delay; it does not damage or split them.

</details>

<details><summary>Commands and K debug</summary>

```mcfunction
/pmb skill active <targets> <skill> get|append|insert|modify|delete ...
/pmb skill conditional <targets> <skill> get|append|insert|modify|delete ...
/pmb skill priority <targets> get <combat|retreat|idle|all>
/pmb skill priority <targets> set <combat|retreat|idle> <list>
/pmb skill priority <targets> reset <combat|retreat|idle|all>
```

`append` creates a node; `insert` adds only absent explicit fields; `modify` changes explicit fields; `delete [a,b]` removes fields; `delete *` clears explicit fields on the selected node; `delete []` removes that node. `traversal` and `fall_rescue` are parent scenes; dotted IDs such as `fall_rescue.ender_pearl` edit only their method nodes. Creating a method node does not enable its parent. The bracket parser completes names and typed values and rejects invalid types, ranges, duplicates, and final min/max relations atomically.

The rebindable `K` probe requires gamemaster permission 2 and a visible Mob under the crosshair. It writes the immutable previous tick snapshot to requester chat and server log. It separates active/conditional skills, attempts, resources, bindings, movement intent, route stage/proposal, conditional event, and air context. Disabled skills are hidden; empty sections remain visible. The probe does not reroll or mutate behavior.

</details>


<details>
<summary>Complete equipment, inventory reference and combined examples</summary>

All PMB skills run through one server-side Mob tick. The authoritative strategy is `retreat` for Faction avoidance, otherwise `combat` with a valid attack target, or `idle` without one. Shield-break vulnerability, NoAI, and death block new actions. Strategy or authority changes revalidate every sustained phase: valid phases still listed by the new strategy continue, while omitted or phase-invalid ones end.

Each skill accepts the optional ordered FetchSource string list: mainhand, offhand, inventory, inventory:1, or inclusive ranges such as inventory:1..9. Public slots are 1–256 and ranges may overlap. The destination hand is selected first. A compatible item already there is used directly without consulting FetchSource; otherwise sources are scanned in list order and slots in ascending order. Omission uses the previous source-hand order. An empty or invalid list offers no fetch locations but does not disable a compatible item already in the destination hand.

bow, shield, wind_charge and ender_pearl support preferredHand: main, off, main-enforce, or off-enforce. Bow defaults to main; the other three default to off. Mace does not accept this field and executes in mainhand. At action startup only, plain main/off may fall back when the preferred hand belongs to an ongoing PMB action. Static held items do not block a hand. Enforced preferences fail when their hand is busy. A same-tick arbitration loss never retries another hand.

Busy source and destination hands cannot be exchanged; unavailable sources are skipped. A cross-hand swap reserves both hands for that tick, then a sustained action binds only its actual use hand. Each check selects one item; a compatible destination item takes precedence over other copies in FetchSource.

Every successful equipment exchange is permanent. Completion, cancellation, NoAI, unloading, conversion and death never restore the previous layout. ActionBinding validates the current action item and binds its hand until completion. Displaced weapons or arrows may become stranded outside FetchSource/AmmoSource; configuration authors must cover the required locations. Final held items follow vanilla equipment drop rules, while final inventory items follow PMB inventory drop rules.



Example (insert into an existing bow node; use modify for fields already written):

```text
/pmb skill active @n[type=husk] bow insert [preferredHand="off-enforce",FetchSource=["mainhand","inventory:1..9"],AmmoSource=["inventory:1..9"]]
```

`PmbInventory` belongs only to Mob entities and is independent of `PmbAi`:

```text
PmbInventory:{
    Slots:<int>,
    DropChance:<float>,
    ScatterDrops:<bool>,
    Items:[
        {Slot:<int>,id:<item id>,count:<int>}
    ]
}
```

`Slots` has range `[0,256]` and default `0`; its backing container is allocated to that actual capacity, so `Slots:0` allocates no inventory entries. `DropChance` has range `[0.0f,1.0f]` and default `0.5f`; `ScatterDrops` is a boolean and defaults to `0b`.

Each enabled, non-empty slot rolls independently on death and a successful roll drops the entire stack. With `ScatterDrops:0b`, that stack uses the existing stationary PMB drop; with `ScatterDrops:1b`, it uses the vanilla player-style death-drop path from `eyeY - 0.3`, with a random radial horizontal speed in `[0.0,0.5)`, vertical speed `0.2`, and a 40-tick pickup delay. The stack remains intact: this option neither damages nor splits it. With `doMobLoot:false`, no PMB inventory items drop.

Reducing `Slots` first merges or moves high-slot contents into enabled low slots, then immediately drops full remainders. `ScatterDrops` affects only successful PMB inventory death drops, not resize/load overflow or held equipment. Invalid slot numbers outside 1–256 are ignored. Existing 0–27-slot data loads unchanged; downgrading data that uses slots 28–256 to an older PMB version loses those high-slot items.

When `CanPickUpLoot:1b` and `mobGriefing:true`, vanilla equipment and species-specific pickup logic runs first. A nearby item stack left on the ground is then merged into PmbInventory like a player inventory; a full inventory leaves the remainder on the ground. This does not add item-seeking navigation or a GUI.

When bow `AmmoSource` is omitted, supported ammunition held in either hand has priority, followed by PmbInventory from its lowest enabled slot upward; scanning stops at the first compatible stack. An explicit `AmmoSource` instead uses only its own ordered locations. Ammunition is read directly and does not need to occupy another hand. With `doConsume:0b`, a real special arrow is used without consumption, or an absent arrow falls back to unlimited normal arrows. With `doConsume:1b`, a real compatible stack is required and one item is removed only after release.

Example:

```text
summon zombie ~ ~ ~ {CanPickUpLoot:1b,PmbInventory:{Slots:9,DropChance:0.5f,ScatterDrops:1b,Items:[{Slot:1,id:"minecraft:mace",count:1},{Slot:2,id:"minecraft:wind_charge",count:16}]},PmbAi:{skill:{active:{wind_charge:{enable:1b,FetchSource:["offhand","inventory:1..9"],doConsume:1b}},conditional:{mace:{enable:1b,FetchSource:["mainhand","inventory:1..9"]}}}}}
```

</details>

<details><summary>Step-by-step editing and deletion examples</summary>

Select a husk without a configured bow node. Append creates it, insert adds only absent explicit fields, and modify changes only present explicit fields. Repeated append, insert of existing fields, and modify of absent fields are errors.

```mcfunction
/pmb skill active @n[type=minecraft:husk] bow append [enable=1b]
/pmb skill active @n[type=minecraft:husk] bow insert [lineMaxRange=24.0f,AmmoSource=["mainhand","offhand","inventory"]]
/pmb skill active @n[type=minecraft:husk] bow modify [lineMaxRange=20.0f]
/pmb skill active @n[type=minecraft:husk] bow get
/pmb skill active @n[type=minecraft:husk] bow delete [lineMaxRange,AmmoSource]
/pmb skill active @n[type=minecraft:husk] bow delete *
/pmb skill active @n[type=minecraft:husk] bow delete []
```

An asterisk removes all explicit parameters including enable and leaves a disabled default node; an empty list removes the whole skill. Suggestions appear after commas and name prefixes, values are typed, and delete lists also complete fields. Invalid input does not partially update a selected group.

</details>

[Back to documentation](../../docs.md)
