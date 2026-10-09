# mace (`skill.conditional.mace`)

Complete default configuration (optional source lists are explained below):

```text
mace:{FetchSource:["mainhand"],enable:0b,smashRange:3.0f,hitChance:0.5f,damageReduction:0.5f,smashCooldownTicks:100,randomCooldownBias:20}
```

Mace AI executes in mainhand. A vanilla mace already there is used directly; otherwise FetchSource supplies a permanent exchange. An offhand-source swap reserves both hands plus SMASH for that tick. Falling, target, chance and cooldown conditions still apply. Mace does not accept preferredHand.

<details>
<summary>activation and damage details</summary>

Conditions for a smash check:

1. The entity is a mob and FetchSource resolves a vanilla mace to the required main-hand attack path.
2. enable is true.
3. The mob is airborne, moving downward, and meets the vanilla minimum fall distance for a mace smash.
4. The mob has a living, attackable, visible hostile target within smashRange.
5. The mob is not in the smash-check cooldown defined by smashCooldownTicks.
6. The mob does not have NoAI enabled and is not in shield-break vulnerability.

After priority and resource admission, the mob performs one hitChance check. It enters the smashCooldownTicks cooldown whether that check succeeds or fails. On success, the mob faces the target, swings its main hand, and attacks through the vanilla Mob and mace damage paths. On failure, no attack is performed.

While the skill is enabled and the mob is in a valid falling-mace state, attacks attempted directly by an ordinary melee goal without this skill's check are prevented. This stops vanilla AI from bypassing hitChance, smashCooldownTicks, or damageReduction. Ordinary melee attacks remain unchanged whenever the mob is not in a valid mace-smash fall.

Vanilla first calculates base attack damage, enchantments, and fall-height mace bonus. The final result of the skill-triggered smash is then multiplied by (1-damageReduction). The default damageReduction of 0.5 therefore halves the final mace damage. After a successful hit, PMB resets accumulated fall distance, so a subsequent hit needs a fresh fall of at least the vanilla minimum. A missed or blocked hit does not reset it. Vanilla mace sounds, knockback, and durability use remain intact.

</details>

<details>
<summary>parameters structure</summary>

```text
mace:
    {
        enable: <bool>,
        FetchSource: [<string>, ...],
        smashRange: <float>,
        hitChance: <float>,
        damageReduction: <float>,
        smashCooldownTicks: <int>,
        randomCooldownBias: <int>
    }
```

</details>

<details>
<summary>details of parameters</summary>

```text
enable
Meaning: Whether this AI skill is enabled
Type: bool
Range: 0b, 1b
Default: 0b

FetchSource
Meaning: Ordered locations allowed to provide this skill's required item
Type: list of strings
Range: mainhand, offhand, inventory, inventory:1 through inventory:256, or inclusive ranges such as inventory:1..9
Default: omitted; uses this skill's legacy source-hand order
Note: Used only when the destination hand lacks a suitable item; empty/invalid lists supply no fetch location

smashRange
Meaning: Maximum target distance at which a falling mob can immediately start a smash check; line of sight is also required
Type: float
Range: [0.0f, 64.0f]
Default: 3.0f

hitChance
Meaning: Chance for each smash check to succeed and perform the attack
Type: float
Range: [0.0f, 1.0f]
Default: 0.5f

damageReduction
Meaning: Final damage reduction ratio for a skill-triggered mace smash; actual damage is multiplied by (1-damageReduction)
Type: float
Range: [0.0f, 1.0f]
Default: 0.5f
Note: 0.0 leaves damage unchanged, 0.5 halves final damage, and 1.0 reduces final damage to 0

smashCooldownTicks
Meaning: Game ticks of cooldown after every smash check, whether the check succeeds or fails
Type: integer
Range: [0, 72000]
Default: 100
Note: 0 allows another check on the next game tick

randomCooldownBias
Meaning: Inclusive uniform random extra game ticks added whenever a smash-check cooldown starts
Type: integer
Range: [0, 1200]
Default: 20
Note: 0 makes smash cooldown fixed
```

Values outside these ranges are automatically clamped when the entity data is read.
One second is normally equal to 20 game ticks.

</details>

<details>
<summary>command examples</summary>

```text
Summon a zombie holding a mace in its main hand and using all default mace parameters.
summon zombie ~ ~ ~ {PmbAi:{skill:{conditional:{mace:{enable:1b}}}}, equipment:{mainhand:{id:mace}}}

Summon a zombie that always performs a smash check within 4 blocks, deals 75% of normal mace damage, and enters a 3-second cooldown after every check.
summon zombie ~ ~ ~ {PmbAi:{skill:{conditional:{mace:{enable:1b, smashRange:4.0f, hitChance:1.0f, damageReduction:0.25f, smashCooldownTicks:60}}}}, equipment:{mainhand:{id:mace}}}

Summon a zombie with both downward wind-charge bounce and mace-smash AI enabled.
summon zombie ~ ~ ~ {PmbAi:{skill:{active:{wind_charge:{enable:1b, bounceRange:6.0f, bounceChance:1.0f}},conditional:{mace:{enable:1b}}}}, equipment:{mainhand:{id:mace}, offhand:{id:wind_charge}}}

Disable mace AI on the nearest zombie without removing its other configured parameters.
data merge entity @e[type=zombie,sort=nearest,limit=1] {PmbAi:{skill:{conditional:{mace:{enable:0b}}}}}
```

</details>

<details><summary>Quick enable with the modular command</summary>

```mcfunction
/pmb skill conditional @n[type=minecraft:zombie] mace append [enable=1b]
/pmb skill conditional @n[type=minecraft:zombie] mace get
/pmb skill conditional @n[type=minecraft:zombie] mace modify [enable=0b]
```

Append requires an absent skill node. For an existing node use modify when enable is explicit, or insert when it has not been explicitly written.

</details>

[Back to skills](../README.md)
