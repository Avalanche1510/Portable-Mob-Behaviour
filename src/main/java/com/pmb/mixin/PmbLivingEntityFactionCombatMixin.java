package com.pmb.mixin;

import com.pmb.faction.PmbFactionCombat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class PmbLivingEntityFactionCombatMixin {
	@Unique
	private boolean pmb$factionBlockedCurrentDamage;

	@Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
	private void pmb$blockFactionFriendlyFire(ServerLevel level, DamageSource source, float amount,
			CallbackInfoReturnable<Boolean> info) {
		pmb$factionBlockedCurrentDamage = PmbFactionCombat.blocksDamage(level,
				(LivingEntity) (Object) this, source);
		if (pmb$factionBlockedCurrentDamage) {
			info.setReturnValue(false);
		}
	}

	@Inject(method = "hurtServer", at = @At("RETURN"))
	private void pmb$notifyFactionDamage(ServerLevel level, DamageSource source, float amount,
			CallbackInfoReturnable<Boolean> info) {
		if (!pmb$factionBlockedCurrentDamage && info.getReturnValue()) {
			PmbFactionCombat.afterDamage(level, (LivingEntity) (Object) this, source);
		}
		pmb$factionBlockedCurrentDamage = false;
	}
}
