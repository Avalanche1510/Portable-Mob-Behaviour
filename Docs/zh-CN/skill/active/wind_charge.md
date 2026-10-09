# 风弹 (`skill.active.wind_charge`)

完整默认配置（可选来源列表的省略语义见下文）：

```text
wind_charge:{FetchSource:["mainhand","offhand"],preferredHand:"off",enable:0b,throwRange:16.0f,throwChance:0.35f,throwCooldownTicks:40,throwAccuracy:0.9f,bounceRange:4.0f,bounceChance:0.35f,bounceCooldownTicks:40,bouncePostJumpTicks:2,randomCooldownBias:20,doConsume:0b}
```

风弹AI为持有原版风弹的生物加入投掷和弹射两种模式，也可以作为风弹重锤与后续风弹矛AI共用的基础行为。mace技能现已提供独立的下落重锤攻击检查，并可与风弹弹射后的目标保持和空中推进协同工作；风弹矛的专用攻击决策尚未实现。

<details>
<summary>触发、行为与兼容性细节</summary>

风弹触发条件:

1. 实体属于生物，并且主手或副手持有原版风弹。
2. 启用(enable)为真。
3. 存在一个存活的敌对目标，或生物正在执行Faction的passively_evasive/actively_evasive逃跑并持有有效威胁目标。
4. 生物没有启用NoAI，且未处于破盾虚弱。
5. 对应模式的距离、冷却和概率检查均符合要求。

弹射模式在两个模式均可检查时优先执行。生物在bounceRange范围内且位于地面时，会每隔bounceCooldownTicks游戏刻检查一次bounceChance。默认`bouncePostJumpTicks:2`在检查成功后先从地面跳起，两刻后从当时实际脚下发射；设为`0`保留原先同刻起跳并发射。准备阶段持续占用选定手位和LOOK；目标／物品失效、提前落地、NoAI、死亡、虚弱或更高优先动作成功抢占会取消释放，但不退还已消耗的尝试冷却。物品交换与消耗只在释放时发生。已经在空中的生物不会开始或缓存新的弹射。

投掷模式会在目标处于throwRange范围内且视线可见时，每隔throwCooldownTicks游戏刻检查一次throwChance。检查成功后，生物会挥动持有风弹的手，并向目标当前位置与移动速度计算出的提前量投掷风弹。throwAccuracy控制原版投射物随机散布，1.0表示无随机散布。

执行Faction逃跑时，风弹AI会把当前Faction规避目标作为风弹目标，而不会把它重新设置为攻击目标。throw模式会在释放时将视线、头部和身体短暂转向追击威胁，同时保持逃跑导航不变，随后继续逃离；bounce模式仍按bounceRange、bounceChance和bounceCooldownTicks触发脚下弹射，但弹起后的水平引导与身体朝向改为远离威胁。两个模式继续使用原有的距离、概率、冷却、精度和doConsume参数，不会因为处于evasive状态而绕过任何检查。

doConsume统一控制投掷和弹射两种模式是否消耗风弹。默认情况下，风弹仅作为技能所需的持有物，不会在触发后被消耗；启用后，每次成功投掷或弹射都会从实际挥动的手中消耗一个风弹。

脚下弹射会复用原版生物的冲量坠落保护机制。生物返回风弹弹射发生的高度时不会受到本次弹射造成的坠落伤害；如果落点低于弹射起点，超出的跌落高度仍会正常造成伤害。生物在本次弹射后确实离地并重新落到地面时，由服务端播放一次原版玩家无伤落地音；未离地、死亡或仅执行throw模式不会触发该音效。

风弹弹起后的空中追踪由`pathfinding.air_tracking`控制，而非风弹字段；耗尽最后一个风弹不会取消已锁存会话。

落地当刻由PMB冲击会话占用一次LOOK，下一刻释放。只有真实风弹爆炸影响到生物才写入保护，落地后清除本次PMB自有保护；外部替换的冲击上下文不被清除。

</details>

<details>
<summary>参数结构</summary>

```text
wind_charge:
    {
        enable: <bool>,
        FetchSource: [<string>, ...],
        preferredHand: <string>,
        doConsume: <bool>,
        throwRange: <float>,
        throwChance: <float>,
        throwCooldownTicks: <int>,
        throwAccuracy: <float>,
        bounceRange: <float>,
        bounceChance: <float>,
        bounceCooldownTicks: <int>,
        bouncePostJumpTicks: <int>,
        randomCooldownBias: <int>
    }
```

</details>

<details>
<summary>参数细节</summary>

```text
enable
含义: 是否启用这项AI技能
传参类型: 布尔值
值域: 0b, 1b
默认值: 0b

FetchSource
含义: 可为该技能提供必需物品的有序位置列表
传参类型: 字符串列表
值域: mainhand、offhand、inventory、inventory:1至inventory:256，或inventory:1..9等闭区间
默认值: 省略；保留该技能原有的手槽要求
说明: 目标手无合适物品时才按顺序扫描；空或无效列表不提供可取物来源

doConsume
含义: 投掷或脚下弹射成功触发时是否消耗一个风弹
传参类型: 布尔值
值域: 0b, 1b
默认值: 0b
说明: 同时控制两种模式，并从触发时实际持有风弹的手中消耗

throwRange
含义: 可以触发投掷模式的最大目标距离，同时要求生物与目标之间存在视线
传参类型: 浮点数
值域: [0.0f, 64.0f]
默认值: 16.0f

throwChance
含义: 每次投掷检查的成功概率
传参类型: 浮点数
值域: [0.0f, 1.0f]
默认值: 0.35f

throwCooldownTicks
含义: 两次投掷概率检查之间的游戏刻
传参类型: 整数
值域: [0, 72000]
默认值: 40
说明: 0表示每个游戏刻均可检查

throwAccuracy
含义: 投掷风弹的精度；数值越高，随机散布越小
传参类型: 浮点数
值域: [0.0f, 1.0f]
默认值: 0.9f
说明: 1.0表示无随机散布

bounceRange
含义: 可以触发脚下弹射模式的最大目标距离
传参类型: 浮点数
值域: [0.0f, 64.0f]
默认值: 4.0f

bounceChance
含义: 每次脚下弹射检查的成功概率
传参类型: 浮点数
值域: [0.0f, 1.0f]
默认值: 0.35f

bounceCooldownTicks
含义: 两次脚下弹射概率检查之间的游戏刻
传参类型: 整数
值域: [0, 72000]
默认值: 40
说明: 0表示每个游戏刻均可检查

bouncePostJumpTicks
含义: 成功从地面起跳后等待多少刻才向脚下释放风弹；0为同刻起跳并释放
传参类型: 整数
值域: [0, 10]
默认值: 2
说明: 不影响远距离throw模式

randomCooldownBias
含义: 每次任一模式开始检查冷却时额外增加的闭区间均匀随机游戏刻数
传参类型: 整数
值域: [0, 1200]
默认值: 20
说明: throw与bounce分别独立抽取；0表示两种冷却均固定

preferredHand
类型: string
值域: main、off、main-enforce、off-enforce
默认值: "off"
含义: 动作开始时选择使用手；普通值仅因持续PMB手占用而回退，enforce禁止回退
```

实体读取数据时，超出值域的参数会被自动限制到相应边界。
通常情况下，20游戏刻等于1秒。

</details>

<details>
<summary>指令示例</summary>

```text
生成一个副手持有风弹，并使用全部默认风弹参数的僵尸。
summon zombie ~ ~ ~ {PmbAi:{skill:{active:{wind_charge:{enable:1b}}}}, equipment:{mainhand:{id:iron_sword}, offhand:{id:wind_charge}}}

生成一个会精确投掷风弹，并在近距离必定用风弹弹射追击的僵尸。每次成功使用都会消耗一个风弹。
summon zombie ~ ~ ~ {PmbAi:{skill:{active:{wind_charge:{enable:1b, doConsume:1b, throwRange:24.0f, throwChance:1.0f, throwCooldownTicks:40, throwAccuracy:1.0f, bounceRange:6.0f, bounceChance:1.0f, bounceCooldownTicks:60}}},pathfinding:{air_tracking:{trackAcceleration:0.012f,trackMaxHorizontalSpeed:0.3f,trackDurationTicks:-1}}}, equipment:{mainhand:{id:wind_charge,count:64}}}

保留其他参数，仅禁用最近一只僵尸的风弹AI。
data merge entity @e[type=zombie,sort=nearest,limit=1] {PmbAi:{skill:{active:{wind_charge:{enable:0b}}}}}
```

</details>


<details><summary>模块化指令快速启用</summary>

```mcfunction
/pmb skill active @n[type=minecraft:zombie] wind_charge append [enable=1b]
/pmb skill active @n[type=minecraft:zombie] wind_charge get
/pmb skill active @n[type=minecraft:zombie] wind_charge modify [enable=0b]
```

append要求技能节点尚不存在。已有节点的enable若为显式字段使用modify；若尚未手写则使用insert。

</details>

[返回技能指南](../README.md)
