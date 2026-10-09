package com.pmb.mixin;

import com.pmb.faction.PmbFactionAi;
import com.pmb.faction.PmbBrainAiDebug;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.hoglin.HoglinAi;
import net.minecraft.sounds.SoundEvent;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Optional;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HoglinAi.class)
public class PmbHoglinAiFactionMixin {
	@Inject(method = "updateActivity", at = @At("HEAD"))
	private static void pmb$coordinateIntent(Hoglin hoglin, CallbackInfo info) {
		PmbFactionAi.prepareBrainActivityUpdate(hoglin);
	}
	@Inject(method = "updateActivity", at = @At("TAIL"))
	private static void pmb$debugActivity(Hoglin hoglin, CallbackInfo info) {
		PmbBrainAiDebug.event(hoglin, "vanilla_update_activity", () -> "activity="
				+ hoglin.getBrain().getActiveNonCoreActivity().orElse(null));
	}

	@Inject(method = "wasHurtBy", at = @At("TAIL"))
	private static void pmb$debugHurt(ServerLevel level, Hoglin hoglin, LivingEntity attacker, CallbackInfo info) {
		PmbBrainAiDebug.event(hoglin, "vanilla_was_hurt_by", () -> "attacker=" + attacker.getId());
	}

	@Inject(method = "getSoundForCurrentActivity", at = @At("RETURN"))
	private static void pmb$debugActivitySound(Hoglin hoglin,
			CallbackInfoReturnable<Optional<SoundEvent>> info) {
		PmbBrainAiDebug.event(hoglin, "vanilla_activity_sound_selected", () -> "sound="
				+ info.getReturnValue().orElse(null));
	}
	@Inject(method = "wasHurtBy", at = @At("HEAD"), cancellable = true)
	private static void pmb$ignoreNonSurvivalRetaliation(ServerLevel level, Hoglin hoglin,
			LivingEntity attacker, CallbackInfo info) {
		if (!PmbFactionAi.isCombatTargetable(attacker)) {
			info.cancel();
		}
	}
}
