# Portable Mob Behaviour Change Log

## Introduction

Used to record Git updates to this mod and may be referenced by Git commit messages.
The change log aims to record all changes as completely and precisely as possible.

# Format

Year.Month.Day-No.

## Record

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
