package dev.neuxs.sound_controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Mod {
    public static final String MOD_ID = "sound_controller";
    public static final String MOD_NAME = "Sound Controller";
    public static final String VERSION = "1.0.0";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    public static void init(String type) {
        LOGGER.info("{} v{} Initializing with {}...", MOD_NAME, VERSION, type);
        LOGGER.info("{} v{} Initialized!", MOD_NAME, VERSION);
    }
}
