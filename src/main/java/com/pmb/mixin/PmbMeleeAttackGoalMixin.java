package com.pmb.mixin;

import com.pmb.ai.PmbSkillItemAccess;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MeleeAttackGoal.class)
public class PmbMeleeAttackGoalMixin {
	@Shadow @Final protected PathfinderMob mob;

	@Inject(method = {"canUse", "canContinueToUse"}, at = @At("HEAD"), cancellable = true)
	private void pmb$disableMeleeForManagedBow(CallbackInfoReturnable<Boolean> info) {
		if (PmbSkillItemAccess.shouldSuppressMeleeForBow(mob)) info.setReturnValue(false);
	}
}
