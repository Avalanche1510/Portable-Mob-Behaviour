package com.pmb.faction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record PmbFactionDefinition(
		PmbFactionRules rules,
		PmbVanillaCompatRules vanillaCompatRules,
		List<PmbFactionRelationship> relationships,
		Map<String, PmbFactionPlayerData> players) {
	public static final PmbFactionDefinition DEFAULT = new PmbFactionDefinition(PmbFactionRules.DEFAULT,
			PmbVanillaCompatRules.DEFAULT, List.of(), Map.of());

	public static final Codec<PmbFactionDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			PmbFactionRules.CODEC.optionalFieldOf("Rules", PmbFactionRules.DEFAULT).forGetter(PmbFactionDefinition::rules),
			PmbVanillaCompatRules.CODEC.optionalFieldOf("VanillaCompatRules", PmbVanillaCompatRules.DEFAULT)
					.forGetter(PmbFactionDefinition::vanillaCompatRules),
			PmbFactionRelationship.CODEC.listOf().optionalFieldOf("Relationships", List.of())
					.forGetter(PmbFactionDefinition::relationships),
			Codec.unboundedMap(Codec.STRING, PmbFactionPlayerData.CODEC).optionalFieldOf("Players", Map.of())
					.forGetter(PmbFactionDefinition::players)
	).apply(instance, PmbFactionDefinition::new));

	public PmbFactionDefinition {
		relationships = List.copyOf(relationships);
		players = Map.copyOf(new LinkedHashMap<>(players));
	}

	public PmbFactionDefinition withRules(PmbFactionRules value) {
		return new PmbFactionDefinition(value, vanillaCompatRules, relationships, players);
	}

	public PmbFactionDefinition withVanillaCompatRules(PmbVanillaCompatRules value) {
		return new PmbFactionDefinition(rules, value, relationships, players);
	}

	public PmbFactionDefinition withRelationships(List<PmbFactionRelationship> value) {
		return new PmbFactionDefinition(rules, vanillaCompatRules, value, players);
	}

	public PmbFactionDefinition withPlayers(Map<String, PmbFactionPlayerData> value) {
		return new PmbFactionDefinition(rules, vanillaCompatRules, relationships, value);
	}
}
