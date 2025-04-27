package dev.neuxs.sound_controller.mixins;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import dev.neuxs.sound_controller.Mod;
import dev.neuxs.sound_controller.ui.SoundControllerMenu;
import finalforeach.cosmicreach.gamestates.GameState;
import finalforeach.cosmicreach.gamestates.OptionsMenu;
import finalforeach.cosmicreach.ui.widgets.CRButton;
import finalforeach.cosmicreach.ui.widgets.CRSlider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SuppressWarnings("unused")
@Mixin(OptionsMenu.class)
public abstract class OptionsMenuMixin extends GameState {
    @Unique
    private final Vector2 tmpVec = new Vector2();

    @ModifyVariable(method = "create()V", at = @At(value = "STORE"), name = "soundSlider", require = 1)
    private CRSlider captureSoundSlider(CRSlider originalSlider) {
        Mod.capturedSoundSlider = originalSlider;
        return originalSlider;
    }

    @ModifyVariable(method = "create()V", at = @At(value = "STORE"), name = "table", require = 1)
    private Table captureTable(Table originalTable) {
        Mod.capturedTable = originalTable;
        return originalTable;
    }

    @Inject(method = "create()V", at = @At("RETURN"))
    private void sound_controller$createSoundToggleButton(CallbackInfo ci) {
        if (this.stage == null) {
            return;
        }

        final String buttonText = "Sound Controller";
        final float buttonWidth = 150f;
        final float buttonHeight = 50f;

        CRButton soundToggleButton = new CRButton(buttonText) {
            @Override
            public void onClick() {
                super.onClick();
                GameState.switchToGameState(new SoundControllerMenu(OptionsMenuMixin.this));
            }
        };

        soundToggleButton.setSize(buttonWidth, buttonHeight);

        Mod.soundControllerButton = soundToggleButton;
        this.stage.addActor(soundToggleButton);
    }

    @Inject(method = "render()V", at = @At("TAIL"))
    private void sound_controller$positionSoundToggleButton(CallbackInfo ci) {
        if (Mod.capturedSoundSlider != null &&
                Mod.soundControllerButton != null &&
                Mod.capturedSoundSlider.getStage() != null)
        {
            CRSlider slider = Mod.capturedSoundSlider;
            CRButton button = Mod.soundControllerButton;

            Vector2 sliderStageCoords = slider.localToStageCoordinates(tmpVec.set(0, 0));

            if (slider.getWidth() > 0 || slider.getHeight() > 0) {
                float buttonX = sliderStageCoords.x - button.getWidth() - 10f;
                float buttonY = sliderStageCoords.y + (slider.getHeight() / 2f) - (button.getHeight() / 2f);

                button.setPosition(buttonX, buttonY);
            }
        }
    }
}