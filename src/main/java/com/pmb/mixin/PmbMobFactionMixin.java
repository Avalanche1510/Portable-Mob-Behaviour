package com.pmb.mixin;

import com.pmb.faction.PmbFactionAi;
import com.pmb.faction.PmbFactionMobState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public class PmbMobFactionMixin implements PmbFactionMobState {
	@Unique
	private LivingEntity pmb$factionCombatTarget;
	@Unique
	private LivingEntity pmb$factionAvoidTarget;
	@Unique
	private int pmb$factionHurtTimestamp = -1;
	@Unique
	private boolean pmb$factionAvoiding;
	@Unique
	private boolean pmb$factionGroupRevenge;

	@Override
	public LivingEntity pmb$getFactionCombatTarget() {
		return pmb$factionCombatTarget;
	}

	@Override
	public void pmb$setFactionCombatTarget(LivingEntity target) {
		pmb$factionCombatTarget = target;
	}

	@Override
	public LivingEntity pmb$getFactionAvoidTarget() {
		return pmb$factionAvoidTarget;
	}

	@Override
	public void pmb$setFactionAvoidTarget(LivingEntity target) {
		pmb$factionAvoidTarget = target;
	}

	@Override
	public int pmb$getFactionHurtTimestamp() {
		return pmb$factionHurtTimestamp;
	}

	@Override
	public void pmb$setFactionHurtTimestamp(int timestamp) {
		pmb$factionHurtTimestamp = timestamp;
	}

	@Override
	public boolean pmb$wasFactionAvoiding() {
		return pmb$factionAvoiding;
	}

	@Override
	public void pmb$setFactionAvoiding(boolean avoiding) {
		pmb$factionAvoiding = avoiding;
	}

	@Override
	public boolean pmb$isFactionGroupRevenge() {
		return pmb$factionGroupRevenge;
	}

	@Override
	public void pmb$setFactionGroupRevenge(boolean groupRevenge) {
		pmb$factionGroupRevenge = groupRevenge;
	}

	@Inject(method = "serverAiStep", at = @At("HEAD"))
	private void pmb$tickFactionAi(CallbackInfo info) {
		Mob mob = (Mob) (Object) this;
		PmbFactionAi.tick(mob, (ServerLevel) mob.level());
	}

	@Inject(method = "serverAiStep", at = @At(value = "FIELD",
			target = "Lnet/minecraft/world/entity/Mob;goalSelector:Lnet/minecraft/world/entity/ai/goal/GoalSelector;",
			opcode = Opcodes.GETFIELD))
	private void pmb$restoreFactionTargetBeforeGoals(CallbackInfo info) {
		/*
		 * Goal-based mobs run targetSelector before goalSelector. A vanilla target
		 * goal may clear a PMB target which lies outside that goal's own acquisition
		 * distance, preventing the attack/movement goals from seeing the faction
		 * target at all. Restore it between both selectors so FOLLOW_RANGE remains the
		 * authoritative faction range without replacing the mob's combat goals.
		 */
		PmbFactionAi.enforceAfterVanillaAi((Mob) (Object) this);
	}

	@Inject(method = "serverAiStep", at = @At("TAIL"))
	private void pmb$restoreFactionAiAfterVanilla(CallbackInfo info) {
		PmbFactionAi.enforceAfterVanillaAi((Mob) (Object) this);
	}

	@Inject(method = "canAttack", at = @At("RETURN"), cancellable = true)
	private void pmb$applyFactionAttackPermission(LivingEntity target, CallbackInfoReturnable<Boolean> info) {
		if (info.getReturnValue() && !PmbFactionAi.mayAttack((Mob) (Object) this, target)) {
			info.setReturnValue(false);
		}
	}
}
