# 动态 Faction

Faction是独立于PmbAi技能的服务端动态关系模块。玩家和所有LivingEntity均可加入一个派系，但不能同时属于两个派系。成员身份保存在实体根NBT中：

```text
PmbFaction:{
    FactionName:"EmpireGuard"
}
```

FactionName是区分大小写的普通字符串，不受Minecraft资源ID的`namespace:path`和小写限制。指令中直接输入不含空白的名称，不需要添加引号，例如`EmpireGuard`、`北方联盟`或`Raiders-02`。FactionName必须对应已经创建的服务端派系。删除派系会清除已加载成员；未加载成员在下次加载时清除失效名称。玩家死亡重生后保留成员身份。

PmbFaction不是客户端显示用的临时数据，而是由服务端读取和保存的实体持久NBT；完整成员关系不会同步给客户端。可以在生成实体时直接写入，无需先生成再使用选择器：

```mcfunction
/pmb faction create 灾厄庭
/summon minecraft:vindicator ~ ~ ~ {PmbFaction:{FactionName:"灾厄庭"}}
/summon minecraft:pillager ~ ~ ~ {PmbFaction:{FactionName:"灾厄庭"},PmbAi:{skill:{active:{bow:{enable:1b}}}},equipment:{mainhand:{id:"minecraft:bow"}}}
/data merge entity @e[type=minecraft:vindicator,sort=nearest,limit=1] {PmbFaction:{FactionName:"灾厄庭"}}
```

`/summon`只写入成员身份，不会自动创建派系；对应派系必须已经存在于服务端SavedData中，且FactionName必须大小写完全一致。不存在的名称会在实体的下一个服务端tick被清除。数据包函数可以先创建派系，再在同一函数中直接执行带PmbFaction的summon。

派系定义保存在服务端主世界SavedData中并由所有维度共享，不会把完整规则同步给客户端：

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

- `allowInternalConflict`：默认`0b`。关闭时同派系成员固定按allied处理；开启后Relationships和defaultAttitude可以使同派系成员敌对或逃避。
- `allowFriendlyFire`：默认`1b`。设为`0b`时，由受击方派系阻止member或allied造成的近战、投射物和有责任者爆炸伤害。环境伤害不受影响。
- `groupRevenge`：默认`0b`。启用后，member或单向allied对象受击会使附近成员使用各自FOLLOW_RANGE反击责任者；不会反击另一个member或allied。
- `overrideTeamRules`：默认`1b`。启用时Faction可以覆盖原版team对同队索敌、反击和友伤的限制；关闭时同team关系优先遵守原版规则。
- `defaultAttitude`：默认`"neutral"`。控制没有匹配任何Relationship的目标。

### VanillaCompatRules

VanillaCompatRules与Rules、Relationships平级，专门控制Faction如何保留各类原版Brain生物的物种特色。内部先按生物分组，再填写该生物的兼容规则；缺少整个集合、缺少生物组或缺少单项时均使用默认值。当前支持：

- `piglin.greed`：默认`1b`。保留普通猪灵拾取、欣赏和交易黄金的行为；关闭时阻止新的黄金拾取或欣赏，并清理相关Brain记忆。
- `piglin.guarding`：默认`1b`。允许neutral普通猪灵保留打开受保护容器、破坏金块等原版事件产生的ANGRY_AT并发起攻击；关闭后neutral仍可在实际受击时反击。
- `piglin.avoidance`：默认`1b`。保留普通猪灵对僵尸猪灵等僵尸化生物及其他原版AVOID_TARGET的规避；该兼容规则高于Faction hostile，因此启用时不会把原版要求规避的目标设为Faction战斗目标。

  设为`0b`后压制原版规避，此时派系hostile可以使猪灵主动攻击僵尸猪灵。passively_evasive和actively_evasive产生的PMB规避不受此项影响。

  压制原版AVOID_TARGET时，若猪灵已有派系或Brain战斗目标，不清除FIGHT活动用于追击的WALK_TARGET；没有战斗目标时才清理旧规避路径。
- `piglin.unreachableGiveUp`：默认`1b`。仅对加入Faction的普通猪灵生效。原版Brain因持续超过200刻无法接近而放弃目标时，PMB在约60刻内不重新指定同一目标；目标明显移动、重新受击，或手持弓／弩且符合当前的射程、视线及适用弹药条件时解除临时抑制。不进行额外路径搜索。
- `piglin.admiringPausesActiveSkills`：默认`1b`。普通猪灵欣赏金物期间暂停PMB主动战斗技能、弓蓄力与战斗移动，仍允许条件坠落自救。独立的Brain意图协调还会暂缓发布派系与原版战斗目标，避免欣赏与FIGHT／弩射击争抢；欣赏结束后，内部保留的派系目标经距离和关系校验再发布。猪灵蛮兵、疣猪兽也通过同一协调边界处理战斗／规避，但没有欣赏金物意图。`greed:0b`时欣赏暂停不生效。
- `piglin.hurtAdmirationPolicy`：默认`"vanilla"`，只能为`"vanilla"`、`"combat"`或`"continue"`。`vanilla`保留自己实际受击的欣赏中断和队友受击的群体怒意；`combat`还会在合法派系援护，或确认由队友受击广播并实际写入对应ANGRY_AT时中断欣赏参战。金块、容器守护产生的怒意不属于受击广播。`continue`让自己或队友受击时继续欣赏，但不取消实际伤害和怒意。
- `piglin.hurtAdmirationDisableTicks`：整数，默认`400`，范围`[0,72000]`。统一控制玩家、其他生物、猪灵同族实际受击，以及`combat`策略下合法队友援护中断欣赏后的禁用时间。`0`表示**不打断当前欣赏**且不设置禁用计时；`continue`也始终不打断。只有有效派系普通猪灵且`greed:1b`时该项生效；普通自己受击仍沿用原版副手物品处置。
- `piglin.hurtAdmiredItemPolicy`：默认`"drop"`，可选`"drop"`或`"store"`。仅在新增的`combat`队友受击中断中应用：把副手物品丢到世界，或先放入猪灵原版库存、溢出再掉落。掉落物短暂禁止拾取。自己受击继续使用原版物品处理。
- `brainAiDebug`：顶层开关，默认`0b`。启用时，仅对该Faction中的普通猪灵、猪灵蛮兵和疣猪兽，每游戏刻向服务端终端打印一份Brain与PMB状态，并记录关键活动、声音、目标、怒意、受击和弓事件。不会同步到客户端聊天栏；日志量极大，可能显著增加磁盘和服务器刻开销，定位问题后应关闭。僵尸猪灵不包含在内。
- `breeze.windChargeNoAnger`：默认`1b`。原版风弹伤害命中`#minecraft:no_anger_from_wind_charge`内的实体时会特意跳过`lastHurtByMob`记录；PMB会在伤害已成功接受后使用伤害源已有的合法LivingEntity owner进行neutral反击补偿，但该规则启用时仍保留旋风人风弹不激怒的原版特性。

  其他合法LivingEntity发射的风弹可以触发neutral反击；设为`0b`后，合法的旋风人来源也可以触发。补偿只适用于该原版标签内且已加入有效Faction的Mob，仍要求责任者存活、可攻击、位于受击者自身FOLLOW_RANGE以内，并且实时态度为neutral。

  通用的`#minecraft:no_anger`伤害类型标签始终优先并关闭该补偿。不会凭空生成owner，也不改变groupRevenge及其他伤害类型。

猪灵蛮兵对PMB目标的合法性兼容属于防止Brain状态循环的必要修复，不是可关闭的特色行为，因此不放入piglin组。后续生物会以`hoglin:{...}`、`warden:{...}`等独立组加入，不会把不同生物的字段混在同一级。

灾厄村民的Faction战斗兼容同样不改变非战斗状态的原版特色行为。掠夺者和卫道士获得有效Faction战斗目标时，会暂时退出巡逻队原版“原地警戒并等待目标靠近”的HoldGround行为；正在寻路返回袭击或回收袭击旗帜的Raider也会先进入Faction战斗。

目标失效后，这些原版行为仍可按原条件重新启动。唤魔者原版没有主动接近敌人的战斗Goal，因此会在非施法阶段接近Faction目标至16格以内，再继续使用原版唤魔与尖牙施法；该行为不提供新的伤害方式。

### Relationships和态度

Relationships按列表顺序检查，最后一条匹配规则生效。单个target中所有非空类别之间为AND；同一数组内的多个值为OR。空target不允许写入。

- `factions`匹配目标唯一的FactionName；`teams`匹配原版记分板team名称。
- `types`接受实体类型ID或以`#`开头的实体类型标签；`tags`匹配实体的`/tag`。
- `reputation`接受精确值`"5"`、上界`"..100"`、下界`"1.."`和闭区间`"1..100"`。
- `reputation`与`roles`只匹配玩家，并读取目标玩家在规则所属派系中的Players记录；玩家不需要加入该派系即可拥有记录。

五种态度的固定语义：

- `hostile`：在自身FOLLOW_RANGE内发现可见目标后主动索敌；暂时失去视线仍可继续追击，离开范围后清除目标。

  每十刻扫描从本轮可见的敌人中选取最近候选；不要求它比当前目标更近。若本轮没有可见敌人，已通过实时验证的战斗目标仍会保留；目标死亡、跨维度、超出FOLLOW_RANGE或未通过实时态度检查时由验证阶段清除。
- `neutral`：不主动索敌，受到有效攻击后允许反击。`VanillaCompatRules.piglin.guarding:1b`时，猪灵打开受保护容器、破坏金块等原版事件产生的ANGRY_AT属于保留的物种特有被动敌意，也可以触发攻击。

  对于原版风弹不激怒标签内的受击者，`VanillaCompatRules.breeze.windChargeNoAnger`控制旋风人owner是否继续保留该例外；其他合法LivingEntity owner仍可触发PMB neutral反击。
- `allied`：不主动索敌且不反击；是否阻止实际伤害由受击方allowFriendlyFire决定。
- `passively_evasive`：受到攻击后开始逃跑，直到攻击者离开自身FOLLOW_RANGE。
- `actively_evasive`：可见目标进入自身FOLLOW_RANGE时主动逃跑，目标离开范围后停止。

Faction的扫描、反击、已有目标验证和猪灵专用Brain目标解析均会排除死亡实体，以及处于创造或旁观模式的玩家。即使此类玩家攻击成员，也不会被写入Faction战斗或规避目标。

猪灵、猪灵蛮兵与疣猪兽的原版受击反击入口同样会在非生存模式攻击者处停止，避免原版Brain与Faction目标反复争夺而出现举起武器、放下武器和怒吼循环。

Faction态度覆盖成员的原版主动敌意，同时管理普通Mob目标和Brain的ATTACK_TARGET/AVOID_TARGET。对Goal生物，PMB会在原版targetSelector完成后、攻击与移动goalSelector运行前重新确认Faction目标，避免原版较短的索敌距离清除FOLLOW_RANGE内的远距离目标；PMB不会因此新增通用近战Goal。

仅在Brain注册了对应MemoryModule时才读取记忆，并且仅在目标变化时写入；Faction不会无条件清除未由PMB持有的原版AVOID_TARGET。猪灵和猪灵蛮兵使用专用兼容层，使PMB战斗目标通过原版目标合法性检查，同时保留neutral猪灵由原版ANGRY_AT驱动的守卫与贪婪行为。

灾厄巡逻队的HoldGround、袭击寻路和旗帜回收不会压住更高优先级的Faction战斗；没有原版接近Goal的唤魔者只获得进入原版施法有效距离所需的专用导航，不获得新攻击。hostile只提供目标，不会为没有攻击行为的生物凭空添加近战技能。

两种evasive只产生撤退目标；速度、选点与实际移动由[pathfinding.retreat](pathfinding.md)负责。

### `/pmb faction`指令

所有管理指令要求游戏管理员权限。主要语法如下：

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

关系target直接使用SNBT compound。例如：

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

设置成员时派系必须存在，选择结果必须全部为LivingEntity。关系写入会验证态度、派系、team、实体类型、实体类型标签、声望范围和列表下标。批量设置只修改当前选中的实体，不会保存或持续维护FactionMembers列表。

[返回文档主页](../模组文档.md)
