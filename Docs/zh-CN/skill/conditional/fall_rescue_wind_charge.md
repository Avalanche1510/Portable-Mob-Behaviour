# 风弹自救（`skill.conditional.fall_rescue.wind_charge`）

完整默认方法配置。`fall_rescue`父层默认关闭；只有父层启用、风弹方法启用且当前策略包含`fall_rescue`时，才会检查坠落。

```text
fall_rescue:{enable:0b,methods:["wind_charge","ender_pearl"],wind_charge:{enable:1b,FetchSource:["offhand","mainhand"],preferredHand:"off",doConsume:0b,successChance:1.0f,cooldownTicks:20,randomCooldownBias:0,rescueLeadTicks:4}}
```

<details>
<summary>触发与释放细节</summary>

`rescueLeadTicks`是预计落地前开始检查的窗口，不指定固定释放刻。进入窗口后，必须先预测向下发射的风弹能及时在生物附近爆炸并产生推动，且预计落地伤害达到父层的`minRescueDamage`。找到可行方案后，生物占用`LOOK`看向预测爆炸落点。意外坠落经优先级与资源准入才检查概率；失败消耗尝试冷却，本次坠落不回退珍珠方法。

成功释放后看向风弹预计爆炸落点，直至真正落地的完成刻；上升阶段不会提前结束。风弹只有实际爆炸并推动生物后才授予PMB摔落保护。若物品不可用、资源被挡、实际生成失败或方案在提交前失效，本刻LOOK结束，不消耗物品；已经发生的概率检查冷却不退款。计划坠落只在成功提交时消耗冷却，不做概率检查。

</details>

<details>
<summary>参数细节</summary>

```text
enable
含义: 是否启用风弹自救方法；父层fall_rescue必须启用
传参类型: 布尔值
值域: 0b, 1b
默认值: 1b

FetchSource
含义: 可提供风弹的有序物品位置列表
传参类型: 字符串列表
值域: mainhand、offhand、inventory、inventory:1至inventory:256或闭区间inventory:1..9
默认值: 省略；先检查offhand，再检查mainhand
说明: 目标手已有风弹时直接使用，否则按顺序交换；范围可重叠

preferredHand
含义: 自救风弹优先使用的手
传参类型: 字符串
值域: main、off、main-enforce、off-enforce
默认值: off
说明: 普通偏好仅在PMB占用首选手时回退；enforce不回退

doConsume
含义: 成功释放时是否消耗一枚风弹
传参类型: 布尔值
值域: 0b, 1b
默认值: 0b

successChance
含义: 意外坠落中可行且获准的风弹自救检查成功概率
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
含义: 预计碰地前多少刻进入检查窗口
传参类型: 整数
值域: [0, 1200]
默认值: 4
说明: 0为最后一个空中检查刻，仍必须能够及时爆炸推动生物
```

</details>

<details>
<summary>完整配置与测试案例</summary>

```mcfunction
/summon minecraft:zombie ~ ~ ~ {Tags:["pmb_wind_rescue"],equipment:{offhand:{id:"minecraft:wind_charge",count:64}},PmbAi:{skill:{conditional:{fall_rescue:{enable:1b,methods:["wind_charge"],wind_charge:{enable:1b,FetchSource:["offhand","mainhand"],preferredHand:"off",doConsume:0b,successChance:1.0f,cooldownTicks:20,randomCooldownBias:0,rescueLeadTicks:4}}}}}}
/pmb skill conditional @e[tag=pmb_wind_rescue] fall_rescue.wind_charge modify [rescueLeadTicks=10]
```

让生物从高台落下并观察K调试的`fall_rescue`方法原因。将窗口由4改为10后，可行风弹应更早进入检查；无及时爆炸或物品时不应生成风弹。`fall_rescue.wind_charge delete []`删除方法节点，但只要父层仍启用且`methods`仍包含风弹，默认启用的方法仍可运行；若要禁止该方法，请显式写`enable=0b`或将其从`methods`移除。

</details>

[返回坠落自救](fall_rescue.md)
