package com.pmb.mixin;

import com.pmb.PortableMobBehaviour;
import com.pmb.ai.PmbAiHolder;
import com.pmb.ai.PmbShieldAiData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class PmbMobShieldMixin extends LivingEntity {
	private static final String GUARD_VILLAGERS_NAMESPACE = "guardvillagers";
	private static final Identifier SHIELD_SPEED_REDUCTION_ID = PortableMobBehaviour.id("shield_speed_reduction");

	protected PmbMobShieldMixin(EntityType<? extends LivingEntity> entityType, Level level) {
		super(entityType, level);
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void pmb$tickShieldAi(CallbackInfo info) {
		if (level().isClientSide()) {
			return;
		}

		Mob mob = (Mob) (Object) this;
		if (isGuardVillagersEntity(mob)) {
			removePmbShieldSpeedModifier();
			return;
		}

		PmbShieldAiData shieldAi = ((PmbAiHolder) this).pmb$getAiData().shield();
		shieldAi.tickCooldowns();
		if (!shieldAi.isConfigured()) {
			removePmbShieldSpeedModifier();
			return;
		}

		if (!shieldAi.canUse() || mob.isNoAi() || getOffhandItem().getItem() != Items.SHIELD) {
			stopPmbShield(shieldAi);
			return;
		}

		LivingEntity target = mob.getTarget();
		if (target == null || !target.isAlive()) {
			stopPmbShield(shieldAi);
			return;
		}

		if (shieldAi.useTicks() > 0) {
			continuePmbShield(shieldAi);
			return;
		}

		if (isUsingItem() && getUsedItemHand() == InteractionHand.OFF_HAND) {
			stopUsingItem();
			shieldAi.setCooldown(shieldAi.cooldownTicks());
			removePmbShieldSpeedModifier();
			return;
		}

		if (!shouldPmbRaiseShield(shieldAi, target) || shieldAi.cooldown() > 0) {
			return;
		}

		if (getRandom().nextFloat() <= shieldAi.chance()) {
			shieldAi.setUseTicks(randomPmbShieldDuration(shieldAi));
			startUsingItem(InteractionHand.OFF_HAND);
			updatePmbShieldSpeedModifier(shieldAi);
		} else {
			shieldAi.setCooldown(shieldAi.cooldownTicks());
			removePmbShieldSpeedModifier();
		}
	}

	private boolean isGuardVillagersEntity(Mob mob) {
		Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
		return GUARD_VILLAGERS_NAMESPACE.equals(id.getNamespace());
	}

	private boolean shouldPmbRaiseShield(PmbShieldAiData shieldAi, LivingEntity target) {
		float range = shieldAi.range();
		return distanceToSqr(target) <= range * range && hasLineOfSight(target);
	}

	private void continuePmbShield(PmbShieldAiData shieldAi) {
		if (!isUsingItem()) {
			startUsingItem(InteractionHand.OFF_HAND);
		}

		if (!isUsingItem() || getUsedItemHand() != InteractionHand.OFF_HAND) {
			shieldAi.setUseTicks(0);
			return;
		}

		shieldAi.setUseTicks(shieldAi.useTicks() - 1);
		updatePmbShieldSpeedModifier(shieldAi);
	}

	private int randomPmbShieldDuration(PmbShieldAiData shieldAi) {
		int min = shieldAi.minUseTicks();
		int max = shieldAi.maxUseTicks();
		if (max <= min) {
			return min;
		}

		return min + getRandom().nextInt(max - min + 1);
	}

	private void stopPmbShield(PmbShieldAiData shieldAi) {
		if (isUsingItem() && getUsedItemHand() == InteractionHand.OFF_HAND
				&& (shieldAi.useTicks() > 0 || getOffhandItem().getItem() == Items.SHIELD)) {
			stopUsingItem();
		}

		shieldAi.setUseTicks(0);
		removePmbShieldSpeedModifier();
	}

	private void updatePmbShieldSpeedModifier(PmbShieldAiData shieldAi) {
		AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}

		float reduction = shieldAi.speedReduction();
		if (reduction <= 0.0F || !isUsingItem() || getUsedItemHand() != InteractionHand.OFF_HAND) {
			speed.removeModifier(SHIELD_SPEED_REDUCTION_ID);
			return;
		}

		speed.addOrUpdateTransientModifier(new AttributeModifier(SHIELD_SPEED_REDUCTION_ID, -reduction,
				AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
	}

	private void removePmbShieldSpeedModifier() {
		AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(SHIELD_SPEED_REDUCTION_ID);
		}
	}
}
