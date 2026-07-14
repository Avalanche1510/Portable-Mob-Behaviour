package com.pmb.faction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

public record PmbFactionPlayerData(int reputation, List<String> roles) {
	public static final PmbFactionPlayerData DEFAULT = new PmbFactionPlayerData(0, List.of());

	public static final Codec<PmbFactionPlayerData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.optionalFieldOf("reputation", 0).forGetter(PmbFactionPlayerData::reputation),
			Codec.STRING.listOf().optionalFieldOf("roles", List.of()).forGetter(PmbFactionPlayerData::roles)
	).apply(instance, PmbFactionPlayerData::new));

	public PmbFactionPlayerData {
		roles = List.copyOf(roles);
	}
}
