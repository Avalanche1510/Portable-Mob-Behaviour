# Portable Mob Behaviour Change Log

## Introduction

Used to record Git updates to this mod and may be referenced by Git commit messages.
The change log aims to record all changes as completely and precisely as possible.

# Format

Year.Month.Day-No.

## Record

### 26.9.18-2

Added independent `PmbAi.air_tracking`. Defaults are `enable:0b`, `activationSkills:["wind_charge","mace"]`, `trackAcceleration:0.012f`, `trackMaxHorizontalSpeed:0.3f`, `trackDurationTicks:-1`, and `requireEyeSight:1b`. Its ordered source list accepts every registered PMB skill except itself. A wind bounce creates a latch across ascent and descent; a usable falling mace with its authority target inside FOLLOW_RANGE provides a conditional source before smash range; another listed skill provides a generic source while it owns a sustained phase. Higher listed sources supersede lower sources immediately, which are re-evaluated on the next tick.

The skill owns required `NAVIGATION` and optional `LOOK`. It preserves vertical velocity, accelerates horizontally toward the target (or away from the threat for evasive wind bounces), and caps that horizontal velocity. `trackDurationTicks` uses `-1` for terminal-bound tracking, `0` for no session, and positive values for exact control ticks; eyesight is required only at session start. Landing, water, invalid source/target, omission, and navigation preemption clear the session. Defaults now insert air_tracking after wind_charge in combat and retreat priority lists.

Breaking migration: `wind_charge.inAirTrackStrength` and `wind_charge.inAirTrackDurationTicks` were removed. Configure the new `air_tracking` skill instead. K debug reports its source, inactive/infinite/remaining state, and latest clear reason.

### 26.9.18-1

Expanded `PmbInventory.Slots` from `[0,27]` to `[0,256]`. Its `SimpleContainer` now matches the actual clamped `Slots` value instead of allocating the maximum for every Mob, so the default zero-slot state has zero backing entries. Read, write, death-drop, pickup, conversion-copy, and permanent item/ammunition access continue to operate only on the configured capacity. Existing low-slot merge and immediate overflow-drop behavior is preserved when loaded data is reduced.

`FetchSource` and `AmmoSource` now accept `inventory:256` and inclusive ranges through `inventory:1..256`; the `inventory` alias represents the full 1–256 declaration and runtime access is still truncated to the Mob's actual `Slots`. Slot numbers 0, 257, and reversed ranges remain invalid. Existing 0–27-slot data is directly compatible. Data stored in slots 28–256 is not backward-compatible and is lost if loaded by an older PMB version.

### 26.9.17-2

Added the boolean `PmbInventory.ScatterDrops`, default `0b`. After the existing `doMobLoot` gate and independent per-slot `DropChance` roll succeed, `0b` preserves the existing stationary PMB drop while `1b` routes that intact stack through the vanilla player-style death-drop path: it spawns near `eyeY - 0.3`, receives random radial horizontal speed in `[0.0,0.5)` plus vertical speed `0.2`, and uses the vanilla 40-tick pickup delay. The option never damages or splits a stack and does not affect resize/load overflow, held equipment, or any failed death-drop roll. The field is persisted and copied during Mob conversion; old or missing data resolves to `0b`.

### 26.9.17-1

Replaced category/rank-only conflict ordering with persisted per-strategy `skillPriorities` lists for combat, retreat, and idle. Strict tiers use quoted skill IDs and nested lists define equal tiers. Omitted skills cannot start in that strategy; `vanilla` is required exactly once as the ordinary combat-navigation and standard-melee boundary. Defaults are combat `mace > ender_pearl > wind_charge > bow > shield > vanilla`, retreat `ender_pearl > wind_charge > vanilla`, and idle `vanilla`. Equal-tier candidates with ready fixed triggers and cooldowns form a contested set; its cursor orders probability checks, later checks stop after the first commit, and only a successful contested commit advances to the skill after the actual winner. Running equal phases do not preempt one another.

The scheduler now admits at most one new action or irreversible completion each tick. Chance and cooldown still occur before resource arbitration. Final checks and a complete preemption plan finish before incumbents are cancelled; strictly higher tiers may preempt conflicting required resources, while optional resources never block required ones and are reoffered each tick. Preempted actions retain their remaining cooldown without refund, reset, caching, or automatic resumption. Bow release and standard Goal/Brain melee now use the same commit slot; special attacks, ranged attacks, explosions, spells, and beams remain outside the vanilla bridge.

Strategy or authority changes now revalidate each active phase instead of cancelling every skill indiscriminately; BLOCKED, death, NoAI, omission, and a phase's own invalid authority still cancel it. Standard vanilla melee is revalidated against current melee reach and sensing line of sight. Item, binding, and ammunition plans are checked before preemption; an impossible commit failure after preemption is reported as `COMMIT_FAILED_AFTER_PREEMPT` and ends arbitration for that tick.

`NAVIGATION` is the sole movement-decision resource and includes HOLD. Bow charge requires it for holding, retreating, and strafing; wind bounce tracking requires NAVIGATION and optionally uses LOOK; mace does not use it. Shield preemption uses normal cancellation and resets toughness. Wind charge adds `inAirTrackDurationTicks` in `[-1,72000]`, default `-1`: unlimited until landing, water, invalid state, or preemption; `0` disables follow-up tracking; positive values count exact subsequent tracking ticks and are not refreshed by Wind Burst mace impacts.

The breaking command grammar is now `/pmb skills skill <targets> ...` for the existing skill editor and `/pmb skills priority <targets> get|set|reset ...` for atomic complete priority-list overrides. The old selector-immediately-after-skills form is not accepted. Priority completion tracks quoted strings and bracket depth, so nested groups cannot be suggested inside an equal tier. K debug reports now include the effective priority source/list, active required/optional phases, the latest preemption, wind tracking as inactive/infinite/remaining, and its latest clear reason. Documentation and English/Simplified-Chinese chat labels were updated.

### 26.9.11-1

Added a one-shot PMB skill debug probe bound to `K` by default and rebindable in Controls. A gamemaster may aim at a configured PmbAi Mob and press the key to request the same report in both their own chat and the server console/log. The chat copy is one multiline vanilla system message and is not broadcast to other players. The server validates permission level 2, same-level entity identity, interaction range, line of sight, Mob type and configured skill data, rate-limits each player to one accepted request per 250 milliseconds, and removes limiter state on disconnect. The optional play-stage request uses a varint entity ID and does not change `PMB_NETWORK_PROTOCOL`; clients check channel support before sending, and no custom S2C debug payload or complete AI data is sent to the client.

Reordered the report so `MELEE` immediately follows entity identity as the highest-priority warning, followed by enabled skill state, attempts, ownership details, and runtime context. `SKILLS` now hides both unconfigured and configured-but-disabled skills, while exact empty markers remain visible as `SKILLS: []`, `LAST ATTEMPTS: []`, `RESOURCES: {}`, and `BINDINGS: []`. Server logs retain stable English technical output; the chat copy uses client-resolved English or Simplified Chinese headings and state labels with distinct colors for the title, active/success, waiting, blocked/suppressed, and secondary information.

The scheduler now retains one immutable, nonpersistent diagnostic copy of its last completed configured skill tick. The snapshot captures configured/enabled/active state and cooldowns for all five skills, while the rendered `SKILLS` section lists only enabled entries. It also captures snapshot age, strategy and authority target; shield use and vulnerability; candidate labels and `PRECLAIM_REJECTED`, `RESOURCE_BLOCKED`, `ADMITTED+FINAL_REJECTED`, or `ADMITTED+EXECUTED` outcomes; blocking resources and owners; sustained and final claims; scalar action-binding locations and actual item-use hand; the independently applied locomotion, hard-stop and velocity modifiers; and ordinary-melee suppression reasons for bow, falling mace, shield vulnerability, or death. `activeThisTick` means sustained ownership at tick start or a candidate reaching its real execution point.

Diagnostic capture does not re-run chance, pre-claim, resource resolution, winner validation, item movement, projectile creation, or any action. Winner execution markers are accepted only for the currently executing candidate and owner. The real falling-mace interception and the report share one read-only suppression predicate. Snapshots retain no Entity, ItemStack, Supplier, Runnable or historical list and are neither written to NBT nor synchronized.

Narrowed bow ordinary-melee suppression to a lightweight live predicate shared by MeleeAttackGoal, direct Mob damage interception, and the K-key report. It now requires an enabled bow skill, a living same-level attackable current target, a bow already held in a hand allowed by preferredHand, target distance inside a nonzero-chance line or arc range, and real AmmoSource ammunition only when doConsume is enabled. Inventory/FetchSource bows and bindings no longer suppress by themselves; losing any live condition restores vanilla melee navigation and ordinary damage. Cooldown, the current chance roll, resource ownership, line of sight, and movement settings do not participate. Vanilla RangedBowAttackGoal suppression remains unchanged so skeletons cannot bypass PMB firing.

### 26.9.10-1

Unified all five skills so an eligible check starts its cooldown and rolls its chance before entering resource arbitration. A failed chance therefore claims no hand, USE_ITEM, LOOK, or SMASH resource; a successful roll keeps its cooldown even if it later loses resource arbitration or fails final execution revalidation, without caching or retrying. Chance values at 0.0 now fail deterministically and values at 1.0 succeed deterministically without consuming a random draw. Bow retains no same-tick mode fallback, while wind_charge retains throw fallback only when bounce's chance fails; a successful bounce roll never falls back after a resource or final-validation failure.

Ender-pearl final target, range, visibility, water, and ballistic validation now produces a short-lived launch plan before any permanent equipment exchange. An invalid plan keeps the already-started cooldown but does not move or consume a pearl; a valid plan acquires the resolved item and then performs the release.

Wind-charge bounce now applies its existing grounded termination boundary before sustained resource claims and before pose/look writes. The first two grounded launch ticks retain their original grace, but the first terminating tick releases LOOK immediately instead of making an otherwise valid bow or pearl check lose resources and consume a full randomized cooldown.

Added `randomCooldownBias` to shield, wind_charge, mace, bow, and ender_pearl. It is an integer from 0 through 1200, defaults to 20, and adds an independently drawn inclusive uniform integer from 0 through its value whenever an active check cooldown starts. Bow line/arc and wind-charge throw/bounce share their skill's setting but draw independently. It affects only active-check cooldowns, not shield disable/vulnerability, use or charge duration, airborne tracking, or temporary look duration. Old data without the field defaults to 20, and saved runtime cooldown validation now accepts the configured base plus bias.

Added `VanillaCompatRules.breeze.windChargeNoAnger`, defaulting to `1b`. Vanilla deliberately omits `lastHurtByMob` when wind-charge damage hits an entity in `#minecraft:no_anger_from_wind_charge`; PMB now uses the accepted damage source's existing living owner as a narrow neutral-retaliation fallback. With the compatibility rule enabled, Breeze-owned wind charges retain vanilla no-anger behavior, while wind charges from other valid living owners may make a neutral Faction mob retaliate. At `0b`, a valid Breeze owner may also trigger that retaliation.

The fallback applies only to valid Faction-member mobs in the vanilla no-anger entity tag, only after damage succeeds, and still requires a living attackable owner inside the victim's FOLLOW_RANGE whose real-time Faction attitude is neutral. The general `#minecraft:no_anger` damage-type tag remains authoritative and disables this fallback without changing the existing group-revenge path. It does not alter ownerless damage, non-Faction behavior, group revenge, friendly fire, other attitudes, goat rams, status effects, or later fire damage. Added `/pmb faction compat set breeze windChargeNoAnger <faction> <boolean>` and included the value in faction info; old SavedData defaults the new group and field to enabled.

### 26.9.8-2

Parameter completion now requires an opening `[` before suggesting fields, avoiding bare parameter suggestions at the empty argument entry.

Bow positioning now runs only while a successful bow action is actually drawing. Cooldown, failed chance, missing ammunition and action-resource conflicts no longer submit bow locomotion or intercept vanilla navigation or movement control; whether the mob actually pursues still depends on an applicable vanilla Goal or Brain behavior remaining able to run. This prevents wind charge from leaving a bow user strafing without turning, attacking or shooting while LOOK is unavailable.

Added preferredHand with main, off, main-enforce and off-enforce values and enum completion. Bow defaults to main; shield, wind_charge and ender_pearl default to off. Mace does not expose the field. Hand selection reads a frozen snapshot of ongoing PMB hand ownership only; static equipment is exchangeable. Plain values may fall back, enforced values never do, and same-tick losers never retry another hand.

Compatible destination-hand equipment now takes precedence over FetchSource. Otherwise the first available ordered source permanently exchanges with that hand. Busy source/destination hands cannot be swapped. Cross-hand exchanges reserve both hands for the swap tick; continued actions bind only their actual hand. All five skills, including mace, retain the final layout after completion, cancellation, loading, conversion and death. ActionBinding tracks action identity without restoration. Displaced equipment/ammunition may be stranded outside configured ranges; future checks use the real layout. Final positions determine drop rules. Old PmbSwapJournal data gets one safe load recovery; new exchanges write no journal.

Removed NAVIGATION from exclusive skill resources. Independent movement intentions prioritize vulnerability HARD_STOP, retreat, active-skill movement, passive stance, then vanilla movement. One LOCOMOTION wins; wind airborne tracking adds VELOCITY_MODIFIER while preserving vertical velocity. Losing movement never cancels a skill. Bow draw reserves its actual hand, USE_ITEM and LOOK, and only an active draw submits bow locomotion. Intents are revalidated before the next MoveControl phase and interception follows the actual movement winner.

Split bow's lineRange into canonical lineMinRange and lineMaxRange. The closed shooting interval defaults to 0.0f through 16.0f; lineMaxRange is clamped to at least lineMinRange. Targets below lineMinRange cannot start or continue line fire. Data written with the temporary minLineRange/maxLineRange names remains readable when neither canonical field exists. Older lineRange data is read as a 0.0f-to-legacy-max interval only when neither canonical nor temporary field exists; subsequent saves use only the canonical fields.

Renamed the five skills' externally written source-list field from `activationSources` to `FetchSource`. Commands, defaults, saved output, README, and complete documentation now use the new canonical spelling. Existing data with the old field remains readable; if both fields exist, `FetchSource` wins and only it is written back.

Bow now has its own optional ordered `AmmoSource`, using the same hand and one-based PMB-inventory location syntax. It scans real supported arrows only and never moves or exchanges them; FetchSource resolves the bow alone and cannot restrict ammunition. Omitting AmmoSource preserves the prior hands-first then lowest-enabled-PMB-inventory scan. An explicit empty or invalid list has no real ammunition source, so `doConsume:1b` cannot fire while `doConsume:0b` retains the unlimited normal-arrow fallback. A previously selected hand arrow follows a permanent weapon swap into its actual new hand or inventory slot for that action. The next action scans AmmoSource again.

When `doConsume:1b` exhausts the last supported arrow, an inactive bow no longer suppresses ordinary melee goals, direct melee damage, or movement control. An active draw retains ownership until its binding is released. Vanilla ranged-bow suppression remains active for a configured resolvable bow, preventing native skeleton bow AI from bypassing PMB ammunition rules.

### 26.9.8-1

Added the permission-level-2 skill testing command for shield, wind_charge, mace, bow, and ender_pearl (current syntax: `/pmb skills skill <targets> <skill> <mode>`). Its schema-aware bracket argument completes unused parameter names and typed values, validates canonical case-sensitive fields, strict NBT-style booleans, integer and float syntax, quoted enums and activation-source lists, field ranges, and final cross-field relationships.

Added atomic `append`, `insert`, `modify`, and `delete` operations plus single-target `get`. Append creates only a missing node, insert adds only absent explicit fields, modify changes only existing explicit fields, keyed delete restores defaults, `delete *` retains an empty disabled node, and `delete []` removes the node. Every selected entity must be a Mob and the complete batch is validated before any mutation.

Changed all five skill compounds to sparse persistence with explicit canonical-field tracking derived from the fields actually present in NBT. Empty and partial nodes survive saving, direct summon/data edits remain meaningful, existing full compounds retain all present fields as explicit, and supported legacy aliases map to canonical presence without entering the command schema.

Successful writes immediately cancel only the selected skill, restore its temporary item lease, clear its runtime action and cooldown state, and refresh shield toughness for shield edits. Updated both complete documents and READMEs with command syntax, completion and validation rules, persistence semantics, and examples; also added ender_pearl to the previously incomplete implemented-skill list.

### 26.9.4-2

Prevented combat and evasive ender-pearl throws when the launch point is submerged in water, while retaining shallow-water use above the fluid surface. Replaced the 300-tick trajectory cutoff with the equivalent discrete air trajectory and restricted moving-target prediction to horizontal motion. Valid geometry with insufficient maxThrowPower still fires at the cap; impossible geometry, near-zero horizontal distance, or invalid numerical solutions now skip the throw with normal cooldown and no pearl consumption. Failed releases do not start throw feedback or its continued look state, and temporary item leases are always restored.

Added the default-true `requireEyeSight` boolean to bow and ender_pearl. With `1b`, their existing line-of-sight gates remain required; with `0b`, only those gates are skipped, while target validation, ranges, cooldowns, chance checks, items, and all other skill behavior remain unchanged.

Fixed `requireEyeSight:0b` for original vanilla TargetGoal continuation. It may retain only an already acquired `mob.getTarget()` through obstruction, only inside FOLLOW_RANGE and the enabled bow or combat-pearl range, with an applicable activation source resolving a bow to the main hand or a pearl to either hand; pearl also requires throwChance above 0.0. Initial target discovery remains line-of-sight limited. Faction, Brain and evasive paths, general melee, and other skills remain unchanged.

Added one server-side scheduler for shield, bow, mace, wind charge, and ender pearl. It derives the unique `retreat`, `combat`, or `idle` strategy from Faction avoidance and the attack target, cancels sustained actions when the authoritative strategy or target changes, and blocks actions during death, NoAI, or shield-break vulnerability. The five independent Mob tick injections were replaced by this single top-level execution point.

Added conflict categories, fixed skill ranks, and concrete resources. Combat uses `main > off > throw > food > block`, retreat uses `throw > off > food > main > block`, and idle uses `block > food > off > main > throw`. Mace ranks over bow and ender pearl over wind charge when tied. At this stage, a losing ready candidate ended its normal check window without rolling chance or moving an item; this ordering was superseded by the pre-claim chance flow in `26.9.10-1`. Each physical hand performs at most one PMB action per tick. Wind bounce retains its bounce-first and failed-bounce fall-through behavior, and continued flight uses `LOOK + NAVIGATION`, never `SMASH` or a released hand.

Added ordered `FetchSource` to all five skills. It accepts `mainhand`, `offhand`, `inventory`, one-based slots such as `inventory:3`, and overlapping inclusive ranges such as `inventory:1..9`. Omission preserves old hand requirements; an explicitly invalid list cannot activate. Inventory core items are atomically swapped into the required hand and safely restored after completion or cancellation.

Added Mob-only `PmbInventory` with `Slots` `[0,27]`, `DropChance` `[0.0f,1.0f]`, and one-based `Items`. Capacity reduction merges or moves high-slot contents into enabled low slots and immediately drops full remainders. Every slot rolls independently for its full stack on death; `doMobLoot:false` suppresses these drops. With `CanPickUpLoot:1b` and `mobGriefing:true`, vanilla equipment and species pickup runs first, then remaining nearby items merge into PMB storage.

Bow ammunition now resolves supported stacks from hands first, then enabled PMB slots from low to high, without occupying another hand. Non-consuming mode retains unlimited normal-arrow fallback and can use real special arrows without consuming them; consuming mode removes one item from its actual source only after release.

Skill-check cooldowns now persist with save world time, while sustained actions do not resume after loading. A private minimal swap journal rolls back intact temporary swaps without overwriting externally changed slots. Death restores temporary equipment before inventory drops, and Mob conversion preserves PMB skill configuration, remaining cooldowns, and the extra inventory.

### 26.9.4-1
Fixed overrideTeamRules not fully replacing vanilla team relationships. A Faction member with this rule enabled now resolves vanilla alliance against any LivingEntity from the source faction's directional attitude instead of requiring both entities to already share a team. Player friendly-fire checks are also delegated to Faction rules whenever either participant belongs to a valid Faction with overrideTeamRules enabled, then use the victim faction's allowFriendlyFire and member/allied relationship to decide whether damage is allowed.

Restored the incorrect vulnerDamageMultiplier default from 1.5f to 2.0f, matching the intended default of double incoming damage during shield-break vulnerability. Its range and damage formula are unchanged. Entities that explicitly saved 1.5f or another valid value are not migrated; only configurations omitting the field use the new default.

Optimized groupRevenge responder lookup. It returns immediately for missing responsible attackers, self-damage, or when no Faction enables groupRevenge. Otherwise it queries only mobs inside a 2048-block AABB around the attacker, then applies each responder's current Faction rules, member/allied relationships, and own FOLLOW_RANGE as an exact spherical check instead of traversing every loaded entity in the dimension after each effective hit.

Centralized the side-effect-free line and arc movement-control range checks in PmbBowAiData and reused them from both bow shooting movement and MoveControl suppression, preserving the existing mode priority, range, and safe-distance semantics.

Added the package-private stateless PmbAiNbtReader to share NBT numeric-type tolerance, field detection, ordered alias reads, and clamping across the shield, bow, mace, wind-charge, and ender-pearl AI data classes. Shield nested/legacy-flat formats, all alias orders, percentage speed conversion, and new-before-legacy ender-pearl fields remain intact; each skill still owns its defaults, normalization, saved form, and runtime-state reset.

### 26.8.9-1
Fixed Faction mobs treating creative- or spectator-mode players as targets. Non-survival players are now excluded consistently from scanning, damage retaliation, existing-target validation, group-revenge authorization, and piglin/piglin-brute-specific Brain target resolution; piglin, piglin-brute, and hoglin vanilla hurt-retaliation entry points also stop. Attacking a piglin, hoglin, or any other Faction member as such a player no longer writes a combat or avoid target, preventing the repeated weapon-raise and roar loop caused by Brain and Faction contention.

Tidied piglin compatibility logic: when VanillaCompatRules disables vanilla piglin avoidance, vanilla avoid and walk memories are now cleared directly instead of through a condition already made unconditional by the preceding branch. Piglin brutes also use a distinct pattern-variable name, removing the IDE's duplicate `piglin` diagnostic without changing behavior.

### 26.7.15-1
Added the independent ender_pearl AI skill. A mob holding a vanilla ender pearl in either hand can perform throw checks using minThrowRange, maxThrowRange, throwChance, and throwCooldownTicks, apply random spread through throwAccuracy, and use doConsume to decide whether one pearl is consumed from the hand that actually throws. Ordinary combat checks only inside the default 4-to-16-block interval, preventing repeated pearl use after a target closes in. A chance of 0.0 fully disables checks, and every eligible check enters cooldown whether it succeeds or fails. The old throwRange input is accepted only as a read-compatibility value for maxThrowRange; saves use the new fields.

Refactored throwPower into maxThrowPower and added throwAngle with a default of 30.0f and range of [1.0f,89.0f]. maxThrowPower defaults to 1.0f with range [0.1f,10.0f] and limits solved speed as a multiplier of the vanilla stationary-player launch speed of 1.5. The trajectory solver discretely simulates ender-pearl drag of 0.99 and gravity of 0.03, binary-searches required speed at the fixed throwAngle, and iteratively leads moving targets. It uses the speed limit when the required speed exceeds it or no complete solution exists at that angle. Old throwPower is accepted only as a read-compatibility value for maxThrowPower; saves use the new field.

Added compatibility with Faction passively_evasive and actively_evasive attitudes. Evasive checks ignore minThrowRange and may run whenever the current avoid threat is visible and no farther than maxThrowRange. The pearl is neither thrown toward that threat nor used to write an attack target. Its destination prefers the current reachable escape-navigation target that is farther from the threat and is clamped to maxThrowRange; without a valid path target, it uses a stable directly-away position. Combat and evasive throws both face the actual launch trajectory, swing the hand holding the pearl, and play the vanilla ender-pearl throw sound.

Added ender_pearl to PmbAi NBT reading, saving, configuration detection, defaults, and clamping, with bilingual README text, complete structures, parameter documentation, and command examples.

### 26.7.13-2
Added the dynamic Faction module independently of PmbAi skills. Every LivingEntity and player can store one FactionName in the separate PmbFaction root tag. Faction definitions, relationships, rules, player reputation, and player roles are stored in overworld server SavedData, read by every dimension in real time, and are not fully synchronized to clients.

Added the Rules fields allowInternalConflict, allowFriendlyFire, groupRevenge, overrideTeamRules, defaultAttitude, and evasiveSpeedMultiplier with defaults of 0b, 1b, 0b, 1b, "neutral", and 1.25f. When enabled, overrideTeamRules lets Faction decisions replace vanilla team targeting, retaliation, and friendly-fire restrictions. When disabled, entities on the same vanilla team continue to use vanilla team rules first.

Added directional Relationships with hostile, passively_evasive, actively_evasive, neutral, and allied attitudes. Relationships are checked in list order and the last matching rule wins. Non-empty target categories use AND while values inside each category use OR. Targets support factions, scoreboard teams, entity type IDs, #entity-type tags, entity /tag values, player reputation ranges, and player roles.

Faction attitude now overrides vanilla proactive hostility for members at the highest priority while supporting ordinary Goal targets and Brain ATTACK_TARGET and AVOID_TARGET memories. hostile proactively acquires visible targets inside FOLLOW_RANGE; neutral only retaliates; allied neither attacks nor retaliates; passively_evasive flees after being attacked; actively_evasive flees when a visible threat enters FOLLOW_RANGE. Fleeing reuses the mob's navigation and movement controls at evasiveSpeedMultiplier speed.

Added groupRevenge. When enabled, an effective attack against a member or directional ally causes nearby responders to check the responsible attacker using each responder's own FOLLOW_RANGE. Revenge does not trigger when the attacker is itself a member or ally.

Added server-authoritative friendly-fire arbitration. With allowFriendlyFire 0b, the victim's faction blocks damage from members and allies while tracing melee entities, projectile owners, and explosion responsibility. Environmental damage without a responsible entity is unchanged. True same-faction AI conflict requires both allowInternalConflict and allowFriendlyFire.

Added the complete /pmb faction command tree for creating, deleting, and inspecting factions; changing every Rule; adding, inserting, replacing, removing, moving, and listing indexed Relationships; assigning members in bulk with entity selectors; and independently managing player reputation and roles in any faction. Relationship targets use the same SNBT compound shape as saved data and validate faction, team, entity type, type tag, reputation range, attitude, and list index values.

Restructured rule set completion so every concrete Rules name appears immediately after set, followed by that rule's typed faction and value arguments instead of mixing faction IDs with rule names at the same suggestion level. The four boolean Rules accept both true/false and NBT-style 1b/0b, for example `/pmb faction rule set groupRevenge EmpireGuard 1b`.

Removed the Minecraft Identifier restriction from FactionName. Faction names are now ordinary case-sensitive strings and commands accept any non-whitespace name directly without quotes, including uppercase letters, Chinese text, and common symbols, such as `EmpireGuard`, `北方联盟`, and `Raiders-02`. Every faction argument continues to suggest existing names, and old namespace:path names remain valid without migration. Registered a two-sided network serializer for the custom FactionName command argument, fixing local worlds rejecting player entry with “Invalid player data” when the server attempted to send an unknown argument type in its command tree.

Fixed Faction and the piglin Brain competing over ATTACK_TARGET, which made piglins repeatedly raise and lower their weapons and emit continuous angry sounds. PMB targets now participate in the vanilla target-validity checks of piglins and piglin brutes instead of being sustained by deleting and restoring memories every tick. Brain target memory is now written only when the value actually changes, and a MemoryModule is checked for registration before it is read, preventing Goal mobs from crashing on an unregistered ATTACK_TARGET.

Preserved vanilla piglin greed and guarding behavior. neutral does not restore proactive vanilla hostility such as targeting players without gold armor, but ANGRY_AT created by opening protected containers, breaking gold blocks, or other vanilla anger events may still start a fight. allied and both evasive attitudes still prevent that attack. Ending Faction evasion now clears only the AVOID_TARGET held by PMB instead of unconditionally deleting vanilla avoidance memories used for zombified mobs, hoglins, and other piglin behavior.

Added VanillaCompatRules alongside Rules and Relationships to manage vanilla Brain species behavior in per-mob groups. The first `piglin` group contains greed, guarding, and avoidance, all defaulting to 1b. They respectively control ordinary piglin gold pickup/admiration/bartering, vanilla ANGRY_AT-driven guarding while neutral, and vanilla AVOID_TARGET outside Faction evasion. Added the independent `/pmb faction compat set piglin <greed|guarding|avoidance> <faction> <boolean>` command tree. Missing collections, mob groups, or fields in old SavedData automatically use defaults.

Fixed ordinary Goal mobs failing to pursue Faction targets across an expanded FOLLOW_RANGE. Vanilla targetSelector could remove the distant target selected by PMB using its own shorter acquisition distance, so illagers and other Goal mobs only began fighting after coming closer. A still-valid Faction target is now restored after targetSelector finishes and before the attack and movement goalSelector runs. The fix reuses the mob's existing combat Goals and does not add generic pursuit or melee capability.

Fixed illager patrols holding a Faction target while only witches and vexes advanced. Vanilla HoldGround makes patrolling pillagers and vindicators stand still until the target enters 10 blocks and has priority above or equal to their crossbow and melee Goals. A Faction combat target now temporarily bypasses HoldGround and interrupts an already-running path-to-raid or banner-collection Goal so their existing combat Goal can control movement. Evokers have no vanilla combat Goal that approaches an enemy, so while not casting they now approach a Faction target to within 16 blocks and then continue using vanilla summoning and fang attacks. Patrol, raid-pathing, and banner behavior can resume after the target becomes invalid.

Fixed every PmbAi mob potentially completing an attack, throw, or shot after its death animation had begun. Reaching zero health now uniformly stops serverAiStep and melee execution for configured mobs, while each skill clears shield use, wind-charge bounce tracking, mace execution state, and any unreleased bow charge. This death gate does not clear knockback momentum, disable gravity, or replace the vanilla death process.

Documented direct entity-NBT assignment for PmbFaction. PmbFaction is server-read persistent membership data rather than a client-display-only field; summon data may now be written as `{PmbFaction:{FactionName:"IllagerCourt"}}`, including alongside PmbAi, without selecting the entity after it spawns. Direct NBT does not create a faction, and an unknown or case-mismatched FactionName is still cleared on the next server tick.

Fixed ordinary piglins freezing, repeatedly roaring, raising their weapon, and failing to move around a faction-hostile zombified piglin, and clarified VanillaCompatRules.piglin.avoidance precedence. At 1b it fully preserves vanilla avoidance of zombified piglins, other zombified mobs, and other AVOID_TARGET values; even a Faction-hostile avoided entity is not written as a combat target, and any existing conflicting ATTACK_TARGET or Faction combat state is cleared before vanilla selects an activity. Only at 0b are the vanilla isNearZombified avoidance decisions bypassed at their source, allowing Faction hostile to make the piglin attack a zombified piglin. Both Faction evasive attitudes remain unaffected.

Extended wind_charge integration with both Faction evasive attitudes. A fleeing mob with wind-charge AI enabled and a held wind charge now uses its current Faction avoid target for throw and bounce checks. Throw mode fires at the threat; bounce mode launches beneath the mob and uses inAirTrackStrength to steer and face away from the threat without restoring it as an attack target. Existing range, chance, cooldown, accuracy, and doConsume settings remain authoritative. If doConsume uses the last wind charge, airborne steering from an already-started bounce continues until landing, target invalidation, or timeout.

Fixed evasive mobs throwing a wind charge without ever facing the threat. On throw release, the mob briefly turns its view, head, and body toward the actual target without changing its attack target or interrupting away-from-threat navigation. Also fixed prolonged corner spinning while fleeing: a valid away path is retained, repathing first selects a pathfinder-confirmed reachable position away from the threat, and failed ground paths no longer write an unreachable wall coordinate directly to move control.

Added landing feedback to downward wind-charge bounces. After the mob has actually left the ground from that bounce and lands again, the server broadcasts the vanilla PLAYER_SMALL_FALL player no-damage landing sound once. Never leaving the ground, dying, or using throw mode alone cannot trigger it accidentally.

Deleting a faction immediately clears every loaded member's FactionName, while unloaded entities clear invalid faction names when next loaded on the server. Players retain membership across death and respawn. Updated the bilingual README, complete structures, rule precedence, parameter reference, and command examples.

### 26.7.13-1
Enabled Loom splitEnvironmentSourceSets. Common and server code remains in src/main, while every model, render-state helper, client mixin, client helper, and data-generation entrypoint has moved to src/client. The mod still builds as one universal two-sided JAR.

Split the former mixin configuration into a common configuration and a client-only configuration. fabric.mod.json now declares separate common and client entrypoints and an environment-restricted client mixin configuration. Common sources no longer reference net.minecraft.client packages, so a dedicated server never loads client classes.

Added an independent network protocol number and a bidirectional configuration-stage handshake. Before a player enters the world, the server verifies the PMB configuration channel, sends a protocol challenge, and waits for the client response. Missing PMB or incompatible protocols produce an explicit disconnect reason. The client also rejects servers that do not declare the PMB response channel. Display versions may differ while the network protocol remains compatible.

Audited client animation-state synchronization. Shield use, bow drawing, held items, and aiming rotation continue to use vanilla tracked entity state. Shield-break vulnerability shaking is confirmed to use remaining vulnerability ticks synchronized through SynchedEntityData, and client rendering does not read server-only PmbAi configuration.

Refactored the meaning of the bow AI linePower and arcMaxPower parameters. They no longer represent absolute initial speed directly; both are multipliers of the vanilla player's fully drawn bow speed of 3.0. Both now default to 1.0f, or 100% vanilla full-draw speed. line uses 3.0 times linePower as its fixed initial speed, while arc uses 3.0 times arcMaxPower as the trajectory solver's speed limit. The existing [0.1f,10.0f] range therefore represents 10% through 1000%.

The dynamic Faction module is not implemented in this version and remains deferred to 26.7.13-2.

### 26.7.11-2
Added the bow AI skill, which runs only while a mob holds a vanilla bow in its main hand, with a low-trajectory line mode and fixed high-angle arc mode.

Added enable, doConsume, and modePriority plus independent range, cooldown, shooting chance, accuracy, charge time, and initial-speed parameters for both modes. modePriority accepts only "line" or "arc" and invalid values fall back to "line". A ShootChance of 0.0 completely disables that mode.

Added mobileWhileShooting, lineSafeDistance, and arcSafeDistance. Inside the current mode's SafeDistance, the shooter stops melee navigation and retreats while aiming regardless of mobileWhileShooting. Outside the safe distance but still inside the mode range, mobileWhileShooting controls skeleton-like randomized forward, backward, and sideways strafing.

line can be checked from lineMinRange through lineMaxRange and uses linePower, moving-target lead, and light gravity compensation. arc can be checked from arcMinRange through arcMaxRange, uses arcAngle as a fixed angle, and numerically simulates vanilla arrow drag of 0.99 and gravity of 0.05 to binary-search the required initial speed, limited by arcMaxPower.

range controls only whether shooting behaviour starts and does not guarantee physical reach. If arc requires more than arcMaxPower or has no complete solution, it still fires at the configured angle and power limit and may miss.

modePriority resolves simultaneous ready modes. A failed priority-mode chance check does not fall back during the same tick; if the priority mode is cooling down or otherwise not ready, the other mode may be checked. Selecting a mode immediately begins its CooldownTicks whether the chance succeeds or fails.

After a successful chance check, the mob draws its main-hand bow for the corresponding ChargeTicks; 0 releases immediately. Its head and arms follow the calculated launch vector while drawing, giving line a low aiming pose and arc a visibly raised pose matching its designed angle. Charging is cancelled while preserving cooldown if the target dies, becomes unattackable, leaves range or sight, the bow leaves the main hand, ammunition becomes unavailable, NoAI is enabled, or shield-break vulnerability begins.

Supported off-hand arrows are preferred. With doConsume 0b they are not consumed and an empty off hand supplies unlimited normal arrows. With doConsume 1b a supported off-hand arrow is required and one is consumed only after release. The empty off hand is explicitly synchronized after the last arrow. Bow durability is not additionally consumed.

With bow enabled and held in the main hand, all melee attacks are blocked so melee goals cannot deal damage while shooting. Once a usable mode range is reached, melee navigation stops and bow-specific safe-distance movement takes over. Vanilla RangedBowAttackGoal draw and fire calls remain suppressed, preventing skeletons from bypassing PMB chances and cooldowns.

Suppressed strafe commands written directly by the skeleton's vanilla RangedBowAttackGoal while permitting only PMB bow retreat and randomized movement commands, so mobileWhileShooting 0b now makes skeletons genuinely shoot from a stationary position.

The main-hand bow pose is now forcibly applied at the end of model animation for normal humanoids, skeletons, zombies, and illagers so model-specific attacks or crossed-arm animations cannot replace it. The special held-item layers used by vindicators and evokers also render their bow and arms while drawing.

All parameters are included in NBT reading, saving, configuration detection, defaults, and clamping, with complete English and Chinese structures, references, and command examples.

### 26.7.11-1
Added the mace AI skill.

Added enable, smashRange, hitChance, damageReduction, and smashCooldownTicks with defaults of 0b, 3.0, 0.5, 0.5, and 100 respectively. All parameters are included in NBT reading, saving, configuration detection, and clamping.

Mace AI activates only while a mob holds a vanilla mace in its main hand, is airborne and moving downward, and meets the vanilla minimum fall distance for a mace smash. The target must also be alive, attackable, visible, and within smashRange.

Once eligible, the mob immediately performs one hitChance check and enters the smashCooldownTicks cooldown whether the check succeeds or fails. On success, it faces the target, swings its main hand, and attacks through the vanilla Mob and mace paths. On failure, no attack is performed.

While the skill is enabled and the mob is in a valid falling-mace state, ordinary melee-goal attacks that did not pass through the mace skill check are prevented. This stops them from bypassing hitChance, smashCooldownTicks, or damageReduction. Ordinary melee attacks in other states remain unchanged.

A skill-triggered mace attack first calculates vanilla base damage, enchantments, and fall-height bonus, then multiplies the final damage by (1-damageReduction). The default 0.5 halves final damage while preserving vanilla mace sounds, knockback, durability use, and fall-state handling.

Mace AI does not trigger while the mob has NoAI enabled or is in shield-break vulnerability. Added the complete English and Chinese structures, parameter references, and command examples.

mace can work together with downward wind_charge bounce: wind-charge AI handles launch, airborne target retention, and movement, while mace AI controls the falling smash chance, cooldown, and damage.

### 26.7.10-2
Added the wind_charge AI skill as a shared foundation that can also be used by future wind-charge mace and wind-charge spear AI behaviours.

Added enable, throwRange, throwChance, throwCooldownTicks, throwAccuracy, bounceRange, bounceChance, and bounceCooldownTicks. Wind-charge AI only runs while the mob holds a vanilla wind charge in either hand and does not consume it by default.

Added the boolean doConsume parameter with a default of 0b. It controls both throw and bounce mode. When enabled, each successful trigger consumes one wind charge from the hand that performed the action.

Added the float inAirTrackStrength parameter with a range of 0.0–1.0 and a default of 0.012. It controls the maximum horizontal acceleration added toward the target per game tick after a downward self-bounce. A value of 0.0 disables horizontal tracking acceleration but does not disable airborne facing correction.

Added wind-charge throw mode. When a visible target is within throwRange, the mob checks throwChance every throwCooldownTicks game ticks. On success, it swings the hand holding the wind charge and throws with random spread controlled by throwAccuracy toward a lead point calculated from the target's position and velocity.

Added wind-charge bounce mode. When the mob is on the ground and its target is within bounceRange, it checks bounceChance every bounceCooldownTicks game ticks. On success, it immediately looks down, swings, jumps, and fires a wind charge beneath its feet to maximize upward launch. Bounce mode has priority when both modes can be checked.

Added post-bounce airborne pursuit. The mob remembers its bounce target for up to 60 game ticks, restores that target if vanilla airborne pathfinding clears it, and applies capped horizontal steering so it continues advancing while preserving gravity and wind-charge launch velocity. The state clears after landing, target invalidation, or timeout.

Reduced post-bounce airborne steering strength. It now applies only a small acceleration while targetward velocity is below a threshold and does not reduce or replace existing horizontal inertia, making airborne turning much less conspicuous.

Mobs in post-bounce flight now continuously align their view, head, and body with the target after the initial downward pose. Starting a melee attack during that pose ends it immediately and turns the mob toward the entity being attacked, preventing hits while visibly facing away.

Fixed the client continuing to render a wind charge after doConsume depleted the final item in that hand even though the server-side hand was empty. Consumption now resets the mob's equipment slot with the remaining stack or an explicit empty stack so the equipment change is synchronized.

Dedicated wind-charge mace and wind-charge spear attack decisions are not implemented yet. The high success rate of mace attacks after bouncing is an emergent compatibility effect of target retention and airborne movement.

Downward self-bounce now gives the mob vanilla LivingEntity impulse fall protection. Returning to the impulse height does not deal fall damage from that launch; when landing lower, the additional drop below the launch point is still included in normal fall-damage calculation.

Wind-charge AI does not trigger while the mob has NoAI enabled or is in shield-break vulnerability. All parameters are included in NBT reading, saving, configuration detection, defaults, clamping, and the complete English and Chinese structures, parameter references, and command examples.

### 26.7.10-1
Added shield-break vulnerability after true shield breaks.

Added the integer shield parameter disableVulnerTicks with a range of 0–72000 and a default value of 40. When this value is 0, shield-break vulnerability is skipped.

Added the float shield parameter vulnerDamageMultiplier with a range of 1.0–100.0 and a default value of 1.5.

Added the float shield parameter disableKBMultiplier with a range of 0.0–100.0 and a default value of 1.2. When this value is 0.0, the extra shield-break knockback is skipped.

The final hit that truly breaks the shield now applies extra shield-break knockback. The applied base strength is 0.6 multiplied by disableKBMultiplier.

During shield-break vulnerability, the mob cannot attack, keeps clearing its target and navigation, and suppresses AI movement input while preserving existing velocity from knockback or other external forces. It renders with the same shaking style used by zombie-villager curing or piglin zombification. The implementation does not enable NoAI, so normal physics such as gravity continue to apply.

Damage received during shield-break vulnerability is multiplied by vulnerDamageMultiplier.

Adjusted shield-raising animations for zombie villagers and zombified piglins so their blocking arm pose is no longer overwritten by zombie-arm animation. Illager shield rendering now also forces the blocking arm render state to BLOCK and switches crossed-arm poses to NEUTRAL while a shield is being used. The special held-item layers used by vindicators and evokers now render held items while the mob is using a blocking item instead of only rendering during vanilla aggressive or spellcasting states.

Shield toughness now resets immediately when the current shield use is interrupted without a true shield break, such as when the mob loses its target while shielding.

### 26.7.9-3
Added the integer shield parameter critToughnessDamage with a range of 1–100 and a default value of 1.

The player's vanilla critical-hit eligibility is captured before attack charge resets. Critical shield-disabling attacks consume critToughnessDamage points, while normal shield-disabling attacks consume 1 point.

A non-breaking critical shield-disabling hit plays the zombie wooden-door attack sound and emits 10 oak-door debris particles. A non-breaking normal shield-disabling hit emits 6 particles, and any other blocked hit emits 3 particles. A true shield break plays the zombie wooden-door break sound and emits 24 particles; the effects do not overlap on the final hit.

PMB-controlled shields now continue to be recognized while the server still sees the mob actively using its off-hand shield, covering the final transition tick where the internal shield-use counter may have just reached zero.

PMB shield angle checks now prefer the attacker's entity position over the raw damage-source position when available, avoiding close-range or extended-interaction-range source positions flipping across the defender and causing apparent shield penetration.

### 26.7.9-2
Added the integer shield parameter shieldToughness.

It controls how many shield-disabling attacks are required to break the current shield-use round.

The counter refreshes when the normal shield cooldown ends and before a new round after a forced cooldown.

The default value is 1, preserving the previous behaviour.

Fixed the vanilla shield activation delay leaving a mob visually shielding but still vulnerable during the first few ticks.

PMB-controlled shields now block from the first shield-use tick, preventing rapid attacks from slipping through.

Fixed repeated attacks accumulating shield-impact momentum while the target was still in its damage invulnerability interval.

Shield recoil, durability damage, and shieldToughness consumption now occur at most once per valid damage interval. Attacks ignored by the normal damage cooldown can still be visually blocked, but no longer accumulate velocity or consume additional shield toughness.

### 26.7.9-1
This mod was separated from the [Age of Cavalry](https://github.com/Avalanche1510/Age-of-Cavalry) mod and is now developed as an independent project.

Add change log and documentations

Implemented basic shield usage for mobs and correct shield-holding animations for supported humanoid mobs.
Shield-holding animations are not guaranteed to look correct on non-humanoid mobs.

Currently supported shield-holding animations: Zombies, Piglins, Skeletons, Illagers.

New parameters and structure:
```text
{
    PmbAi:
        {
            shield:
                {
                    enable: <bool>,
                    range: <float>,
                    shieldChance: <float>,
                    minUseTicks: <int>,
                    maxUseTicks: <int>,
                    cooldownTicks: <int>,
                    blockingAngle: <float>,
                    axeDisableCooldownTicks: <int>,
                    speedReduction: <float>
                }
        }
}
```
