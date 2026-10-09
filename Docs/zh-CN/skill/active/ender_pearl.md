# 末影珍珠 (`skill.active.ender_pearl`)

完整默认配置（可选来源列表的省略语义见下文）：

```text
ender_pearl:{FetchSource:["mainhand","offhand"],preferredHand:"off",enable:0b,minThrowRange:4.0f,maxThrowRange:16.0f,throwChance:0.35f,throwCooldownTicks:40,throwAccuracy:0.9f,maxThrowPower:1.0f,throwAngle:30.0f,doConsume:0b,doTeleportDamage:0b,requireEyeSight:1b,randomCooldownBias:20}
```

末影珍珠AI使主手或副手持有原版末影珍珠的生物能够自动计算弹道并投掷。普通战斗模式以当前攻击目标为落点；执行Faction的passively_evasive或actively_evasive逃跑时，则使用远离当前规避威胁的逃生位置作为落点。

<details>
<summary>触发、弹道与逃跑细节</summary>

投掷检查条件:

1. 实体属于生物，并且主手或副手持有原版末影珍珠。
2. 启用(enable)为真，且throwChance大于0.0。
3. 存在一个存活的攻击目标，或生物正在执行Faction逃跑并持有有效规避威胁。
4. requireEyeSight为1b时，普通攻击目标需位于minThrowRange至maxThrowRange之间且视线可见；Faction规避威胁需位于maxThrowRange以内且视线可见。为0b时只跳过这一视线要求。仅对原版TargetGoal，0b还可在目标已被取得后、目标存活且处于同一世界、位于FOLLOW_RANGE和此战斗投掷范围内、throwChance大于0.0、且FetchSource可将珍珠解析到任一手时，隔墙保留mob.getTarget()；绝不改变初次索敌。
5. 不处于throwCooldownTicks规定的投掷检查冷却。
6. 生物没有启用NoAI，且未处于破盾虚弱。

每次满足条件时会立即进入throwCooldownTicks冷却，然后进行一次throwChance检查。因此概率检查失败同样会冷却，0游戏刻表示下一个游戏刻可以再次检查。检查成功后，生物会面向实际发射轨迹、挥动持有末影珍珠的手，并播放原版末影珍珠投掷音效。

maxThrowPower不是绝对初速度，而是原版玩家站立不动时投掷末影珍珠初速度1.5的倍率上限。默认1.0表示求解速度最高为1.5，2.0表示最高为3.0。throwAngle固定末影珍珠相对水平面的投掷仰角，默认为30度。

求解器按照原版末影珍珠每刻0.99空气阻力和0.03重力的离散弹道，在固定仰角下二分求解命中所需初速度，并仅对目标水平移动迭代修正提前量，保持当前眼部高度。几何可达但1.5乘以maxThrowPower不足时，仍按上限投掷，允许落点不足。

固定仰角几何不可达、水平距离接近零或数值求解无效时跳过投掷，不消耗珍珠，保留正常检查冷却。计算不再具有300刻弹道截断。

末影珍珠发射点（眼部下方0.1格）浸入水中时禁止投掷；浅水中该点露出实际流体表面时仍可使用。战斗与规避模式均会在提交投掷前和实际释放前检查。

minThrowRange与maxThrowRange控制普通战斗的投掷检查区间，使目标过近时生物不会继续反复使用珍珠；这两个参数不保证当前maxThrowPower和throwAngle能够覆盖整个物理射程。

执行Faction逃跑时会忽略minThrowRange；requireEyeSight为1b时，只要规避威胁不超过maxThrowRange且视线可见即可检查，为0b时只跳过这一视线要求。珍珠不会向威胁投掷，也不会将威胁写为攻击目标。

实际落点优先使用当前逃跑导航中比当前位置更远离威胁的可达目标，并限制在maxThrowRange内，没有有效导航目标时使用稳定的直接远离位置。珍珠命中后仍执行原版末影珍珠传送、落点碰撞和伤害流程。

throwAccuracy为1.0时不添加随机散布，数值越低散布越大。doConsume为0b时，手中的末影珍珠只作为技能所需持有物，不会被消耗；为1b时，每次实际成功投掷后从挥动的手消耗一个，最后一个耗尽时显式同步空手。距离或概率检查不会提前消耗物品。

</details>

<details>
<summary>参数结构</summary>

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
        doTeleportDamage: <bool>,
        doConsume: <bool>,
        requireEyeSight: <bool>,
        randomCooldownBias: <int>
    }
```

</details>

<details>
<summary>参数细节</summary>

```text
doTeleportDamage
含义: 成功传送后是否通过原版末影珍珠伤害源造成5点伤害；摔落保护及适用的原版防护可以减免。传送失败不会造成传送伤害。
传参类型: 布尔值
值域: 0b, 1b
默认值: 0b

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

minThrowRange
含义: 普通战斗中能够启动末影珍珠投掷检查的最小目标距离
传参类型: 浮点数
值域: [0.0f, 256.0f]
默认值: 4.0f
说明: Faction逃跑检查会忽略该下限

maxThrowRange
含义: 能够启动末影珍珠投掷检查的最大目标或规避威胁距离；Faction逃跑落点也会被限制在该距离内
传参类型: 浮点数
值域: [minThrowRange, 256.0f]
默认值: 16.0f
说明: 只控制AI触发范围，不保证当前初速度具有足够物理射程；小于minThrowRange的输入会被限制为minThrowRange

throwChance
含义: 每次投掷检查成功并实际投掷的概率
传参类型: 浮点数
值域: [0.0f, 1.0f]
默认值: 0.35f
说明: 0.0完全关闭投掷检查

throwCooldownTicks
含义: 每次投掷检查后进入冷却的游戏刻，无论概率成功或失败都会应用
传参类型: 整数
值域: [0, 72000]
默认值: 40
说明: 0表示下一个游戏刻即可再次检查

throwAccuracy
含义: 投掷末影珍珠的精度；数值越高，随机散布越小
传参类型: 浮点数
值域: [0.0f, 1.0f]
默认值: 0.9f
说明: 1.0不添加随机散布

maxThrowPower
含义: 末影珍珠弹道求解速度上限相对于原版玩家静止投掷速度1.5的倍率
传参类型: 浮点数
值域: [0.1f, 10.0f]
默认值: 1.0f
说明: 1.0表示最大初速度1.5；求解器可以使用不超过该上限的较低速度

throwAngle
含义: 末影珍珠投掷方向相对于水平面的固定仰角
传参类型: 浮点数
值域: [1.0f, 89.0f]
默认值: 30.0f
说明: 求解器保持该角度并改变初速度

doConsume
含义: 成功投掷时是否消耗一个末影珍珠
传参类型: 布尔值
值域: 0b, 1b
默认值: 0b
说明: 从实际挥动并持有末影珍珠的手中消耗

requireEyeSight
含义: 末影珍珠投掷检查是否要求能看见攻击目标或Faction规避威胁
传参类型: 布尔值
值域: 0b, 1b
默认值: 1b
说明: 0b只跳过这一视线门；目标有效性、范围、冷却、概率、物品与其他全部行为保持不变

randomCooldownBias
含义: 每次投掷检查开始冷却时额外增加的闭区间均匀随机游戏刻数
传参类型: 整数
值域: [0, 1200]
默认值: 20
说明: 0表示投掷检查使用固定冷却

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
生成一个副手持有末影珍珠，并使用全部默认末影珍珠参数的僵尸。
summon zombie ~ ~ ~ {PmbAi:{skill:{active:{ender_pearl:{enable:1b}}}}, equipment:{mainhand:{id:iron_sword}, offhand:{id:ender_pearl}}}

生成一个会在6至32格内以30度仰角精确投掷、使用两倍原版初速度上限并消耗珍珠的僵尸。
summon zombie ~ ~ ~ {PmbAi:{skill:{active:{ender_pearl:{enable:1b, minThrowRange:6.0f, maxThrowRange:32.0f, throwChance:1.0f, throwCooldownTicks:60, throwAccuracy:1.0f, maxThrowPower:2.0f, throwAngle:30.0f, doConsume:1b}}}}, equipment:{mainhand:{id:ender_pearl,count:16}}}

生成一个可由Faction evasive关系驱动、向逃生位置投掷珍珠的卫道士。
summon vindicator ~ ~ ~ {PmbAi:{skill:{active:{ender_pearl:{enable:1b, minThrowRange:8.0f, maxThrowRange:24.0f, throwChance:1.0f}}}}, equipment:{offhand:{id:ender_pearl}}}

保留其他参数，仅禁用最近一只僵尸的末影珍珠AI。
data merge entity @e[type=zombie,sort=nearest,limit=1] {PmbAi:{skill:{active:{ender_pearl:{enable:0b}}}}}
```

</details>


<details><summary>模块化指令快速启用</summary>

```mcfunction
/pmb skill active @n[type=minecraft:zombie] ender_pearl append [enable=1b]
/pmb skill active @n[type=minecraft:zombie] ender_pearl get
/pmb skill active @n[type=minecraft:zombie] ender_pearl modify [enable=0b]
```

append要求技能节点尚不存在。已有节点的enable若为显式字段使用modify；若尚未手写则使用insert。

</details>

[返回技能指南](../README.md)
