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

PmbFaction是服务端持久实体NBT，可直接写入summon数据，例如`/summon minecraft:vindicator ~ ~ ~ {PmbFaction:{FactionName:"灾厄庭"}}`；派系必须预先存在。

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

PmbFaction is persistent server-side entity NBT and can be supplied directly to summon, for example `/summon minecraft:vindicator ~ ~ ~ {PmbFaction:{FactionName:"IllagerCourt"}}`; the faction must already exist.

## Resource Guide:

[Documentation](https://github.com/Avalanche1510/Portable-Mob-Behaviour/blob/26.1.1/Docs/docs.md)  
[Change Log](https://github.com/Avalanche1510/Portable-Mob-Behaviour/blob/26.1.1/ChangeLog/changelog.md)

This mod includes AI-generated code.

~~plz remind me in issues if I forget to update the links in each branch !~~

</details>
