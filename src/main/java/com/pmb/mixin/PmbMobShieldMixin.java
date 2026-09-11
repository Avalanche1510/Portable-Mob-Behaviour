package com.pmb.mixin;

import com.pmb.PortableMobBehaviour;
import com.pmb.ai.PmbAiHolder;
import com.pmb.ai.PmbShieldAiData;
import com.pmb.ai.PmbShieldVulnerableHolder;
import com.pmb.ai.PmbSkillHooks;
import com.pmb.ai.PmbSkillItemAccess;
import com.pmb.ai.PmbSkillScheduler;
import com.pmb.ai.PmbSkillTiming;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public abstract class PmbMobShieldMixin extends LivingEntity implements PmbSkillHooks.Shield {
	@Unique private PmbSkillItemAccess.ActionBinding pmb$shieldLease;
	@Unique private InteractionHand pmb$shieldHand;
	private static final String GUARD_VILLAGERS_NAMESPACE = "guardvillagers";
	private static final Identifier SHIELD_SPEED_REDUCTION_ID = PortableMobBehaviour.id("shield_speed_reduction");

	protected PmbMobShieldMixin(EntityType<? extends LivingEntity> entityType, Level level) {
		super(entityType, level);
	}

	@Inject(method = "doHurtTarget", at = @At("HEAD"), cancellable = true)
	private void pmb$preventAttackingWhileShieldVulnerable(ServerLevel level, Entity target,
			CallbackInfoReturnable<Boolean> info) {
		PmbShieldAiData shieldAi = ((PmbAiHolder) this).pmb$getAiData().shield();
		if (shieldAi.isVulnerable()) {
			pmb$stopForShieldVulnerability((Mob) (Object) this, shieldAi);
			info.setReturnValue(false);
		}
	}

	@Inject(method = "serverAiStep", at = @At("TAIL"))
	private void pmb$softStopAiWhileShieldVulnerable(CallbackInfo info) {
		PmbShieldAiData shieldAi = ((PmbAiHolder) this).pmb$getAiData().shield();
		if (shieldAi.isVulnerable()) {
			pmb$stopForShieldVulnerability((Mob) (Object) this, shieldAi);
		}
	}

	@Override
	public void pmb$tickShieldSkill() {
		if (level().isClientSide()) {
			return;
		}

		Mob mob = (Mob) (Object) this;
		PmbShieldAiData shieldAi = ((PmbAiHolder) this).pmb$getAiData().shield();
		if (!isAlive()) {
			stopPmbShield(shieldAi);
			((PmbShieldVulnerableHolder) this).pmb$setSyncedShieldVulnerableTicks(0);
			return;
		}
		if (isGuardVillagersEntity(mob)) {
			removePmbShieldSpeedModifier();
			return;
		}

		shieldAi.tickCooldowns();
		if (!shieldAi.isConfigured()) {
			((PmbShieldVulnerableHolder) this).pmb$setSyncedShieldVulnerableTicks(0);
			removePmbShieldSpeedModifier();
			return;
		}

		if (shieldAi.isVulnerable()) {
			pmb$tickShieldVulnerability(mob, shieldAi);
			return;
		}

		((PmbShieldVulnerableHolder) this).pmb$setSyncedShieldVulnerableTicks(0);

		if (!shieldAi.canUse() || mob.isNoAi() || (pmb$shieldLease == null && PmbSkillItemAccess.resolvePreferred(mob, shieldAi.fetchSource(), List.of(InteractionHand.OFF_HAND),
				stack -> stack.is(Items.SHIELD), shieldAi.preferredHand(), "shield") == null)) {
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
		PmbSkillScheduler scheduler = PmbSkillScheduler.of(mob);
		if (scheduler.hasBinding("shield")) {
			if (scheduler.release(mob, "shield")) {
				pmb$shieldLease = null;
				pmb$shieldHand = null;
			}
			if (scheduler.hasBinding("shield")) return;
		}

		if (isUsingItem() && pmb$shieldHand != null && getUsedItemHand() == pmb$shieldHand) {
			stopUsingItem();
			shieldAi.resetCooldown(getRandom());
			removePmbShieldSpeedModifier();
			return;
		}

		if (!shouldPmbRaiseShield(shieldAi, target) || shieldAi.cooldown() > 0) {
			return;
		}
		PmbSkillItemAccess.Resolved item = PmbSkillItemAccess.resolvePreferred(mob,
				shieldAi.fetchSource(), List.of(InteractionHand.OFF_HAND),
				stack -> stack.is(Items.SHIELD), shieldAi.preferredHand(), "shield");
		if (item == null) return;
		PmbSkillScheduler.of(mob).offer("shield", PmbSkillScheduler.Category.OFF, 10,
				() -> {
					shieldAi.resetCooldown(getRandom());
					return PmbSkillTiming.passesChance(getRandom(), shieldAi.chance());
				}, () -> {
					if (!shieldAi.canUse() || mob.isNoAi() || shieldAi.isVulnerable()
							|| mob.getTarget() != target || !target.isAlive()
							|| !shouldPmbRaiseShield(shieldAi, target)) return;
					pmb$shieldLease = PmbSkillItemAccess.acquire(mob, item);
					if (pmb$shieldLease == null) return;
					if (!PmbSkillScheduler.of(mob).bind("shield", pmb$shieldLease)) { pmb$shieldLease = null; return;
					}
					pmb$shieldHand = pmb$shieldLease.hand();
					shieldAi.setUseTicks(randomPmbShieldDuration(shieldAi));
					startUsingItem(pmb$shieldHand);
					updatePmbShieldSpeedModifier(shieldAi);
				}, item.resources(PmbSkillScheduler.Resource.USE_ITEM));
	}

	@Override
	public void pmb$cancelShieldSkill() { stopPmbShield(((PmbAiHolder) this).pmb$getAiData().shield()); }

	@Override
	public void pmb$claimShieldResources(PmbSkillScheduler scheduler) {
		if (((PmbAiHolder) this).pmb$getAiData().shield().useTicks() > 0)
			scheduler.claim("shield", pmb$shieldHand == InteractionHand.MAIN_HAND
					? PmbSkillScheduler.Resource.MAIN_HAND : PmbSkillScheduler.Resource.OFF_HAND,
					PmbSkillScheduler.Resource.USE_ITEM);
	}

	@Override
	public void pmb$authorizeShieldAction() {
		if (pmb$shieldLease != null) pmb$shieldLease.authorizeAction((Mob) (Object) this);
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
		if (pmb$shieldLease == null || !pmb$shieldLease.matches((Mob) (Object) this)) {
			stopPmbShield(shieldAi); return;
		}
		if (!isUsingItem()) {
			startUsingItem(pmb$shieldHand);
		}

		if (!isUsingItem() || pmb$shieldHand == null || getUsedItemHand() != pmb$shieldHand) {
			stopPmbShield(shieldAi);
			return;
		}

		shieldAi.setUseTicks(shieldAi.useTicks() - 1);
		if (shieldAi.useTicks() <= 0) {
			shieldAi.resetCooldown(getRandom());
			stopPmbShield(shieldAi);
			return;
		}
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
		boolean resetToughness = shieldAi.disabledCooldown() <= 0 && (shieldAi.useTicks() > 0
				|| (isUsingItem() && pmb$shieldHand != null && getUsedItemHand() == pmb$shieldHand
						&& getItemInHand(pmb$shieldHand).is(Items.SHIELD)));
		if (isUsingItem() && pmb$shieldHand != null && getUsedItemHand() == pmb$shieldHand
				&& (shieldAi.useTicks() > 0 || getItemInHand(pmb$shieldHand).is(Items.SHIELD))) {
			stopUsingItem();
		}

		shieldAi.setUseTicks(0);
		if (resetToughness) {
			shieldAi.resetShieldToughness();
		}
		removePmbShieldSpeedModifier();
		if (pmb$shieldLease != null) {
			if (PmbSkillScheduler.of((Mob) (Object) this).release((Mob) (Object) this, "shield")) {
				pmb$shieldLease = null;
				pmb$shieldHand = null;
			}
		} else {
			pmb$shieldHand = null;
		}
	}

	private void updatePmbShieldSpeedModifier(PmbShieldAiData shieldAi) {
		AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}

		float reduction = shieldAi.speedReduction();
		if (reduction <= 0.0F || !isUsingItem() || pmb$shieldHand == null
				|| getUsedItemHand() != pmb$shieldHand) {
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

	private void pmb$tickShieldVulnerability(Mob mob, PmbShieldAiData shieldAi) {
		pmb$stopForShieldVulnerability(mob, shieldAi);
		shieldAi.tickShieldVulnerability();
		((PmbShieldVulnerableHolder) this).pmb$setSyncedShieldVulnerableTicks(shieldAi.vulnerableTicks());
	}

	private void pmb$stopForShieldVulnerability(Mob mob, PmbShieldAiData shieldAi) {
		stopPmbShield(shieldAi);
		mob.getNavigation().stop();
		mob.setTarget(null);
		mob.setAggressive(false);
	}
}
