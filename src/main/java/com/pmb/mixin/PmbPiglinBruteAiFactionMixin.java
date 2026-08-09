package com.pmb.mixin;

import com.pmb.faction.PmbFactionAi;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.entity.monster.piglin.PiglinBruteAi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(PiglinBruteAi.class)
public class PmbPiglinBruteAiFactionMixin {
	@Inject(method = "wasHurtBy", at = @At("HEAD"), cancellable = true)
	private static void pmb$ignoreNonSurvivalRetaliation(ServerLevel level, PiglinBrute piglin,
			LivingEntity attacker, CallbackInfo info) {
		if (!PmbFactionAi.isCombatTargetable(attacker)) {
			info.cancel();
		}
	}

	@Inject(method = "findNearestValidAttackTarget", at = @At("RETURN"), cancellable = true)
	private static void pmb$resolveFactionAttackTarget(ServerLevel level, AbstractPiglin abstractPiglin,
			CallbackInfoReturnable<Optional<? extends LivingEntity>> info) {
		if (!(abstractPiglin instanceof PiglinBrute piglin)) {
			return;
		}
		LivingEntity vanillaTarget = info.getReturnValue().orElse(null);
		LivingEntity resolvedTarget = PmbFactionAi.resolvePiglinBruteAttackTarget(piglin, vanillaTarget);
		info.setReturnValue(Optional.ofNullable(resolvedTarget));
	}
}
