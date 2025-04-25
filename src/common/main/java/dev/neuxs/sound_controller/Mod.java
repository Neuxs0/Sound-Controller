package dev.neuxs.sound_controller;

import dev.neuxs.sound_controller.settings.SettingsManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Mod {
    public static final Logger LOGGER = LoggerFactory.getLogger("Sound Controller");

    public static void init() {
        SettingsManager.initialize();
    }
}
