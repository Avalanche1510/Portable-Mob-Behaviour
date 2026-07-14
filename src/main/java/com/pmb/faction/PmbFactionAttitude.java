package com.pmb.faction;

import java.util.Locale;

public enum PmbFactionAttitude {
	HOSTILE("hostile"),
	PASSIVELY_EVASIVE("passively_evasive"),
	ACTIVELY_EVASIVE("actively_evasive"),
	NEUTRAL("neutral"),
	ALLIED("allied");

	private final String serializedName;

	PmbFactionAttitude(String serializedName) {
		this.serializedName = serializedName;
	}

	public String serializedName() {
		return serializedName;
	}

	public static PmbFactionAttitude parse(String value) {
		String normalized = value.toLowerCase(Locale.ROOT);
		for (PmbFactionAttitude attitude : values()) {
			if (attitude.serializedName.equals(normalized)) {
				return attitude;
			}
		}
		throw new IllegalArgumentException("Unknown faction attitude: " + value);
	}

	public static PmbFactionAttitude parseOrNeutral(String value) {
		try {
			return parse(value);
		} catch (IllegalArgumentException ignored) {
			return NEUTRAL;
		}
	}
}
