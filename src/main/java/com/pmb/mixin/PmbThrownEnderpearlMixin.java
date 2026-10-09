package com.pmb.mixin;

import com.pmb.ai.PmbEnderPearlDamageHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ThrownEnderpearl.class)
public abstract class PmbThrownEnderpearlMixin {
	@Unique private net.minecraft.world.phys.Vec3 pmb$ownerPositionBeforeHit;
	@Inject(method = "onHit", at = @At("HEAD"))
	private void pmb$captureOwnerBeforeHit(HitResult hit, CallbackInfo info) {
		ThrownEnderpearl pearl = (ThrownEnderpearl) (Object) this;
		pmb$ownerPositionBeforeHit = pearl.getOwner() == null ? null : pearl.getOwner().position();
	}
	@Inject(method = "onHit", at = @At("TAIL"))
	private void pmb$damageMobOwnerAfterTeleport(HitResult hit, CallbackInfo info) {
		ThrownEnderpearl pearl = (ThrownEnderpearl) (Object) this;
		if (!((PmbEnderPearlDamageHolder) pearl).pmb$doesTeleportDamage()
				|| !(pearl.getOwner() instanceof Mob owner)
				|| !(owner.level() instanceof ServerLevel level) || !owner.isAlive()
				|| pmb$ownerPositionBeforeHit == null
				|| owner.position().distanceToSqr(pmb$ownerPositionBeforeHit) <= 1.0E-8) return;
		owner.hurtServer(level, pearl.damageSources().enderPearl(), 5.0F);
		pmb$ownerPositionBeforeHit = null;
	}
}
