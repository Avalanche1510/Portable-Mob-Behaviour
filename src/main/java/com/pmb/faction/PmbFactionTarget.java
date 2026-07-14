package com.pmb.faction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

public record PmbFactionTarget(
		List<String> factions,
		List<String> teams,
		List<String> types,
		List<String> tags,
		List<String> reputation,
		List<String> roles) {
	public static final Codec<PmbFactionTarget> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.listOf().optionalFieldOf("factions", List.of()).forGetter(PmbFactionTarget::factions),
			Codec.STRING.listOf().optionalFieldOf("teams", List.of()).forGetter(PmbFactionTarget::teams),
			Codec.STRING.listOf().optionalFieldOf("types", List.of()).forGetter(PmbFactionTarget::types),
			Codec.STRING.listOf().optionalFieldOf("tags", List.of()).forGetter(PmbFactionTarget::tags),
			Codec.STRING.listOf().optionalFieldOf("reputation", List.of()).forGetter(PmbFactionTarget::reputation),
			Codec.STRING.listOf().optionalFieldOf("roles", List.of()).forGetter(PmbFactionTarget::roles)
	).apply(instance, PmbFactionTarget::new));

	public PmbFactionTarget {
		factions = List.copyOf(factions);
		teams = List.copyOf(teams);
		types = List.copyOf(types);
		tags = List.copyOf(tags);
		reputation = List.copyOf(reputation);
		roles = List.copyOf(roles);
	}

	public boolean isEmpty() {
		return factions.isEmpty() && teams.isEmpty() && types.isEmpty() && tags.isEmpty()
				&& reputation.isEmpty() && roles.isEmpty();
	}
}
