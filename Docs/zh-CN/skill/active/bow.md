# 弓箭 (`skill.active.bow`)

完整默认配置（可选来源列表的省略语义见下文）：

```text
bow:{FetchSource:["mainhand"],preferredHand:"main",enable:0b,doConsume:0b,requireEyeSight:1b,modePriority:"line",mobileWhileShooting:1b,lineMinRange:0.0f,lineMaxRange:16.0f,lineSafeDistance:6.0f,lineCooldownTicks:40,lineShootChance:0.8f,lineShootAccuracy:0.9f,lineChargeTicks:20,linePower:1.0f,arcMinRange:16.0f,arcMaxRange:48.0f,arcSafeDistance:12.0f,arcCooldownTicks:60,randomCooldownBias:20,arcShootChance:0.8f,arcShootAccuracy:0.9f,arcChargeTicks:20,arcAngle:42.0f,arcMaxPower:1.0f}
```

弓箭AI由preferredHand选择使用手，默认main。该手已有原版弓时直接使用，否则从FetchSource永久换入。弓仍属于main类别，但持续拉弓只占实际使用手、USE_ITEM和LOOK。该技能包含低弹道line与高仰角arc模式；range控制射击检查距离，power和原版物理决定实际射程。

<details>
<summary>触发、弹药、移动与渲染细节</summary>

两个模式均要求目标存活且可攻击。requireEyeSight为1b时还要求视线可见；为0b时只跳过这一视线要求。仅对原版TargetGoal，0b还可在目标已被取得后、目标存活且处于同一世界、位于FOLLOW_RANGE和已启用line或arc的射击范围内、且按目标手和FetchSource规则可取得弓时，隔墙保留mob.getTarget()；绝不改变初次索敌。

此规则仅作用于TargetGoal，不影响Faction或Brain索敌、任何规避路径（包括珍珠规避）、通用近战或其他技能。line在目标距离处于lineMinRange至lineMaxRange之间时可检查；arc在目标距离处于arcMinRange至arcMaxRange之间时可检查。任一ShootChance为0.0时，对应模式完全关闭。

当两个模式同时满足距离、弹药且冷却就绪时，由modePriority选择line或arc。优先模式概率失败后，本游戏刻不会回退另一模式；若优先模式仍在冷却或其他检查条件不满足，则允许另一模式检查。选中模式后先通过优先级与资源准入，再检查概率并进入对应CooldownTicks冷却，无论概率成功或失败。被压制的候选不消耗该冷却。

概率成功后，生物使用对应ChargeTicks开始用解析到的手拉弓；0表示当刻释放。蓄力期间头部和双臂持续对准当前模式实际计算的发射向量，因此line显示低弹道瞄准，arc显示与其设计仰角一致的抬弓瞄准。

目标死亡、不可攻击、离开当前模式范围、requireEyeSight为1b时失去视线、弓离开当前使用手、所需弹药失效、启用NoAI或进入破盾虚弱时，蓄力立即取消且已经开始的冷却保留。

linePower和arcMaxPower均为原版玩家满蓄力弓箭初速度3.0的倍率，而非绝对初速度。line模式使用3.0乘以linePower作为固定初速度，根据目标最新位置、移动速度和轻微重力补偿形成低弹道。

arc模式使用arcAngle作为相对水平面的固定仰角，按原版箭矢0.99空气阻力和0.05重力数值模拟、二分求解所需初速度，并迭代修正移动目标提前量。所需速度超过3.0乘以arcMaxPower或没有完整解时，仍按该速度上限射出，因此可能因物理能力不足而落空。Accuracy为1.0时不添加随机散布。

`AmmoSource`独立限制弓箭AI读取真实受支持箭矢的位置，使用与FetchSource相同的有序来源语法，但绝不移动或交换弹药。省略时保留原有行为：两只手中的受支持箭矢优先，其次从已启用PmbInventory低位到高位寻找。显式列表只按书写顺序扫描其中的位置；字段存在但为空或无效时不存在真实弹药来源。

弓换装挤出此前选中的手持箭矢时，本次动作跟踪箭堆到另一只手或库存格并在真实位置扣除。下一动作重新严格扫描AmmoSource；不单独交换弹药，也不保留长期范围豁免。

doConsume为0b时不消耗弹药，没有真实箭时使用无限普通箭；为1b时必须存在真实受支持箭矢，并在实际释放成功后从真实来源消耗一个。弓不会额外损失耐久。FetchSource只解析弓本身，绝不限制本次弹药查询。

普通近战只由轻量实时条件压制：bow已启用；当前目标非空、存活、同世界且可以攻击；弓已经实际位于允许的手中；目标距离位于至少一个ShootChance大于0的line或arc闭区间；doConsume为1b时AmmoSource仍能找到受支持箭矢。

普通main/off偏好允许弓已在任一手，main-enforce和off-enforce则只接受指定手。库存弓、FetchSource可取弓和动作binding均不算实际手持。冷却、本轮概率、资源claim、视线/requireEyeSight、modePriority、SafeDistance、mobileWhileShooting和FOLLOW_RANGE都不参与这项实时判定。

任何必要条件失效时，原版近战Goal导航和普通直接近战伤害会立即恢复。FetchSource仍只负责启动技能和换入弓，不会单独压制近战。K键报告的`bow`压制原因使用同一个predicate。

调度器选中的PMB重锤下落锤击仍允许通过，因此有效的弓近战压制不会取消合法的风弹/重锤等组合。弓箭只在一次成功检查已获得所需动作资源、绑定弓并开始拉弓后接管移动。

在该拉弓期间，进入对应SafeDistance以内会使生物边后退边瞄准，不受mobileWhileShooting开关影响。目标位于SafeDistance之外但仍在模式范围内时，mobileWhileShooting为1b会进行类似骷髅的随机前后侧移，为0b则原地射击。

冷却、概率失败、缺少弹药或动作资源冲突时不提交弓箭移动，也不再拦截原版导航或移动控制。生物是否实际追击，仍取决于当时是否存在能够运行的原版Goal或Brain行为。原版RangedBowAttackGoal的屏蔽范围保持不变，防止骷髅绕过PMB射击规则。

原版RangedBowAttackGoal的拉弓、射箭和侧移写入会被屏蔽，以避免骷髅绕过本技能的模式、概率、冷却或mobileWhileShooting；PMB自身的后退与随机游走指令仍可正常通过。拉弓姿势会在模型动画的最后阶段强制覆盖，避免僵尸或灾厄村民等模型自己的攻击、抱手动画再次覆盖双臂；卫道士和唤魔者的专用手持物品层也会显示弓和双臂。

</details>

<details>
<summary>参数结构</summary>

```text
bow:
    {
        enable: <bool>,
        FetchSource: [<string>, ...],
        preferredHand: <string>,
        AmmoSource: [<string>, ...],
        doConsume: <bool>,
        requireEyeSight: <bool>,
        modePriority: <string>,
        mobileWhileShooting: <bool>,
        lineMinRange: <float>,
        lineMaxRange: <float>,
        lineSafeDistance: <float>,
        lineCooldownTicks: <int>,
        lineShootChance: <float>,
        lineShootAccuracy: <float>,
        lineChargeTicks: <int>,
        linePower: <float>,
        arcMinRange: <float>,
        arcMaxRange: <float>,
        arcSafeDistance: <float>,
        arcCooldownTicks: <int>,
        arcShootChance: <float>,
        arcShootAccuracy: <float>,
        arcChargeTicks: <int>,
        arcAngle: <float>,
        arcMaxPower: <float>,
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

AmmoSource
含义: 弓箭AI可读取真实受支持箭矢的有序位置列表
传参类型: 字符串列表
值域: mainhand、offhand、inventory、inventory:1至inventory:256，或inventory:1..9等闭区间
默认值: 省略；先检查两只手中的受支持箭矢，再从已启用PmbInventory低位到高位检查
说明: 只扫描弹药，绝不交换弹药；显式空列表或无效列表不提供真实箭矢，FetchSource不限制弹药

doConsume
含义: 实际释放箭矢时是否从手部或PmbInventory的真实来源消耗一个受支持箭矢
传参类型: 布尔值
值域: 0b, 1b
默认值: 0b

requireEyeSight
含义: 弓箭检查与已经开始的拉弓是否要求能看见目标
传参类型: 布尔值
值域: 0b, 1b
默认值: 1b
说明: 0b只跳过这些视线门；目标有效性、范围、冷却、概率、物品与其他全部行为保持不变

modePriority
含义: line与arc同时可以检查时优先选择的模式
传参类型: 字符串
值域: "line", "arc"
默认值: "line"
说明: 无效值回退为"line"

mobileWhileShooting
含义: 目标在安全距离之外且仍处于当前模式范围内时是否随机前后侧移
传参类型: 布尔值
值域: 0b, 1b
默认值: 1b
说明: 目标进入安全距离时始终后退，不受该参数影响

lineMinRange
含义: line模式可以开始射击检查的最小目标距离
传参类型: 浮点数
值域: [0.0f, 256.0f]
默认值: 0.0f

lineMaxRange
含义: line模式可以开始射击检查的最大目标距离
传参类型: 浮点数
值域: [lineMinRange, 256.0f]
默认值: 16.0f

lineSafeDistance
含义: line模式开始强制后退的目标距离
传参类型: 浮点数
值域: [0.0f, 256.0f]
默认值: 6.0f

lineCooldownTicks
含义: 每次line检查后进入冷却的游戏刻，检查失败同样应用
传参类型: 整数
值域: [0, 72000]
默认值: 40

lineShootChance
含义: line检查成功并开始蓄力的概率
传参类型: 浮点数
值域: [0.0f, 1.0f]
默认值: 0.8f
说明: 0.0完全关闭line模式

lineShootAccuracy
含义: line箭矢准度，数值越高随机散布越小
传参类型: 浮点数
值域: [0.0f, 1.0f]
默认值: 0.9f

lineChargeTicks
含义: line检查成功后的拉弓游戏刻
传参类型: 整数
值域: [0, 72000]
默认值: 20

linePower
含义: line固定初速度相对于原版玩家满蓄力弓箭初速度3.0的倍率
传参类型: 浮点数
值域: [0.1f, 10.0f]
默认值: 1.0f
说明: 1.0表示100%原版满弓初速度，实际初速度为3.0；值域对应10%至1000%

arcMinRange
含义: arc模式可以开始射击检查的最小目标距离
传参类型: 浮点数
值域: [0.0f, 256.0f]
默认值: 16.0f

arcMaxRange
含义: arc模式可以开始射击检查的最大目标距离；小于arcMinRange的值会被提升至arcMinRange
传参类型: 浮点数
值域: [arcMinRange, 256.0f]
默认值: 48.0f

arcSafeDistance
含义: arc模式开始强制后退的目标距离
传参类型: 浮点数
值域: [0.0f, 256.0f]
默认值: 12.0f

arcCooldownTicks
含义: 每次arc检查后进入冷却的游戏刻，检查失败同样应用
传参类型: 整数
值域: [0, 72000]
默认值: 60

arcShootChance
含义: arc检查成功并开始蓄力的概率
传参类型: 浮点数
值域: [0.0f, 1.0f]
默认值: 0.80f
说明: 0.0完全关闭arc模式

arcShootAccuracy
含义: arc箭矢准度，数值越高随机散布越小
传参类型: 浮点数
值域: [0.0f, 1.0f]
默认值: 0.9f

arcChargeTicks
含义: arc检查成功后的拉弓游戏刻
传参类型: 整数
值域: [0, 72000]
默认值: 20

arcAngle
含义: arc模式相对水平面的固定射击仰角，单位为度
传参类型: 浮点数
值域: [1.0f, 89.0f]
默认值: 42.0f

arcMaxPower
含义: arc弹道求解速度上限相对于原版玩家满蓄力弓箭初速度3.0的倍率
传参类型: 浮点数
值域: [0.1f, 10.0f]
默认值: 1.0f
说明: 1.0表示速度上限为3.0；达到上限仍无法命中时依然按上限射出

randomCooldownBias
含义: 每次任一模式开始检查冷却时额外增加的闭区间均匀随机游戏刻数
传参类型: 整数
值域: [0, 1200]
默认值: 20
说明: line与arc分别独立抽取；0表示两种冷却均固定

preferredHand
类型: string
值域: main、off、main-enforce、off-enforce
默认值: "main"
含义: 动作开始时选择使用手；普通值仅因持续PMB手占用而回退，enforce禁止回退
```

实体读取数据时，超出值域的参数会被自动限制到相应边界。
通常情况下，20游戏刻等于1秒。

</details>

<details>
<summary>指令示例</summary>

```text
生成一个主手持弓，使用默认弓箭AI且无需箭矢的僵尸。
summon zombie ~ ~ ~ {PmbAi:{skill:{active:{bow:{enable:1b}}}}, equipment:{mainhand:{id:bow}}}

生成一个副手持有64支箭、实际消耗箭矢，并自定义line与arc参数的骷髅。
summon skeleton ~ ~ ~ {PmbAi:{skill:{active:{bow:{enable:1b, doConsume:1b, modePriority:"arc", mobileWhileShooting:1b, lineMinRange:0.0f, lineMaxRange:14.0f, lineSafeDistance:6.0f, lineCooldownTicks:30, lineShootChance:0.8f, lineShootAccuracy:1.0f, lineChargeTicks:10, linePower:0.8f, arcMinRange:12.0f, arcMaxRange:64.0f, arcSafeDistance:10.0f, arcShootChance:0.6f, arcShootAccuracy:0.95f, arcChargeTicks:20, arcAngle:60.0f, arcMaxPower:1.25f}}}}, equipment:{mainhand:{id:bow}, offhand:{id:arrow,count:64}}}

保留其他参数，仅禁用最近一只骷髅的弓箭AI。
data merge entity @e[type=skeleton,sort=nearest,limit=1] {PmbAi:{skill:{active:{bow:{enable:0b}}}}}
```

</details>


`AmmoSource`为可选字段：省略时等效按`["mainhand","offhand","inventory"]`查找，显式`[]`禁止检索真实箭矢；`doConsume:0b`仍允许无限普通箭。它独立于`FetchSource`且不交换弹药。


<details><summary>模块化指令快速启用</summary>

```mcfunction
/pmb skill active @n[type=minecraft:zombie] bow append [enable=1b]
/pmb skill active @n[type=minecraft:zombie] bow get
/pmb skill active @n[type=minecraft:zombie] bow modify [enable=0b]
```

append要求技能节点尚不存在。已有节点的enable若为显式字段使用modify；若尚未手写则使用insert。

</details>

[返回技能指南](../README.md)
