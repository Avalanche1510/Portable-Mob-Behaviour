# 重锤 (`skill.conditional.mace`)

完整默认配置（可选来源列表的省略语义见下文）：

```text
mace:{FetchSource:["mainhand"],enable:0b,smashRange:3.0f,hitChance:0.5f,damageReduction:0.5f,smashCooldownTicks:100,randomCooldownBias:20}
```

重锤AI固定使用主手。主手已有原版重锤时直接使用，否则从FetchSource永久换入；副手来源的交换当刻占用双手和SMASH。重锤只在下落、目标与概率冷却条件满足时执行，不接受preferredHand。

<details>
<summary>触发与伤害细节</summary>

锤击检查条件:

1. 实体属于生物，并且FetchSource可将原版重锤解析到所需的主手攻击路径。
2. 启用(enable)为真。
3. 生物未着地，垂直速度向下，并满足原版重锤锤击所需的最低坠落距离。
4. 存在一个存活、可攻击、视线可见且进入smashRange的敌对目标。
5. 不处于smashCooldownTicks规定的锤击检查冷却。
6. 生物没有启用NoAI，且未处于破盾虚弱。

通过优先级与资源准入后进行一次hitChance检查。不论检查成功或失败，都会立即进入smashCooldownTicks游戏刻的冷却。检查成功时，生物会面向目标、挥动主手，并通过原版Mob攻击与重锤伤害流程执行锤击；检查失败时不发动攻击。

技能启用且生物处于有效的下落重锤状态时，普通近战Goal未经本技能检查发起的攻击会被阻止。这样原版AI不会绕过hitChance、smashCooldownTicks或damageReduction。生物不处于有效重锤下落状态时，普通近战不受影响。

锤击会先由原版计算基础攻击伤害、附魔和坠落高度提供的重锤额外伤害，再将最终结果乘以(1-damageReduction)。默认damageReduction为0.5，因此由本技能触发的最终重锤伤害减半。成功命中后会清零累计坠落距离，下一次锤击必须重新满足原版最低坠落距离；未命中或被阻止的攻击不清零。原版重锤音效、击退和耐久消耗保持不变。

</details>

<details>
<summary>参数结构</summary>

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

smashRange
含义: 下落时能够立刻启动锤击检查的最大目标距离，同时要求生物与目标之间存在视线
传参类型: 浮点数
值域: [0.0f, 64.0f]
默认值: 3.0f

hitChance
含义: 每次锤击检查成功并实际发动攻击的概率
传参类型: 浮点数
值域: [0.0f, 1.0f]
默认值: 0.5f

damageReduction
含义: 本技能触发的重锤最终伤害衰减比例；实际伤害乘以(1-damageReduction)
传参类型: 浮点数
值域: [0.0f, 1.0f]
默认值: 0.5f
说明: 0.0不衰减伤害，0.5使最终伤害减半，1.0使最终伤害为0

smashCooldownTicks
含义: 每次锤击检查后进入冷却的游戏刻，无论检查成功或失败都会应用
传参类型: 整数
值域: [0, 72000]
默认值: 100
说明: 0表示下一个游戏刻即可再次检查

randomCooldownBias
含义: 每次锤击检查开始冷却时额外增加的闭区间均匀随机游戏刻数
传参类型: 整数
值域: [0, 1200]
默认值: 20
说明: 0表示锤击检查使用固定冷却
```

实体读取数据时，超出值域的参数会被自动限制到相应边界。
通常情况下，20游戏刻等于1秒。

</details>

<details>
<summary>指令示例</summary>

```text
生成一个主手持有重锤，并使用全部默认重锤参数的僵尸。
summon zombie ~ ~ ~ {PmbAi:{skill:{conditional:{mace:{enable:1b}}}}, equipment:{mainhand:{id:mace}}}

生成一个在4格范围内必定发动锤击、造成75%原版重锤伤害，并在每次检查后冷却3秒的僵尸。
summon zombie ~ ~ ~ {PmbAi:{skill:{conditional:{mace:{enable:1b, smashRange:4.0f, hitChance:1.0f, damageReduction:0.25f, smashCooldownTicks:60}}}}, equipment:{mainhand:{id:mace}}}

生成一个同时启用脚下风弹弹射和重锤锤击的僵尸。
summon zombie ~ ~ ~ {PmbAi:{skill:{active:{wind_charge:{enable:1b, bounceRange:6.0f, bounceChance:1.0f}},conditional:{mace:{enable:1b}}}}, equipment:{mainhand:{id:mace}, offhand:{id:wind_charge}}}

保留其他参数，仅禁用最近一只僵尸的重锤AI。
data merge entity @e[type=zombie,sort=nearest,limit=1] {PmbAi:{skill:{conditional:{mace:{enable:0b}}}}}
```

</details>

<details><summary>模块化指令快速启用</summary>

```mcfunction
/pmb skill conditional @n[type=minecraft:zombie] mace append [enable=1b]
/pmb skill conditional @n[type=minecraft:zombie] mace get
/pmb skill conditional @n[type=minecraft:zombie] mace modify [enable=0b]
```

append要求技能节点尚不存在。已有节点的enable若为显式字段使用modify；若尚未手写则使用insert。

</details>

[返回技能指南](../README.md)
