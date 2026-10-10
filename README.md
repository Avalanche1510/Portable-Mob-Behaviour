<details>
<summary>中文</summary>

# 热插拔怪物AI

## 许可证

本项目采用CC0许可证。您可以自由学习、使用并将其纳入自己的项目。

## 简介
本模组引入了新的、高度自定义且相对简单的怪物AI行为数据结构。
该数据结构可以通过使用指令或数据包，在游戏内即时修改并生效，立刻修改或允许特定的AI行为。
可选的参数和定义数据结构的语法将在文档和更新日志中给出。

本模组需要安装在客户端和服务端。双方通过独立网络协议号检查兼容性，专用服务端与客户端使用同一个通用JAR。

除可移植AI技能外，模组还提供服务端动态Faction模块，用于实时管理实体和玩家之间的敌对、中立、结盟、逃避、群体复仇和友伤关系；独立的VanillaCompatRules按生物管理原版Brain特色行为的保留方式。

启用wind_charge技能并持有风弹的Faction逃跑生物可以回头向威胁投掷风弹或用脚下弹射加速撤离；弹射后正常落地会播放原版玩家无伤落地音。

ender_pearl技能允许持有末影珍珠的生物在自定义最小至最大距离区间内按固定仰角自动求解所需初速度；普通战斗会预判移动目标，Faction逃跑忽略最小距离并将珍珠投向远离威胁的逃生位置。

PmbFaction是服务端持久实体NBT，可直接写入summon数据，例如`/summon minecraft:vindicator ~ ~ ~ {PmbFaction:{FactionName:"灾厄庭"}}`；派系必须预先存在。

`26.10.4-1`将AI分成动作技能与独立寻路层：`skill.active`包含弓、战斗风弹、战斗珍珠和盾；`skill.conditional`包含重锤、特殊跨越和坠落自救，后两者分别在父场景下配置风弹与珍珠方法。`pathfinding`统一管理路线、战术移动、撤退与空中追踪。动作技能共用策略优先级，寻路不再作为会锁死其他动作的资源。

本次继续修正风弹局部跨越：两格墙／沟、栏杆及水／蛛网路线按真实可通行性和代价评估；关闭的跨越功能不触发额外路径搜索。成功重锤命中会清零累计坠落距离。实际跨越与地狱高密度实体性能仍需实机验证。

Faction新增猪灵不可达目标暂缓重选、金物欣赏期间暂停主动技能，以及受击欣赏策略。`VanillaCompatRules.brainAiDebug`可让派系猪灵、蛮兵、疣猪兽逐刻向服务端终端输出AI状态；默认关闭，开启时日志量很大。

派系猪灵系现在通过统一意图边界协调原版Brain与PMB：普通猪灵欣赏金物时暂缓战斗目标与弩攻击，结束后重新校验目标；猪灵蛮兵和疣猪兽保留原版具体行为。`hurtAdmirationDisableTicks`统一受击欣赏禁用时长，默认400刻，设为0则不打断欣赏。

迁移注意：`26.10.4-1`不会读取旧扁平`PmbAi`、旧`skillPriorities`、技能形式的`air_tracking`及平铺的`wind_traversal`/`pearl_traversal`。请先备份世界并转换summon与数据包。使用第28–256格库存的数据降级到旧版时仍会丢失这些高位物品。

<details>
<summary>近期版本概览</summary>

`26.10.4-1`破坏性重构为`PmbAi.skill.{active,conditional,priorities}`与`PmbAi.pathfinding`。`conditional.traversal`与`conditional.fall_rescue`各有父层总开关、可调方法顺序及独立风弹/珍珠方法，指令使用`traversal.wind_charge`等点号ID。新增局部跨越、坠落自救、寻路策略和珍珠传送伤害开关；空中追踪改为成功动作提交的运行时上下文；Faction逃跑移动移入pathfinding。风弹跨越新增默认0格的最小水平连接距离、有限侧向落点和柱顶空中接敌；启用的坠落自救在入水时取消。特殊路线按服务端公平预算跨刻检查，但当前只建立到当前目标的保守局部连接，不是任意地形全局路线图。指令改为`/pmb skill ...`与`/pmb pathfinding ...`，旧`/pmb skills`不再存在。实机路线与释放时机仍需测试。

`26.9.19-1`修复`air_tracking`的风弹锁存：成功弹射后会等待下一次空中刻，并始终追踪当刻捕获的目标，不再被之后原版/Faction重新索敌取消。重锤下落来源扩展到完整FOLLOW_RANGE（含`smashRange`以内）；K调试补充锁存、时长、目标、空中状态、优先级与可选LOOK诊断。

`26.9.18-3`将中英文文档改为分层折叠结构，并把`air_tracking`整理为与其他技能同级的完整章节。

`26.9.4-2`加入统一的PMB技能调度入口与生物额外物品栏。技能可通过`FetchSource`从主手、副手或指定库存区间取得物品；装备交换永久保留。启用原版`CanPickUpLoot`时，未被原版装备或物种专用逻辑取走的物品可以进入额外库存。bow和ender_pearl新增默认`1b`的`requireEyeSight`；设为`0b`时只跳过各自的视线检查。

`26.9.8-1`曾加入模块化技能测试指令（当时语法为`/pmb skills skill <targets> <skill> <mode>`）。该旧入口已由`26.10.4-1`的`/pmb skill ...`替代。

`26.9.8-2`新增`preferredHand`，支持`main`、`off`、`main-enforce`、`off-enforce`及指令补全。bow默认main，shield/wind_charge/ender_pearl默认off，mace固定主手。目标手已有正确物品时直接使用，否则按`FetchSource`永久换装。弓的`AmmoSource`独立控制箭矢来源，省略时双手优先再扫描PMB库存。动作结束不归还物品；移动意图独立裁决，不阻止其他技能激活。

`26.9.10-1`统一五项技能为先判定概率、再竞争动作资源，并加入默认20的`randomCooldownBias`随机冷却偏移；概率失败不再无效抢占资源。该版本还新增`VanillaCompatRules.breeze.windChargeNoAnger`，默认保留旋风人风弹的原版不激怒行为，同时允许其他合法LivingEntity来源的风弹使neutral Faction生物反击。

`26.9.11-1`加入默认`K`键的服务端技能调试探针。管理员将准星对准具有PmbAi技能的Mob并按键后，请求者聊天栏与服务端日志会同时输出上一完整技能刻的诊断报告；报告在实体身份后立即突出普通近战是否被压制，再展示启用技能及其正在执行/等待状态、最近候选、资源、绑定和运行上下文。聊天版本按客户端语言本地化并为关键状态着色；按键可在控制设置中重绑。聊天输出使用原版系统消息，不广播给其他玩家，也不使用自定义S2C负载同步完整PmbAi。本版本还将弓的近战压制改为实时判定：只有实际手持弓、存在合法当前目标、距离位于有效射击模式范围内，且消费模式仍有AmmoSource弹药时才压制；FetchSource只负责技能启动与换装。

`26.9.17-1`加入按combat、retreat和idle分别配置的技能优先列表，并支持严格高优先级对冲突的持续阶段进行资源级抢占。每刻最多提交一个新动作或不可逆完成动作；平级同刻候选按实体轮换，平级运行阶段不会互相抢占。策略变化逐阶段复核，不再无差别取消仍然有效的动作；物品、弹药和绑定计划会在抢占前完成，提交失败会进入调试报告并停止本刻仲裁。`NAVIGATION`统一表示移动决策（包括站桩），`vanilla`表示普通战斗寻路和标准Goal/Brain近战边界。技能测试指令改为`/pmb skills skill ...`，并新增`/pmb skills priority ...`。

`26.9.17-2`为`PmbInventory`新增默认`0b`的`ScatterDrops`。开启后，怪物死亡时通过该格`DropChance`检查的完整物品堆会像玩家死亡掉落物一样从胸前向外随机飞散，并使用原版40刻拾取延迟。该参数不会损坏、拆分或额外掉落物品，也不影响溢出物品和手持装备。

`26.9.18-1`将`PmbInventory.Slots`上限从27扩展至256，并按实际`Slots`动态分配底层库存；未配置库存的Mob不会预留256格。`FetchSource`和`AmmoSource`同步支持`inventory:256`与`inventory:1..256`，完整范围仍可简写为`inventory`。旧的0–27格数据直接兼容；包含第28–256格物品的数据降级到旧版本时会丢失这些高位物品。

`26.9.18-2`新增独立`air_tracking`技能。它以有序`activationSkills`选择空中追踪来源，统一控制横向加速度、最高水平速度、持续时间、初始视线要求与`NAVIGATION`/可选`LOOK`。风弹弹射在上升和下落间保持锁存；重锤下落且目标位于FOLLOW_RANGE内时可在砸击距离外提供来源；其他注册技能可在自身持续阶段中提供通用来源。旧`wind_charge.inAirTrackStrength`和`inAirTrackDurationTicks`已移除，必须迁移至`air_tracking`。

</details>

## 资源指南：

[模组文档](Docs/模组文档.md)
[更新日志](ChangeLog/更新日志.md)

该模组包含AI生成的代码。


</details>




















<details>
<summary>English</summary>

# Portable Mob Behaviour

## License

This project is available under the CC0 license. Feel free to learn from it and incorporate it into your own projects.

## Introduction
This mod introduces a new, customizable, portable data structure for mob AI behaviours.
Players can dynamically modify or enable special mob behaviours using commands or data packs.
Available parameters and syntax are documented in the documentation and change log.

This mod is required on both client and server. Both sides verify compatibility through an independent network protocol number and use the same universal JAR.

In addition to portable AI skills, the mod provides a server-side dynamic Faction module for live hostile, neutral, allied, evasive, group-revenge, and friendly-fire relationships between entities and players. Separate per-mob VanillaCompatRules control how vanilla Brain species behavior is preserved.

A Faction-fleeing mob with the wind_charge skill enabled and a held wind charge can look back to throw at its threat or self-bounce to accelerate its escape; a normal post-bounce landing plays the vanilla player's no-damage landing sound.

The ender_pearl skill lets a mob holding an ender pearl solve the required initial speed at a fixed launch angle inside a configurable minimum-to-maximum distance interval. Combat throws lead moving targets, while Faction evasion ignores the minimum and throws toward an escape position away from the threat.

PmbFaction is persistent server-side entity NBT and can be supplied directly to summon, for example `/summon minecraft:vindicator ~ ~ ~ {PmbFaction:{FactionName:"IllagerCourt"}}`; the faction must already exist.

`26.10.4-1` separates action skills from pathfinding. `skill.active` contains bow, combat wind charge, combat ender pearl, and shield; `skill.conditional` contains mace, special traversal, and fall rescue. Traversal and rescue each configure wind and pearl methods under one parent scene. `pathfinding` owns route policy, tactical movement, retreat, and airborne tracking. Actions share strategy priorities; navigation is no longer a resource that can lock unrelated actions.

This update also revises local wind traversal across two-block walls/gaps, fences, and slow water/cobweb routes using passability and estimated cost. Disabled traversal does not run extra path searches. Successful mace hits clear accumulated fall distance. Live traversal and crowded-Nether performance still need gameplay verification.

Faction adds a temporary unreachable-target give-up for piglins, pauses active skills while they admire gold, and offers configurable hurt-admiration behavior. `VanillaCompatRules.brainAiDebug` can print per-tick server-terminal AI state for faction piglins, brutes, and hoglins; it is off by default and produces very large logs when enabled.

Faction Brain mobs now share an intent boundary between PMB and vanilla behavior: an ordinary piglin admiring gold defers combat targets and crossbow attacks, then revalidates its pending target; brutes and hoglins retain their vanilla behavior execution. `hurtAdmirationDisableTicks` unifies the disable duration after damage, defaults to 400 ticks, and at 0 does not interrupt admiration.

Migration notice: `26.10.4-1` does not read the old flat `PmbAi`, old `skillPriorities`, skill-form `air_tracking`, or flat `wind_traversal`/`pearl_traversal`. Back up worlds and convert summon/data-pack data first. Downgrading data that uses inventory slots 28–256 still loses those high-slot items.

<details>
<summary>Recent version overview</summary>

`26.10.4-1` destructively reorganizes data as `PmbAi.skill.{active,conditional,priorities}` plus `PmbAi.pathfinding`. `conditional.traversal` and `conditional.fall_rescue` each have a parent switch, ordered methods, and independent wind/pearl settings. Commands use dotted IDs such as `traversal.wind_charge`. It adds local traversal, fall rescue, route policy, and optional pearl teleport damage; airborne tracking becomes a runtime context installed by committed actions, and Faction retreat movement moves into pathfinding. Wind traversal now has a zero-block default minimum horizontal span, bounded lateral landing probes, and pillar-top airborne contact; enabled fall rescue cancels on water contact. Special-route checks advance under a fair server budget, but currently build only a conservative local connection to the current objective rather than an arbitrary-terrain global route graph. Commands move to `/pmb skill ...` and `/pmb pathfinding ...`; `/pmb skills` is removed. Live route and release timing still require gameplay testing.

`26.9.19-1` fixes the `air_tracking` wind latch: a successful bounce waits for the next airborne tick and continues to use its captured target instead of being cancelled by later vanilla/Faction retargeting. Falling-mace sourcing now covers full FOLLOW_RANGE including smash range; K debug adds latch, duration, target, airborne, priority, and optional-LOOK diagnostics.

`26.9.18-3` reorganizes the bilingual documentation into layered folds and gives `air_tracking` a complete peer skill section.

`26.9.4-2` adds one coordinated PMB skill tick and a mob-only extra inventory. A skill can use `FetchSource` to obtain its real item from either hand or selected inventory ranges; equipment exchanges retain their final layout. With vanilla `CanPickUpLoot` enabled, items left behind by vanilla equipment and species-specific pickup logic may enter this extra inventory. bow and ender_pearl add `requireEyeSight`, defaulting to `1b`; `0b` skips only that skill's line-of-sight check.

`26.9.8-1` introduced the modular test command (then `/pmb skills skill <targets> <skill> <mode>`). That old root is replaced by `/pmb skill ...` in `26.10.4-1`.

`26.9.8-2` adds `preferredHand` with `main`, `off`, `main-enforce`, `off-enforce` and enum completion. Bow defaults to main, shield/wind_charge/ender_pearl to off; mace always uses mainhand. A suitable destination-hand item is used directly; otherwise `FetchSource` supplies a permanent exchange. Bow `AmmoSource` independently selects arrows, defaulting to hands then PMB inventory. Actions no longer restore equipment and movement intentions do not block skill activation.

`26.9.10-1` makes all five skills roll chance before competing for action resources and adds `randomCooldownBias`, defaulting to 20, so failed rolls no longer occupy unusable resources. It also adds `VanillaCompatRules.breeze.windChargeNoAnger`, preserving vanilla no-anger behavior for Breeze-owned wind charges by default while allowing wind charges from other valid living owners to make neutral Faction mobs retaliate.

`26.9.11-1` adds a server-side skill debug probe bound to `K` by default. When a gamemaster aims at a Mob with PmbAi skills and presses the key, both the requester's chat and the server log print a diagnostic report for the last completed skill tick. Immediately after entity identity, the report highlights whether ordinary melee is suppressed, then shows enabled skills and their active/ready state, recent candidates, resources, bindings, and runtime context. The chat copy is localized by the client and color-codes important states. The key can be rebound in Controls. Chat output uses a vanilla system message, is not broadcast to other players, and does not use a custom S2C payload to synchronize complete PmbAi data. This version also makes bow melee suppression a live predicate: it applies only with a bow actually held, a valid current target inside an enabled shooting mode's range, and AmmoSource ammunition when consuming; FetchSource is used only to start the skill and exchange equipment.

`26.9.17-1` adds separate combat, retreat, and idle skill-priority lists plus strict higher-priority resource preemption for conflicting sustained phases. At most one new action or irreversible completion commits per tick; equal simultaneous candidates rotate per Mob and equal running phases do not preempt. Strategy changes revalidate phases instead of indiscriminately cancelling still-valid actions; item, ammunition, and binding plans finish before preemption, while a commit failure is reported and ends that tick's arbitration. `NAVIGATION` uniformly represents movement decisions including holding still, while `vanilla` marks ordinary combat navigation and standard Goal/Brain melee. Skill editing moves to `/pmb skills skill ...` and `/pmb skills priority ...` manages complete priority lists.

`26.9.17-2` adds `ScatterDrops` to `PmbInventory`, defaulting to `0b`. When enabled, each intact stack that passes its slot's `DropChance` roll on mob death scatters outward from the chest like a player death drop and uses the vanilla 40-tick pickup delay. It never damages, splits, or creates additional drops and does not affect overflow items or held equipment.

`26.9.18-1` expands `PmbInventory.Slots` from 27 to 256 and allocates its backing inventory to the actual `Slots` value, so Mobs without configured storage do not reserve 256 slots. `FetchSource` and `AmmoSource` now accept `inventory:256` and `inventory:1..256`; the complete range still formats as `inventory`. Existing 0–27-slot data remains compatible. Downgrading data that uses slots 28–256 to an older version loses those high-slot items.

`26.9.18-2` adds independent `air_tracking`. Its ordered `activationSkills` selects an airborne source and centrally controls horizontal acceleration, maximum horizontal speed, duration, initial eyesight, and required `NAVIGATION` plus optional `LOOK`. Wind bounce latches across ascent and descent; a falling mace can provide a source before smash range while its target is inside FOLLOW_RANGE; another registered skill can provide a generic source during its sustained phase. Old `wind_charge.inAirTrackStrength` and `inAirTrackDurationTicks` were removed and must migrate to `air_tracking`.

</details>

## Resource Guide:

[Documentation](Docs/docs.md)
[Change Log](ChangeLog/changelog.md)

This mod includes AI-generated code.


</details>
