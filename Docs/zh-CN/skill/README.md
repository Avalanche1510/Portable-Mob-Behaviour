# 技能

`skill.active`根据世界状态主动发起动作；`skill.conditional`响应战斗、坠落或寻路已经产生的具体事件。进入候选后，两类技能共用同一张优先表、动作资源、抢占规则和“每刻最多提交一个不可逆动作”。

- [主动技能](active/README.md)
- [条件技能](conditional/README.md)

<details><summary>优先级与生命周期</summary>

```text
combat:["mace","fall_rescue","ender_pearl","wind_charge","traversal","bow","shield","vanilla"]
retreat:["fall_rescue","ender_pearl","wind_charge","traversal","vanilla"]
idle:["fall_rescue","traversal","vanilla"]
```

嵌套列表表示同级。同级候选使用稳定裁决；只有严格更高且资源冲突的技能能抢占运行阶段。未写入当前策略的技能不能启动。`vanilla`只代表参与竞争的原版不可逆动作，不代表导航。技能必须同时满足`enable:1b`且位于当前策略列表。

优先级与资源准入后才进行概率检查并启动尝试冷却。确定性条件技能只在真实释放/提交后消耗提交冷却。

</details>

<details><summary>物品、弹药与库存</summary>

`FetchSource`接受`mainhand`、`offhand`、`inventory`或1–256格的`inventory:n`/`inventory:a..b`，定义技能可从哪里取得并交换物品。`preferredHand`接受`main`、`off`、`main-enforce`、`off-enforce`；enforce不回退。弓使用独立`AmmoSource`。先检查目标手已有合适物品，再fetch。交换永久保留，动作结束不会自动复原布局。

`PmbInventory`是与`PmbAi`同级的实体根：`PmbInventory:{Slots:0,DropChance:0.5f,ScatterDrops:0b,Items:[]}`。`Slots`范围`0..256`，`DropChance`范围`0..1`，物品槽从1开始，并按实际格数分配存储。开启原版`CanPickUpLoot`时，未被装备或物种拾取逻辑消费的剩余物可进入库存；拾取时装不下的剩余物留在地面；主动缩容溢出物立即丢出。死亡时每个完整堆独立检查`DropChance`；`ScatterDrops:1b`让成功掉落的完整堆获得类似玩家死亡的外飞速度与原版拾取延迟，不损坏或拆分物品。

</details>

<details><summary>指令与K调试</summary>

```mcfunction
/pmb skill active <targets> <skill> get|append|insert|modify|delete ...
/pmb skill conditional <targets> <skill> get|append|insert|modify|delete ...
/pmb skill priority <targets> get <combat|retreat|idle|all>
/pmb skill priority <targets> set <combat|retreat|idle> <list>
/pmb skill priority <targets> reset <combat|retreat|idle|all>
```

`append`创建节点；`insert`只添加未显式写入字段；`modify`只改显式字段；`delete [a,b]`移除字段；`delete *`清除所选节点的显式字段；`delete []`移除所选节点。`traversal`和`fall_rescue`为父场景，点号ID如`fall_rescue.ender_pearl`只操作其方法节点；仅创建方法节点不会打开父层。方括号解析器补全名称与类型值，并以原子方式拒绝类型、范围、重复键与最终min/max关系错误。

可重绑`K`探针要求2级管理员权限与准星下可见Mob。它把上一完整刻的不可变快照写入请求者聊天与服务端日志，分开展示主动/条件技能、尝试、资源、绑定、移动意图、路线阶段/提案、条件事件与空中上下文。禁用技能隐藏，空集合仍显示。探针不会重投概率或修改行为。

</details>


<details>
<summary>完整物品调取、库存参数与组合示例</summary>

所有PMB技能由一个服务端Mob游戏刻入口统一调用。存在Faction规避威胁时策略为`retreat`，否则存在有效攻击目标时为`combat`，没有权威目标时为`idle`。破盾虚弱、NoAI和死亡会阻止新动作；策略或权威目标改变时会逐个复核持续阶段，仍在新策略中列出且自身有效的阶段继续，省略或失效的阶段结束。

每项技能均可选填有序字符串列表`FetchSource`，支持`mainhand`、`offhand`、`inventory`、`inventory:1`及`inventory:1..9`等闭区间。公开库存格从1编号到256，条目和区间允许重叠。先确定本次使用手；该手已有正确物品时直接使用，不检查FetchSource。否则按列表顺序、区间内按格号升序取物。省略来源列表时使用原来的来源手顺序；显式空或无效列表不提供可取物来源，但不阻止直接使用目标手已有物品。

bow、shield、wind_charge和ender_pearl支持`preferredHand`：`main`、`off`、`main-enforce`、`off-enforce`。bow默认main，其余三项默认off；mace不支持该参数，固定主手执行。普通main/off只在动作开始时，因首选手被持续PMB动作占用而尝试另一手；静态物品不会使手不可用。enforce指定手被占用时本次失败。同刻候选竞争失败不会改手重试。

来源手或目标手被持续动作占用时不能交换，来源不可用则继续扫描。跨手交换当刻占用两手，随后持续动作只绑定实际使用手。每次检查只选择一件物品；目标手已有正确物品时不会追逐来源中另一件同类物品。

所有技能的成功换装永久保留：完成、取消、NoAI、卸载、转换和死亡都不自动换回。ActionBinding只校验当前动作物品并绑定使用手，结束后释放。接受被挤出的武器或弹药落入其FetchSource/AmmoSource之外；配置者负责范围覆盖。最终在手上的物品使用原版装备掉落规则，库存物品使用PMB库存掉落规则。

`PmbInventory`只属于Mob生物，并独立于`PmbAi`：

```text
PmbInventory:{
    Slots:<int>,
    DropChance:<float>,
    ScatterDrops:<bool>,
    Items:[
        {Slot:<int>,id:<物品ID>,count:<int>}
    ]
}
```

`Slots`值域为`[0,256]`、默认0；底层容器按实际`Slots`容量分配，因此`Slots:0`不会分配库存格。`DropChance`值域为`[0.0f,1.0f]`、默认0.5f。死亡时每个已启用且非空的格子独立检查一次，成功则掉落该格的完整堆叠；`doMobLoot:false`时额外库存不掉落。缩小`Slots`会先尝试把高位物品合并或移动到启用的低位格，装不下的完整剩余物会立刻掉出。1–256以外的无效格号会被忽略。原有0–27格数据可直接加载；若将使用第28–256格的数据降级到旧版PMB，这些高位物品会丢失。

`ScatterDrops`为布尔值，默认`0b`。`0b`保持原有PMB原地死亡掉落；`1b`仅将已通过`doMobLoot`限制和该格`DropChance`检查的完整堆叠交给原版玩家死亡掉落路径，从`eyeY - 0.3`附近以`[0.0,0.5)`的随机径向水平速度和`0.2`的竖直速度飞散，并使用40刻拾取延迟。该选项不损坏、不拆分堆叠，也不影响缩容/加载溢出或手持装备。

当`CanPickUpLoot:1b`且`mobGriefing:true`时，原版装备逻辑与物种专用拾取逻辑先运行；仍留在地上的近距离物品随后像玩家背包一样合并进PmbInventory，库存满时剩余物继续留在地上。该功能不会增加主动寻找物品的导航，也不提供GUI。

弓省略`AmmoSource`时，受支持弹药先检查两只手，再从PmbInventory最低的已启用格开始向后检查，找到第一个兼容堆叠便停止；显式`AmmoSource`则只按其自身有序位置扫描。弹药会被直接读取，不需要额外换到另一只手。`doConsume:0b`时优先使用真实特殊箭但不消耗，没有箭则使用无限普通箭；`doConsume:1b`时必须存在真实兼容弹药，并只在成功释放后扣除一个。

示例：

```text
summon zombie ~ ~ ~ {CanPickUpLoot:1b,PmbInventory:{Slots:9,DropChance:0.5f,ScatterDrops:1b,Items:[{Slot:1,id:"minecraft:mace",count:1},{Slot:2,id:"minecraft:wind_charge",count:16}]},PmbAi:{skill:{active:{wind_charge:{enable:1b,FetchSource:["offhand","inventory:1..9"],doConsume:1b}},conditional:{mace:{enable:1b,FetchSource:["mainhand","inventory:1..9"]}}}}}
```

</details>

<details><summary>逐步修改与删除示例</summary>

选中一个尚未配置bow的尸壳。append创建；insert只添加未显式写入项；modify只修改已显式写入项。重复append、向已存在字段insert或向不存在字段modify都会报错。

```mcfunction
/pmb skill active @n[type=minecraft:husk] bow append [enable=1b]
/pmb skill active @n[type=minecraft:husk] bow insert [lineMaxRange=24.0f,AmmoSource=["mainhand","offhand","inventory"]]
/pmb skill active @n[type=minecraft:husk] bow modify [lineMaxRange=20.0f]
/pmb skill active @n[type=minecraft:husk] bow get
/pmb skill active @n[type=minecraft:husk] bow delete [lineMaxRange,AmmoSource]
/pmb skill active @n[type=minecraft:husk] bow delete *
/pmb skill active @n[type=minecraft:husk] bow delete []
```

星号清除包括enable的手写参数，节点保留但默认关闭；空列表删除整个技能。逗号后与参数首字母均有补全，等号后按类型提供建议；删除字段列表也补全。浮点字段使用浮点输入，布尔支持指令提示的布尔形式；错误不会部分写入批量选中的生物。

</details>

[返回文档主页](../../模组文档.md)
