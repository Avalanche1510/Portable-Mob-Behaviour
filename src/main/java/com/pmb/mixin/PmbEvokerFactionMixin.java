package com.pmb.mixin;

import com.pmb.faction.PmbEvokerFactionApproachGoal;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.illager.Evoker;
import net.minecraft.world.entity.monster.illager.SpellcasterIllager;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Evoker.class)
public abstract class PmbEvokerFactionMixin extends SpellcasterIllager {
	protected PmbEvokerFactionMixin(EntityType<? extends SpellcasterIllager> type, Level level) {
		super(type, level);
	}

	@Inject(method = "registerGoals", at = @At("TAIL"))
	private void pmb$addFactionApproachGoal(CallbackInfo info) {
		goalSelector.addGoal(7, new PmbEvokerFactionApproachGoal((Evoker) (Object) this));
	}
}
