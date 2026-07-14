package com.pmb.mixin;

import com.pmb.faction.PmbFactionAttitude;
import com.pmb.faction.PmbFactionDefinition;
import com.pmb.faction.PmbFactionResolver;
import com.pmb.faction.PmbFactionSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class PmbEntityFactionTeamMixin {
	@Inject(method = "isAlliedTo(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
	private void pmb$overrideTeamAlliance(Entity other, CallbackInfoReturnable<Boolean> info) {
		Entity self = (Entity) (Object) this;
		if (!(self instanceof LivingEntity source) || !(other instanceof LivingEntity target)
				|| !(self.level() instanceof ServerLevel level) || source.getTeam() == null
				|| source.getTeam() != target.getTeam()) {
			return;
		}
		PmbFactionSavedData data = PmbFactionSavedData.get(level.getServer());
		var factionId = PmbFactionResolver.factionOf(source);
		PmbFactionDefinition definition = factionId == null ? null : data.get(factionId);
		if (definition != null && definition.rules().overrideTeamRules()) {
			info.setReturnValue(PmbFactionResolver.attitude(data, source, target) == PmbFactionAttitude.ALLIED);
		}
	}
}
