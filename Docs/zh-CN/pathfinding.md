# 寻路

寻路层持有移动策略。技能只请求动作，Faction 只提供威胁；二者不再直接写入互相竞争的导航。

```text
MovementIntent -> RoutePlanner -> MovementController -> ConditionalEvent
RETREAT > ACTIVE_TACTICAL > PURSUIT > IDLE
```

<details><summary>持久结构</summary>

```text
pathfinding:{
 planner:{planningHorizon:32.0f,repathIntervalTicks:10,objectiveMemoryTicks:40,targetMoveThreshold:2.0f,conditionalWaitTicks:40,debugPathParticles:0b},
 strategies:{
  combat:{routeMode:"fallback",dangerousFallPolicy:"conditional_required",requireEyeSight:0b},
  retreat:{routeMode:"fallback",dangerousFallPolicy:"conditional_required",requireEyeSight:0b},
  idle:{routeMode:"vanilla_only",dangerousFallPolicy:"disable",requireEyeSight:0b}
 },
 air_tracking:{trackAcceleration:0.012f,trackMaxHorizontalSpeed:0.3f,trackDurationTicks:-1},
 retreat:{speedMultiplier:1.25f,searchHorizontalRange:16,searchVerticalRange:7,repathIntervalTicks:10}
}
```

</details>

<details><summary>路线与坠落策略</summary>

`vanilla_only`只走原版路线；`fallback`在原版完整路径不可用时尝试特殊局部连接；`efficient`可在至少节省`minSavedTicks`时用风弹抄近路。跨越方法默认先风弹再珍珠，可通过`traversal.methods`调整检查顺序；珍珠不会用于效率捷径。

`disable`拒绝计划危险下降；`enable`无需救援即可允许有支撑的边；`conditional_required`必须先取得`fall_rescue`承诺。

`requireEyeSight`只限制特殊路线的建立/重规划。已索敌、启用跨越方法且战斗路线不是`vanilla_only`时，关闭此参数还允许原版TargetGoal在暂时被遮挡后继续保持目标；从未发现的目标不会因此被隔墙索敌。FOLLOW_RANGE决定实体目标是否仍有效，不决定局部规划半径。

</details>

<details><summary>规划生命周期与限制</summary>

特殊检查在服务端公平预算下经历`VANILLA`、`WIND`、`PEARL`阶段；预算不足只延后，不判定不可达。每个Mob每刻最多运行一个规划阶段。计划下降经历`APPROACH -> ARMED -> AIRBORNE`，等待条件动作超过`conditionalWaitTicks`后失效。

风弹每轮最多检查6个直连候选与4个边缘候选。正常重试分别从各自游标继续，不再始终重复前几个候选；地形和轨迹仍重新计算。明显位移、更换目标或策略、关闭跨越或成功释放后重置进度。

风弹穿越不再要求证明落点侧属于封闭连通区域。不存在完整原版路线时，已实际计算但结果不完整或为空的局部路径，若普通局部行走确实受阻，且跨越端点在三维距离上严格更接近目标，即可允许已验证物理安全的风弹连接。预算不足而未执行的检查不算这种情况。

完整原版全局路线仍优先：`fallback`保留它，`efficient`必须安全接入其后续路径并满足`minSavedTicks`。没有完整全局路线时，若局部路线能绕过灯笼等小障碍，仍须满足节时门槛。既有的两端干地流水豁免保持不变；空中接敌仍必须通过接触碰撞与安全下降验证。

规划只使用已加载区块、碰撞净空、支撑、世界边界和实体路径类型。运行时目标、搜索游标、提案、承诺、路线与空中追踪上下文不持久化。当前只构造到当前目标的保守局部边，不是任意地形的永久全局图。

空中追踪不是技能。成功提交的动作安装实体或坐标上下文，只有另一个成功动作能替换；它保留竖直速度，并在落地、失效或超时前施加受限水平加速度。

管理员准星指向已配置PMB生物按K，可将其加入持续观测列表；对同一只再按K移除。每位管理员最多观测4只，第5只会提示列表已满。聊天只确认列表变更；选中目标的完整K快照每刻写入服务端终端与`latest.log`，包括位置、速度、当前目标和视线状态。需要定位瞬间时，可在聊天发送`1`，用日志时间戳查找前后的快照。持续记录可能产生大量日志，只在排查时开启观测。

观测对象有仅对该管理员可见的高亮粒子。另行设置`debugPathParticles:true`后，才绘制已缓存的原版比较路径及风弹候选，不为绘制重新寻路。原版比较路径最多64节点；真实完成物理模拟但后续规则拒绝的风弹路线最多两条，采纳路线另计，各最多80个飞行采样点。各路线颜色在一次规划内稳定且不同，采纳路线粒子更大。飞行弧落地或经过80刻后清理。高亮与路线共用每位管理员每刻144点的上限；关闭路线开关不影响K日志和高亮。

死亡、卸载、跨维度、失权或离线会停止对应观测；短暂失去视线不会停止。没有有效轨迹时仍可从持续日志查看规划与拒绝原因。

`WIND_PLANNER`记录当前生效的视线规则、目标、距离/高度上限，以及规划前被阻止的原因。每个被观测的规划轮次保留最多10个已检查候选，包含精确起跳点、落点、安全下降点、失败阶段和拒绝原因。预算总数、未检查数与下一游标单列，不覆盖碰撞或策略失败。`roundTick`和`roundAge`标明缓存结果属于哪一轮；全局规划预算不足与普通重规划等待也分别记录。关闭路线粒子不影响这些终端细节。

准入日志区分`partial_route_obstacle_progress`、`no_local_obstacle`、`no_target_progress`、局部路径预算不足以及完整/局部路线的节时失败。同轮复用局部路径结果；已移除的封闭性证明不再产生拒绝原因。

</details>

<details><summary>指令</summary>

```mcfunction
/pmb pathfinding planner <targets> get
/pmb pathfinding planner <targets> set planningHorizon 32.0
/pmb pathfinding planner <targets> set debugPathParticles true
/pmb pathfinding strategy <targets> get
/pmb pathfinding strategy <targets> set combat routeMode fallback
/pmb pathfinding strategy <targets> set combat dangerousFallPolicy conditional_required
/pmb pathfinding strategy <targets> set combat requireEyeSight false
/pmb pathfinding air_tracking <targets> get
/pmb pathfinding air_tracking <targets> set trackDurationTicks -1
/pmb pathfinding retreat <targets> get
/pmb pathfinding retreat <targets> set speedMultiplier 1.25
```

</details>


<details>
<summary>参数细节</summary>

```text
planner.planningHorizon
含义: 局部规划半径；远目标通过滚动规划到达，不等于FOLLOW_RANGE
传参类型: 浮点数
值域: [1.0f, 256.0f]
默认值: 32.0f

planner.repathIntervalTicks
含义: 普通路线重规划间隔；目标明显移动等失效条件可提前触发
传参类型: 整数
值域: [1, 1200]
默认值: 10

planner.objectiveMemoryTicks
含义: 一轮规划结束且没有待执行提案时，与repathIntervalTicks取较大值作为重规划等待时间；不负责保留被遮挡目标，也不绕过requireEyeSight
传参类型: 整数
值域: [0, 72000]
默认值: 40

planner.targetMoveThreshold
含义: 目标位移达到此格数时重新规划
传参类型: 浮点数
值域: [0.0f, 64.0f]
默认值: 2.0f

planner.conditionalWaitTicks
含义: 到达锚点后等待条件动作提交的最长时间
传参类型: 整数
值域: [1, 72000]
默认值: 40

planner.debugPathParticles
含义: 允许已用K观测的管理员看到该PMB生物缓存的路径粒子；不控制K日志与高亮
传参类型: 布尔值
值域: true、false
默认值: false

strategies.*.routeMode
含义: 原版专用、原版不可达时尝试特殊连接，或允许风弹效率捷径
传参类型: 字符串
值域: vanilla_only、fallback、efficient
默认值: combat/retreat为fallback，idle为vanilla_only
说明: 跨越方法的默认顺序由traversal.methods给出，并可调整

strategies.*.dangerousFallPolicy
含义: 允许危险下降、禁止危险下降，或要求自救可行承诺；弓后退和侧移也遵守
传参类型: 字符串
值域: enable、disable、conditional_required
默认值: combat/retreat为conditional_required，idle为disable

strategies.*.requireEyeSight
含义: 是否要求视线才能创建或重规划特殊路线，不撤销已释放动作
传参类型: 布尔值
值域: 0b、1b
默认值: 0b

air_tracking.trackAcceleration
含义: 每刻增加的水平追踪速度，0不追加水平加速
传参类型: 浮点数
值域: [0.0f, 1.0f]
默认值: 0.012f

air_tracking.trackMaxHorizontalSpeed
含义: 追踪施加的水平速度上限，不截断垂直速度
传参类型: 浮点数
值域: [0.0f, 3.0f]
默认值: 0.3f

air_tracking.trackDurationTicks
含义: 会话时长；-1直到落地、目标失效或被替换，0不保持追踪
传参类型: 整数
值域: [-1, 72000]
默认值: -1

retreat.speedMultiplier
含义: 撤退相对移动速度倍率，替代旧Faction逃跑速度字段
传参类型: 浮点数
值域: [0.0f, 100.0f]
默认值: 1.25f

retreat.searchHorizontalRange
含义: 撤退候选落点水平搜索范围
传参类型: 整数
值域: [1, 256]
默认值: 16

retreat.searchVerticalRange
含义: 撤退候选落点垂直搜索范围
传参类型: 整数
值域: [1, 256]
默认值: 7

retreat.repathIntervalTicks
含义: 撤退目的地重选间隔；威胁更换或明显移动会提前重选
传参类型: 整数
值域: [1, 1200]
默认值: 10
```

</details>

<details><summary>场景与完整配置示例</summary>

下面的完整实体开启风弹跨墙，并允许寻路规划在自救可行时下落。把目标放在短墙后；将routeMode改成vanilla_only可作对照。是否存在可执行连接还取决于碰撞、落点、物品与冷却。

```mcfunction
/summon minecraft:zombie ~ ~ ~ {Tags:["pmb_path_test"],equipment:{offhand:{id:"minecraft:wind_charge",count:64}},PmbAi:{skill:{conditional:{traversal:{enable:1b,methods:["wind_charge"],wind_charge:{enable:1b,doConsume:1b,cooldownTicks:40,randomCooldownBias:0,maxConnectionDistance:16.0f,maxConnectionHeight:8.0f,minSavedTicks:10}},fall_rescue:{enable:1b,methods:["wind_charge"]}}},pathfinding:{planner:{planningHorizon:32.0f,repathIntervalTicks:10,objectiveMemoryTicks:40,targetMoveThreshold:2.0f,conditionalWaitTicks:40},strategies:{combat:{routeMode:"efficient",dangerousFallPolicy:"conditional_required",requireEyeSight:0b}},air_tracking:{trackAcceleration:0.012f,trackMaxHorizontalSpeed:0.3f,trackDurationTicks:-1},retreat:{speedMultiplier:1.25f,searchHorizontalRange:16,searchVerticalRange:7,repathIntervalTicks:10}}}}
/pmb pathfinding strategy @e[tag=pmb_path_test] set combat routeMode vanilla_only
/pmb pathfinding strategy @e[tag=pmb_path_test] set combat routeMode efficient
```

撤退使用同一套路线设置，但目标来自Faction威胁。风弹重锤连击则由提交成功的风弹/重锤更新空追目标，无需额外启用air_tracking技能。

</details>

[返回文档主页](../模组文档.md)
