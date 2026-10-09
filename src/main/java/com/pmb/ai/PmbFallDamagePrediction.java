package com.pmb.ai;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/** Mirrors the enchantment reduction used by LivingEntity for predicted fall damage. */
public final class PmbFallDamagePrediction {
	private PmbFallDamagePrediction() {}
	public static float afterProtection(ServerLevel level, Mob mob, float rawDamage) {
		if (!(rawDamage > 0) || !Float.isFinite(rawDamage)) return 0;
		float protection = EnchantmentHelper.getDamageProtection(level, mob, mob.damageSources().fall());
		return CombatRules.getDamageAfterMagicAbsorb(rawDamage, protection);
	}
}
