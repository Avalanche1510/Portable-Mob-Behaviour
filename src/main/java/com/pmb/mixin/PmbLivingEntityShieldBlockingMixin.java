package com.pmb.mixin;

import com.pmb.ai.PmbAiHolder;
import com.pmb.ai.PmbCriticalAttackHolder;
import com.pmb.ai.PmbShieldAiData;
import com.pmb.ai.PmbShieldVulnerableHolder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class PmbLivingEntityShieldBlockingMixin {
	private static final String GUARD_VILLAGERS_NAMESPACE = "guardvillagers";
	private static final double SHIELD_BREAK_KNOCKBACK_STRENGTH = 0.6D;

	@Shadow
	public abstract ItemStack getItemBlockingWith();

	@Shadow
	public abstract InteractionHand getUsedItemHand();

	@Shadow
	protected abstract void blockUsingItem(ServerLevel level, LivingEntity attacker);

	@Redirect(method = "applyItemBlocking", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getItemBlockingWith()Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack pmb$getImmediateBlockingItem(LivingEntity entity) {
		return pmb$getPmbBlockingItem();
	}

	@Redirect(method = "applyItemBlocking", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/component/BlocksAttacks;resolveBlockedDamage(Lnet/minecraft/world/damagesource/DamageSource;FD)F"))
	private float pmb$resolveBlockedDamage(BlocksAttacks blocksAttacks, DamageSource source, float amount, double angle) {
		if (pmb$isGuardVillagersEntity()) {
			return blocksAttacks.resolveBlockedDamage(source, amount, angle);
		}

		PmbShieldAiData shieldAi = pmb$shieldAi();
		if (!shieldAi.isConfigured() || !shieldAi.isEnabled()) {
			return blocksAttacks.resolveBlockedDamage(source, amount, angle);
		}

		double configuredAngle = Math.toRadians(shieldAi.blockingAngle());
		double stableAngle = pmb$stableShieldBlockingAngle(source, angle);
		double effectiveAngle = stableAngle <= configuredAngle ? 0.0D : Math.PI;
		return blocksAttacks.resolveBlockedDamage(source, amount, effectiveAngle);
	}

	@Redirect(method = "applyItemBlocking", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;blockUsingItem(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)V"))
	private void pmb$applyShieldRecoilOnce(LivingEntity defender, ServerLevel level, LivingEntity attacker,
			ServerLevel methodLevel, DamageSource source, float amount) {
		PmbShieldAiData shieldAi = pmb$shieldAi();
		if (pmb$isGuardVillagersEntity() || !shieldAi.isConfigured() || !shieldAi.isEnabled()
				|| pmb$canApplyShieldImpact(source)) {
			blockUsingItem(level, attacker);
		}
	}

	@Inject(method = "applyItemBlocking", at = @At("RETURN"))
	private void pmb$afterItemBlocking(ServerLevel level, DamageSource source, float amount,
			CallbackInfoReturnable<Float> info) {
		if (pmb$isGuardVillagersEntity()) {
			return;
		}

		float blockedDamage = info.getReturnValue();
		PmbShieldAiData shieldAi = pmb$shieldAi();
		if (blockedDamage <= 0.0F || !shieldAi.isConfigured() || !shieldAi.isEnabled()) {
			return;
		}
		if (!pmb$canApplyShieldImpact(source)) {
			return;
		}

		ItemStack blockingItem = pmb$getPmbBlockingItem();
		if (blockingItem == null || blockingItem.isEmpty()) {
			return;
		}

		BlocksAttacks blocksAttacks = blockingItem.get(DataComponents.BLOCKS_ATTACKS);
		if (blocksAttacks == null) {
			return;
		}

		LivingEntity self = pmb$self();
		if (!(self instanceof Player)) {
			hurtNonPlayerBlockingItem(blockingItem, blocksAttacks, blockedDamage);
			if (!disableNonPlayerShieldIfNeeded(level, source, blockingItem, blocksAttacks, shieldAi)) {
				playNonBreakingShieldHitEffect(level, source);
			}
		}
	}

	private void hurtNonPlayerBlockingItem(ItemStack blockingItem, BlocksAttacks blocksAttacks, float blockedDamage) {
		int itemDamage = blocksAttacks.itemDamage().apply(blockedDamage);
		if (itemDamage > 0) {
			blockingItem.hurtAndBreak(itemDamage, pmb$self(), getUsedItemHand().asEquipmentSlot());
		}
	}

	private boolean disableNonPlayerShieldIfNeeded(ServerLevel level, DamageSource source, ItemStack blockingItem,
			BlocksAttacks blocksAttacks, PmbShieldAiData shieldAi) {
		float disableSeconds = getDisableSeconds(source);
		if (disableSeconds <= 0.0F) {
			return false;
		}

		boolean criticalAttack = isCriticalShieldDisablingAttack(source);
		int toughnessDamage = criticalAttack ? shieldAi.critToughnessDamage() : 1;
		if (!shieldAi.consumeShieldToughness(toughnessDamage)) {
			return false;
		}

		playShieldBreakEffect(level);
		applyShieldBreakKnockback(source, shieldAi);
		blocksAttacks.disable(level, pmb$self(), disableSeconds, blockingItem);
		shieldAi.setUseTicks(0);
		shieldAi.setDisabledCooldown(shieldAi.axeDisableCooldownTicks());
		if (shieldAi.disableVulnerTicks() > 0) {
			shieldAi.startShieldVulnerability();
			((PmbShieldVulnerableHolder) this).pmb$setSyncedShieldVulnerableTicks(shieldAi.vulnerableTicks());
		}
		return true;
	}

	private void playNonBreakingShieldHitEffect(ServerLevel level, DamageSource source) {
		boolean shieldDisablingAttack = getDisableSeconds(source) > 0.0F;
		boolean criticalShieldDisablingAttack = shieldDisablingAttack && isCriticalShieldDisablingAttack(source);
		int particleCount = criticalShieldDisablingAttack ? 10 : shieldDisablingAttack ? 6 : 3;
		Vec3 position = pmb$shieldFeedbackPosition();

		if (criticalShieldDisablingAttack) {
			level.playSound(null, position.x, position.y, position.z, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR,
					SoundSource.HOSTILE, 0.8F, 1.0F);
		}

		spawnOakDoorParticles(level, position, particleCount, 0.2D, 0.25D, 0.2D, 0.035D);
	}

	private void playShieldBreakEffect(ServerLevel level) {
		Vec3 position = pmb$shieldFeedbackPosition();
		level.playSound(null, position.x, position.y, position.z, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR,
				SoundSource.HOSTILE, 1.0F, 0.9F);
		spawnOakDoorParticles(level, position, 24, 0.4D, 0.5D, 0.4D, 0.08D);
	}

	private void applyShieldBreakKnockback(DamageSource source, PmbShieldAiData shieldAi) {
		float multiplier = shieldAi.disableKbMultiplier();
		if (multiplier <= 0.0F) {
			return;
		}

		LivingEntity self = pmb$self();
		Vec3 direction = pmb$shieldBreakKnockbackDirection(source);
		if (direction.lengthSqr() < 1.0E-7D) {
			return;
		}

		self.knockback(SHIELD_BREAK_KNOCKBACK_STRENGTH * multiplier, direction.x, direction.z);
	}

	private Vec3 pmb$shieldBreakKnockbackDirection(DamageSource source) {
		LivingEntity self = pmb$self();
		Vec3 sourcePosition = pmb$stableDamageSourcePosition(source);
		if (sourcePosition != null) {
			return new Vec3(sourcePosition.x - self.getX(), 0.0D, sourcePosition.z - self.getZ());
		}

		return self.calculateViewVector(0.0F, self.getYHeadRot()).multiply(1.0D, 0.0D, 1.0D);
	}

	private Vec3 pmb$shieldFeedbackPosition() {
		LivingEntity self = pmb$self();
		Vec3 forward = self.calculateViewVector(0.0F, self.getYHeadRot()).multiply(1.0D, 0.0D, 1.0D).normalize();
		double x = self.getX() + forward.x * 0.45D;
		double y = self.getY() + self.getBbHeight() * 0.65D;
		double z = self.getZ() + forward.z * 0.45D;
		return new Vec3(x, y, z);
	}

	private void spawnOakDoorParticles(ServerLevel level, Vec3 position, int count, double xSpread, double ySpread,
			double zSpread, double speed) {
		BlockParticleOption particles = new BlockParticleOption(ParticleTypes.BLOCK,
				Blocks.OAK_DOOR.defaultBlockState());
		level.sendParticles(particles, position.x, position.y, position.z, count, xSpread, ySpread, zSpread, speed);
	}

	private float getDisableSeconds(DamageSource source) {
		if (source.getDirectEntity() instanceof LivingEntity attacker) {
			float disableSeconds = attacker.getSecondsToDisableBlocking();
			if (disableSeconds > 0.0F) {
				return disableSeconds;
			}
		}

		ItemStack weapon = source.getWeaponItem();
		if (weapon != null && !weapon.isEmpty() && weapon.typeHolder().is(ItemTags.AXES)) {
			return 1.6F;
		}

		return 0.0F;
	}

	private boolean isCriticalShieldDisablingAttack(DamageSource source) {
		return source.getDirectEntity() instanceof PmbCriticalAttackHolder criticalAttackHolder
				&& criticalAttackHolder.pmb$isCriticalAttack();
	}

	@Unique
	private LivingEntity pmb$self() {
		return (LivingEntity) (Object) this;
	}

	@Unique
	private boolean pmb$isGuardVillagersEntity() {
		Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(pmb$self().getType());
		return GUARD_VILLAGERS_NAMESPACE.equals(id.getNamespace());
	}

	@Unique
	private PmbShieldAiData pmb$shieldAi() {
		return ((PmbAiHolder) this).pmb$getAiData().shield();
	}

	@Unique
	private boolean pmb$canApplyShieldImpact(DamageSource source) {
		return pmb$self().invulnerableTime <= 10 || source.is(DamageTypeTags.BYPASSES_COOLDOWN);
	}

	@Unique
	private double pmb$stableShieldBlockingAngle(DamageSource source, double fallbackAngle) {
		LivingEntity self = pmb$self();
		Vec3 sourcePosition = pmb$stableDamageSourcePosition(source);
		if (sourcePosition == null) {
			return fallbackAngle;
		}

		Vec3 incomingDirection = new Vec3(sourcePosition.x - self.getX(), 0.0D, sourcePosition.z - self.getZ());
		if (incomingDirection.lengthSqr() < 1.0E-7D) {
			return fallbackAngle;
		}

		Vec3 shieldDirection = self.calculateViewVector(0.0F, self.getYHeadRot()).multiply(1.0D, 0.0D, 1.0D);
		if (shieldDirection.lengthSqr() < 1.0E-7D) {
			return fallbackAngle;
		}

		double dot = incomingDirection.normalize().dot(shieldDirection.normalize());
		return Math.acos(Math.max(-1.0D, Math.min(1.0D, dot)));
	}

	@Unique
	private Vec3 pmb$stableDamageSourcePosition(DamageSource source) {
		LivingEntity self = pmb$self();
		Entity directEntity = source.getDirectEntity();
		if (directEntity != null && directEntity != self) {
			return directEntity.position();
		}

		Entity causingEntity = source.getEntity();
		if (causingEntity != null && causingEntity != self) {
			return causingEntity.position();
		}

		return source.getSourcePosition();
	}

	@Unique
	private ItemStack pmb$getPmbBlockingItem() {
		ItemStack vanillaBlockingItem = getItemBlockingWith();
		if (vanillaBlockingItem != null) {
			return vanillaBlockingItem;
		}

		PmbShieldAiData shieldAi = pmb$shieldAi();
		LivingEntity self = pmb$self();
		if (shieldAi.canUse() && self.isUsingItem()
				&& self.getUsedItemHand() == InteractionHand.OFF_HAND
				&& self.getOffhandItem().is(Items.SHIELD)) {
			return self.getOffhandItem();
		}

		return null;
	}
}
