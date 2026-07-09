package com.pmb.mixin;

import com.pmb.ai.PmbCriticalAttackHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PmbPlayerCriticalAttackMixin implements PmbCriticalAttackHolder {
	@Unique
	private boolean pmb$criticalAttack;

	@Override
	public boolean pmb$isCriticalAttack() {
		return pmb$criticalAttack;
	}

	@Inject(method = "attack", at = @At("HEAD"))
	private void pmb$clearCriticalAttackAtStart(Entity target, CallbackInfo info) {
		pmb$criticalAttack = false;
	}

	@Inject(method = "attack", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/player/Player;onAttack()V"))
	private void pmb$captureCriticalAttack(Entity target, CallbackInfo info) {
		Player self = (Player) (Object) this;
		pmb$criticalAttack = target instanceof LivingEntity
				&& self.getAttackStrengthScale(0.5F) > 0.9F
				&& self.fallDistance > 0.0D
				&& !self.onGround()
				&& !self.onClimbable()
				&& !self.isInWater()
				&& !self.isMobilityRestricted()
				&& !self.isPassenger()
				&& !self.isSprinting();
	}

	@Inject(method = "attack", at = @At("RETURN"))
	private void pmb$clearCriticalAttackAtEnd(Entity target, CallbackInfo info) {
		pmb$criticalAttack = false;
	}
}
