# Dynamic Factions

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
/summon minecraft:pillager ~ ~ ~ {PmbFaction:{FactionName:"IllagerCourt"},PmbAi:{skill:{active:{bow:{enable:1b}}}},equipment:{mainhand:{id:"minecraft:bow"}}}
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
            defaultAttitude:"neutral"
        },
        VanillaCompatRules:{
            piglin:{
                greed:1b,
                guarding:1b,
                avoidance:1b,
                unreachableGiveUp:1b,
                admiringPausesActiveSkills:1b,
                hurtAdmirationPolicy:"vanilla",
                hurtAdmirationDisableTicks:400,
                hurtAdmiredItemPolicy:"drop"
            },
            breeze:{
                windChargeNoAnger:1b
            },
            brainAiDebug:0b
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

### VanillaCompatRules

VanillaCompatRules is parallel to Rules and Relationships and exclusively controls how Faction preserves species-specific behavior of vanilla Brain mobs. Rules are grouped by mob before individual compatibility fields. A missing collection, mob group, or field uses its default. Currently supported:

- `piglin.greed` defaults to `1b`. It preserves ordinary piglin gold pickup, admiration, and bartering. Disabling it prevents new pickup or admiration and clears the related Brain memories.
- `piglin.guarding` defaults to `1b`. It lets a neutral ordinary piglin retain ANGRY_AT created by vanilla events such as opening protected containers or breaking gold blocks and start a fight. Disabling it does not prevent neutral retaliation after actual damage.
- `piglin.avoidance` defaults to `1b`. It preserves an ordinary piglin's avoidance of zombified piglins and other vanilla zombified mobs, as well as other vanilla AVOID_TARGET values. This compatibility rule takes priority over Faction hostile, so an entity that vanilla requires the piglin to avoid is not selected as a Faction combat target while the rule is enabled.

  At `0b`, vanilla avoidance is suppressed and Faction hostile may make the piglin actively attack a zombified piglin. PMB passively_evasive and actively_evasive behavior is unaffected.

  While suppressing vanilla AVOID_TARGET, an existing Faction or Brain combat target preserves the FIGHT activity's pursuit WALK_TARGET. The stale avoidance walk target is cleared only when there is no combat target.
- `piglin.unreachableGiveUp` defaults to `1b` and applies only to a Faction-member ordinary piglin. When vanilla Brain abandons a target after over 200 ticks of unreachable pursuit, PMB temporarily avoids reassigning that target for about 60 ticks. Significant target movement, a fresh attack, or a held bow/crossbow meeting the applicable range, sight, and ammunition conditions lifts suppression. No extra path search is performed.
- `piglin.admiringPausesActiveSkills` defaults to `1b`. While admiring gold, an ordinary piglin pauses PMB active combat skills, bow charge, and combat movement, but conditional fall rescue remains available. Separate Brain intent arbitration defers publication of Faction and vanilla combat targets, preventing admiration from competing with FIGHT or crossbow fire. After admiration ends, a pending Faction target is revalidated for range and attitude before publication. Piglin brutes and hoglins use the same combat/avoid boundary, but have no gold-admiration intent. Admiration pausing is inert with `greed:0b`.
- `piglin.hurtAdmirationPolicy` defaults to `"vanilla"` and accepts only `"vanilla"`, `"combat"`, or `"continue"`. `vanilla` preserves interruption after accepted self damage and ally-hit group anger; `combat` additionally interrupts admiration for legal Faction revenge or a confirmed ally-hurt broadcast that actually writes the corresponding ANGRY_AT. Anger caused by guarding gold blocks or containers is not a hurt broadcast. `continue` retains admiration after self or ally damage without cancelling damage or anger.
- `piglin.hurtAdmirationDisableTicks` is an integer defaulting to `400`, in `[0,72000]`. It sets one admiration-disable duration after accepted damage from a player, another mob, or another piglin, and after legal ally-hurt interruption under `combat`. At `0`, current admiration is **not interrupted** and no disable timer is set; `continue` also always preserves admiration. It applies only to valid Faction ordinary piglins with `greed:1b`. Vanilla offhand-item handling remains in place for direct damage.
- `piglin.hurtAdmiredItemPolicy` defaults to `"drop"` and accepts `"drop"` or `"store"`. It applies only to the new `combat` ally-hit interruption: drop the offhand item into the world, or first store it in the piglin's vanilla inventory and drop any overflow. Dropped items briefly cannot be picked up. Self-hit item handling remains vanilla.
- `brainAiDebug` is a top-level switch defaulting to `0b`. When enabled, it prints one Brain/PMB state snapshot per tick for this Faction's piglins, piglin brutes, and hoglins, plus key activity, sound, target, anger, hurt, and bow events to the server terminal. It does not send chat messages. The log volume may severely increase disk and server-tick load; disable it after diagnosis. Zombified piglins are excluded.
- `breeze.windChargeNoAnger` defaults to `1b`. Vanilla wind-charge damage deliberately does not write `lastHurtByMob` for victims in `#minecraft:no_anger_from_wind_charge`. PMB uses the accepted damage source's existing living owner as a neutral-retaliation fallback, but keeps Breeze-owned wind charges non-angering while this rule is enabled.

  Wind charges from other valid living owners may trigger neutral retaliation; at `0b`, a valid Breeze owner may do so as well. The fallback applies only to Faction-member mobs in that vanilla tag and still requires a living, attackable owner inside the victim's FOLLOW_RANGE whose current attitude is neutral.

  The general `#minecraft:no_anger` damage-type tag always takes priority and disables this fallback. It does not invent an owner or alter group revenge and other damage types.

Piglin-brute validation of PMB targets is a mandatory fix for Brain state loops rather than an optional species behavior, so it is not part of the piglin group. Future mobs will receive separate groups such as `hoglin:{...}` and `warden:{...}` instead of mixing fields from different species at one level.

Illager Faction combat compatibility likewise leaves vanilla species behavior unchanged outside combat. When a pillager or vindicator has a valid Faction combat target, it temporarily leaves the patrol HoldGround behavior that normally watches in place and waits for the target to approach. A Raider already pathfinding back to a raid or collecting a raid banner also enters Faction combat first.

Those vanilla behaviors may start again under their original conditions after the target becomes invalid. Because an evoker has no vanilla combat Goal that approaches its enemy, it approaches a Faction target to within 16 blocks while not casting and then continues using vanilla summoning and fang spells; this adds no new damage method.

### Relationships and attitudes

Relationships are checked in list order and the last matching rule wins. Non-empty target categories use AND, while values inside each category use OR. Empty targets are rejected.

- `factions` matches the target's unique FactionName; `teams` matches vanilla scoreboard team names.
- `types` accepts entity type IDs and `#` entity-type tags; `tags` matches entity `/tag` values.
- `reputation` accepts exact `"5"`, upper-bound `"..100"`, lower-bound `"1.."`, and closed `"1..100"` forms.
- `reputation` and `roles` only match players and read their Players record inside the rule owner's faction. A player does not need membership to have either value.

Attitudes have fixed meanings:

- `hostile` proactively acquires visible targets inside FOLLOW_RANGE, retains pursuit through temporary loss of sight, and clears targets outside range.

  The ten-tick scan selects the nearest candidate visible in that scan; it need not be nearer than the current target. If none is visible, it retains a combat target that passed live validation. Death, dimension or FOLLOW_RANGE changes, or a failed live attitude check clear it during validation.
- `neutral` never proactively acquires a target but may retaliate after an effective attack. With `VanillaCompatRules.piglin.guarding:1b`, vanilla piglin ANGRY_AT events such as opening protected containers or breaking gold blocks are retained as species-specific reactive hostility and may also start a fight.

  For victims in vanilla's wind-charge no-anger tag, `VanillaCompatRules.breeze.windChargeNoAnger` controls whether a Breeze owner keeps that exception; other valid living owners can still trigger PMB neutral retaliation.
- `allied` neither attacks nor retaliates. The victim's allowFriendlyFire decides whether actual damage is blocked.
- `passively_evasive` flees after being attacked until the attacker leaves FOLLOW_RANGE.
- `actively_evasive` flees when a visible threat enters FOLLOW_RANGE and stops after it leaves.

Faction scanning, retaliation, existing-target validation, and piglin-specific Brain target resolution all exclude dead entities and players in creative or spectator mode. Even if such a player attacks a member, it is not written as a Faction combat or avoid target.

The vanilla hurt-retaliation entry points of piglins, piglin brutes, and hoglins also stop for non-survival attackers, preventing the repeated weapon-raise, weapon-lower, and roar loop caused by vanilla Brain and Faction target contention.

Faction attitude overrides vanilla proactive hostility for members and manages both ordinary Mob targets and Brain ATTACK_TARGET/AVOID_TARGET memories. For Goal mobs, PMB reasserts the Faction target after vanilla targetSelector finishes and before the attack and movement goalSelector runs. This prevents a shorter vanilla acquisition range from removing a distant target still inside FOLLOW_RANGE; it does not add a generic melee Goal.

A Brain memory is read only when its MemoryModule is registered and is written only when its target changes. Faction does not unconditionally erase vanilla AVOID_TARGET memories it does not own. A dedicated piglin and piglin-brute compatibility layer makes PMB combat targets pass vanilla target-validity checks while preserving neutral piglins' vanilla ANGRY_AT-driven guarding and greed behavior.

Illager patrol HoldGround, raid pathfinding, and banner collection no longer suppress higher-priority Faction combat. An evoker, which has no vanilla approach Goal, only receives the navigation needed to enter the effective range of its existing spells and gains no new attack. Likewise, hostile supplies a target but never grants a generic melee skill.

Both evasive attitudes only provide a retreat objective; speed, destination selection, and actual movement belong to [pathfinding.retreat](pathfinding.md).

Both evasive attitudes retain a valid navigation path whose destination is already farther from the threat. When repathing is required, the mob's pathfinder first selects a reachable away position. A failed ground path no longer points move control directly into a wall or another unreachable coordinate, while flying and aquatic navigation can still use stable direct-away movement.

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
/pmb faction compat set piglin greed <faction> <true|false|1b|0b>
/pmb faction compat set piglin guarding <faction> <true|false|1b|0b>
/pmb faction compat set piglin avoidance <faction> <true|false|1b|0b>
/pmb faction compat set piglin unreachableGiveUp <faction> <true|false|1b|0b>
/pmb faction compat set piglin admiringPausesActiveSkills <faction> <true|false|1b|0b>
/pmb faction compat set piglin hurtAdmirationPolicy <faction> <vanilla|combat|continue>
/pmb faction compat set piglin hurtAdmirationDisableTicks <faction> <0..72000>
/pmb faction compat set piglin hurtAdmiredItemPolicy <faction> <drop|store>
/pmb faction compat set brainAiDebug <faction> <true|false|1b|0b>
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

[Back to documentation](../docs.md)
