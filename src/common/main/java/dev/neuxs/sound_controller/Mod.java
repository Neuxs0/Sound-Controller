package dev.neuxs.sound_controller;

import com.badlogic.gdx.scenes.scene2d.ui.Table;
import dev.neuxs.sound_controller.settings.SettingsManager;
import dev.neuxs.sound_controller.ui.CRImageButton;
import finalforeach.cosmicreach.ui.widgets.CRSlider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Mod {
    public static final String MOD_NAME = "Sound Controller";
    public static final String MOD_ID = "sound_controller";
    public static final String VERSION = "1.1.0";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);
    public static CRSlider capturedSoundSlider = null;
    public static Table capturedTable = null;
    public static CRImageButton soundControllerButton = null;

    public static void init() {
        LOGGER.info("{} v{} Initializing...", MOD_NAME, VERSION);
        SettingsManager.initialize();
        LOGGER.info("{} v{} Initialized!", MOD_NAME, VERSION);
    }
}
