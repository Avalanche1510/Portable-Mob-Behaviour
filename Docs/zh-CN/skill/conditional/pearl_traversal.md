# 珍珠跨越（`skill.conditional.traversal.ender_pearl`）

原版路线不可达时，可按父层`methods`顺序执行寻路层提出的珍珠边。默认排在风弹之后；调到前面则优先尝试珍珠。珍珠不只为提高效率使用。

```text
traversal:{enable:0b,methods:["wind_charge","ender_pearl"],ender_pearl:{FetchSource:["offhand","mainhand"],preferredHand:"off",enable:1b,doConsume:0b,doTeleportDamage:0b,cooldownTicks:40,randomCooldownBias:20,maxConnectionDistance:64.0f,maxConnectionHeight:64.0f,throwAngle:30.0f,maxThrowPower:1.0f,throwAccuracy:1.0f}}
```

<details><summary>参数与生命周期</summary>

连接距离/高度`0..256`；冷却`0..72000`；偏移`0..1200`；角度`1..89`；动能`0.1..10`；准度`0..1`。求解器逐刻模拟`0.03`重力、`0.99/0.8`空气/水阻力和已加载区块内首个方块碰撞，要求传送碰撞箱净空和安全支撑；故意不预检查实体。无完整解则无边。真实实体提前命中时接受实际传送位置并丢弃/重规划路线。`doTeleportDamage`与主动珍珠语义相同。

</details>

```mcfunction
/pmb skill conditional @e[type=minecraft:zombie,limit=1] traversal append [enable=1b]
/pmb skill conditional @e[type=minecraft:zombie,limit=1] traversal.ender_pearl append [enable=1b]
```

<details>
<summary>参数细节</summary>

```text
enable
含义: 是否启用珍珠跨越方法；父层traversal必须启用并列入当前策略优先表
传参类型: 布尔值
值域: 0b, 1b
默认值: 1b

FetchSource
含义: 可提供末影珍珠的有序位置列表
传参类型: 字符串列表
值域: mainhand、offhand、inventory、inventory:1至inventory:256或闭区间inventory:1..9
默认值: 省略；先检查offhand，再检查mainhand
说明: 目标手没有珍珠时才按顺序交换；范围可重叠，空列表不妨碍目标手已有珍珠

preferredHand
含义: 珍珠跨越优先使用的手
传参类型: 字符串
值域: main、off、main-enforce、off-enforce
默认值: off
说明: 普通偏好仅在PMB占用首选手时回退；enforce不回退

doConsume
含义: 成功投掷时是否消耗一枚珍珠
传参类型: 布尔值
值域: 0b, 1b
默认值: 0b

doTeleportDamage
含义: 成功传送后是否通过原版珍珠伤害源施加5点伤害
传参类型: 布尔值
值域: 0b, 1b
默认值: 0b
说明: 摔落保护可能减免此伤害

cooldownTicks
含义: 实际投掷后的基础冷却游戏刻
传参类型: 整数
值域: [0, 72000]
默认值: 40

randomCooldownBias
含义: 每次冷却额外增加的均匀随机游戏刻上限
传参类型: 整数
值域: [0, 1200]
默认值: 20

maxConnectionDistance
含义: 珍珠连接允许的最大距离，不保证物理可达
传参类型: 浮点数
值域: [0.0f, 256.0f]
默认值: 64.0f

maxConnectionHeight
含义: 珍珠连接允许的最大高度差，仍须通过实际弹道检查
传参类型: 浮点数
值域: [0.0f, 256.0f]
默认值: 64.0f

throwAngle
含义: 相对水平面的固定投掷仰角，单位为度
传参类型: 浮点数
值域: [1.0f, 89.0f]
默认值: 30.0f

maxThrowPower
含义: 相对原版静止玩家1.5格每刻投掷速度的倍率上限
传参类型: 浮点数
值域: [0.1f, 10.0f]
默认值: 1.0f

throwAccuracy
含义: 投掷准度；降低后即使规划可行也可能射偏
传参类型: 浮点数
值域: [0.0f, 1.0f]
默认值: 1.0f
说明: 1.0f不添加随机散布
```

</details>

<details><summary>完整配置与场景示例</summary>

```mcfunction
/summon minecraft:zombie ~ ~ ~ {Tags:["pmb_pearl_traversal"],equipment:{offhand:{id:"minecraft:ender_pearl",count:64}},PmbAi:{skill:{conditional:{traversal:{enable:1b,methods:["ender_pearl"],ender_pearl:{FetchSource:["offhand","mainhand"],preferredHand:"off",enable:1b,doConsume:0b,doTeleportDamage:0b,cooldownTicks:40,randomCooldownBias:20,maxConnectionDistance:64.0f,maxConnectionHeight:64.0f,throwAngle:30.0f,maxThrowPower:1.0f,throwAccuracy:1.0f}}}},pathfinding:{strategies:{combat:{routeMode:"fallback",dangerousFallPolicy:"conditional_required",requireEyeSight:0b}}}}}
/pmb skill conditional @e[tag=pmb_pearl_traversal] traversal.ender_pearl modify [enable=0b]
```

把目标放在原版无法连接的安全平台。默认会先尝试风弹；可用父层`methods`调整顺序。珍珠不用于绕开一条可行的正常路线以赶路；没有完整弹道解时不发射。调低maxThrowPower可验证能力不足会拒绝此路径。

</details>

[返回特殊跨越](traversal.md)
