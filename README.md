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

`26.9.4-2`加入统一的PMB技能调度入口与生物额外物品栏。技能可以通过`FetchSource`从主手、副手或指定的1–27号库存区间取得实际物品；装备交换永久保留。启用原版`CanPickUpLoot`时，未被原版装备或物种专用逻辑取走的物品可以进入额外库存。bow和ender_pearl新增默认`1b`的`requireEyeSight`；设为`0b`时只跳过各自的视线检查。

`26.9.8-1`加入模块化`/pmb skills <targets> <skill> <mode>`测试指令。它为五种技能提供参数名和值补全、严格类型和值域检查、原子批量写入，以及可跨保存区分的显式参数增删；无需再手写整段PmbAi SNBT。

`26.9.8-2`新增`preferredHand`，支持`main`、`off`、`main-enforce`、`off-enforce`及指令补全。bow默认main，shield/wind_charge/ender_pearl默认off，mace固定主手。目标手已有正确物品时直接使用，否则按`FetchSource`永久换装。弓的`AmmoSource`独立控制箭矢来源，省略时双手优先再扫描PMB库存。动作结束不归还物品；移动意图独立裁决，不阻止其他技能激活。

`26.9.10-1`统一五项技能为先判定概率、再竞争动作资源，并加入默认20的`randomCooldownBias`随机冷却偏移；概率失败不再无效抢占资源。该版本还新增`VanillaCompatRules.breeze.windChargeNoAnger`，默认保留旋风人风弹的原版不激怒行为，同时允许其他合法LivingEntity来源的风弹使neutral Faction生物反击。

`26.9.11-1`加入默认`K`键的服务端技能调试探针。管理员将准星对准具有PmbAi技能的Mob并按键后，请求者聊天栏与服务端日志会同时输出上一完整技能刻的诊断报告；报告在实体身份后立即突出普通近战是否被压制，再展示启用技能及其正在执行/等待状态、最近候选、资源、绑定和运行上下文。聊天版本按客户端语言本地化并为关键状态着色；按键可在控制设置中重绑。聊天输出使用原版系统消息，不广播给其他玩家，也不使用自定义S2C负载同步完整PmbAi。本版本还将弓的近战压制改为实时判定：只有实际手持弓、存在合法当前目标、距离位于有效射击模式范围内，且消费模式仍有AmmoSource弹药时才压制；FetchSource只负责技能启动与换装。

## 资源指南：

[模组文档](https://github.com/Avalanche1510/Portable-Mob-Behaviour/blob/26.1.1/Docs/模组文档.md)  
[更新日志](https://github.com/Avalanche1510/Portable-Mob-Behaviour/blob/26.1.1/ChangeLog/更新日志.md)

该模组包含AI生成的代码。

~~请在issues提醒我，如果我忘记了更新这个分支的链接！~~


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

`26.9.4-2` adds one coordinated PMB skill tick and a mob-only extra inventory. A skill can use `FetchSource` to obtain its real item from either hand or selected one-based inventory ranges from 1 through 27; equipment exchanges retain their final layout. With vanilla `CanPickUpLoot` enabled, items left behind by vanilla equipment and species-specific pickup logic may enter this extra inventory. bow and ender_pearl add `requireEyeSight`, defaulting to `1b`; `0b` skips only that skill's line-of-sight check.

`26.9.8-1` adds the modular `/pmb skills <targets> <skill> <mode>` testing command. It provides parameter-name and value completion for all five skills, strict type and range validation, atomic bulk writes, and persistent explicit-field editing without hand-writing complete PmbAi SNBT.

`26.9.8-2` adds `preferredHand` with `main`, `off`, `main-enforce`, `off-enforce` and enum completion. Bow defaults to main, shield/wind_charge/ender_pearl to off; mace always uses mainhand. A suitable destination-hand item is used directly; otherwise `FetchSource` supplies a permanent exchange. Bow `AmmoSource` independently selects arrows, defaulting to hands then PMB inventory. Actions no longer restore equipment and movement intentions do not block skill activation.

`26.9.10-1` makes all five skills roll chance before competing for action resources and adds `randomCooldownBias`, defaulting to 20, so failed rolls no longer occupy unusable resources. It also adds `VanillaCompatRules.breeze.windChargeNoAnger`, preserving vanilla no-anger behavior for Breeze-owned wind charges by default while allowing wind charges from other valid living owners to make neutral Faction mobs retaliate.

`26.9.11-1` adds a server-side skill debug probe bound to `K` by default. When a gamemaster aims at a Mob with PmbAi skills and presses the key, both the requester's chat and the server log print a diagnostic report for the last completed skill tick. Immediately after entity identity, the report highlights whether ordinary melee is suppressed, then shows enabled skills and their active/ready state, recent candidates, resources, bindings, and runtime context. The chat copy is localized by the client and color-codes important states. The key can be rebound in Controls. Chat output uses a vanilla system message, is not broadcast to other players, and does not use a custom S2C payload to synchronize complete PmbAi data. This version also makes bow melee suppression a live predicate: it applies only with a bow actually held, a valid current target inside an enabled shooting mode's range, and AmmoSource ammunition when consuming; FetchSource is used only to start the skill and exchange equipment.

## Resource Guide:

[Documentation](https://github.com/Avalanche1510/Portable-Mob-Behaviour/blob/26.1.1/Docs/docs.md)  
[Change Log](https://github.com/Avalanche1510/Portable-Mob-Behaviour/blob/26.1.1/ChangeLog/changelog.md)

This mod includes AI-generated code.

~~plz remind me in issues if I forget to update the links in each branch !~~

</details>
