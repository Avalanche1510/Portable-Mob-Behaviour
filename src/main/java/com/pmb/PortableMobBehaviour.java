package com.pmb;

import com.pmb.network.PmbProtocolNetworking;
import com.pmb.network.PmbSkillDebugNetworking;
import com.pmb.faction.PmbFactionCommands;
import com.pmb.faction.PmbFactionSavedData;
import com.pmb.command.PmbSkillsCommands;
import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PortableMobBehaviour implements ModInitializer {
	public static final String MOD_ID = "portable-mob-behaviour";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		PmbProtocolNetworking.initialize();
		PmbSkillDebugNetworking.initialize();
		PmbFactionSavedData.initialize();
		PmbFactionCommands.initialize();
		PmbSkillsCommands.initialize();
		LOGGER.info("Portable Mob Behaviour initialized.");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
