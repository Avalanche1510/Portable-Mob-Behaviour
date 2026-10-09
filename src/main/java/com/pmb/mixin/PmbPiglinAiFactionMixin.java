package com.pmb.mixin;

import com.pmb.faction.PmbFactionAi;
import com.pmb.faction.PmbBrainAiDebug;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.sounds.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(PiglinAi.class)
public class PmbPiglinAiFactionMixin {
	@Shadow
	private static void stopHoldingOffHandItem(ServerLevel level, Piglin piglin, boolean bartered) {
		throw new AssertionError();
	}

	@Redirect(method = "wasHurtBy", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/monster/piglin/PiglinAi;stopHoldingOffHandItem(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/monster/piglin/Piglin;Z)V"))
	private static void pmb$preserveAdmiredGoldOnHurt(ServerLevel level, Piglin piglin, boolean bartered) {
		if (!PmbFactionAi.preservesAdmirationOnHurt(piglin)) stopHoldingOffHandItem(level, piglin, bartered);
	}

	@Redirect(method = "wasHurtBy", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/ai/Brain;eraseMemory(Lnet/minecraft/world/entity/ai/memory/MemoryModuleType;)V"))
	private static void pmb$preserveAdmirationMemoryOnHurt(Brain<?> brain, MemoryModuleType<?> memory,
			ServerLevel level, Piglin piglin, LivingEntity attacker) {
		if (memory != MemoryModuleType.ADMIRING_ITEM || !PmbFactionAi.preservesAdmirationOnHurt(piglin))
			brain.eraseMemory(memory);
	}

	@Redirect(method = "wasHurtBy", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/ai/Brain;setMemoryWithExpiry(Lnet/minecraft/world/entity/ai/memory/MemoryModuleType;Ljava/lang/Object;J)V",
			ordinal = 0))
	private static void pmb$setConfiguredAdmirationTimer(Brain<?> brain, MemoryModuleType<?> memory,
			Object value, long vanillaTicks, ServerLevel level, Piglin piglin, LivingEntity attacker) {
		int configured = PmbFactionAi.piglinHurtDisableTicks(piglin);
		if (configured != 0) brain.setMemoryWithExpiry(MemoryModuleType.ADMIRING_DISABLED, true,
				configured < 0 ? vanillaTicks : configured);
	}

	@Inject(method = "wasHurtBy", at = @At("HEAD"))
	private static void pmb$handleFactionPiglinAttacker(ServerLevel level, Piglin piglin,
			LivingEntity attacker, CallbackInfo info) {
		// Vanilla returns before admiration handling for another Piglin attacker.
		if (!(attacker instanceof Piglin) || PmbFactionAi.piglinHurtDisableTicks(piglin) <= 0
				|| !piglin.getBrain().hasMemoryValue(MemoryModuleType.ADMIRING_ITEM)) return;
		if (!piglin.getOffhandItem().isEmpty()) stopHoldingOffHandItem(level, piglin, false);
		piglin.getBrain().eraseMemory(MemoryModuleType.ADMIRING_ITEM);
	}

	@Inject(method = "wasHurtBy", at = @At("TAIL"))
	private static void pmb$setNonPlayerAdmirationTimer(ServerLevel level, Piglin piglin,
			LivingEntity attacker, CallbackInfo info) {
		if (attacker instanceof net.minecraft.world.entity.player.Player) return;
		int ticks = PmbFactionAi.piglinHurtDisableTicks(piglin);
		if (ticks > 0)
			piglin.getBrain().setMemoryWithExpiry(MemoryModuleType.ADMIRING_DISABLED, true, ticks);
	}

	@WrapMethod(method = "broadcastAngerTarget")
	private static void pmb$wrapDamageBroadcast(ServerLevel level, AbstractPiglin victim,
			LivingEntity attacker, Operation<Void> original) {
		PmbFactionAi.withVanillaHurtBroadcast(victim, attacker,
				() -> original.call(level, victim, attacker));
	}

	@WrapMethod(method = "broadcastUniversalAnger")
	private static void pmb$wrapUniversalDamageBroadcast(ServerLevel level, AbstractPiglin victim,
			Operation<Void> original) {
		PmbFactionAi.withVanillaHurtBroadcast(victim, null,
				() -> original.call(level, victim));
	}

	@Inject(method = "setAngerTarget", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/ai/Brain;setMemoryWithExpiry(Lnet/minecraft/world/entity/ai/memory/MemoryModuleType;Ljava/lang/Object;J)V",
			ordinal = 0, shift = At.Shift.AFTER))
	private static void pmb$observeWrittenAnger(ServerLevel level, AbstractPiglin responder,
			LivingEntity attacker, CallbackInfo info) {
		PmbFactionAi.onVanillaGroupAngerWritten(responder, attacker);
		if (responder instanceof Piglin piglin)
			PmbBrainAiDebug.event(piglin, "vanilla_set_anger_target", () ->
					"target=" + attacker.getId() + " outcome=ANGRY_AT_written");
	}

	@Inject(method = "wasHurtBy", at = @At("TAIL"))
	private static void pmb$debugHurt(ServerLevel level, Piglin piglin, LivingEntity attacker, CallbackInfo info) {
		PmbBrainAiDebug.event(piglin, "vanilla_was_hurt_by", () -> "attacker=" + attacker.getId()
				+ " admiring=" + piglin.getBrain().hasMemoryValue(MemoryModuleType.ADMIRING_ITEM));
	}

	@Inject(method = "updateActivity", at = @At("TAIL"))
	private static void pmb$debugActivity(Piglin piglin, CallbackInfo info) {
		PmbBrainAiDebug.event(piglin, "vanilla_update_activity", () -> "activity="
				+ piglin.getBrain().getActiveNonCoreActivity().orElse(null));
	}

	@Inject(method = "getSoundForCurrentActivity", at = @At("RETURN"))
	private static void pmb$debugActivitySound(Piglin piglin,
			CallbackInfoReturnable<Optional<SoundEvent>> info) {
		PmbBrainAiDebug.event(piglin, "vanilla_activity_sound_selected", () -> "sound="
				+ info.getReturnValue().orElse(null));
	}
	@Inject(method = "wasHurtBy", at = @At("HEAD"), cancellable = true)
	private static void pmb$ignoreNonSurvivalRetaliation(ServerLevel level, Piglin piglin, LivingEntity attacker,
			CallbackInfo info) {
		if (!PmbFactionAi.isCombatTargetable(attacker)) {
			info.cancel();
		} else {
			PmbFactionAi.onPiglinDirectHurt(piglin, attacker);
		}
	}

	@Inject(method = "isNearZombified", at = @At("HEAD"), cancellable = true)
	private static void pmb$allowFactionCombatWithZombified(Piglin piglin,
			CallbackInfoReturnable<Boolean> info) {
		if (PmbFactionAi.shouldFightNearestZombified(piglin)) {
			info.setReturnValue(false);
		}
	}

	@Inject(method = "updateActivity", at = @At("HEAD"))
	private static void pmb$resolveFactionAttackAvoidConflict(Piglin piglin, CallbackInfo info) {
		PmbFactionAi.prepareBrainActivityUpdate(piglin);
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
