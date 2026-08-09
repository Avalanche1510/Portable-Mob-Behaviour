package com.pmb.mixin;

import com.pmb.faction.PmbFactionAi;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.hoglin.HoglinAi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HoglinAi.class)
public class PmbHoglinAiFactionMixin {
	@Inject(method = "wasHurtBy", at = @At("HEAD"), cancellable = true)
	private static void pmb$ignoreNonSurvivalRetaliation(ServerLevel level, Hoglin hoglin,
			LivingEntity attacker, CallbackInfo info) {
		if (!PmbFactionAi.isCombatTargetable(attacker)) {
			info.cancel();
		}
	}
}
