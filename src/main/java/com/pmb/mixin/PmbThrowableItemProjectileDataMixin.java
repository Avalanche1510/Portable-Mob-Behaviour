package com.pmb.mixin;

import com.pmb.ai.PmbEnderPearlDamageHolder;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stores PMB's pearl-only teleport damage flag on the nearest vanilla class
 * which actually declares the projectile item save methods in 26.2.
 */
@Mixin(ThrowableItemProjectile.class)
public abstract class PmbThrowableItemProjectileDataMixin implements PmbEnderPearlDamageHolder {
	@Unique private boolean pmb$teleportDamage;

	@Override
	public void pmb$setTeleportDamage(boolean enabled) {
		pmb$teleportDamage = enabled;
	}

	@Override
	public boolean pmb$doesTeleportDamage() {
		return pmb$teleportDamage;
	}

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void pmb$saveTeleportDamage(ValueOutput output, CallbackInfo info) {
		if ((Object) this instanceof ThrownEnderpearl && pmb$teleportDamage) {
			output.putBoolean("PmbTeleportDamage", true);
		}
	}

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void pmb$readTeleportDamage(ValueInput input, CallbackInfo info) {
		pmb$teleportDamage = (Object) this instanceof ThrownEnderpearl
				&& input.getBooleanOr("PmbTeleportDamage", false);
	}
}
