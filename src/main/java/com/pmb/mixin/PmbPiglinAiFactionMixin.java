package com.pmb.mixin;

import com.pmb.faction.PmbFactionAi;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(PiglinAi.class)
public class PmbPiglinAiFactionMixin {
	@Inject(method = "isNearZombified", at = @At("HEAD"), cancellable = true)
	private static void pmb$allowFactionCombatWithZombified(Piglin piglin,
			CallbackInfoReturnable<Boolean> info) {
		if (PmbFactionAi.shouldFightNearestZombified(piglin)) {
			info.setReturnValue(false);
		}
	}

	@Inject(method = "updateActivity", at = @At("HEAD"))
	private static void pmb$resolveFactionAttackAvoidConflict(Piglin piglin, CallbackInfo info) {
		PmbFactionAi.preparePiglinActivityUpdate(piglin);
	}

	@Inject(method = "canAdmire", at = @At("RETURN"), cancellable = true)
	private static void pmb$applyFactionGreedRule(Piglin piglin, ItemStack stack,
			CallbackInfoReturnable<Boolean> info) {
		if (info.getReturnValue() && !PmbFactionAi.preservesPiglinGreed(piglin)) {
			info.setReturnValue(false);
		}
	}

	@Inject(method = "wantsToPickup", at = @At("RETURN"), cancellable = true)
	private static void pmb$applyFactionPickupRule(Piglin piglin, ItemStack stack,
			CallbackInfoReturnable<Boolean> info) {
		if (info.getReturnValue() && !PmbFactionAi.preservesPiglinGreed(piglin)) {
			info.setReturnValue(false);
		}
	}

	@Inject(method = "findNearestValidAttackTarget", at = @At("RETURN"), cancellable = true)
	private static void pmb$resolveFactionAttackTarget(ServerLevel level, Piglin piglin,
			CallbackInfoReturnable<Optional<? extends LivingEntity>> info) {
		LivingEntity vanillaTarget = info.getReturnValue().orElse(null);
		LivingEntity resolvedTarget = PmbFactionAi.resolvePiglinAttackTarget(piglin, vanillaTarget);
		info.setReturnValue(Optional.ofNullable(resolvedTarget));
	}
}
