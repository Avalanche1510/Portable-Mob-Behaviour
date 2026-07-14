package com.pmb.faction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record PmbFactionRelationship(PmbFactionAttitude attitude, PmbFactionTarget target) {
	public static final Codec<PmbFactionRelationship> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.xmap(PmbFactionAttitude::parseOrNeutral, PmbFactionAttitude::serializedName)
					.fieldOf("attitude").forGetter(PmbFactionRelationship::attitude),
			PmbFactionTarget.CODEC.fieldOf("target").forGetter(PmbFactionRelationship::target)
	).apply(instance, PmbFactionRelationship::new));
}
