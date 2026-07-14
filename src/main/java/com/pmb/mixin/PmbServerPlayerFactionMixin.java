package com.pmb.mixin;

import com.pmb.faction.PmbFactionHolder;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public class PmbServerPlayerFactionMixin {
	@Inject(method = "restoreFrom", at = @At("TAIL"))
	private void pmb$copyFactionOnRespawn(ServerPlayer oldPlayer, boolean alive, CallbackInfo info) {
		((PmbFactionHolder) this).pmb$setFactionId(((PmbFactionHolder) oldPlayer).pmb$getFactionId());
	}
}
