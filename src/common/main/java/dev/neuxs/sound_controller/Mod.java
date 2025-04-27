package dev.neuxs.sound_controller;

import com.badlogic.gdx.scenes.scene2d.ui.Table;
import dev.neuxs.sound_controller.settings.SettingsManager;
import finalforeach.cosmicreach.ui.widgets.CRButton;
import finalforeach.cosmicreach.ui.widgets.CRSlider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Mod {
    public static final Logger LOGGER = LoggerFactory.getLogger("Sound Controller");
    public static CRSlider capturedSoundSlider = null;
    public static Table capturedTable = null;
    public static CRButton soundControllerButton = null;

    public static void init() {
        SettingsManager.initialize();
    }
}
