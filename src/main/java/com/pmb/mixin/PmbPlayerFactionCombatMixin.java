package com.pmb.mixin;

import com.pmb.faction.PmbFactionCombat;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public class PmbPlayerFactionCombatMixin {
	@Inject(method = "canHarmPlayer", at = @At("RETURN"), cancellable = true)
	private void pmb$overrideTeamFriendlyFire(Player target, CallbackInfoReturnable<Boolean> info) {
		info.setReturnValue(PmbFactionCombat.overridePlayerHarm((Player) (Object) this, target,
				info.getReturnValue()));
	}
}
