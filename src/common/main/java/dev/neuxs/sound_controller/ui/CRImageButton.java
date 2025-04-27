package dev.neuxs.sound_controller.ui;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Event;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Scaling;
import de.pottgames.tuningfork.SoundBuffer;
import dev.neuxs.sound_controller.Mod;
import finalforeach.cosmicreach.GameAssetLoader;
import finalforeach.cosmicreach.audio.SoundManager;
import finalforeach.cosmicreach.ui.GameStyles;

public class CRImageButton extends Button {

    public static SoundBuffer onHoverSound;
    public static SoundBuffer onClickSound;

    static {
        onHoverSound = SoundManager.INSTANCE.loadSound(GameAssetLoader.loadAsset("sounds/ui/e-button-hover.ogg"));
        onClickSound = SoundManager.INSTANCE.loadSound(GameAssetLoader.loadAsset("sounds/ui/e-button-click.ogg"));
    }

    public CRImageButton(TextureRegion imageRegion) {
        super(createStyleFromTextButtonStyle());

        Image imageActor = new Image(new TextureRegionDrawable(imageRegion));
        imageActor.setScaling(Scaling.fit);
        this.add(imageActor).grow();
        imageActor.setTouchable(Touchable.disabled);

        this.addListener(this::onEvent);
    }

    private static ButtonStyle createStyleFromTextButtonStyle() {
        ButtonStyle style = new ButtonStyle();
        if (GameStyles.textButtonStyle != null) {
            style.up = GameStyles.textButtonStyle.up;
            style.down = GameStyles.textButtonStyle.down;
            style.over = GameStyles.textButtonStyle.over;
            style.disabled = GameStyles.textButtonStyle.disabled;
        } else Mod.LOGGER.error("GameStyles.textButtonStyle is null!");
        return style;
    }

    protected boolean onEvent(Event event) {
        if (event.getTarget() != this) return false;

        if (event instanceof InputEvent ie) {
            if (ie.getType() == InputEvent.Type.enter && !this.isDisabled()) {
                SoundManager.INSTANCE.playSound(onHoverSound);
            }
        } else if (event instanceof ChangeListener.ChangeEvent) {
            if (!this.isDisabled()) {
                SoundManager.INSTANCE.playSound(onClickSound);
                this.onClick();
                event.handle();
            }
        }
        return false;
    }

    public void onClick() {}
}
