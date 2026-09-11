package com.pmb.mixin;

import com.pmb.ai.PmbSchedulerHolder;
import com.pmb.ai.PmbSkillHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Rolls back old-Mob transient skill state before cross-dimension restoreFrom serialization. */
@Mixin(Entity.class)
public class PmbEntityTeleportMixin {
	@Inject(method = "teleport", at = @At("HEAD"))
	private void pmb$cancelSkillsBeforeCrossDimension(TeleportTransition transition,
			CallbackInfoReturnable<Entity> info) {
		Entity self = (Entity) (Object) this;
		if (!(self instanceof Mob mob) || !(self.level() instanceof ServerLevel oldLevel)
				|| transition.newLevel().dimension() == oldLevel.dimension()) return;
		((PmbSkillHooks.Bow) mob).pmb$cancelBowSkill();
		((PmbSkillHooks.Shield) mob).pmb$cancelShieldSkill();
		((PmbSkillHooks.Wind) mob).pmb$cancelWindSkill();
		((PmbSkillHooks.Pearl) mob).pmb$cancelPearlSkill();
		((PmbSchedulerHolder) mob).pmb$getSkillScheduler().clearBindings(mob);
	}
}
