package com.pmb.mixin;

import com.pmb.faction.PmbFactionAi;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.sensing.Sensor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Sensor.class)
public class PmbSensorFactionMixin {
	@Inject(method = "isEntityAttackable", at = @At("RETURN"), cancellable = true)
	private static void pmb$filterBrainAttackTarget(ServerLevel level, LivingEntity source, LivingEntity target,
			CallbackInfoReturnable<Boolean> info) {
		if (info.getReturnValue() && source instanceof Mob mob && !PmbFactionAi.mayAttack(mob, target)) {
			info.setReturnValue(false);
		}
	}

	@Inject(method = "isEntityAttackableIgnoringLineOfSight", at = @At("RETURN"), cancellable = true)
	private static void pmb$filterBrainAttackTargetIgnoringSight(ServerLevel level, LivingEntity source,
			LivingEntity target, CallbackInfoReturnable<Boolean> info) {
		if (info.getReturnValue() && source instanceof Mob mob && !PmbFactionAi.mayAttack(mob, target)) {
			info.setReturnValue(false);
		}
	}
}
