# 坠落自救（`skill.conditional.fall_rescue`）

响应预测到的有害落地。`methods`按顺序检查物理可行且能够取得资源的方法，默认先风弹、再末影珍珠。`singleMethodPerFall:1b`使每次真正落地前最多成功释放一种自救方法。概率失败仍会终结本次坠落的后续尝试；资源冲突或物理不可行不会锁定方法，也允许尝试后续方法。

在各方法的`rescueLeadTicks`窗口内找到物理可行且取得资源的方案后，自救占用必需的`LOOK`，投出前看预测落点。风弹投出后看爆炸落点；珍珠投出后逐刻看珍珠实体，消失后保持最后已知位置或预测落点。

LOOK持续至真正落地（含完成刻，下一刻释放），不持续占用手部或`USE_ITEM`。概率、资源、最终轨迹或生成失败会当刻结束LOOK；更高优先动作暂抢视线后，自救仍有效时会自动恢复。

风弹弹起或珍珠空中传送不会结束。禁用场景或方法、移出优先表、死亡、NoAI、破盾虚弱或跨维度会清除状态。

预计轨迹会计入当前水平动量；只有先接触水面、而不是固体时，才按无摔伤处理，未知轨迹不取消自救。已启用的自救在生物入水或进入气泡柱时结束本次计划、取消LOOK并释放资源；水中不会继续低头等待落地。

```text
fall_rescue:{enable:0b,minRescueDamage:1.0f,singleMethodPerFall:1b,methods:["wind_charge","ender_pearl"],wind_charge:{enable:1b,preferredHand:"off",FetchSource:["offhand","mainhand"],doConsume:0b,successChance:1.0f,cooldownTicks:20,randomCooldownBias:0,rescueLeadTicks:4},ender_pearl:{enable:1b,preferredHand:"off",FetchSource:["offhand","mainhand"],doConsume:0b,doTeleportDamage:0b,successChance:1.0f,cooldownTicks:20,randomCooldownBias:0,rescueLeadTicks:4,maxThrowPower:1.0f,throwAccuracy:1.0f}}
```

父层指令`fall_rescue`只接受`enable`、`minRescueDamage`、`singleMethodPerFall`和`methods`。`fall_rescue.wind_charge`与`fall_rescue.ender_pearl`分别填写方法参数，并提供独立自动补全；方法节点可以先创建，但父层仍默认关闭。旧的父层`[wind_charge={...}]`指令格式不再接受。

<details><summary>意外与计划坠落</summary>

意外坠落中，每个物理可行且已启用的方法每次坠落最多投一次概率。更靠前的方法若资源被占用，则尝试后续可行方法；若概率失败，则消耗尝试冷却并终结本次坠落的后续尝试。

默认`singleMethodPerFall:1b`时，首次成功释放便锁定所有自救方法，直到真正落地；风弹反弹、珍珠空中传送、计划坠落转为意外坠落均不会解除锁定。设为`0b`可恢复原行为：若已经释放的方法仍留下危险，则可尝试尚未提交的下一方法。

后续更高优先动作只暂停自救LOOK，不解除方法锁定。预计伤害会计入现有风弹/重锤冲击保护。

`dangerousFallPolicy:"conditional_required"`下的计划坠落是确定性的：寻路只请求可行承诺，不投概率。承诺风弹前会从计划起跳高度预测坠落过程，要求在设定的释放窗口内存在能及时爆炸且处于影响半径内的时刻；风弹未通过时可继续承诺可行的珍珠。

珍珠承诺也从预计进入提前窗口的首刻求解；窗口只有0或1刻时，珍珠无法早于生物落地，因此不会被错误承诺。承诺锁定方法与冲击端点。`rescueLeadTicks:0`表示碰地前最后一个空中刻。只有实际释放才消耗物品和冷却。

珍珠救援在最大动能内求解角度与时间，可接受更早的安全方块碰撞；预测剩余坠落加可选且经保护修正的珍珠伤害必须严格小于原冲击。不会预检查实体；真实提前命中会被接受并重新评估危险。

</details>

```mcfunction
/pmb skill conditional @e[type=minecraft:zombie,limit=1] fall_rescue append [enable=1b]
/pmb skill conditional @e[type=minecraft:zombie,limit=1] fall_rescue.wind_charge append [rescueLeadTicks=6,successChance=1.0f]
/pmb skill conditional @e[type=minecraft:zombie,limit=1] fall_rescue.ender_pearl append [rescueLeadTicks=10,maxThrowPower=1.0f]
```

<details>
<summary>参数细节</summary>

```text
enable
含义: 是否启用坠落自救场景；方法节点启用仍需要父层开启
传参类型: 布尔值
值域: 0b, 1b
默认值: 0b

minRescueDamage
含义: 预计伤害达到此值才考虑自救，已计入冲击和摔落保护
传参类型: 浮点数
值域: [0.0f, 1000.0f]
默认值: 1.0f

singleMethodPerFall
含义: 第一次成功释放后禁止后续自救直到真正落地
传参类型: 布尔值
值域: 0b, 1b
默认值: 1b
说明: 设为0b时若危险仍在，可尝试尚未使用的方法

methods
含义: 风弹和珍珠救援方法的检查顺序
传参类型: 字符串列表
值域: wind_charge、ender_pearl，各最多一次，也可为空
默认值: ["wind_charge","ender_pearl"]
```

</details>

<details><summary>完整配置与场景示例</summary>

```mcfunction
/summon minecraft:zombie ~ ~ ~ {Tags:["pmb_fall_rescue"],equipment:{offhand:{id:"minecraft:wind_charge",count:64}},PmbAi:{skill:{conditional:{fall_rescue:{enable:1b,minRescueDamage:1.0f,singleMethodPerFall:1b,methods:["wind_charge","ender_pearl"],wind_charge:{enable:1b,preferredHand:"off",FetchSource:["offhand","mainhand"],doConsume:0b,successChance:1.0f,cooldownTicks:20,randomCooldownBias:0,rescueLeadTicks:4},ender_pearl:{enable:1b,preferredHand:"off",FetchSource:["offhand","mainhand"],doConsume:0b,successChance:1.0f,cooldownTicks:20,randomCooldownBias:0,rescueLeadTicks:4,maxThrowPower:1.0f,throwAccuracy:1.0f}}}},pathfinding:{strategies:{combat:{routeMode:"fallback",dangerousFallPolicy:"conditional_required",requireEyeSight:0b}}}}}
/pmb skill conditional @e[tag=pmb_fall_rescue] fall_rescue modify [enable=0b]
```

意外坠落测试：让该生物从高台落下。概率1只保证已可行且获准的检查通过，不保证投射物能赶在落地前起效；可提前检查或提高珍珠`maxThrowPower`。若风弹物理不可用或资源被占用，则尝试珍珠；风弹概率失败则不回退。

默认每次坠落只允许成功释放一次自救；设置`singleMethodPerFall:0b`后，首次释放仍留下危险时才允许尝试后续未提交的方法。珍珠要求至少比生物早一刻安全碰撞，无法满足时不丢出无效救援。仅救援珍珠继承向下速度，主动和寻路珍珠仍使用其原有速度规则。

风弹仅在真实爆炸推动生物后授予保护，PMB自有保护在该次落地后清除。LOOK从窗口内求出可行方案到落地完成刻持续尝试占用；高优先动作暂抢后恢复。生成投射物失败时回滚交换，不扣物品，已消耗的尝试冷却不退款。

</details>

- [风弹自救方法与参数](fall_rescue_wind_charge.md)
- [珍珠自救方法与参数](fall_rescue_ender_pearl.md)

[返回条件技能](README.md)
