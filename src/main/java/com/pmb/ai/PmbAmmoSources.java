package com.pmb.ai;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Ordered, public one-based locations from which bow AI may read ammunition. */
public final class PmbAmmoSources {
	public static final String FIELD = "AmmoSource";

	private boolean explicit;
	private final List<PmbActivationSources.Source> sources = new ArrayList<>();

	public boolean isExplicit() { return explicit; }
	public List<PmbActivationSources.Source> sources() { return Collections.unmodifiableList(sources); }

	public void read(ValueInput input) {
		sources.clear();
		explicit = input.contains(FIELD);
		Optional<ValueInput.TypedInputList<String>> values = input.list(FIELD, Codec.STRING);
		if (values.isEmpty()) return;
		for (String value : values.get()) {
			PmbActivationSources.Source parsed = parse(value);
			if (parsed != null) sources.add(parsed);
		}
	}

	public void write(ValueOutput output) {
		if (!explicit) return;
		ValueOutput.TypedOutputList<String> values = output.list(FIELD, Codec.STRING);
		for (PmbActivationSources.Source source : sources) values.add(format(source));
	}

	public void clear() {
		explicit = false;
		sources.clear();
	}

	private static PmbActivationSources.Source parse(String raw) {
		String value = raw.trim().toLowerCase();
		if (value.equals("mainhand")) return new PmbActivationSources.Source(PmbActivationSources.Kind.MAIN_HAND, 0, 0);
		if (value.equals("offhand")) return new PmbActivationSources.Source(PmbActivationSources.Kind.OFF_HAND, 0, 0);
		if (value.equals("inventory")) return new PmbActivationSources.Source(
				PmbActivationSources.Kind.INVENTORY, 1, PmbInventory.MAX_SLOTS);
		if (!value.startsWith("inventory:")) return null;
		String range = value.substring("inventory:".length());
		try {
			int separator = range.indexOf("..");
			int first = separator < 0 ? Integer.parseInt(range) : Integer.parseInt(range.substring(0, separator));
			int last = separator < 0 ? first : Integer.parseInt(range.substring(separator + 2));
			if (first < 1 || last > PmbInventory.MAX_SLOTS || first > last) return null;
			return new PmbActivationSources.Source(PmbActivationSources.Kind.INVENTORY, first, last);
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

	private static String format(PmbActivationSources.Source source) {
		return switch (source.kind()) {
			case MAIN_HAND -> "mainhand";
			case OFF_HAND -> "offhand";
			case INVENTORY -> source.firstSlot() == 1 && source.lastSlot() == PmbInventory.MAX_SLOTS ? "inventory"
					: source.firstSlot() == source.lastSlot() ? "inventory:" + source.firstSlot()
					: "inventory:" + source.firstSlot() + ".." + source.lastSlot();
		};
	}
}
