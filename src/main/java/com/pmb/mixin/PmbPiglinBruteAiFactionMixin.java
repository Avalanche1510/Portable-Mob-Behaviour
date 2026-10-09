package com.pmb.mixin;

import com.pmb.faction.PmbFactionAi;
import com.pmb.faction.PmbBrainAiDebug;
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
	@Inject(method = "updateActivity", at = @At("HEAD"))
	private static void pmb$coordinateIntent(PiglinBrute brute, CallbackInfo info) {
		PmbFactionAi.prepareBrainActivityUpdate(brute);
	}
	@Inject(method = "updateActivity", at = @At("TAIL"))
	private static void pmb$debugActivity(PiglinBrute brute, CallbackInfo info) {
		PmbBrainAiDebug.event(brute, "vanilla_update_activity", () -> "activity="
				+ brute.getBrain().getActiveNonCoreActivity().orElse(null));
	}

	@Inject(method = "wasHurtBy", at = @At("TAIL"))
	private static void pmb$debugHurt(ServerLevel level, PiglinBrute brute, LivingEntity attacker, CallbackInfo info) {
		PmbBrainAiDebug.event(brute, "vanilla_was_hurt_by", () -> "attacker=" + attacker.getId());
	}

	@Inject(method = "setAngerTarget", at = @At("TAIL"))
	private static void pmb$debugAnger(PiglinBrute brute, LivingEntity attacker, CallbackInfo info) {
		PmbBrainAiDebug.event(brute, "vanilla_set_anger_target", () -> "target=" + attacker.getId());
	}

	@Inject(method = "playActivitySound", at = @At("TAIL"))
	private static void pmb$debugActivitySound(PiglinBrute brute, CallbackInfo info) {
		PmbBrainAiDebug.event(brute, "vanilla_activity_sound", () -> "activity="
				+ brute.getBrain().getActiveNonCoreActivity().orElse(null));
	}
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
