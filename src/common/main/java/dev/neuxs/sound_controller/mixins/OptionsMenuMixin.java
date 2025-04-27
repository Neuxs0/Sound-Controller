package dev.neuxs.sound_controller.mixins;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import dev.neuxs.sound_controller.Mod;
import dev.neuxs.sound_controller.ui.CRImageButton;
import dev.neuxs.sound_controller.ui.SoundControllerMenu;
import finalforeach.cosmicreach.GameAssetLoader;
import finalforeach.cosmicreach.GameSingletons;
import finalforeach.cosmicreach.Threads;
import finalforeach.cosmicreach.gamestates.GameState;
import finalforeach.cosmicreach.gamestates.OptionsMenu;
import finalforeach.cosmicreach.rendering.GameTexture;
import finalforeach.cosmicreach.ui.widgets.CRSlider;
import finalforeach.cosmicreach.util.Identifier;
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

    @Unique
    private static GameTexture soundControllerTexture = null;
    @Unique
    private static TextureRegion soundControllerRegion = null;
    @Unique
    private static final Identifier SOUND_CONTROLLER_ICON_ID = Identifier.of(Mod.MOD_ID, "textures/sound_icon.png");

    @Unique
    private static void loadSoundControllerAssets() {
        if (soundControllerTexture == null) {
            soundControllerTexture = GameTexture.load(String.valueOf(SOUND_CONTROLLER_ICON_ID));

            GameTexture cachedTex = GameTexture.map.get(SOUND_CONTROLLER_ICON_ID);
            if (cachedTex != null) {
                soundControllerTexture = cachedTex;
            } else {
                soundControllerTexture = new GameTexture(SOUND_CONTROLLER_ICON_ID);
                if (GameSingletons.isClient) {
                    //noinspection deprecation
                    Threads.runOnMainThread(() -> soundControllerTexture.set(GameAssetLoader.getTexture(SOUND_CONTROLLER_ICON_ID)));
                }

                GameTexture.map.put(SOUND_CONTROLLER_ICON_ID, soundControllerTexture);
            }

            if (soundControllerTexture != null) {
                soundControllerRegion = new TextureRegion(soundControllerTexture.get());
            } else {
                Mod.LOGGER.error("Failed to load button texture: {}", SOUND_CONTROLLER_ICON_ID);
            }
        }
    }

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
        if (this.stage == null) return;

        loadSoundControllerAssets();
        if (soundControllerRegion == null) {
            System.err.println("[SoundController] Cannot create image button, texture region is null.");
            return;
        }

        CRImageButton soundToggleButton = new CRImageButton(soundControllerRegion) {
            @Override
            public void onClick() {
                super.onClick();
                GameState.switchToGameState(new SoundControllerMenu(OptionsMenuMixin.this));
            }
        };

        final float buttonWidth = 50f;
        final float buttonHeight = 50f;
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
            Button button = Mod.soundControllerButton;

            Vector2 sliderStageCoords = slider.localToStageCoordinates(tmpVec.set(0, 0));

            if (slider.getWidth() > 0 || slider.getHeight() > 0) {
                float buttonX = sliderStageCoords.x - button.getWidth() - 10f;
                float buttonY = sliderStageCoords.y + (slider.getHeight() / 2f) - (button.getHeight() / 2f);

                button.setPosition(buttonX, buttonY);
            }
        }
    }
}
