package com.pmb.mixin;

import com.pmb.faction.PmbFactionAi;
import net.minecraft.world.entity.raid.Raider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.entity.raid.Raider$ObtainRaidLeaderBannerGoal")
public class PmbRaiderBannerFactionMixin {
	@Shadow
	@Final
	private Raider mob;

	@Inject(method = {"canUse", "canContinueToUse"}, at = @At("HEAD"), cancellable = true)
	private void pmb$interruptBannerForFactionCombat(CallbackInfoReturnable<Boolean> info) {
		if (PmbFactionAi.hasFactionCombatTarget(mob)) {
			info.setReturnValue(false);
		}
	}
}
