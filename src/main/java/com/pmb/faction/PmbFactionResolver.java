package com.pmb.faction;

import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.Team;

import java.util.Set;

public final class PmbFactionResolver {
	private PmbFactionResolver() {
	}

	public static String factionOf(LivingEntity entity) {
		return ((PmbFactionHolder) entity).pmb$getFactionId();
	}

	public static PmbFactionAttitude attitude(PmbFactionSavedData data, LivingEntity source, LivingEntity target) {
		String sourceFaction = factionOf(source);
		PmbFactionDefinition definition = sourceFaction == null ? null : data.get(sourceFaction);
		if (definition == null) {
			return null;
		}

		String targetFaction = factionOf(target);
		if (!definition.rules().overrideTeamRules() && source.getTeam() != null
				&& source.getTeam() == target.getTeam()) {
			return PmbFactionAttitude.ALLIED;
		}
		if (sourceFaction.equals(targetFaction) && !definition.rules().allowInternalConflict()) {
			return PmbFactionAttitude.ALLIED;
		}

		PmbFactionAttitude result = definition.rules().defaultAttitude();
		for (PmbFactionRelationship relationship : definition.relationships()) {
			if (matches(data, sourceFaction, definition, relationship.target(), target)) {
				result = relationship.attitude();
			}
		}
		return result;
	}

	public static boolean isMemberOrAllied(PmbFactionSavedData data, LivingEntity source, LivingEntity target) {
		String sourceFaction = factionOf(source);
		if (sourceFaction == null || data.get(sourceFaction) == null) {
			return false;
		}
		if (sourceFaction.equals(factionOf(target))) {
			return true;
		}
		return attitude(data, source, target) == PmbFactionAttitude.ALLIED;
	}

	public static boolean matches(PmbFactionSavedData data, String sourceFaction,
			PmbFactionDefinition definition, PmbFactionTarget rule, LivingEntity target) {
		if (rule.isEmpty()) {
			return false;
		}

		String targetFaction = factionOf(target);
		if (!rule.factions().isEmpty() && (targetFaction == null
				|| rule.factions().stream().noneMatch(targetFaction::equals))) {
			return false;
		}

		Team team = target.getTeam();
		if (!rule.teams().isEmpty() && (team == null || rule.teams().stream().noneMatch(team.getName()::equals))) {
			return false;
		}

		if (!rule.types().isEmpty() && rule.types().stream().noneMatch(value -> matchesType(value, target))) {
			return false;
		}

		Set<String> entityTags = target.entityTags();
		if (!rule.tags().isEmpty() && rule.tags().stream().noneMatch(entityTags::contains)) {
			return false;
		}

		if (!rule.reputation().isEmpty()) {
			if (!(target instanceof Player)) {
				return false;
			}
			int reputation = definition.players()
					.getOrDefault(target.getUUID().toString(), PmbFactionPlayerData.DEFAULT).reputation();
			if (rule.reputation().stream().noneMatch(value -> matchesReputation(value, reputation))) {
				return false;
			}
		}

		if (!rule.roles().isEmpty()) {
			if (!(target instanceof Player)) {
				return false;
			}
			Set<String> roles = Set.copyOf(definition.players()
					.getOrDefault(target.getUUID().toString(), PmbFactionPlayerData.DEFAULT).roles());
			if (rule.roles().stream().noneMatch(roles::contains)) {
				return false;
			}
		}

		return true;
	}

	private static boolean matchesType(String value, LivingEntity target) {
		if (value.startsWith("#")) {
			Identifier id = Identifier.tryParse(value.substring(1));
			return id != null && target.getType().builtInRegistryHolder().is(TagKey.create(Registries.ENTITY_TYPE, id));
		}
		Identifier id = Identifier.tryParse(value);
		return id != null && id.equals(BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()));
	}

	private static boolean matchesReputation(String value, int reputation) {
		try {
			return PmbReputationRange.parse(value).contains(reputation);
		} catch (IllegalArgumentException ignored) {
			return false;
		}
	}
}
