# 风弹跨越（`skill.conditional.traversal.wind_charge`）

执行寻路层提出的风弹边。它是确定性条件技能：寻路负责规划，本技能负责物品可行性与释放。

起跳前，生物先在三维位置上接近规划起跳点，再保持停止移动输入，等待已有水平惯性自然衰减。只有实际出发位置通过飞行校验后，才开始预跳并消耗冷却；预跳期间持续保持移动控制，不清除重力或外部击退速度。

最终释放失败会在同刻 K 快照中保留具体原因，包括状态改变、水平惯性、碰撞、未接触目标或缺少安全下降路径。同一轮规划复用相同终点的局部路径结果，保留原有搜索预算；出现候选弧线本身不代表一定能释放。

```text
traversal:{enable:0b,methods:["wind_charge","ender_pearl"],wind_charge:{FetchSource:["offhand","mainhand"],preferredHand:"off",enable:1b,doConsume:0b,cooldownTicks:40,randomCooldownBias:20,maxConnectionDistance:16.0f,minHorizontalConnectionDistance:0.0f,maxConnectionHeight:8.0f,minSavedTicks:10,postJumpTicks:2}}
```

<details><summary>参数与生命周期</summary>

连接距离/高度`0..256`；最小水平跨度默认`0.0f`；冷却`0..72000`；偏移`0..1200`；节省刻`0..72000`。高度上限约束起跳点与落点的高差，不约束风弹弧线顶点。

`fallback`保留完整原版路线。有完整全局路线时，`efficient`必须通过碰撞检查连接其后续节点，并至少节省`minSavedTicks`。没有完整全局路线但局部路线完整时，仍比较节时，灯笼等小障碍同样受此限制。

已执行的局部寻路返回不完整或空路径时，只要普通局部行走确实受阻，且端点在三维距离上严格更接近目标，就允许物理安全的跨越。因预算不足而未检查不算这种情况；不再要求封闭连通或孤柱证明。

既有`efficient`流水豁免仍要求真实的两端干地跨水连接及可继续接近目标的后续路线。静水、连接侧面的水和空中接敌不适用该豁免。

规划要求可达起跳点、飞行全程无碰撞、已加载区块和可用落点。水与蛛网可以作为落点；生物碰撞箱任何部位接触岩浆都要求免疫火焰。有净空的普通一格上跳按步行处理，不抢占风弹候选。直线候选之外，仅在风弹跨越启用且就绪时，以目标为中心探测半径1至6格的8个方向，也可将未完成的原版路线末端作为有界的可达边缘锚点；这仍不是全局寻路。直线与外缘阶段各保留独立的轨迹检查配额，直线阶段给空中接敌留一份。空中接敌是区别于站立落点的终点：普通局部步行受阻、目标可攻击时，同高或目标占据窄边也可尝试；飞行必须不撞方块、接触目标，再抵达安全的接触后下降位置。这只提供接敌路线，不额外赋予近战攻击能力。

从较高位置越过墙进入低处时，直线搜索也会有限地向后移动起跳点，并在目标附近检查最多9个低处落点。预计落地或接触后的下降会造成未受保护的摔落伤害时拒绝该连接；承载目标的载具也按实际碰撞障碍处理。这些局部检查不保证任意墙、屋檐或壕沟都有可行路线。`efficient`比较普通路线时使用模拟所得飞行刻数，不再固定至少35刻。K诊断会暂时显示最近候选的端点及拒绝阶段，例如起跳点路径、轨迹预算、方块／载具碰撞、未接触目标、不安全下降、未知局部路径或节时不足；这只是排查线索，不代表已确认游戏中的原因。

接近起跳点时每10刻最多重提一次导航；若导航提前结束但仍远离起跳点，或30刻没有明显进展，便暂时放弃该点，60刻内不重复选择。已启用风弹跨越的追击生物若沿原版路线持续30刻无水平进展，也会停止陈旧导航并重新规划，60刻内最多重置一次。到锚点后事件最多等待`conditionalWaitTicks`。默认`postJumpTicks:2`在获得调度后先从地面起跳，两刻后从实际脚下释放；设为`0`保留原先从地面立即释放。准备阶段占用选定手位与LOOK；提前落地、碰撞／释放前轨迹复核失败、目标／物品失效、虚弱、NoAI、死亡或更高优先动作抢占会取消释放，但保留起跳时记下的冷却；物品交换／消耗仅在释放时发生。已经在空中的生物不会开始或缓存该跳跃。现在按26.2原版距离、遮挡和抗性依赖的风弹冲量估计预跳后的落点，并在释放前按实际状态重验；配置的高度上限不保证物理可达。成功释放的当刻面向接敌或落地端点。空中接敌会按与可行性模拟相同的水平控制规则追踪预先核验的固定安全下降航点，不会在接触后继续动态追逐目标；普通落点仍在拥有LOOK时追踪其端点。

起跳证明逐刻推进风弹和生物到地面碰撞，在爆炸刻的预测生物碰撞箱而非释放当刻位置采样遮挡；未知或受阻轨迹保守拒绝。预跳等待和爆炸前刻数均计入路线耗时。PMB在Mob刻末排入转向，直到下一次`serverAiStep`才施加，因此延迟为0时爆炸后第一个位移仍不转向；延迟为1时前两个位移均不转向。规划和运行时共用此边界。释放前仍按实际状态重验。

</details>

```mcfunction
/pmb skill conditional @e[type=minecraft:zombie,limit=1] traversal append [enable=1b]
/pmb skill conditional @e[type=minecraft:zombie,limit=1] traversal.wind_charge append [enable=1b]
```

<details>
<summary>参数细节</summary>

```text
enable
含义: 是否启用风弹跨越方法；父层traversal必须启用并列入当前策略优先表
传参类型: 布尔值
值域: 0b, 1b
默认值: 1b

FetchSource
含义: 可提供风弹的有序位置列表
传参类型: 字符串列表
值域: mainhand、offhand、inventory、inventory:1至inventory:256或闭区间inventory:1..9
默认值: 省略；先检查offhand，再检查mainhand
说明: 目标手没有风弹时按顺序交换；范围可重叠，空列表不妨碍使用目标手已有风弹

preferredHand
含义: 风弹跨越优先使用的手
传参类型: 字符串
值域: main、off、main-enforce、off-enforce
默认值: off
说明: 普通偏好仅在PMB占用首选手时回退；enforce不回退

doConsume
含义: 成功释放时是否消耗一枚风弹
传参类型: 布尔值
值域: 0b, 1b
默认值: 0b

cooldownTicks
含义: 真实提交后的基础冷却游戏刻
传参类型: 整数
值域: [0, 72000]
默认值: 40
说明: 0允许下一刻重新检查

randomCooldownBias
含义: 每次冷却额外增加的均匀随机游戏刻上限
传参类型: 整数
值域: [0, 1200]
默认值: 20
说明: 实际偏移在0到设定值之间，包含两端

maxConnectionDistance
含义: 风弹连接允许的最大距离，不保证物理可达
传参类型: 浮点数
值域: [0.0f, 256.0f]
默认值: 16.0f

minHorizontalConnectionDistance
含义: 风弹连接起跳点到落点／空中接敌点允许的最小水平跨度；0允许近距离竖直跃升
传参类型: 浮点数
值域: [0.0f, 256.0f]
默认值: 0.0f

maxConnectionHeight
含义: 风弹连接允许的最大高度差，仍须通过实际轨迹检查
传参类型: 浮点数
值域: [0.0f, 256.0f]
默认值: 8.0f

minSavedTicks
含义: efficient模式下相对原版路线至少节省的预计游戏刻
传参类型: 整数
值域: [0, 72000]
默认值: 10

postJumpTicks
含义: 风弹跨越成功从地面起跳后到发射的准备刻数；0保留从地面立即发射
传参类型: 整数
值域: [0, 10]
默认值: 2
```

</details>

<details><summary>完整配置与场景示例</summary>

```mcfunction
/summon minecraft:zombie ~ ~ ~ {Tags:["pmb_wind_traversal"],equipment:{offhand:{id:"minecraft:wind_charge",count:64}},PmbAi:{skill:{conditional:{traversal:{enable:1b,methods:["wind_charge"],wind_charge:{FetchSource:["offhand","mainhand"],preferredHand:"off",enable:1b,doConsume:0b,cooldownTicks:40,randomCooldownBias:20,maxConnectionDistance:16.0f,minHorizontalConnectionDistance:0.0f,maxConnectionHeight:8.0f,minSavedTicks:10,postJumpTicks:2}}}},pathfinding:{strategies:{combat:{routeMode:"fallback",dangerousFallPolicy:"conditional_required",requireEyeSight:0b}}}}}
/pmb skill conditional @e[tag=pmb_wind_traversal] traversal.wind_charge modify [enable=0b]
```

把目标放在树冠或短墙另一侧。fallback只在完整原版路径不可用时考虑跳跃；测试玻璃迷宫抄近路时，把routeMode改成efficient。到达起跳锚点后等待实际释放；高墙、低顶或无安全落点应拒绝。

</details>

[返回特殊跨越](traversal.md)
