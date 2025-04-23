package dev.neuxs.sound_controller.mixins;

import com.badlogic.gdx.math.Vector2;
import dev.neuxs.sound_controller.Mod;
import dev.neuxs.sound_controller.ui.SoundControllerMenu;
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
                GameState.switchToGameState(new SoundControllerMenu(GameState.currentGameState));
            }
        };
        soundToggleButton.setSize(buttonTextDim.x + 10f, 50f);
        soundToggleButton.setPosition(5f, 5f);
        this.stage.addActor(soundToggleButton);
    }
}
