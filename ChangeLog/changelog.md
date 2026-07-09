# Portable Mob Behaviour Change Log

## Introduction

Used to record Git updates to this mod and may be referenced by Git commit messages.
The change log aims to record all changes as completely and precisely as possible.

# Format

Year.Month.Day-No.

## Record

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
