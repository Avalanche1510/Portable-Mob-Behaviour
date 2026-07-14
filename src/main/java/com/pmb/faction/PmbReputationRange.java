package com.pmb.faction;

public record PmbReputationRange(Integer minimum, Integer maximum) {
	public boolean contains(int value) {
		return (minimum == null || value >= minimum) && (maximum == null || value <= maximum);
	}

	public static PmbReputationRange parse(String value) {
		String text = value.trim();
		if (text.isEmpty()) {
			throw new IllegalArgumentException("Empty reputation range");
		}
		int separator = text.indexOf("..");
		if (separator < 0) {
			int exact = Integer.parseInt(text);
			return new PmbReputationRange(exact, exact);
		}
		if (text.indexOf("..", separator + 2) >= 0) {
			throw new IllegalArgumentException("Invalid reputation range: " + value);
		}
		String lower = text.substring(0, separator);
		String upper = text.substring(separator + 2);
		if (lower.isEmpty() && upper.isEmpty()) {
			throw new IllegalArgumentException("Unbounded reputation range is not useful");
		}
		Integer minimum = lower.isEmpty() ? null : Integer.valueOf(lower);
		Integer maximum = upper.isEmpty() ? null : Integer.valueOf(upper);
		if (minimum != null && maximum != null && minimum > maximum) {
			throw new IllegalArgumentException("Reputation minimum exceeds maximum: " + value);
		}
		return new PmbReputationRange(minimum, maximum);
	}
}
