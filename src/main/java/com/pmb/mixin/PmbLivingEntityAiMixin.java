package com.pmb.mixin;

import com.pmb.ai.PmbAiData;
import com.pmb.ai.PmbAiHolder;
import com.pmb.ai.PmbShieldVulnerableHolder;
import com.pmb.faction.PmbFactionHolder;
import com.pmb.faction.PmbFactionSavedData;
import net.minecraft.server.level.ServerLevel;
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
public class PmbLivingEntityAiMixin implements PmbAiHolder, PmbShieldVulnerableHolder, PmbFactionHolder {
	@Unique
	private static final EntityDataAccessor<Integer> PMB_SHIELD_VULNERABLE_TICKS = SynchedEntityData
			.defineId(LivingEntity.class, EntityDataSerializers.INT);

	@Unique
	private final PmbAiData pmb$aiData = new PmbAiData();

	@Unique
	private String pmb$factionId;

	@Override
	public PmbAiData pmb$getAiData() {
		return pmb$aiData;
	}

	@Override
	public String pmb$getFactionId() {
		return pmb$factionId;
	}

	@Override
	public void pmb$setFactionId(String factionId) {
		pmb$factionId = factionId;
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
		if (pmb$factionId != null) {
			output.child("PmbFaction").putString("FactionName", pmb$factionId);
		}
	}

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void pmb$readAiData(ValueInput input, CallbackInfo info) {
		pmb$aiData.read(input);
		pmb$factionId = input.child("PmbFaction")
				.flatMap(value -> value.getString("FactionName"))
				.filter(value -> !value.isBlank())
				.orElse(null);
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void pmb$clearMissingFaction(CallbackInfo info) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (pmb$factionId != null && self.level() instanceof ServerLevel level
				&& !PmbFactionSavedData.get(level.getServer()).contains(pmb$factionId)) {
			pmb$factionId = null;
		}
	}
}
