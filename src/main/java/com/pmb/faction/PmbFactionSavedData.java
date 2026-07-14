package com.pmb.faction;

import com.mojang.serialization.Codec;
import com.pmb.PortableMobBehaviour;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

public final class PmbFactionSavedData extends SavedData {
	private static final Codec<PmbFactionSavedData> CODEC = Codec
			.unboundedMap(Codec.STRING, PmbFactionDefinition.CODEC)
			.fieldOf("Factions")
			.codec()
			.xmap(PmbFactionSavedData::new, data -> data.factions);

	public static final SavedDataType<PmbFactionSavedData> TYPE = new SavedDataType<>(
			PortableMobBehaviour.id("factions"), PmbFactionSavedData::new, CODEC, DataFixTypes.LEVEL);

	private final Map<String, PmbFactionDefinition> factions;

	public PmbFactionSavedData() {
		this.factions = new LinkedHashMap<>();
	}

	private PmbFactionSavedData(Map<String, PmbFactionDefinition> factions) {
		this.factions = new LinkedHashMap<>(factions);
	}

	public static void initialize() {
		// Forces codec and SavedDataType construction during common initialization.
	}

	public static PmbFactionSavedData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public Map<String, PmbFactionDefinition> factions() {
		return Collections.unmodifiableMap(factions);
	}

	public PmbFactionDefinition get(String id) {
		return factions.get(id);
	}

	public boolean contains(String id) {
		return factions.containsKey(id);
	}

	public boolean create(String id) {
		if (factions.putIfAbsent(id, PmbFactionDefinition.DEFAULT) != null) {
			return false;
		}
		setDirty();
		return true;
	}

	public boolean delete(String id) {
		if (factions.remove(id) == null) {
			return false;
		}
		setDirty();
		return true;
	}

	public boolean update(String id, UnaryOperator<PmbFactionDefinition> update) {
		PmbFactionDefinition current = factions.get(id);
		if (current == null) {
			return false;
		}
		factions.put(id, update.apply(current));
		setDirty();
		return true;
	}
}
