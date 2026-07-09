package com.pmb.mixin;

import com.pmb.ai.PmbAiData;
import com.pmb.ai.PmbAiHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class PmbLivingEntityAiMixin implements PmbAiHolder {
	@Unique
	private final PmbAiData pmb$aiData = new PmbAiData();

	@Override
	public PmbAiData pmb$getAiData() {
		return pmb$aiData;
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
