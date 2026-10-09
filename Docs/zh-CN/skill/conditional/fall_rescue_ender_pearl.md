# 珍珠自救（`skill.conditional.fall_rescue.ender_pearl`）

完整默认方法配置。父层`fall_rescue`默认关闭，珍珠方法默认开启；父层、方法与当前策略优先级都必须允许，才能进行检查。

```text
fall_rescue:{enable:0b,methods:["wind_charge","ender_pearl"],ender_pearl:{enable:1b,FetchSource:["offhand","mainhand"],preferredHand:"off",doConsume:0b,doTeleportDamage:0b,successChance:1.0f,cooldownTicks:20,randomCooldownBias:0,rescueLeadTicks:4,maxThrowPower:1.0f,throwAccuracy:1.0f}}
```

<details>
<summary>轨迹与LOOK细节</summary>

`rescueLeadTicks`只决定开始检查的窗口。珍珠求解器在该窗口内按当前高度、向下速度与目标安全碰撞位置计算额外投掷冲量；救援珍珠继承生物当刻的向下速度，不继承水平或向上速度。`maxThrowPower`限制额外冲量大小。珍珠必须至少比生物早一刻安全碰撞，传送后预计剩余摔落伤害加可选的可减免珍珠伤害必须严格低于不自救的伤害。不会预检查途中实体。

物理可行后，生物在概率检查和投掷前占用`LOOK`看预测碰撞点。发射成功后每刻跟随珍珠实体；珍珠消失后保留最后位置或预测落点，直至真正落地完成刻。随机散布只作用于额外冲量，随后叠加继承的向下速度；按实际采样后的初速度重新模拟。若轨迹变得不安全或来不及、资源被挡、概率失败或实体生成失败，本刻LOOK结束。生成失败时回滚物品交换、不扣珍珠，已消耗的尝试冷却不退款。

</details>

<details>
<summary>参数细节</summary>

```text
enable
含义: 是否启用珍珠自救方法；父层fall_rescue必须启用
传参类型: 布尔值
值域: 0b, 1b
默认值: 1b

FetchSource
含义: 可提供末影珍珠的有序物品位置列表
传参类型: 字符串列表
值域: mainhand、offhand、inventory、inventory:1至inventory:256或闭区间inventory:1..9
默认值: 省略；先检查offhand，再检查mainhand
说明: 目标手已有珍珠时直接使用，否则按顺序交换；范围可重叠

preferredHand
含义: 自救珍珠优先使用的手
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
含义: 传送后是否施加原版珍珠的5点可减免伤害
传参类型: 布尔值
值域: 0b, 1b
默认值: 0b
说明: 求解器会把经摔落保护修正的伤害计入净伤害

successChance
含义: 意外坠落中可行且获准的珍珠自救检查成功概率
传参类型: 浮点数
值域: [0.0f, 1.0f]
默认值: 1.0f
说明: 计划坠落承诺不投概率；概率失败结束本次坠落后续尝试

cooldownTicks
含义: 检查后的基础冷却游戏刻
传参类型: 整数
值域: [0, 72000]
默认值: 20
说明: 意外坠落在概率检查时消耗；计划坠落仅成功提交后消耗

randomCooldownBias
含义: 冷却额外增加的均匀随机游戏刻上限
传参类型: 整数
值域: [0, 1200]
默认值: 0

rescueLeadTicks
含义: 预计碰地前多少刻进入珍珠检查窗口
传参类型: 整数
值域: [0, 1200]
默认值: 4
说明: 窗口过晚时即使概率为1，也可能没有提前碰撞的物理解

maxThrowPower
含义: 最大额外投掷冲量，以原版静止玩家1.5格每刻初速度为基准
传参类型: 浮点数
值域: [0.1f, 10.0f]
默认值: 1.0f
说明: 仅救援珍珠额外继承生物向下速度，主动与寻路珍珠不使用此继承规则

throwAccuracy
含义: 珍珠投掷准度
传参类型: 浮点数
值域: [0.0f, 1.0f]
默认值: 1.0f
说明: 1.0f无散布；降低后会产生平面散布，但不安全的采样路径会在发射前被拒绝
```

</details>

<details>
<summary>完整配置与测试案例</summary>

```mcfunction
/summon minecraft:zombie ~ ~ ~ {Tags:["pmb_pearl_rescue"],equipment:{offhand:{id:"minecraft:ender_pearl",count:64}},PmbAi:{skill:{conditional:{fall_rescue:{enable:1b,methods:["ender_pearl"],ender_pearl:{enable:1b,FetchSource:["offhand","mainhand"],preferredHand:"off",doConsume:0b,doTeleportDamage:0b,successChance:1.0f,cooldownTicks:20,randomCooldownBias:0,rescueLeadTicks:10,maxThrowPower:1.0f,throwAccuracy:1.0f}}}}}}
/pmb skill conditional @e[tag=pmb_pearl_rescue] fall_rescue.ender_pearl modify [maxThrowPower=1.5f]
```

让生物从高台落向平坦安全区域。K调试的`no_timely_safe_trajectory`表示在当前时机、速度和动能上限下未找到比生物先到达的安全路径；增大`rescueLeadTicks`或`maxThrowPower`可作为对照。将`throwAccuracy`从1.0f调低以观察散布与发射前安全筛选，落点不必随参数线性变化。

</details>

[返回坠落自救](fall_rescue.md)
