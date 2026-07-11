# Portable Mob Behaviour Change Log

## Introduction

Used to record Git updates to this mod and may be referenced by Git commit messages.
The change log aims to record all changes as completely and precisely as possible.

# Format

Year.Month.Day-No.

## Record

### 26.7.11-1
Added the mace AI skill.

Added enable, smashRange, hitChance, damageReduction, and smashCooldownTicks with defaults of 0b, 3.0, 0.5, 0.5, and 100 respectively. All parameters are included in NBT reading, saving, configuration detection, and clamping.

Mace AI activates only while a mob holds a vanilla mace in its main hand, is airborne and moving downward, and meets the vanilla minimum fall distance for a mace smash. The target must also be alive, attackable, visible, and within smashRange.

Once eligible, the mob immediately performs one hitChance check and enters the smashCooldownTicks cooldown whether the check succeeds or fails. On success, it faces the target, swings its main hand, and attacks through the vanilla Mob and mace paths. On failure, no attack is performed.

While the skill is enabled and the mob is in a valid falling-mace state, ordinary melee-goal attacks that did not pass through the mace skill check are prevented. This stops them from bypassing hitChance, smashCooldownTicks, or damageReduction. Ordinary melee attacks in other states remain unchanged.

A skill-triggered mace attack first calculates vanilla base damage, enchantments, and fall-height bonus, then multiplies the final damage by (1-damageReduction). The default 0.5 halves final damage while preserving vanilla mace sounds, knockback, durability use, and fall-state handling.

Mace AI does not trigger while the mob has NoAI enabled or is in shield-break vulnerability. Added the complete English and Chinese structures, parameter references, and command examples.

mace can work together with downward wind_charge bounce: wind-charge AI handles launch, airborne target retention, and movement, while mace AI controls the falling smash chance, cooldown, and damage.

### 26.7.10-2
Added the wind_charge AI skill as a shared foundation that can also be used by future wind-charge mace and wind-charge spear AI behaviours.

Added enable, throwRange, throwChance, throwCooldownTicks, throwAccuracy, bounceRange, bounceChance, and bounceCooldownTicks. Wind-charge AI only runs while the mob holds a vanilla wind charge in either hand and does not consume it by default.

Added the boolean doConsume parameter with a default of 0b. It controls both throw and bounce mode. When enabled, each successful trigger consumes one wind charge from the hand that performed the action.

Added the float inAirTrackStrength parameter with a range of 0.0–1.0 and a default of 0.012. It controls the maximum horizontal acceleration added toward the target per game tick after a downward self-bounce. A value of 0.0 disables horizontal tracking acceleration but does not disable airborne facing correction.

Added wind-charge throw mode. When a visible target is within throwRange, the mob checks throwChance every throwCooldownTicks game ticks. On success, it swings the hand holding the wind charge and throws with random spread controlled by throwAccuracy toward a lead point calculated from the target's position and velocity.

Added wind-charge bounce mode. When the mob is on the ground and its target is within bounceRange, it checks bounceChance every bounceCooldownTicks game ticks. On success, it immediately looks down, swings, jumps, and fires a wind charge beneath its feet to maximize upward launch. Bounce mode has priority when both modes can be checked.

Added post-bounce airborne pursuit. The mob remembers its bounce target for up to 60 game ticks, restores that target if vanilla airborne pathfinding clears it, and applies capped horizontal steering so it continues advancing while preserving gravity and wind-charge launch velocity. The state clears after landing, target invalidation, or timeout.

Reduced post-bounce airborne steering strength. It now applies only a small acceleration while targetward velocity is below a threshold and does not reduce or replace existing horizontal inertia, making airborne turning much less conspicuous.

Mobs in post-bounce flight now continuously align their view, head, and body with the target after the initial downward pose. Starting a melee attack during that pose ends it immediately and turns the mob toward the entity being attacked, preventing hits while visibly facing away.

Fixed the client continuing to render a wind charge after doConsume depleted the final item in that hand even though the server-side hand was empty. Consumption now resets the mob's equipment slot with the remaining stack or an explicit empty stack so the equipment change is synchronized.

Dedicated wind-charge mace and wind-charge spear attack decisions are not implemented yet. The high success rate of mace attacks after bouncing is an emergent compatibility effect of target retention and airborne movement.

Downward self-bounce now gives the mob vanilla LivingEntity impulse fall protection. Returning to the impulse height does not deal fall damage from that launch; when landing lower, the additional drop below the launch point is still included in normal fall-damage calculation.

Wind-charge AI does not trigger while the mob has NoAI enabled or is in shield-break vulnerability. All parameters are included in NBT reading, saving, configuration detection, defaults, clamping, and the complete English and Chinese structures, parameter references, and command examples.

### 26.7.10-1
Added shield-break vulnerability after true shield breaks.

Added the integer shield parameter disableVulnerTicks with a range of 0–72000 and a default value of 40. When this value is 0, shield-break vulnerability is skipped.

Added the float shield parameter vulnerDamageMultiplier with a range of 1.0–100.0 and a default value of 1.5.

Added the float shield parameter disableKBMultiplier with a range of 0.0–100.0 and a default value of 1.2. When this value is 0.0, the extra shield-break knockback is skipped.

The final hit that truly breaks the shield now applies extra shield-break knockback. The applied base strength is 0.6 multiplied by disableKBMultiplier.

During shield-break vulnerability, the mob cannot attack, keeps clearing its target and navigation, and suppresses AI movement input while preserving existing velocity from knockback or other external forces. It renders with the same shaking style used by zombie-villager curing or piglin zombification. The implementation does not enable NoAI, so normal physics such as gravity continue to apply.

Damage received during shield-break vulnerability is multiplied by vulnerDamageMultiplier.

Adjusted shield-raising animations for zombie villagers and zombified piglins so their blocking arm pose is no longer overwritten by zombie-arm animation. Illager shield rendering now also forces the blocking arm render state to BLOCK and switches crossed-arm poses to NEUTRAL while a shield is being used. The special held-item layers used by vindicators and evokers now render held items while the mob is using a blocking item instead of only rendering during vanilla aggressive or spellcasting states.

Shield toughness now resets immediately when the current shield use is interrupted without a true shield break, such as when the mob loses its target while shielding.

### 26.7.9-3
Added the integer shield parameter critToughnessDamage with a range of 1–100 and a default value of 1.

The player's vanilla critical-hit eligibility is captured before attack charge resets. Critical shield-disabling attacks consume critToughnessDamage points, while normal shield-disabling attacks consume 1 point.

A non-breaking critical shield-disabling hit plays the zombie wooden-door attack sound and emits 10 oak-door debris particles. A non-breaking normal shield-disabling hit emits 6 particles, and any other blocked hit emits 3 particles. A true shield break plays the zombie wooden-door break sound and emits 24 particles; the effects do not overlap on the final hit.

PMB-controlled shields now continue to be recognized while the server still sees the mob actively using its off-hand shield, covering the final transition tick where the internal shield-use counter may have just reached zero.

PMB shield angle checks now prefer the attacker's entity position over the raw damage-source position when available, avoiding close-range or extended-interaction-range source positions flipping across the defender and causing apparent shield penetration.

### 26.7.9-2
Added the integer shield parameter shieldToughness.

It controls how many shield-disabling attacks are required to break the current shield-use round.

The counter refreshes when the normal shield cooldown ends and before a new round after a forced cooldown.

The default value is 1, preserving the previous behaviour.

Fixed the vanilla shield activation delay leaving a mob visually shielding but still vulnerable during the first few ticks.

PMB-controlled shields now block from the first shield-use tick, preventing rapid attacks from slipping through.

Fixed repeated attacks accumulating shield-impact momentum while the target was still in its damage invulnerability interval.

Shield recoil, durability damage, and shieldToughness consumption now occur at most once per valid damage interval. Attacks ignored by the normal damage cooldown can still be visually blocked, but no longer accumulate velocity or consume additional shield toughness.

### 26.7.9-1
This mod was separated from the [Age of Cavalry](https://github.com/Avalanche1510/Age-of-Cavalry) mod and is now developed as an independent project.

Add change log and documentations

Implemented basic shield usage for mobs and correct shield-holding animations for supported humanoid mobs.
Shield-holding animations are not guaranteed to look correct on non-humanoid mobs.

Currently supported shield-holding animations: Zombies, Piglins, Skeletons, Illagers.

New parameters and structure:
```text
{
    PmbAi:
        {
            shield:
                {
                    enable: <bool>,
                    range: <float>,
                    shieldChance: <float>,
                    minUseTicks: <int>,
                    maxUseTicks: <int>,
                    cooldownTicks: <int>,
                    blockingAngle: <float>,
                    axeDisableCooldownTicks: <int>,
                    speedReduction: <float>
                }
        }
}
```
