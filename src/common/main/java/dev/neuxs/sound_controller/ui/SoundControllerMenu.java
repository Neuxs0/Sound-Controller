package dev.neuxs.sound_controller.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.utils.ScreenUtils;
import dev.neuxs.sound_controller.Mod;
import finalforeach.cosmicreach.gamestates.GameState;
import finalforeach.cosmicreach.ui.widgets.CRButton;

public class SoundControllerMenu extends GameState {
    private final GameState previousGameState;

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
}
