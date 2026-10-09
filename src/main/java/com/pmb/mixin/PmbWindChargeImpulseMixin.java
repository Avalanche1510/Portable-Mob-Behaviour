package com.pmb.mixin;

import com.pmb.ai.PmbSkillScheduler;
import com.pmb.ai.PmbWindImpulseHolder;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WindCharge.class)
public abstract class PmbWindChargeImpulseMixin implements PmbWindImpulseHolder {
	@Unique private String pmb$source;
	@Unique private Vec3 pmb$before;
	@Override public void pmb$setImpulseSource(String source) { pmb$source = source; }
	@Inject(method = "explode", at = @At("HEAD"))
	private void pmb$beforeExplosion(Vec3 position, CallbackInfo info) {
		WindCharge charge = (WindCharge) (Object) this;
		if (pmb$source != null && charge.getOwner() instanceof Mob mob)
			pmb$before = mob.getDeltaMovement();
	}
	@Inject(method = "explode", at = @At("TAIL"))
	private void pmb$afterExplosion(Vec3 position, CallbackInfo info) {
		WindCharge charge = (WindCharge) (Object) this;
		if (!charge.level().isClientSide() && pmb$before != null && charge.getOwner() instanceof Mob mob
				&& mob.getDeltaMovement().distanceToSqr(pmb$before) > 1.0E-10) {
			mob.setIgnoreFallDamageFromCurrentImpulse(true, mob.position());
			PmbSkillScheduler.of(mob).impulseSessions().armAppliedImpulse(mob, pmb$source, 4);
		}
		pmb$before = null;
	}
}
