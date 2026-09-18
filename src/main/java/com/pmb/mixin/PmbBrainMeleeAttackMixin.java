package com.pmb.mixin;

import com.pmb.ai.PmbAiHolder;
import com.pmb.ai.PmbSkillScheduler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.MeleeAttack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MeleeAttack.class)
public abstract class PmbBrainMeleeAttackMixin {
	@Redirect(method = "lambda$create$3", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/Mob;doHurtTarget(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;)Z"))
	private static boolean pmb$queueStandardBrainMelee(Mob attacker, ServerLevel level, Entity target) {
		if (!((PmbAiHolder) attacker).pmb$getAiData().isConfigured()) return attacker.doHurtTarget(level, target);
		PmbSkillScheduler.of(attacker).queueVanillaMelee(level, target);
		return true;
	}
}
