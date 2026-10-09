# 特殊跨越（`skill.conditional.traversal`）

寻路层提出跨越目标，`traversal`按`methods`顺序尝试风弹和珍珠。两种方法共享一个`traversal`策略优先级，各自保留启用开关、物品来源和冷却。父层默认关闭；子方法默认开启。

```text
traversal:{enable:0b,methods:["wind_charge","ender_pearl"],wind_charge:{enable:1b,FetchSource:["offhand","mainhand"],preferredHand:"off",doConsume:0b,cooldownTicks:40,randomCooldownBias:20,maxConnectionDistance:16.0f,minHorizontalConnectionDistance:0.0f,maxConnectionHeight:8.0f,minSavedTicks:10},ender_pearl:{enable:1b,FetchSource:["offhand","mainhand"],preferredHand:"off",doConsume:0b,doTeleportDamage:0b,cooldownTicks:40,randomCooldownBias:20,maxConnectionDistance:64.0f,maxConnectionHeight:64.0f,throwAngle:30.0f,maxThrowPower:1.0f,throwAccuracy:1.0f}}
```

```mcfunction
/pmb skill conditional @e[type=minecraft:zombie,limit=1] traversal append [enable=1b,methods=["wind_charge","ender_pearl"]]
/pmb skill conditional @e[type=minecraft:zombie,limit=1] traversal.wind_charge append [doConsume=1b]
/pmb skill conditional @e[type=minecraft:zombie,limit=1] traversal.ender_pearl append [maxThrowPower=1.5f]
```

`traversal`父指令只填写`enable`和`methods`；`traversal.wind_charge`、`traversal.ender_pearl`分别填写各方法参数。方法指令可先于父指令创建节点，但父层仍默认关闭。`delete []`删除所选节点；`delete *`清除该节点的显式字段。旧的`wind_traversal`、`pearl_traversal`节点和优先级条目不再读取。

<details><summary>规划与执行</summary>

`methods`只接受`wind_charge`和`ender_pearl`，可调顺序、不可重复、可为空。寻路首先检查原版路线；`fallback`只在完整原版路线不可用时考虑特殊边。`efficient`可考虑风弹跨越局部无法直通的障碍，或绕开耗时的水和蛛网；非免疫生物跨越岩浆按避险处理，不按节时处理。

珍珠不用于单纯加快赶路；当原版路线不可达时，它与风弹按`methods`顺序检查。两种方法都检查已加载区块、物理轨迹和可用落点，只有成功释放才消耗物品。

风弹的`postJumpTicks`大于0时，获准预跳便开始冷却，随后取消或释放失败也不退还；设为0的风弹与珍珠在成功释放时开始冷却。未配置或关闭的跨越场景不执行额外的原版路径和轨迹检查。父层必须出现在当前策略优先表中。

</details>

<details>
<summary>参数细节</summary>

```text
enable
含义: 是否启用特殊跨越场景；方法节点单独启用仍需要父层开启
传参类型: 布尔值
值域: 0b, 1b
默认值: 0b

methods
含义: 风弹与珍珠跨越方法的检查顺序
传参类型: 字符串列表
值域: wind_charge、ender_pearl，各最多出现一次，也允许空列表
默认值: ["wind_charge","ender_pearl"]
说明: 默认先尝试风弹；将ender_pearl移到前面会先检查珍珠
```

</details>

- [风弹跨越的参数与实例](wind_traversal.md)
- [珍珠跨越的参数与实例](pearl_traversal.md)

[返回条件技能](README.md)
