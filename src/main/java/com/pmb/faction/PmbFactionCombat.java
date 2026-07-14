package com.pmb.faction;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

public final class PmbFactionCombat {
	private PmbFactionCombat() {
	}

	public static LivingEntity responsibleAttacker(DamageSource source) {
		Entity entity = source.getEntity();
		if (entity instanceof LivingEntity living) {
			return living;
		}
		return source.getDirectEntity() instanceof LivingEntity living ? living : null;
	}

	public static boolean blocksDamage(ServerLevel level, LivingEntity victim, DamageSource source) {
		LivingEntity attacker = responsibleAttacker(source);
		if (attacker == null || attacker == victim) {
			return false;
		}
		PmbFactionSavedData data = PmbFactionSavedData.get(level.getServer());
		var factionId = PmbFactionResolver.factionOf(victim);
		PmbFactionDefinition definition = factionId == null ? null : data.get(factionId);
		if (definition != null && !definition.rules().overrideTeamRules() && victim.getTeam() != null
				&& victim.getTeam() == attacker.getTeam()) {
			return false;
		}
		return definition != null && !definition.rules().allowFriendlyFire()
				&& PmbFactionResolver.isMemberOrAllied(data, victim, attacker);
	}

	public static boolean overridePlayerHarm(Player attacker, Player target, boolean vanillaResult) {
		if (!(attacker.level() instanceof ServerLevel level) || attacker.getTeam() == null
				|| attacker.getTeam() != target.getTeam()) {
			return vanillaResult;
		}
		PmbFactionSavedData data = PmbFactionSavedData.get(level.getServer());
		var attackerFaction = PmbFactionResolver.factionOf(attacker);
		var targetFaction = PmbFactionResolver.factionOf(target);
		PmbFactionDefinition attackerDefinition = attackerFaction == null ? null : data.get(attackerFaction);
		PmbFactionDefinition targetDefinition = targetFaction == null ? null : data.get(targetFaction);
		boolean overrides = attackerDefinition != null && attackerDefinition.rules().overrideTeamRules()
				|| targetDefinition != null && targetDefinition.rules().overrideTeamRules();
		if (!overrides) {
			return vanillaResult;
		}
		if (targetDefinition != null && !targetDefinition.rules().allowFriendlyFire()
				&& PmbFactionResolver.isMemberOrAllied(data, target, attacker)) {
			return false;
		}
		return true;
	}

	public static void afterDamage(ServerLevel level, LivingEntity victim, DamageSource source) {
		LivingEntity attacker = responsibleAttacker(source);
		if (attacker == null || attacker == victim) {
			return;
		}
		PmbFactionSavedData data = PmbFactionSavedData.get(level.getServer());
		for (Entity entity : level.getAllEntities()) {
			if (!(entity instanceof Mob responder) || responder == victim || responder == attacker) {
				continue;
			}
			var factionId = PmbFactionResolver.factionOf(responder);
			PmbFactionDefinition definition = factionId == null ? null : data.get(factionId);
			if (definition == null || !definition.rules().groupRevenge()
					|| !PmbFactionResolver.isMemberOrAllied(data, responder, victim)
					|| PmbFactionResolver.isMemberOrAllied(data, responder, attacker)) {
				continue;
			}
			double range = Math.max(0.0D, responder.getAttributeValue(Attributes.FOLLOW_RANGE));
			if (responder.distanceToSqr(attacker) <= range * range) {
				PmbFactionAi.authorizeRevenge(responder, attacker);
				if (!responder.canAttack(attacker)) {
					((PmbFactionMobState) responder).pmb$setFactionCombatTarget(null);
					((PmbFactionMobState) responder).pmb$setFactionGroupRevenge(false);
				}
			}
		}
	}
}
