package com.pmb.mixin;

import com.pmb.ai.PmbAiData;
import com.pmb.ai.PmbAiHolder;
import com.pmb.ai.PmbShieldVulnerableHolder;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class PmbLivingEntityAiMixin implements PmbAiHolder, PmbShieldVulnerableHolder {
	@Unique
	private static final EntityDataAccessor<Integer> PMB_SHIELD_VULNERABLE_TICKS = SynchedEntityData
			.defineId(LivingEntity.class, EntityDataSerializers.INT);

	@Unique
	private final PmbAiData pmb$aiData = new PmbAiData();

	@Override
	public PmbAiData pmb$getAiData() {
		return pmb$aiData;
	}

	@Override
	public boolean pmb$isShieldVulnerable() {
		return ((LivingEntity) (Object) this).getEntityData().get(PMB_SHIELD_VULNERABLE_TICKS) > 0;
	}

	@Override
	public void pmb$setSyncedShieldVulnerableTicks(int ticks) {
		((LivingEntity) (Object) this).getEntityData().set(PMB_SHIELD_VULNERABLE_TICKS, Math.max(0, ticks));
	}

	@Inject(method = "defineSynchedData", at = @At("TAIL"))
	private void pmb$defineSynchedAiData(SynchedEntityData.Builder builder, CallbackInfo info) {
		builder.define(PMB_SHIELD_VULNERABLE_TICKS, 0);
	}

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void pmb$writeAiData(ValueOutput output, CallbackInfo info) {
		pmb$aiData.write(output);
	}

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void pmb$readAiData(ValueInput input, CallbackInfo info) {
		pmb$aiData.read(input);
	}
}
