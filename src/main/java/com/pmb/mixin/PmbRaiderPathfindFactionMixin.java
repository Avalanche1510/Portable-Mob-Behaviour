package com.pmb.mixin;

import com.pmb.faction.PmbFactionAi;
import net.minecraft.world.entity.ai.goal.PathfindToRaidGoal;
import net.minecraft.world.entity.raid.Raider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PathfindToRaidGoal.class)
public class PmbRaiderPathfindFactionMixin {
	@Shadow
	@Final
	private Raider mob;

	@Inject(method = "canContinueToUse", at = @At("HEAD"), cancellable = true)
	private void pmb$interruptRaidPathForFactionCombat(CallbackInfoReturnable<Boolean> info) {
		if (PmbFactionAi.hasFactionCombatTarget(mob)) {
			info.setReturnValue(false);
		}
	}
}
