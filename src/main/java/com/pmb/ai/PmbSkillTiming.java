package com.pmb.ai;

import net.minecraft.util.RandomSource;

/** Shared, stateless timing rules for PMB skill checks. */
public final class PmbSkillTiming {
	public static final int DEFAULT_RANDOM_COOLDOWN_BIAS = 20;
	public static final int MAX_RANDOM_COOLDOWN_BIAS = 1200;

	private PmbSkillTiming() {}

	public static boolean passesChance(RandomSource random, float chance) {
		if (chance <= 0.0F) return false;
		if (chance >= 1.0F) return true;
		return random.nextFloat() < chance;
	}

	public static int cooldown(RandomSource random, int baseTicks, int randomBias) {
		int base = Math.max(0, baseTicks);
		int bias = Math.max(0, Math.min(MAX_RANDOM_COOLDOWN_BIAS, randomBias));
		return base + (bias == 0 ? 0 : random.nextInt(bias + 1));
	}
}
