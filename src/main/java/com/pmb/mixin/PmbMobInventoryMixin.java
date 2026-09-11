package com.pmb.mixin;

import com.pmb.ai.PmbInventory;
import com.pmb.ai.PmbInventoryHolder;
import com.pmb.ai.PmbAiHolder;
import com.pmb.ai.PmbSchedulerHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public abstract class PmbMobInventoryMixin extends LivingEntity implements PmbInventoryHolder {
	@Unique private final PmbInventory pmb$inventory = new PmbInventory();

	protected PmbMobInventoryMixin(EntityType<? extends LivingEntity> type, Level level) { super(type, level); }
	@Shadow protected abstract void pickUpItem(ServerLevel level, ItemEntity entity);
	@Shadow protected abstract Vec3i getPickupReach();

	@Override public PmbInventory pmb$getInventory() { return pmb$inventory; }

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void pmb$saveInventory(ValueOutput output, CallbackInfo info) {
		pmb$inventory.write(output);
	}

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void pmb$loadInventory(ValueInput input, CallbackInfo info) {
		pmb$inventory.read((Mob) (Object) this, input);
		((PmbSchedulerHolder) this).pmb$getSkillScheduler().readAndRollbackSwapJournal((Mob) (Object) this, input);
	}

	@Redirect(method = "aiStep", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/Mob;pickUpItem(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/item/ItemEntity;)V"))
	private void pmb$runVanillaPickupThenStore(Mob instance, ServerLevel level, ItemEntity entity) {
		// Invoke virtually first so overrides such as Piglin's barter/greed pickup keep
		// their exact ordering. Only the remainder still present is offered to PMB.
		pickUpItem(level, entity);
		pmb$storePickupRemainder(level, entity);
	}

	@Inject(method = "aiStep", at = @At("TAIL"))
	private void pmb$storeVanillaRejectedPickups(CallbackInfo info) {
		Mob mob = (Mob) (Object) this;
		if (!(level() instanceof ServerLevel level) || pmb$inventory.slots() <= 0 || !mob.canPickUpLoot()
				|| !mob.isAlive() || !level.getGameRules().get(GameRules.MOB_GRIEFING)) return;
		Vec3i reach = getPickupReach();
		for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class,
				getBoundingBox().inflate(reach.getX(), reach.getY(), reach.getZ()))) {
			if (!entity.hasPickUpDelay()) pmb$storePickupRemainder(level, entity);
		}
	}

	@Unique
	private void pmb$storePickupRemainder(ServerLevel level, ItemEntity entity) {
		Mob mob = (Mob) (Object) this;
		if (entity.isRemoved() || pmb$inventory.slots() <= 0) return;
		ItemStack ground = entity.getItem();
		if (ground.isEmpty()) return;
		ItemStack remainder = pmb$inventory.add(ground);
		int taken = ground.getCount() - remainder.getCount();
		if (taken <= 0) return;
		mob.onItemPickup(entity);
		mob.take(entity, taken);
		if (remainder.isEmpty()) entity.discard(); else entity.setItem(remainder);
	}

	@Inject(method = "dropCustomDeathLoot", at = @At("TAIL"))
	private void pmb$dropInventory(ServerLevel level, net.minecraft.world.damagesource.DamageSource source,
			boolean killedByPlayer, CallbackInfo info) {
		pmb$inventory.dropOnDeath(level, (Mob) (Object) this,
				level.getGameRules().get(GameRules.MOB_DROPS));
	}

	@Inject(method = "convertTo(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/entity/ConversionParams;Lnet/minecraft/world/entity/EntitySpawnReason;Lnet/minecraft/world/entity/ConversionParams$AfterConversion;)Lnet/minecraft/world/entity/Mob;",
			at = @At("RETURN"))
	private <T extends Mob> void pmb$copyInventoryOnConversion(EntityType<T> type, ConversionParams params,
			EntitySpawnReason reason, ConversionParams.AfterConversion<T> after,
			CallbackInfoReturnable<T> info) {
		T converted = info.getReturnValue();
		if (converted instanceof PmbInventoryHolder holder) pmb$inventory.copyTo(holder.pmb$getInventory());
		if (converted instanceof PmbAiHolder holder) {
			Mob source = (Mob) (Object) this;
			((PmbAiHolder) this).pmb$getAiData().copyTo(holder.pmb$getAiData(),
					source.level().getGameTime(), source.registryAccess());
		}
	}
}
