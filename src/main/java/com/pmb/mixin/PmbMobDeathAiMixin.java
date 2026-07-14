package com.pmb.mixin;

import com.pmb.ai.PmbAiHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public class PmbMobDeathAiMixin {
	@Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
	private void pmb$stopConfiguredAiAfterDeath(CallbackInfo info) {
		Mob mob = (Mob) (Object) this;
		if (!mob.isAlive() && ((PmbAiHolder) mob).pmb$getAiData().isConfigured()) {
			info.cancel();
		}
	}

	@Inject(method = "doHurtTarget", at = @At("HEAD"), cancellable = true)
	private void pmb$preventConfiguredAiMeleeAfterDeath(ServerLevel level, Entity target,
			CallbackInfoReturnable<Boolean> info) {
		Mob mob = (Mob) (Object) this;
		if (!mob.isAlive() && ((PmbAiHolder) mob).pmb$getAiData().isConfigured()) {
			info.setReturnValue(false);
		}
	}
}
