package com.pmb.ai;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Ordered, public one-based locations from which a skill may obtain its core item. */
public final class PmbActivationSources {
	public static final String FIELD = "FetchSource";
	public static final String LEGACY_FIELD = "activationSources";
	public enum Kind { MAIN_HAND, OFF_HAND, INVENTORY }

	public record Source(Kind kind, int firstSlot, int lastSlot) {}

	private boolean explicit;
	private final List<Source> sources = new ArrayList<>();

	public boolean isExplicit() { return explicit; }
	public List<Source> sources() { return Collections.unmodifiableList(sources); }

	public void read(ValueInput input) {
		sources.clear();
		boolean hasCanonical = input.contains(FIELD);
		Optional<ValueInput.TypedInputList<String>> values = input.list(hasCanonical ? FIELD : LEGACY_FIELD, Codec.STRING);
		// A present canonical field with the wrong list/element type is intentionally
		// different from an omitted field: it disables activation instead of restoring
		// defaults. Legacy data is read only when the canonical field is absent.
		explicit = hasCanonical || input.contains(LEGACY_FIELD);
		if (values.isEmpty()) return;
		for (String value : values.get()) {
			Source parsed = parse(value);
			if (parsed != null) sources.add(parsed);
		}
	}

	public void write(ValueOutput output) {
		if (!explicit) return;
		ValueOutput.TypedOutputList<String> values = output.list(FIELD, Codec.STRING);
		for (Source source : sources) values.add(format(source));
	}

	public void clear() {
		explicit = false;
		sources.clear();
	}

	private static Source parse(String raw) {
		String value = raw.trim().toLowerCase();
		if (value.equals("mainhand")) return new Source(Kind.MAIN_HAND, 0, 0);
		if (value.equals("offhand")) return new Source(Kind.OFF_HAND, 0, 0);
		if (value.equals("inventory")) return new Source(Kind.INVENTORY, 1, PmbInventory.MAX_SLOTS);
		if (!value.startsWith("inventory:")) return null;
		String range = value.substring("inventory:".length());
		try {
			int separator = range.indexOf("..");
			int first = separator < 0 ? Integer.parseInt(range) : Integer.parseInt(range.substring(0, separator));
			int last = separator < 0 ? first : Integer.parseInt(range.substring(separator + 2));
			if (first < 1 || last > PmbInventory.MAX_SLOTS || first > last) return null;
			return new Source(Kind.INVENTORY, first, last);
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

	private static String format(Source source) {
		return switch (source.kind()) {
			case MAIN_HAND -> "mainhand";
			case OFF_HAND -> "offhand";
			case INVENTORY -> source.firstSlot() == 1 && source.lastSlot() == PmbInventory.MAX_SLOTS ? "inventory"
					: source.firstSlot() == source.lastSlot() ? "inventory:" + source.firstSlot()
					: "inventory:" + source.firstSlot() + ".." + source.lastSlot();
		};
	}
}
