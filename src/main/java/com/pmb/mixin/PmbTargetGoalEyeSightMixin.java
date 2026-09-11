package com.pmb.mixin;

import com.pmb.ai.PmbSkillItemAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.sensing.Sensing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(TargetGoal.class)
public abstract class PmbTargetGoalEyeSightMixin {
	@Shadow @Final protected Mob mob;

	@Redirect(method = "canContinueToUse", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/ai/sensing/Sensing;hasLineOfSight(Lnet/minecraft/world/entity/Entity;)Z"))
	private boolean pmb$hasLineOfSightOrRetainsPmbTarget(Sensing sensing, Entity target) {
		if (sensing.hasLineOfSight(target)) return true;
		return target == mob.getTarget() && target instanceof LivingEntity living
				&& PmbSkillItemAccess.canRetainTargetWithoutEyeSight(mob, living);
	}
}
