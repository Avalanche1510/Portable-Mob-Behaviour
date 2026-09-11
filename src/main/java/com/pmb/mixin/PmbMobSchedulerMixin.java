package com.pmb.mixin;

import com.pmb.ai.PmbSchedulerHolder;
import com.pmb.ai.PmbSkillScheduler;
import net.minecraft.world.entity.Mob;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public class PmbMobSchedulerMixin implements PmbSchedulerHolder {
	@Unique private final PmbSkillScheduler pmb$scheduler = new PmbSkillScheduler();
	@Override public PmbSkillScheduler pmb$getSkillScheduler() { return pmb$scheduler; }
	@Inject(method = "tick", at = @At("TAIL"))
	private void pmb$tickAllSkills(CallbackInfo info) {
		Mob mob = (Mob) (Object) this;
		if (!mob.level().isClientSide()) pmb$scheduler.tick(mob);
	}
	@Inject(method = "serverAiStep", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/ai/control/MoveControl;tick()V"))
	private void pmb$applyMovementBeforeControl(CallbackInfo info) {
		pmb$scheduler.movement().apply((Mob) (Object) this, pmb$scheduler);
	}
	@Inject(method = "dropCustomDeathLoot", at = @At("HEAD"))
	private void pmb$rollbackBeforeDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer,
			CallbackInfo info) { pmb$scheduler.clearBindings((Mob) (Object) this); }
	@Inject(method = "convertTo(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/entity/ConversionParams;Lnet/minecraft/world/entity/EntitySpawnReason;Lnet/minecraft/world/entity/ConversionParams$AfterConversion;)Lnet/minecraft/world/entity/Mob;",
			at = @At("HEAD"))
	private <T extends Mob> void pmb$rollbackBeforeConversion(EntityType<T> type, ConversionParams params,
			EntitySpawnReason reason, ConversionParams.AfterConversion<T> after,
			org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<T> info) {
		pmb$scheduler.clearBindings((Mob) (Object) this);
	}
}
