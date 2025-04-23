package dev.neuxs.sound_controller.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.ScreenUtils;
import finalforeach.cosmicreach.gamestates.GameState;
import finalforeach.cosmicreach.lang.Lang;
import finalforeach.cosmicreach.settings.INumberSetting;
import finalforeach.cosmicreach.settings.SoundSettings;
import finalforeach.cosmicreach.settings.types.IntSetting;
import finalforeach.cosmicreach.ui.widgets.CRButton;
import finalforeach.cosmicreach.ui.widgets.CRSlider;

import java.text.DecimalFormat;
import java.text.NumberFormat;

public class SoundControllerMenu extends GameState {
    private final GameState previousGameState;
    private final NumberFormat intFormat = new DecimalFormat("#");
    private final NumberFormat percentFormat = Lang.getPercentFormatter();

    public SoundControllerMenu(GameState previousGameState) {
        this.previousGameState = previousGameState;
    }

    @Override
    public void create() {
        super.create();
        Gdx.input.setInputProcessor(this.stage);

        CRButton backButton = new CRButton("Back") {
            @Override
            public void onClick() {
                super.onClick();
                GameState.switchToGameState(previousGameState);
            }
        };
        backButton.setSize(150f, 50f);
        backButton.setPosition(5f, this.newUiViewport.getWorldHeight() - 5f - backButton.getHeight());
        this.stage.addActor(backButton);

        CRSlider soundSlider = this.createSettingsCRSlider(SoundSettings.soundVolume, "Global", 0.0f, 1.0f, 0.01f, this.percentFormat);
        CRSlider musicSlider = this.createSettingsCRSlider(SoundSettings.musicVolume, "Music", 0.0f, 1.0f, 0.01f, this.percentFormat);
        CRSlider musicFreqSlider = this.createSettingsCRSlider(SoundSettings.musicFrequency, "Music Freq (in Minutes)", 0.0f, 20.0f, 1.0f, this.intFormat);

        Table table = new Table();
        table.setFillParent(true);
        this.stage.addActor(table);
        table.add().height(50.0F).expand();
        table.row();
        table.add().expand();
        table.add(soundSlider).width(250.0f).top().padRight(12.0f);
        table.add(musicSlider).width(250.0f).top();
        table.add(musicFreqSlider).width(250.0f).top().padLeft(12.0f);
        table.add().expand();
        table.row();
        table.add().expand();
    }

    @Override
    public void render() {
        super.render();
        this.stage.act();

        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) || Gdx.input.isButtonJustPressed(Input.Buttons.BACK)) {
            GameState.switchToGameState(previousGameState);
        }

        ScreenUtils.clear(0.145F, 0.078F, 0.153F, 1.0F, true);

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LESS);
        Gdx.gl.glEnable(GL20.GL_CULL_FACE);
        Gdx.gl.glCullFace(GL20.GL_BACK);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Gdx.gl.glCullFace(GL20.GL_FRONT);
        this.stage.draw();
    }

    private CRSlider createSettingsCRSlider(final INumberSetting setting, final String prefix, float min, float max, float stepSize, final NumberFormat valueTextFormat) {
        CRSlider slider = new CRSlider(null, min, max, stepSize, false) {
            protected void onChangeEvent(ChangeListener.ChangeEvent event) {
                float currentValue = this.getValue();
                setting.setValue(currentValue);
                String formattedValue;
                if (valueTextFormat == null) {
                    if (setting instanceof IntSetting) {
                        formattedValue = "" + (int)currentValue;
                    } else {
                        formattedValue = "" + currentValue;
                    }
                } else {
                    formattedValue = valueTextFormat.format(currentValue);
                }

                this.setText(prefix + formattedValue);
            }
        };
        slider.setWidth(250.0F);
        slider.setValue(setting.getValueAsFloat());
        return slider;
    }
}
