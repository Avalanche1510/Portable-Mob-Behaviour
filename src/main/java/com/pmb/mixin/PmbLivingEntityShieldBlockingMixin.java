package com.pmb.mixin;

import com.pmb.ai.PmbAiHolder;
import com.pmb.ai.PmbShieldAiData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlocksAttacks;
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
		double effectiveAngle = angle <= configuredAngle ? 0.0D : Math.PI;
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
			disableNonPlayerShieldIfNeeded(level, source, blockingItem, blocksAttacks, shieldAi);
		}
	}

	private void hurtNonPlayerBlockingItem(ItemStack blockingItem, BlocksAttacks blocksAttacks, float blockedDamage) {
		int itemDamage = blocksAttacks.itemDamage().apply(blockedDamage);
		if (itemDamage > 0) {
			blockingItem.hurtAndBreak(itemDamage, pmb$self(), getUsedItemHand().asEquipmentSlot());
		}
	}

	private void disableNonPlayerShieldIfNeeded(ServerLevel level, DamageSource source, ItemStack blockingItem,
			BlocksAttacks blocksAttacks, PmbShieldAiData shieldAi) {
		float disableSeconds = getDisableSeconds(source);
		if (disableSeconds <= 0.0F) {
			return;
		}

		if (!shieldAi.consumeShieldToughness()) {
			return;
		}

		blocksAttacks.disable(level, pmb$self(), disableSeconds, blockingItem);
		shieldAi.setUseTicks(0);
		shieldAi.setDisabledCooldown(shieldAi.axeDisableCooldownTicks());
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
	private ItemStack pmb$getPmbBlockingItem() {
		ItemStack vanillaBlockingItem = getItemBlockingWith();
		if (vanillaBlockingItem != null) {
			return vanillaBlockingItem;
		}

		PmbShieldAiData shieldAi = pmb$shieldAi();
		LivingEntity self = pmb$self();
		if (shieldAi.canUse() && shieldAi.useTicks() > 0 && self.isUsingItem()
				&& self.getUsedItemHand() == InteractionHand.OFF_HAND
				&& self.getOffhandItem().is(Items.SHIELD)) {
			return self.getOffhandItem();
		}

		return null;
	}
}
