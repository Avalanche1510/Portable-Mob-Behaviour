# Portable Mob Behaviour Docs

## Introduction

This mod introduces a new, highly customizable, and relatively simple data structure for mob AI behaviours.
Players can use commands or data packs to modify the data in game. Changes take effect immediately and can modify or enable specific AI behaviours.
Available parameters and the syntax used to define the data structure are given below.

## Two-sided deployment and synchronization

The mod is built as one universal JAR for both client and server, while common and client sources are separated through Loom source sets. Dedicated servers load only common and server logic; models, rendering, and client mixins load only on physical clients.

Both client and server must install this mod. During connection configuration they exchange an independent PMB network protocol number. A missing client mod, missing server mod, or incompatible protocol disconnects before entering the world with an explicit reason. Display versions do not need to match when their network protocol is compatible.

AI decisions, damage, and PmbAi configuration remain server-authoritative. Shield use, bow drawing, held items, and rotation use vanilla synchronized state. Remaining shield-break vulnerability time is synchronized through SynchedEntityData so remote clients render shaking correctly. Complete PmbAi configuration is not sent to clients.

## Modular skills testing command

Operators with gamemaster permission level 2 may edit one skill on one or more mobs without writing complete SNBT:

```mcfunction
/pmb skills <targets> <skill> get
/pmb skills <targets> <skill> append [<parameter>=<value>,...]
/pmb skills <targets> <skill> insert [<parameter>=<value>,...]
/pmb skills <targets> <skill> modify [<parameter>=<value>,...]
/pmb skills <targets> <skill> delete [<parameter>,...]
/pmb skills <targets> <skill> delete *
/pmb skills <targets> <skill> delete []
```

`<skill>` completes to `shield`, `wind_charge`, `mace`, `bow`, or `ender_pearl`. `append` creates a missing node, including an empty node with `append []`. `insert` adds only fields not already written explicitly. `modify` changes only explicitly written fields. `delete [a,b]` removes those explicit fields and restores their defaults. `delete *` retains an empty, disabled skill node with every value at its default. `delete []` removes the skill node itself. `get` accepts exactly one Mob and reports whether the node exists, its explicit fields, and every effective value with its explicit/default source.

The target selector must resolve only to Mob entities. Bulk writes first validate every target, every operation precondition, and every resulting complete configuration; one failure leaves every selected mob unchanged. A successful write immediately cancels only the selected skill, retains its committed equipment layout, clears that skill's cooldown and action state, and refreshes shield toughness when editing shield. Other skills and their cooldowns continue unchanged.

The bracket parser completes unused parameter names after `[` and each top-level comma, including typed tooltips and `key=` insertion. After `=`, booleans complete as `0b`/`1b`, `modePriority` as quoted `"line"`/`"arc"`, numeric defaults with the required suffix, and activation sources as quoted entries. Delete lists provide the same unused-key completion. Booleans accept only `0b` or `1b`; integers accept no decimal or suffix; floats require an `f` suffix and must be finite; strings require quotes. Unknown or duplicate keys, wrong types, values outside the documented ranges, invalid final min/max relationships, missing brackets, and trailing input are rejected at the command cursor. Only canonical case-sensitive names are offered; legacy NBT aliases remain read-compatible but are not command parameters.

`FetchSource` is a quoted string list. It accepts `mainhand`, `offhand`, `inventory`, or `inventory:n`/`inventory:a..b` with `1 <= a <= b <= 27`; repeated and overlapping sources are allowed:

```mcfunction
/pmb skills @e[type=minecraft:zombie,limit=1] bow append [enable=1b,doConsume=1b,modePriority="arc",mobileWhileShooting=1b,lineMinRange=0.0f,lineMaxRange=14.0f,lineSafeDistance=6.0f,lineCooldownTicks=30,lineShootChance=0.8f,lineShootAccuracy=1.0f,lineChargeTicks=10,linePower=0.8f,arcMinRange=12.0f,arcMaxRange=64.0f,arcSafeDistance=10.0f,arcCooldownTicks=50,arcShootChance=0.6f,arcShootAccuracy=0.95f,arcChargeTicks=20,arcAngle=60.0f,arcMaxPower=1.25f]
/pmb skills @e[type=minecraft:zombie,limit=1] bow insert [FetchSource=["mainhand","inventory:1..9","inventory:7..16"]]
/pmb skills @e[type=minecraft:zombie,limit=1] bow modify [lineShootChance=1.0f]
/pmb skills @e[type=minecraft:zombie,limit=1] bow delete [lineShootChance,linePower]
```

Skill compounds are now saved sparsely: an existing child node and the ordered canonical fields actually present in it define node existence and explicit fields. Empty nodes, partial configurations, direct `/summon` data, and later `/data merge` or `/data remove` edits therefore retain their meaning across save and reload. Older full compounds are interpreted as having every present canonical field explicitly written; supported legacy aliases map to their canonical field when read.

## Skill debug probe

The client key `K` (rebindable as **Print PMB Skill Debug Snapshot** under Controls) sends one diagnostic request for the entity under the crosshair. The request is accepted only from a player with gamemaster permission level 2, only for a visible Mob in the same level and inside the player's entity interaction range, and only when that Mob has configured PmbAi skill data. Requests are limited to one every 250 milliseconds per player. The same report is written to the server console/log and shown only in the requesting player's chat as one multiline vanilla system message. It is not broadcast, uses no custom S2C debug payload, and does not synchronize complete PmbAi configuration to the client.

The report is an immutable copy of the most recently completed PMB skill tick and includes `ageTicks`. After entity identity, its sections are ordered as `MELEE`, `SKILLS`, `LAST ATTEMPTS`, `RESOURCES`, `BINDINGS`, and `CONTEXT`, so ordinary-melee suppression is an immediate high-priority warning. `SKILLS` lists only skills whose resolved `enable` value is true; configured-but-disabled and unconfigured skills are hidden. Each listed skill shows its active/ready state, cooldowns, and relevant runtime state. The remaining sections record candidate admission results; sustained and final resource owners; action bindings; the unique strategy and authority target; actual vanilla item-use hand; and the independently applied locomotion, hard-stop, and velocity-modifier result.

Empty sections remain explicit as `SKILLS: []`, `LAST ATTEMPTS: []`, `RESOURCES: {}`, and `BINDINGS: []`, so an empty result cannot be mistaken for a truncated report. The server log stays in stable English technical text. Chat headings and status labels are localized by the receiving client; active/success, waiting, blocked/suppressed, secondary details, and the title use distinct colors while IDs and diagnostic field names remain technical.

`activeThisTick` means that the skill owned a sustained resource at the start of the recorded tick or that its admitted candidate reached a real execution point during that tick. Candidate results distinguish `PRECLAIM_REJECTED`, `RESOURCE_BLOCKED` with the blocking resource and owner, `ADMITTED+FINAL_REJECTED`, and `ADMITTED+EXECUTED`. The probe observes already-computed results: it does not repeat chance rolls, final checks, resource arbitration, item swaps, or actions. A configured Mob without a completed snapshot reports `SNAPSHOT: unavailable` in the log and its localized equivalent in chat.

## Dynamic Factions

Faction is a server-side dynamic relationship module independent of PmbAi skills. Players and every LivingEntity may join exactly one faction. Membership is stored in entity root NBT:

```text
PmbFaction:{
    FactionName:"EmpireGuard"
}
```

FactionName is an ordinary case-sensitive string and is not restricted by Minecraft's lowercase `namespace:path` resource-ID format. Commands accept any non-whitespace name directly without quotation marks, such as `EmpireGuard`, `北方联盟`, or `Raiders-02`. FactionName must refer to an existing server faction. Deleting a faction clears loaded members immediately, while unloaded members clear the invalid name when next loaded. Players retain membership after death and respawn.

PmbFaction is persistent entity NBT read and saved by the server, not temporary client-display data; complete membership is not synchronized to clients. It may be written directly when an entity is summoned, so no follow-up selector is required:

```mcfunction
/pmb faction create IllagerCourt
/summon minecraft:vindicator ~ ~ ~ {PmbFaction:{FactionName:"IllagerCourt"}}
/summon minecraft:pillager ~ ~ ~ {PmbFaction:{FactionName:"IllagerCourt"},PmbAi:{bow:{enable:1b}},equipment:{mainhand:{id:"minecraft:bow"}}}
/data merge entity @e[type=minecraft:vindicator,sort=nearest,limit=1] {PmbFaction:{FactionName:"IllagerCourt"}}
```

`/summon` writes only membership and does not create the faction. The named faction must already exist in server SavedData and FactionName must match its case exactly. An unknown name is cleared on the entity's next server tick. A data-pack function may create the faction first and then run a summon carrying PmbFaction in the same function.

Definitions are stored in overworld SavedData, shared by every dimension, and are not fully synchronized to clients:

```text
Factions:{
    "EmpireGuard":{
        Rules:{
            allowInternalConflict:0b,
            allowFriendlyFire:1b,
            groupRevenge:0b,
            overrideTeamRules:1b,
            defaultAttitude:"neutral",
            evasiveSpeedMultiplier:1.25f
        },
        VanillaCompatRules:{
            piglin:{
                greed:1b,
                guarding:1b,
                avoidance:1b
            },
            breeze:{
                windChargeNoAnger:1b
            }
        },
        Relationships:[
            {
                attitude:"hostile",
                target:{
                    factions:["Raiders"],
                    teams:["red"],
                    types:["minecraft:zombie","#minecraft:undead"],
                    tags:["traitor"],
                    reputation:["1..100"],
                    roles:["guard"]
                }
            }
        ],
        Players:{
            "<uuid>":{
                reputation:0,
                roles:["guard"]
            }
		}
	}
}
```

### Rules

- `allowInternalConflict` defaults to `0b`. Disabled members are always allied; enabling it allows Relationships and defaultAttitude to produce internal hostility or evasion.
- `allowFriendlyFire` defaults to `1b`. At `0b`, the victim's faction blocks melee, projectile-owner, and responsible-explosion damage from members or allies. Environmental damage is unchanged.
- `groupRevenge` defaults to `0b`. Members respond when a member or directional ally is attacked, using each responder's FOLLOW_RANGE, but never retaliate against another member or ally.
- `overrideTeamRules` defaults to `1b`. It allows Faction to replace vanilla same-team targeting, retaliation, and friendly-fire restrictions. At `0b`, vanilla rules retain priority for entities on the same team.
- `defaultAttitude` defaults to `"neutral"` and applies when no Relationship matches.
- `evasiveSpeedMultiplier` defaults to `1.25f` with range `[0.0f,100.0f]` and controls navigation speed during both evasive attitudes.

### VanillaCompatRules

VanillaCompatRules is parallel to Rules and Relationships and exclusively controls how Faction preserves species-specific behavior of vanilla Brain mobs. Rules are grouped by mob before individual compatibility fields. A missing collection, mob group, or field uses its default. Currently supported:

- `piglin.greed` defaults to `1b`. It preserves ordinary piglin gold pickup, admiration, and bartering. Disabling it prevents new pickup or admiration and clears the related Brain memories.
- `piglin.guarding` defaults to `1b`. It lets a neutral ordinary piglin retain ANGRY_AT created by vanilla events such as opening protected containers or breaking gold blocks and start a fight. Disabling it does not prevent neutral retaliation after actual damage.
- `piglin.avoidance` defaults to `1b`. It preserves an ordinary piglin's avoidance of zombified piglins and other vanilla zombified mobs, as well as other vanilla AVOID_TARGET values. This compatibility rule takes priority over Faction hostile, so an entity that vanilla requires the piglin to avoid is not selected as a Faction combat target while the rule is enabled. At `0b`, vanilla avoidance is suppressed and Faction hostile may make the piglin actively attack a zombified piglin. PMB passively_evasive and actively_evasive behavior is unaffected.
- `breeze.windChargeNoAnger` defaults to `1b`. Vanilla wind-charge damage deliberately does not write `lastHurtByMob` for victims in `#minecraft:no_anger_from_wind_charge`. PMB uses the accepted damage source's existing living owner as a neutral-retaliation fallback, but keeps Breeze-owned wind charges non-angering while this rule is enabled. Wind charges from other valid living owners may trigger neutral retaliation; at `0b`, a valid Breeze owner may do so as well. The fallback applies only to Faction-member mobs in that vanilla tag and still requires a living, attackable owner inside the victim's FOLLOW_RANGE whose current attitude is neutral. The general `#minecraft:no_anger` damage-type tag always takes priority and disables this fallback. It does not invent an owner or alter group revenge and other damage types.

Piglin-brute validation of PMB targets is a mandatory fix for Brain state loops rather than an optional species behavior, so it is not part of the piglin group. Future mobs will receive separate groups such as `hoglin:{...}` and `warden:{...}` instead of mixing fields from different species at one level.

Illager Faction combat compatibility likewise leaves vanilla species behavior unchanged outside combat. When a pillager or vindicator has a valid Faction combat target, it temporarily leaves the patrol HoldGround behavior that normally watches in place and waits for the target to approach. A Raider already pathfinding back to a raid or collecting a raid banner also enters Faction combat first. Those vanilla behaviors may start again under their original conditions after the target becomes invalid. Because an evoker has no vanilla combat Goal that approaches its enemy, it approaches a Faction target to within 16 blocks while not casting and then continues using vanilla summoning and fang spells; this adds no new damage method.

### Relationships and attitudes

Relationships are checked in list order and the last matching rule wins. Non-empty target categories use AND, while values inside each category use OR. Empty targets are rejected.

- `factions` matches the target's unique FactionName; `teams` matches vanilla scoreboard team names.
- `types` accepts entity type IDs and `#` entity-type tags; `tags` matches entity `/tag` values.
- `reputation` accepts exact `"5"`, upper-bound `"..100"`, lower-bound `"1.."`, and closed `"1..100"` forms.
- `reputation` and `roles` only match players and read their Players record inside the rule owner's faction. A player does not need membership to have either value.

Attitudes have fixed meanings:

- `hostile` proactively acquires visible targets inside FOLLOW_RANGE, retains pursuit through temporary loss of sight, and clears targets outside range.
- `neutral` never proactively acquires a target but may retaliate after an effective attack. With `VanillaCompatRules.piglin.guarding:1b`, vanilla piglin ANGRY_AT events such as opening protected containers or breaking gold blocks are retained as species-specific reactive hostility and may also start a fight. For victims in vanilla's wind-charge no-anger tag, `VanillaCompatRules.breeze.windChargeNoAnger` controls whether a Breeze owner keeps that exception; other valid living owners can still trigger PMB neutral retaliation.
- `allied` neither attacks nor retaliates. The victim's allowFriendlyFire decides whether actual damage is blocked.
- `passively_evasive` flees after being attacked until the attacker leaves FOLLOW_RANGE.
- `actively_evasive` flees when a visible threat enters FOLLOW_RANGE and stops after it leaves.

Faction scanning, retaliation, existing-target validation, and piglin-specific Brain target resolution all exclude dead entities and players in creative or spectator mode. Even if such a player attacks a member, it is not written as a Faction combat or avoid target. The vanilla hurt-retaliation entry points of piglins, piglin brutes, and hoglins also stop for non-survival attackers, preventing the repeated weapon-raise, weapon-lower, and roar loop caused by vanilla Brain and Faction target contention.

Faction attitude overrides vanilla proactive hostility for members and manages both ordinary Mob targets and Brain ATTACK_TARGET/AVOID_TARGET memories. For Goal mobs, PMB reasserts the Faction target after vanilla targetSelector finishes and before the attack and movement goalSelector runs, preventing a shorter vanilla acquisition range from removing a distant target that remains inside FOLLOW_RANGE; this does not add a generic melee Goal. A Brain memory is read only when its MemoryModule is registered and is written only when its target changes. Faction does not unconditionally erase vanilla AVOID_TARGET memories it does not own. A dedicated piglin and piglin-brute compatibility layer makes PMB combat targets pass vanilla target-validity checks while preserving neutral piglins' vanilla ANGRY_AT-driven guarding and greed behavior. Illager patrol HoldGround, raid pathfinding, and banner collection no longer suppress higher-priority Faction combat; an evoker, which has no vanilla approach Goal, only receives the dedicated navigation needed to enter the effective range of its existing spells and gains no new attack. hostile supplies a target but never grants a generic melee skill. Both evasive attitudes retain a valid navigation path whose destination is already farther from the threat; when repathing is required, the mob's pathfinder first selects a reachable away position. A failed ground path no longer points move control directly into a wall or another unreachable coordinate, while flying and aquatic navigation can still use stable direct-away movement.

### `/pmb faction` commands

All management commands require game-master permission:

```mcfunction
/pmb faction create <faction>
/pmb faction delete <faction>
/pmb faction list
/pmb faction info <faction>
/pmb faction rule set allowInternalConflict <faction> <true|false|1b|0b>
/pmb faction rule set allowFriendlyFire <faction> <true|false|1b|0b>
/pmb faction rule set groupRevenge <faction> <true|false|1b|0b>
/pmb faction rule set overrideTeamRules <faction> <true|false|1b|0b>
/pmb faction rule set defaultAttitude <faction> <attitude>
/pmb faction rule set evasiveSpeedMultiplier <faction> <float>
/pmb faction compat set piglin greed <faction> <true|false|1b|0b>
/pmb faction compat set piglin guarding <faction> <true|false|1b|0b>
/pmb faction compat set piglin avoidance <faction> <true|false|1b|0b>
/pmb faction compat set breeze windChargeNoAnger <faction> <true|false|1b|0b>
/pmb faction relationship add <faction> <attitude> <target_snbt>
/pmb faction relationship insert <faction> <index> <attitude> <target_snbt>
/pmb faction relationship replace <faction> <index> <attitude> <target_snbt>
/pmb faction relationship remove <faction> <index>
/pmb faction relationship move <faction> <from> <to>
/pmb faction relationship list <faction>
/pmb faction member set <faction> <entities>
/pmb faction member clear <entities>
/pmb faction member get <entity>
/pmb faction reputation set <faction> <players> <value>
/pmb faction reputation add <faction> <players> <value>
/pmb faction reputation get <faction> <players>
/pmb faction role add <faction> <players> <role>
/pmb faction role remove <faction> <players> <role>
/pmb faction role clear <faction> <players>
/pmb faction role list <faction> <players>
```

Relationship targets use an SNBT compound:

```mcfunction
/pmb faction create EmpireGuard
/pmb faction create Raiders
/pmb faction rule set groupRevenge EmpireGuard 1b
/pmb faction compat set piglin guarding EmpireGuard 1b
/pmb faction compat set breeze windChargeNoAnger EmpireGuard 1b
/pmb faction relationship add EmpireGuard hostile {factions:["Raiders"]}
/pmb faction relationship add EmpireGuard allied {teams:["blue"],tags:["trusted"]}
/pmb faction relationship add EmpireGuard neutral {types:["minecraft:player"],reputation:["25.."],roles:["visitor"]}
/pmb faction member set EmpireGuard @e[type=minecraft:vindicator,distance=..16]
/pmb faction reputation set EmpireGuard @a 30
/pmb faction role add EmpireGuard @a[tag=visitor] visitor
```

Member assignment requires an existing faction and a selection containing only LivingEntity instances. Relationship writes validate attitudes, factions, teams, entity types, type tags, reputation ranges, and list indices. Bulk assignment changes only the currently selected entities and never stores or continuously maintains a FactionMembers list.

## Complete data structure

```text
{
    PmbAi:
        {
            shield:
                {
                    enable: <bool>,
                    FetchSource: [<string>, ...],
                    preferredHand: <string>,
                    range: <float>,
                    shieldChance: <float>,
                    minUseTicks: <int>,
                    maxUseTicks: <int>,
                    cooldownTicks: <int>,
                    randomCooldownBias: <int>,
                    blockingAngle: <float>,
                    axeDisableCooldownTicks: <int>,
                    shieldToughness: <int>,
                    critToughnessDamage: <int>,
                    disableVulnerTicks: <int>,
                    vulnerDamageMultiplier: <float>,
                    disableKBMultiplier: <float>,
                    speedReduction: <float>
                },
            wind_charge:
                {
                    enable: <bool>,
                    FetchSource: [<string>, ...],
                    preferredHand: <string>,
                    doConsume: <bool>,
                    inAirTrackStrength: <float>,
                    throwRange: <float>,
                    throwChance: <float>,
                    throwCooldownTicks: <int>,
                    throwAccuracy: <float>,
                    bounceRange: <float>,
                    bounceChance: <float>,
                    bounceCooldownTicks: <int>,
                    randomCooldownBias: <int>
                },
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
                    doConsume: <bool>,
                    requireEyeSight: <bool>,
                    randomCooldownBias: <int>
                },
            mace:
                {
                    enable: <bool>,
                    FetchSource: [<string>, ...],
                    smashRange: <float>,
                    hitChance: <float>,
                    damageReduction: <float>,
                    smashCooldownTicks: <int>,
                    randomCooldownBias: <int>
                },
            bow:
                {
                    enable: <bool>,
                    FetchSource: [<string>, ...],
                    preferredHand: <string>,
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
		},
	PmbFaction:
		{
			FactionName: <string>
		},
	PmbInventory:
		{
			Slots: <int>,
			DropChance: <float>,
			Items: [{Slot: <int>, id: <item id>, count: <int>}, ...]
		}
}
```

## Skill scheduling and PmbInventory

All PMB skills now run through one server-side Mob tick. The scheduler selects one strategy state: Faction avoidance produces `retreat`, otherwise a valid attack target produces `combat`, and no authoritative target produces `idle`. Shield-break vulnerability, NoAI, and death block new skill actions. A strategy or authoritative-target change cancels sustained PMB actions instead of silently retargeting them.

The default conflict category order is `main > off > throw > food > block` in combat, `throw > off > food > main > block` while retreating, and `block > food > off > main > throw` while idle. Category order only resolves genuine resource conflicts; unrelated resources can act during the same tick. Mace ranks above bow inside `main`, and ender pearl ranks above wind charge inside `throw`. Each physical hand performs at most one PMB action per game tick. Sorted candidates first start their cooldown and roll chance; only successful rolls claim resources. A resource loser has therefore already rolled and retains its cooldown, but never moves an item or enters a retry queue.

Wind-charge bounce keeps its existing bounce-first and failed-bounce fall-through behavior. Continued flight owns LOOK, while airborne horizontal tracking submits an independent velocity modifier. Flight does not reserve a hand, USE_ITEM, or SMASH. NAVIGATION is no longer an exclusive skill resource.

Each skill accepts the optional ordered FetchSource string list: mainhand, offhand, inventory, inventory:1, or inclusive ranges such as inventory:1..9. Public slots are 1–27 and ranges may overlap. The destination hand is selected first. A compatible item already there is used directly without consulting FetchSource; otherwise sources are scanned in list order and slots in ascending order. Omission uses the previous source-hand order. An empty or invalid list offers no fetch locations but does not disable a compatible item already in the destination hand.

bow, shield, wind_charge and ender_pearl support preferredHand: main, off, main-enforce, or off-enforce. Bow defaults to main; the other three default to off. Mace does not accept this field and executes in mainhand. At action startup only, plain main/off may fall back when the preferred hand belongs to an ongoing PMB action. Static held items do not block a hand. Enforced preferences fail when their hand is busy. A same-tick arbitration loss never retries another hand.

Busy source and destination hands cannot be exchanged; unavailable sources are skipped. A cross-hand swap reserves both hands for that tick, then a sustained action binds only its actual use hand. Each check selects one item; a compatible destination item takes precedence over other copies in FetchSource.

Every successful equipment exchange is permanent. Completion, cancellation, NoAI, unloading, conversion and death never restore the previous layout. ActionBinding validates the current action item and binds its hand until completion. Displaced weapons or arrows may become stranded outside FetchSource/AmmoSource; configuration authors must cover the required locations. Final held items follow vanilla equipment drop rules, while final inventory items follow PMB inventory drop rules.

Movement is resolved separately: shield-vulnerability HARD_STOP, retreat locomotion, active-skill locomotion, other passive movement, then vanilla Goal/Brain movement. Ties use strategy category, conflictRank and stable skill ID. One LOCOMOTION wins per tick; VELOCITY_MODIFIER may add horizontal steering while preserving vertical velocity. Losing movement never cancels a skill or advances cooldown. Intents are revalidated before the next AI movement-control phase. Drawing a bow owns its actual hand, USE_ITEM and LOOK, and bow locomotion exists only during that active draw. Vulnerability stops AI motion while preserving gravity and knockback.



Example (insert into an existing bow node; use modify for fields already written):

```text
/pmb skills @n[type=husk] bow insert [preferredHand="off-enforce",FetchSource=["mainhand","inventory:1..9"],AmmoSource=["inventory:1..9"]]
```

`PmbInventory` belongs only to Mob entities and is independent of `PmbAi`:

```text
PmbInventory:{
    Slots:<int>,
    DropChance:<float>,
    Items:[
        {Slot:<int>,id:<item id>,count:<int>}
    ]
}
```

`Slots` has range `[0,27]` and default `0`; `DropChance` has range `[0.0f,1.0f]` and default `0.5f`. Each enabled, non-empty slot rolls independently on death and a successful roll drops the entire stack. With `doMobLoot:false`, no PMB inventory items drop. Reducing `Slots` first merges or moves high-slot contents into enabled low slots, then immediately drops full remainders. Invalid slot numbers outside 1–27 are ignored.

When `CanPickUpLoot:1b` and `mobGriefing:true`, vanilla equipment and species-specific pickup logic runs first. A nearby item stack left on the ground is then merged into PmbInventory like a player inventory; a full inventory leaves the remainder on the ground. This does not add item-seeking navigation or a GUI.

When bow `AmmoSource` is omitted, supported ammunition held in either hand has priority, followed by PmbInventory from its lowest enabled slot upward; scanning stops at the first compatible stack. An explicit `AmmoSource` instead uses only its own ordered locations. Ammunition is read directly and does not need to occupy another hand. With `doConsume:0b`, a real special arrow is used without consumption, or an absent arrow falls back to unlimited normal arrows. With `doConsume:1b`, a real compatible stack is required and one item is removed only after release.

Example:

```text
summon zombie ~ ~ ~ {CanPickUpLoot:1b,PmbInventory:{Slots:9,DropChance:0.5f,Items:[{Slot:1,id:"minecraft:mace",count:1},{Slot:2,id:"minecraft:wind_charge",count:16}]},PmbAi:{mace:{enable:1b,FetchSource:["mainhand","inventory:1..9"]},wind_charge:{enable:1b,FetchSource:["offhand","inventory:1..9"],doConsume:1b}}}
```

Skill-check cooldowns persist and account for elapsed world time. Sustained actions do not resume, while the permanent equipment layout is saved normally. Old PmbSwapJournal entries receive a one-time load recovery only when both snapshots match. Changed slots are not overwritten; new swaps write no recovery journal and retain no pending restoration lease.

## PmbAi

PmbAi is the root data tag of this mod. It must be written inside a mob's NBT at the same level as other root tags such as Health, Motion, and equipment.

PmbAi is a compound tag. It can contain multiple manually added AI skills, each represented by another compound tag containing its parameters.

PmbAi runs only while the mob is alive. As soon as health reaches zero and the death animation begins, new AI checks and melee execution stop, and any unreleased bow charge, shield use, wind-charge bounce tracking, or mace execution state is cancelled. A dying mob cannot continue attacking, throwing, or shooting during the animation. This gate affects only AI and skill state: it does not clear death-knockback velocity, disable gravity, or replace the vanilla death process.

All five skills expose `randomCooldownBias`, an integer in `[0, 1200]` with default `20`. Whenever an active skill check starts a cooldown, its actual duration is the configured base cooldown plus one independently drawn, uniformly distributed integer in the inclusive interval `[0, randomCooldownBias]`; `0` restores a fixed cooldown. Bow line/arc and wind-charge throw/bounce share one field within their skill but draw separately. This field does not affect shield-disable or vulnerability timers, use/charge duration, airborne tracking, or temporary look duration. Missing legacy data defaults to `20`.

An eligible skill check starts that cooldown and rolls its chance before resource arbitration. Chance `0.0` always fails and `1.0` always succeeds without consuming a random draw. A failed roll claims no action resource. A successful roll keeps its cooldown if resources are unavailable or final target/item validation fails; it is neither cached nor retried before the next normal check. Items are exchanged or consumed only after the action wins resources. Bow does not fall back to its other mode after a selected-mode failure. Wind charge checks throw in the same tick only when bounce's chance fails; a successful bounce roll does not fall back after later failure.

By default, mobs do not spawn with the PmbAi root tag.
This means that naturally spawned mobs have no AI behaviour changes caused by this mod.

Currently, players must manually add PmbAi using the data merge command, a summon command, or a data pack function.

<details>
<summary>abstract structure</summary>

```text
PmbAi:
    {
        shield:{<parameters>},
        mace:{<parameters>},
        bow:{<parameters>},
        ...
    }
```

shield, wind_charge, mace, bow, and ender_pearl are currently implemented.

</details>

### wind charge

The wind-charge AI gives mobs holding a vanilla wind charge two modes: throwing and self-bouncing. It also provides a shared foundation for wind-charge mace and future wind-charge spear behaviours. The mace skill now supplies a dedicated falling-smash check and can work with post-bounce target retention and airborne movement. Dedicated wind-charge spear attack decisions are not implemented yet.

Conditions for wind-charge use:

1. The entity is a mob and holds a vanilla wind charge in either hand.
2. enable is true.
3. The mob has a living hostile target, or it is currently fleeing through Faction passively_evasive/actively_evasive with a valid threat target.
4. The mob does not have NoAI enabled and is not in shield-break vulnerability.
5. The distance, cooldown, and chance checks for the corresponding mode succeed.

Bounce mode takes priority when both modes can be checked. When the mob is on the ground and its target is within bounceRange, it checks bounceChance every bounceCooldownTicks game ticks. On success, it immediately looks down, swings the hand holding the wind charge, jumps, and fires a wind charge beneath its feet so that the explosion produces as much upward launch as possible.

When a visible target is within throwRange, throw mode checks throwChance every throwCooldownTicks game ticks. On success, the mob swings the hand holding the wind charge and throws at a lead point calculated from the target's position and velocity. throwAccuracy controls vanilla projectile spread; 1.0 means no random spread.

During Faction evasion, wind-charge AI uses the current Faction avoid target as its wind-charge target without restoring it as an attack target. On release, throw mode briefly turns the view, head, and body toward the pursuing threat while leaving escape navigation intact, then resumes fleeing. Bounce mode still uses bounceRange, bounceChance, and bounceCooldownTicks for its downward launch, but airborne steering and body facing point away from the threat. Both modes continue to obey all existing range, chance, cooldown, accuracy, and doConsume settings; evasion never bypasses those checks.

doConsume controls consumption for both throw and bounce mode. By default, the held wind charge only acts as the skill's required equipment and is not consumed. When enabled, every successful throw or bounce consumes one wind charge from the hand that actually performed the action.

The downward bounce uses the vanilla living-entity impulse fall-protection context. Returning to the height where the wind-charge impulse occurred does not deal fall damage from that launch. If the mob lands below the launch point, only the additional drop below that height can still deal normal fall damage. Once the mob has actually left the ground after this bounce and lands again, the server plays the vanilla player's no-damage landing sound once. Never leaving the ground, dying, or using throw mode alone does not trigger it.

After being launched by its own wind charge, the mob remembers that target for up to 60 game ticks. A normal combat bounce restores an attack target temporarily cleared by vanilla airborne pathfinding and applies small, limited targetward acceleration controlled by inAirTrackStrength. A Faction evasive bounce never writes an attack target and instead applies the same limited acceleration away from the threat. Both preserve gravity, wind-charge launch velocity, and existing horizontal inertia rather than replacing movement with conspicuous tracking velocity. A value of 0.0 adds no horizontal steering acceleration.

After the initial downward bounce pose ends, a combat bounce continuously aligns the mob's view, head, and body with the target, while an evasive bounce faces along the direction away from the threat. If a combat-bouncing mob starts a melee attack before the downward pose ends, that pose ends immediately and it faces the entity it is actually attacking, preventing hits while visibly facing away. Facing correction is independent of inAirTrackStrength. The tracking state clears when the mob lands, the target becomes invalid, or the time expires. Consuming the final wind charge with doConsume does not prematurely cancel airborne steering that has already begun.

A grounded bounce retains its existing two-tick launch grace. On the first tick that meets the landing termination boundary, it releases LOOK before scheduler arbitration and before writing pose or look state, so an ended bounce cannot reject another otherwise valid skill check.

<details>
<summary>parameters structure</summary>

```text
wind_charge:
    {
        enable: <bool>,
        FetchSource: [<string>, ...],
        preferredHand: <string>,
        doConsume: <bool>,
        inAirTrackStrength: <float>,
        throwRange: <float>,
        throwChance: <float>,
        throwCooldownTicks: <int>,
        throwAccuracy: <float>,
        bounceRange: <float>,
        bounceChance: <float>,
        bounceCooldownTicks: <int>,
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
Range: mainhand, offhand, inventory, inventory:1 through inventory:27, or inclusive ranges such as inventory:1..9
Default: omitted; uses this skill's legacy source-hand order
Note: Used only when the destination hand lacks a suitable item; empty/invalid lists supply no fetch location

doConsume
Meaning: Whether a successful throw or downward self-bounce consumes one wind charge
Type: bool
Range: 0b, 1b
Default: 0b
Note: Controls both modes and consumes from the hand actually holding the wind charge when the action triggers

inAirTrackStrength
Meaning: Maximum horizontal acceleration added toward the target each game tick after a downward self-bounce
Type: float
Range: [0.0f, 1.0f]
Default: 0.012f
Note: Added only while targetward horizontal velocity is below an internal threshold; 0.0 disables horizontal tracking but does not disable facing the target while airborne

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
summon zombie ~ ~ ~ {PmbAi:{wind_charge:{enable:1b}}, equipment:{mainhand:{id:iron_sword}, offhand:{id:wind_charge}}}

Summon a zombie that throws perfectly accurate wind charges and always uses a wind-charge bounce to pursue nearby targets. Each successful use consumes one wind charge.
summon zombie ~ ~ ~ {PmbAi:{wind_charge:{enable:1b, doConsume:1b, inAirTrackStrength:0.012f, throwRange:24.0f, throwChance:1.0f, throwCooldownTicks:40, throwAccuracy:1.0f, bounceRange:6.0f, bounceChance:1.0f, bounceCooldownTicks:60}}, equipment:{mainhand:{id:wind_charge,count:64}}}

Disable wind-charge AI on the nearest zombie without removing its other configured parameters.
data merge entity @e[type=zombie,sort=nearest,limit=1] {PmbAi:{wind_charge:{enable:0b}}}
```

</details>

### ender pearl

Ender-pearl AI lets a mob holding a vanilla ender pearl in either hand calculate and throw a trajectory automatically. Ordinary combat uses the current attack target as the destination. During Faction passively_evasive or actively_evasive flight, it instead uses an escape position away from the current avoid threat.

Conditions for a throw check:

1. The entity is a mob and holds a vanilla ender pearl in either hand.
2. enable is true and throwChance is greater than 0.0.
3. The mob has a living attack target, or it is currently executing Faction evasion with a valid avoid threat.
4. With requireEyeSight 1b, an ordinary attack target must be visible and between minThrowRange and maxThrowRange; a Faction avoid threat must be visible and no farther than maxThrowRange. With 0b, only this visibility requirement is skipped. For an original vanilla TargetGoal only, 0b may also retain its already acquired mob.getTarget() through obstruction when it is alive, in the same level, inside FOLLOW_RANGE and this combat throw range, throwChance is greater than 0.0, and FetchSource can resolve a pearl to either hand. It never changes initial target discovery.
5. The skill is not in the throw-check cooldown defined by throwCooldownTicks.
6. The mob does not have NoAI enabled and is not in shield-break vulnerability.

Every eligible check immediately enters throwCooldownTicks cooldown before rolling throwChance, so a failed chance roll also starts cooldown. A value of 0 game ticks permits another check on the next game tick. On success, the mob faces the actual launch trajectory, swings the hand holding the pearl, and plays the vanilla ender-pearl throw sound.

maxThrowPower is not an absolute initial speed. It limits solved speed as a multiplier of the vanilla stationary-player ender-pearl launch speed of 1.5. The default 1.0 allows speeds up to 1.5, while 2.0 allows up to 3.0. throwAngle fixes the launch angle above horizontal and defaults to 30 degrees. The solver uses the discrete vanilla air trajectory with drag 0.99 and gravity 0.03, binary-searches the required speed at that fixed angle, and iteratively leads horizontal target movement while preserving its current eye height. If the geometry is valid but 1.5 times maxThrowPower is insufficient, it still launches at that limit and may fall short. Impossible fixed-angle geometry, near-zero horizontal distance, or an invalid numerical solution skips the throw without consuming a pearl; the normal check cooldown remains. The calculation has no 300-tick trajectory cutoff.

Pearls are not thrown while their launch point (0.1 blocks below eye height) is submerged in water. Shallow water remains usable when that point is above the actual fluid surface. This check applies both before submitting a throw and immediately before release, in combat and evasive mode.

minThrowRange and maxThrowRange define the ordinary combat throw-check interval, preventing repeated pearl use after a target gets too close. They do not guarantee that the configured maxThrowPower and throwAngle physically cover the whole interval. Faction evasion ignores minThrowRange: with requireEyeSight 1b, a check is allowed whenever the avoid threat is visible and no farther than maxThrowRange; 0b skips only that visibility requirement. The pearl is neither thrown toward that threat nor used to write an attack target. Its destination prefers the current reachable escape-navigation target that is farther from the threat and is clamped to maxThrowRange; without a valid navigation target, it uses a stable directly-away position. Pearl impact continues through vanilla ender-pearl teleportation, collision, and damage handling.

throwAccuracy 1.0 adds no random spread; lower values add progressively more spread. With doConsume 0b, the held pearl only acts as required equipment and is not consumed. With doConsume 1b, one pearl is consumed from the hand that actually swings after a successful release, and the final pearl explicitly synchronizes an empty hand. Range and chance checks never consume an item early.

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
        doConsume: <bool>,
        requireEyeSight: <bool>,
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
Range: mainhand, offhand, inventory, inventory:1 through inventory:27, or inclusive ranges such as inventory:1..9
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
summon zombie ~ ~ ~ {PmbAi:{ender_pearl:{enable:1b}}, equipment:{mainhand:{id:iron_sword}, offhand:{id:ender_pearl}}}

Summon a zombie that throws accurately from 6 through 32 blocks at a 30-degree angle, allows up to twice vanilla speed, and consumes pearls.
summon zombie ~ ~ ~ {PmbAi:{ender_pearl:{enable:1b, minThrowRange:6.0f, maxThrowRange:32.0f, throwChance:1.0f, throwCooldownTicks:60, throwAccuracy:1.0f, maxThrowPower:2.0f, throwAngle:30.0f, doConsume:1b}}, equipment:{mainhand:{id:ender_pearl,count:16}}}

Summon a vindicator whose pearl skill can be driven by a Faction evasive relationship toward an escape position.
summon vindicator ~ ~ ~ {PmbAi:{ender_pearl:{enable:1b, minThrowRange:8.0f, maxThrowRange:24.0f, throwChance:1.0f}}, equipment:{offhand:{id:ender_pearl}}}

Disable ender-pearl AI on the nearest zombie without removing its other configured parameters.
data merge entity @e[type=zombie,sort=nearest,limit=1] {PmbAi:{ender_pearl:{enable:0b}}}
```

</details>

### mace

Mace AI executes in mainhand. A vanilla mace already there is used directly; otherwise FetchSource supplies a permanent exchange. An offhand-source swap reserves both hands plus SMASH for that tick. Falling, target, chance and cooldown conditions still apply. Mace does not accept preferredHand.

Conditions for a smash check:

1. The entity is a mob and FetchSource resolves a vanilla mace to the required main-hand attack path.
2. enable is true.
3. The mob is airborne, moving downward, and meets the vanilla minimum fall distance for a mace smash.
4. The mob has a living, attackable, visible hostile target within smashRange.
5. The mob is not in the smash-check cooldown defined by smashCooldownTicks.
6. The mob does not have NoAI enabled and is not in shield-break vulnerability.

Once these conditions are met, the mob immediately performs one hitChance check. It enters the smashCooldownTicks cooldown whether that check succeeds or fails. On success, the mob faces the target, swings its main hand, and attacks through the vanilla Mob and mace damage paths. On failure, no attack is performed.

While the skill is enabled and the mob is in a valid falling-mace state, attacks attempted directly by an ordinary melee goal without this skill's check are prevented. This stops vanilla AI from bypassing hitChance, smashCooldownTicks, or damageReduction. Ordinary melee attacks remain unchanged whenever the mob is not in a valid mace-smash fall.

Vanilla first calculates base attack damage, enchantments, and fall-height mace bonus. The final result of the skill-triggered smash is then multiplied by (1-damageReduction). The default damageReduction of 0.5 therefore halves the final mace damage. Vanilla mace sounds, knockback, durability use, and fall-state handling are preserved.

<details>
<summary>parameters structure</summary>

```text
mace:
    {
        enable: <bool>,
        FetchSource: [<string>, ...],
        smashRange: <float>,
        hitChance: <float>,
        damageReduction: <float>,
        smashCooldownTicks: <int>,
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
Range: mainhand, offhand, inventory, inventory:1 through inventory:27, or inclusive ranges such as inventory:1..9
Default: omitted; uses this skill's legacy source-hand order
Note: Used only when the destination hand lacks a suitable item; empty/invalid lists supply no fetch location

smashRange
Meaning: Maximum target distance at which a falling mob can immediately start a smash check; line of sight is also required
Type: float
Range: [0.0f, 64.0f]
Default: 3.0f

hitChance
Meaning: Chance for each smash check to succeed and perform the attack
Type: float
Range: [0.0f, 1.0f]
Default: 0.5f

damageReduction
Meaning: Final damage reduction ratio for a skill-triggered mace smash; actual damage is multiplied by (1-damageReduction)
Type: float
Range: [0.0f, 1.0f]
Default: 0.5f
Note: 0.0 leaves damage unchanged, 0.5 halves final damage, and 1.0 reduces final damage to 0

smashCooldownTicks
Meaning: Game ticks of cooldown after every smash check, whether the check succeeds or fails
Type: integer
Range: [0, 72000]
Default: 100
Note: 0 allows another check on the next game tick

randomCooldownBias
Meaning: Inclusive uniform random extra game ticks added whenever a smash-check cooldown starts
Type: integer
Range: [0, 1200]
Default: 20
Note: 0 makes smash cooldown fixed
```

Values outside these ranges are automatically clamped when the entity data is read.
One second is normally equal to 20 game ticks.

</details>

<details>
<summary>command examples</summary>

```text
Summon a zombie holding a mace in its main hand and using all default mace parameters.
summon zombie ~ ~ ~ {PmbAi:{mace:{enable:1b}}, equipment:{mainhand:{id:mace}}}

Summon a zombie that always performs a smash check within 4 blocks, deals 75% of normal mace damage, and enters a 3-second cooldown after every check.
summon zombie ~ ~ ~ {PmbAi:{mace:{enable:1b, smashRange:4.0f, hitChance:1.0f, damageReduction:0.25f, smashCooldownTicks:60}}, equipment:{mainhand:{id:mace}}}

Summon a zombie with both downward wind-charge bounce and mace-smash AI enabled.
summon zombie ~ ~ ~ {PmbAi:{wind_charge:{enable:1b, bounceRange:6.0f, bounceChance:1.0f}, mace:{enable:1b}}, equipment:{mainhand:{id:mace}, offhand:{id:wind_charge}}}

Disable mace AI on the nearest zombie without removing its other configured parameters.
data merge entity @e[type=zombie,sort=nearest,limit=1] {PmbAi:{mace:{enable:0b}}}
```

</details>

### bow

Bow AI selects its use hand through preferredHand, default main. A vanilla bow already in that hand is used directly; otherwise FetchSource supplies a permanent exchange. Bow remains a main-category skill, but sustained drawing reserves only its actual hand, USE_ITEM and LOOK. It provides low-trajectory line and high-angle arc modes; range controls check eligibility while power and vanilla physics determine actual reach.

Both modes require a living, attackable target. With requireEyeSight 1b, that target must also be visible; 0b skips only this visibility requirement. For an original vanilla TargetGoal only, 0b may also retain its already acquired mob.getTarget() through obstruction when it is alive, in the same level, inside FOLLOW_RANGE and an enabled line or arc shooting range, and a bow is available under the destination-hand and FetchSource rules. It never changes initial target discovery. This TargetGoal-only rule does not affect Faction or Brain targeting, any evasive path including pearl evasion, general melee, or other skills. line can be checked from lineMinRange through lineMaxRange. arc can be checked from arcMinRange through arcMaxRange. Setting either ShootChance to 0.0 completely disables that mode.

When both modes satisfy their distance, ammunition, and cooldown conditions, modePriority selects line or arc. A failed chance check by the priority mode does not fall back to the other mode during the same tick. If the priority mode is still cooling down or otherwise not ready, the other mode may be checked. Selecting a mode immediately starts its CooldownTicks whether the chance check succeeds or fails.

After a successful chance check, the mob draws the resolved bow from its current hand for the corresponding ChargeTicks. A value of 0 releases during the same tick. While drawing, its head and both arms continuously follow the actual calculated launch vector, so line displays low-trajectory aiming and arc visibly raises the bow to its designed angle. Charging is cancelled while preserving the cooldown if the target dies, becomes unattackable, leaves the current mode's range, loses line of sight while requireEyeSight is 1b, the bow leaves its current use hand, required ammunition becomes unavailable, NoAI is enabled, or shield-break vulnerability begins.

linePower and arcMaxPower are multipliers of the vanilla player's fully drawn bow initial speed of 3.0, not absolute initial speeds. line uses 3.0 times linePower as its fixed initial speed and calculates a low trajectory from the target's latest position, velocity, and light gravity compensation. arc uses arcAngle as a fixed angle above horizontal, numerically simulates vanilla arrow drag of 0.99 and gravity of 0.05, binary-searches the required initial speed, and iteratively leads a moving target. If the required speed exceeds 3.0 times arcMaxPower or no complete solution exists, it still fires at that speed limit and may miss because of insufficient physical reach. Accuracy 1.0 adds no random spread.

`AmmoSource` independently limits where bow AI reads real supported arrows. It uses the same ordered source syntax as FetchSource, but never moves or exchanges ammunition. When omitted, supported arrows in either hand are preferred, followed by enabled PmbInventory slots from low to high, preserving the previous behavior. An explicit list scans only its written locations in order; a present empty or invalid list has no real ammunition source. When a bow exchange displaces a previously selected hand arrow, that arrow follows its stack into the other hand or inventory slot for this action only. Subsequent actions scan AmmoSource again at the actual new layout. No separate ammunition exchange or long-lived source exemption is created. With doConsume 0b, ammunition is not consumed and no real arrow falls back to unlimited normal arrows. With doConsume 1b, a real supported arrow is required and one is consumed from its actual source only after release. The bow does not take additional durability damage. FetchSource only resolves the bow itself and never restricts this ammunition lookup.

Ordinary melee is suppressed by a lightweight live predicate only when bow is enabled; the current target is non-null, alive, in the same level and attackable; a bow is already held in an allowed hand; the target distance is inside at least one line or arc closed interval whose ShootChance is greater than zero; and, when doConsume is 1b, AmmoSource still resolves a supported arrow. Soft main/off preferences accept a bow already held in either hand, while main-enforce and off-enforce require their named hand. Inventory bows, FetchSource availability and action bindings do not count as held bows. Cooldowns, the current random roll, resource claims, line of sight/requireEyeSight, modePriority, SafeDistance, mobileWhileShooting and FOLLOW_RANGE do not participate in this predicate. When any required live condition becomes false, vanilla melee Goal navigation and ordinary direct melee damage are immediately available again. FetchSource remains responsible for starting the skill and exchanging the bow; it does not independently suppress melee. The K-key report uses this same predicate for its `bow` suppression reason.

A scheduler-selected PMB mace smash remains allowed, so a valid bow suppression state does not cancel a legal falling-mace combination. Bow movement takes control only after a successful check has acquired the required action resources, bound the bow and begun drawing. During that draw, entering the corresponding SafeDistance makes the mob retreat while aiming regardless of mobileWhileShooting. Outside SafeDistance but still inside the mode range, mobileWhileShooting 1b enables skeleton-like randomized forward, backward, and sideways strafing; 0b keeps the shooter stationary. Cooldown, a failed chance check, missing ammunition or an action-resource conflict submits no bow locomotion and no longer intercepts vanilla navigation or movement control. Whether the mob actually pursues still depends on an applicable vanilla Goal or Brain behavior remaining able to run. Vanilla RangedBowAttackGoal suppression remains broader and unchanged so skeletons cannot bypass PMB firing rules.

Vanilla RangedBowAttackGoal draw, fire, and strafe writes are suppressed so skeletons cannot bypass the configured modes, chances, cooldowns, or mobileWhileShooting; PMB's own retreat and randomized movement commands remain permitted. The bow pose is forcibly reapplied at the end of model animation, preventing zombie attack animations, illager crossed arms, or other model-specific animations from replacing it. The special held-item layers used by vindicators and evokers also render the bow and arms.

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
Range: mainhand, offhand, inventory, inventory:1 through inventory:27, or inclusive ranges such as inventory:1..9
Default: omitted; uses this skill's legacy source-hand order
Note: Used only when the destination hand lacks a suitable item; empty/invalid lists supply no fetch location

AmmoSource
Meaning: Ordered locations from which bow AI may read real supported arrows
Type: list of strings
Range: mainhand, offhand, inventory, inventory:1 through inventory:27, or inclusive ranges such as inventory:1..9
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
summon zombie ~ ~ ~ {PmbAi:{bow:{enable:1b}}, equipment:{mainhand:{id:bow}}}

Summon a skeleton with 64 off-hand arrows, real arrow consumption, and customized line and arc modes.
summon skeleton ~ ~ ~ {PmbAi:{bow:{enable:1b, doConsume:1b, modePriority:"arc", mobileWhileShooting:1b, lineMinRange:0.0f, lineMaxRange:14.0f, lineSafeDistance:6.0f, lineCooldownTicks:30, lineShootChance:0.8f, lineShootAccuracy:1.0f, lineChargeTicks:10, linePower:0.8f, arcMinRange:12.0f, arcMaxRange:64.0f, arcSafeDistance:10.0f, arcShootChance:0.6f, arcShootAccuracy:0.95f, arcChargeTicks:20, arcAngle:60.0f, arcMaxPower:1.25f}}, equipment:{mainhand:{id:bow}, offhand:{id:arrow,count:64}}}

Disable bow AI on the nearest skeleton without removing its other configured parameters.
data merge entity @e[type=skeleton,sort=nearest,limit=1] {PmbAi:{bow:{enable:0b}}}
```

</details>

### shield

Basic shield usage is currently implemented.
An eligible mob repeatedly checks whether it can raise its shield, with a configurable chance of success.

Conditions for raising a shield:

1. The entity is a mob and has a vanilla shield in an allowed activation source. A held shield is used directly in its actual hand; an inventory shield is temporarily placed in the off hand.
2. enable is true.
3. The mob has a living hostile target within range and has line of sight to it.
4. The mob is not in the normal shield cooldown.
5. The mob is not in the forced cooldown caused by shield-disabling attacks.
6. The shieldChance check succeeds.
7. The mob does not have NoAI enabled.

PMB-controlled shields can block from the first shield-use tick and do not use the vanilla player's initial shield activation delay.

For PMB-controlled shields, the blocking angle is resolved from the attacker's entity position when available, rather than only from the raw damage-source position. This avoids close-range or extended-interaction-range source positions flipping across the defender and causing apparent shield penetration.

If the server still sees the mob actively using its shield, PMB can continue recognizing the actual used hand during the final transition tick even if the internal shield-use counter has just reached zero. An ActionBinding validates the actual used-hand item, including authorized shield durability changes or breakage. Ending use releases the binding and retains the final equipment layout.

When the shield is raised, it takes durability damage from blocked attacks.

Attacks capable of disabling shields consume one point of shield toughness.

When the configured number of such attacks is reached during the current shield-use round, shield use stops and the forced cooldown begins.

The counter resets when the normal cooldown ends, before a new round after a forced cooldown, and immediately when the current shield use is interrupted without a true shield break.

Shield recoil, durability loss, and shield toughness consumption occur at most once per normal damage interval. Additional attacks ignored by the entity's damage cooldown do not accumulate these effects.

A fully blocked attack does not produce vanilla critical-hit particles, but the mod captures the player's vanilla critical-hit eligibility before attack charge is reset. A critical shield-disabling attack consumes critToughnessDamage points. If the shield remains intact, a critical shield-disabling attack plays the zombie wooden-door attack sound and emits 10 oak-door debris particles, a normal shield-disabling attack emits 6 particles, and any other blocked attack emits 3 particles. A true shield break instead plays the zombie wooden-door break sound, emits 24 particles, and applies extra shield-break knockback controlled by disableKBMultiplier.

After a true shield break, the mob enters shield-break vulnerability for disableVulnerTicks game ticks. During this window, the mob cannot attack, keeps clearing its target and navigation, and suppresses AI movement input, while existing velocity from knockback or other external forces is preserved. It renders with the same shaking style used by zombie-villager curing or piglin zombification. This does not enable NoAI, so normal physics such as gravity continue to apply. Damage received during this window is multiplied by vulnerDamageMultiplier. If disableVulnerTicks is 0, this vulnerability window is skipped.

Entities from the Guard Villagers namespace are excluded from this behaviour to avoid conflicting with that mod.

<details>
<summary>parameters structure</summary>

```text
shield:
    {
        enable: <bool>,
        FetchSource: [<string>, ...],
        preferredHand: <string>,
        range: <float>,
        shieldChance: <float>,
        minUseTicks: <int>,
        maxUseTicks: <int>,
        cooldownTicks: <int>,
        randomCooldownBias: <int>,
        blockingAngle: <float>,
        axeDisableCooldownTicks: <int>,
        shieldToughness: <int>,
        critToughnessDamage: <int>,
        disableVulnerTicks: <int>,
        vulnerDamageMultiplier: <float>,
        disableKBMultiplier: <float>,
        speedReduction: <float>
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
Range: mainhand, offhand, inventory, inventory:1 through inventory:27, or inclusive ranges such as inventory:1..9
Default: omitted; uses this skill's legacy source-hand order
Note: Used only when the destination hand lacks a suitable item; empty/invalid lists supply no fetch location

range
Meaning: Maximum distance at which the mob can raise its shield; line of sight is also required
Type: float
Range: [0.0f, 64.0f]
Default: 8.0f

shieldChance
Meaning: Chance for each shield-raising check to succeed
Type: float
Range: [0.0f, 1.0f]
Default: 0.35f

minUseTicks
Meaning: Minimum duration of a randomly selected shield use, in game ticks
Type: integer
Range: [1, 72000]
Default: 80

maxUseTicks
Meaning: Maximum duration of a randomly selected shield use, in game ticks; values below minUseTicks are raised to minUseTicks
Type: integer
Range: [minUseTicks, 72000]
Default: 160

cooldownTicks
Meaning: Cooldown after shield use ends or a shieldChance check fails, in game ticks
Type: integer
Range: [0, 400]
Default: 40

blockingAngle
Meaning: Maximum attack angle that the shield can block, in degrees
Type: float
Range: [1.0f, 180.0f]
Default: 30.0f

axeDisableCooldownTicks
Meaning: Forced shield cooldown after an attack disables the shield, in game ticks
Type: integer
Range: [0, 600]
Default: 100

shieldToughness
Meaning: Number of shield-disabling attacks required to break the current shield-use round
Type: integer
Range: [1, 100]
Default: 1
Refresh: Resets after the normal cooldown ends, before a new round after a forced cooldown, and when the current shield use is interrupted without a true shield break

critToughnessDamage
Meaning: Shield toughness consumed by a shield-disabling attack that meets the vanilla player critical-hit conditions
Type: integer
Range: [1, 100]
Default: 1
Note: Normal shield-disabling attacks always consume 1 point; attacks that cannot disable shields consume none

disableVulnerTicks
Meaning: Duration of the shield-break vulnerability window after a true shield break, in game ticks
Type: integer
Range: [0, 72000]
Default: 40
Note: 0 skips the vulnerability window

vulnerDamageMultiplier
Meaning: Damage multiplier applied while the mob is in shield-break vulnerability
Type: float
Range: [1.0f, 100.0f]
Default: 2.0f
Note: 2.0 means the mob receives double damage during shield-break vulnerability; entities that explicitly saved another value are not migrated to the new default

disableKBMultiplier
Meaning: Multiplier for the extra knockback applied by the final hit that truly breaks the shield
Type: float
Range: [0.0f, 100.0f]
Default: 1.2f
Note: 0.0 disables the extra shield-break knockback; the applied base strength is 0.6 multiplied by this value

speedReduction
Meaning: Movement speed reduction ratio while the shield is raised
Type: float
Range: [0.0f, 1.0f]
Default: 0.5f
Compatibility: Values above 1.0 are treated as percentages before clamping; for example, 50 becomes 0.5

randomCooldownBias
Meaning: Inclusive uniform random extra game ticks added whenever a shield-check cooldown starts
Type: integer
Range: [0, 1200]
Default: 20
Note: Does not affect axeDisableCooldownTicks or disableVulnerTicks; 0 makes check cooldown fixed

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
Summon a zombie with an iron sword, a shield, and an iron helmet. Shield usage is enabled and all omitted parameters use their default values.
summon zombie ~ ~ ~ {PmbAi:{shield:{enable:1b}}, equipment:{mainhand:{id:iron_sword}, offhand:{id:shield}, head:{id:iron_helmet}}}

Summon a heavily armoured zombie with a diamond sword and shield.
It can always start to raise its shield within 12 blocks, uses it for 4–8 seconds, has a 3-second normal cooldown, can block attacks from any direction, has 3 shield toughness, takes 2 toughness damage from a critical shield-disabling attack, enters 2 seconds of shield-break vulnerability with double incoming damage after a true shield break, applies 1.5x extra shield-break knockback, has a 2.5-second forced cooldown after its shield is disabled, and loses 90% of its movement speed while shielding.
(This command is long; use a command block.)
summon zombie ~ ~ ~ {PmbAi:{shield:{enable:1b, range:12.0f, shieldChance:1.0f, minUseTicks:80, maxUseTicks:160, cooldownTicks:60, blockingAngle:180.0f, axeDisableCooldownTicks:50, shieldToughness:3, critToughnessDamage:2, disableVulnerTicks:40, vulnerDamageMultiplier:2.0f, disableKBMultiplier:1.5f, speedReduction:0.9f}}, equipment:{mainhand:{id:diamond_sword}, offhand:{id:shield}, head:{id:diamond_helmet}, chest:{id:diamond_chestplate}, legs:{id:diamond_leggings}, feet:{id:diamond_boots}}}

Enable shield usage with default parameters on the nearest zombie that already has a shield in its off hand.
data merge entity @e[type=zombie,sort=nearest,limit=1] {PmbAi:{shield:{enable:1b}}}

Disable shield usage without removing its other configured parameters.
data merge entity @e[type=zombie,sort=nearest,limit=1] {PmbAi:{shield:{enable:0b}}}
```

</details>
