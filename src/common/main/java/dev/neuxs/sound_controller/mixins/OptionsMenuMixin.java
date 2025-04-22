package dev.neuxs.sound_controller.mixins;

import com.badlogic.gdx.math.Vector2;
import dev.neuxs.sound_controller.Mod;
import finalforeach.cosmicreach.gamestates.GameState;
import finalforeach.cosmicreach.gamestates.OptionsMenu;
import finalforeach.cosmicreach.settings.SoundSettings;
import finalforeach.cosmicreach.ui.FontRenderer;
import finalforeach.cosmicreach.ui.widgets.CRButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SuppressWarnings("unused")
@Mixin(OptionsMenu.class)
public abstract class OptionsMenuMixin extends GameState {

    @Inject(method = "create", at = @At("RETURN"))
    private void sound_controller$addSoundToggleButtonToStage(CallbackInfo ci) {
        if (this.stage == null) return;

        final String buttonText = "Sound Controller";
        final Vector2 buttonTextDim = FontRenderer.getTextDimensions(this.uiViewport, buttonText, new Vector2(0f,0f));

        CRButton soundToggleButton = new CRButton(buttonText) {
            @Override
            public void onClick() {
                super.onClick();
                float currentVolume = SoundSettings.soundVolume.getValueAsFloat();
                if (currentVolume > 0) {
                    SoundSettings.soundVolume.setValue(0.0f);
                } else {
                    SoundSettings.soundVolume.setValue(0.5f);
                }
                Mod.LOGGER.info("Sound Toggle Button Clicked! Volume set to: {}", SoundSettings.soundVolume.getValueAsFloat());
            }
        };
        soundToggleButton.setSize(buttonTextDim.x + 10f, 50f);
        soundToggleButton.setPosition(5f, 5f);
        this.stage.addActor(soundToggleButton);
    }
}
