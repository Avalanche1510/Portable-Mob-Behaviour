# Wind rescue (`skill.conditional.fall_rescue.wind_charge`)

Complete default method configuration. The `fall_rescue` parent is disabled by default. The parent and wind method must both be enabled, and the current strategy must include `fall_rescue`.

```text
fall_rescue:{enable:0b,methods:["wind_charge","ender_pearl"],wind_charge:{enable:1b,FetchSource:["offhand","mainhand"],preferredHand:"off",doConsume:0b,successChance:1.0f,cooldownTicks:20,randomCooldownBias:0,rescueLeadTicks:4}}
```

<details>
<summary>activation and release details</summary>

`rescueLeadTicks` opens a pre-impact check window; it is not a fixed release time. Inside that window, wind must be predicted to explode in time within pushing distance of the mob, and expected harm must reach the parent's `minRescueDamage`. A feasible plan claims `LOOK` toward its predicted explosion point. Accidental falls roll chance only after priority and resource admission. Failure spends attempt cooldown and blocks pearl fallback for that fall.

After successful release, the mob looks at the expected wind explosion point through the actual landing completion tick. Rising after the bounce does not end the pose. PMB fall protection is granted only if the real explosion pushes the mob. Missing items, blocked resources, spawn failure, or a plan invalidated before commit clears LOOK this tick without consuming an item; an already spent chance cooldown is not refunded. Planned falls spend cooldown only on commit and skip chance.

</details>

<details>
<summary>details of parameters</summary>

```text
enable
Meaning: Whether the wind rescue method is enabled; the fall_rescue parent must also be enabled
Type: bool
Range: 0b, 1b
Default: 1b

FetchSource
Meaning: Ordered item locations allowed to provide a wind charge
Type: list of strings
Range: mainhand, offhand, inventory, inventory:1 through inventory:256, or inclusive ranges such as inventory:1..9
Default: omitted; offhand then mainhand
Note: Use a charge already in the destination hand or exchange from the listed sources; overlaps are allowed

preferredHand
Meaning: Preferred hand for wind rescue
Type: string
Range: main, off, main-enforce, off-enforce
Default: off
Note: Plain preference falls back only when PMB occupies the preferred hand; enforce never falls back

doConsume
Meaning: Whether a charge is consumed on successful release
Type: bool
Range: 0b, 1b
Default: 0b

successChance
Meaning: Chance of an admitted, physically feasible accidental-fall rescue check succeeding
Type: float
Range: [0.0f, 1.0f]
Default: 1.0f
Note: Planned-fall promises skip chance; chance failure ends further attempts for that fall

cooldownTicks
Meaning: Base cooldown in game ticks after a check
Type: integer
Range: [0, 72000]
Default: 20
Note: Accidental falls spend it on chance check; planned falls spend it only on commit

randomCooldownBias
Meaning: Maximum inclusive uniform random extra ticks added to cooldown
Type: integer
Range: [0, 1200]
Default: 0

rescueLeadTicks
Meaning: Ticks before predicted impact that open the check window
Type: integer
Range: [0, 1200]
Default: 4
Note: 0 means the last airborne check tick; the explosion must still push the mob in time
```

</details>

<details>
<summary>Full configuration and test scenario</summary>

```mcfunction
/summon minecraft:zombie ~ ~ ~ {Tags:["pmb_wind_rescue"],equipment:{offhand:{id:"minecraft:wind_charge",count:64}},PmbAi:{skill:{conditional:{fall_rescue:{enable:1b,methods:["wind_charge"],wind_charge:{enable:1b,FetchSource:["offhand","mainhand"],preferredHand:"off",doConsume:0b,successChance:1.0f,cooldownTicks:20,randomCooldownBias:0,rescueLeadTicks:4}}}}}}
/pmb skill conditional @e[tag=pmb_wind_rescue] fall_rescue.wind_charge modify [rescueLeadTicks=10]
```

Drop the mob from a platform and inspect the `fall_rescue` method reason in K debug. Increasing the window from 4 to 10 permits an earlier feasibility check; no timely explosion or no item must produce no charge. `fall_rescue.wind_charge delete []` removes the method node, but an enabled parent whose `methods` still contains wind uses that method's enabled defaults. To disable it, explicitly set `enable=0b` or remove it from `methods`.

</details>

[Back to fall rescue](fall_rescue.md)
