package dev.neuxs.sound_controller;

import dev.crmodders.cosmicquilt.api.entrypoint.ModInitializer;
import org.quiltmc.loader.api.ModContainer;

@SuppressWarnings("unused")
public class QuiltMod implements ModInitializer {
	@Override
	public void onInitialize(ModContainer mod) {
		Mod.init();
	}
}
