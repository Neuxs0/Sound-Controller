package dev.neuxs.sound_controller.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.ScreenUtils;
import dev.neuxs.sound_controller.Mod;
import dev.neuxs.sound_controller.settings.SettingsManager;
import dev.neuxs.sound_controller.utils.SoundHelper;
import finalforeach.cosmicreach.gamestates.GameState;
import finalforeach.cosmicreach.lang.Lang;
import finalforeach.cosmicreach.settings.INumberSetting;
import finalforeach.cosmicreach.settings.SoundSettings;
import finalforeach.cosmicreach.settings.types.IntSetting;
import finalforeach.cosmicreach.ui.FontRenderer;
import finalforeach.cosmicreach.ui.GameStyles;
import finalforeach.cosmicreach.ui.widgets.CRButton;
import finalforeach.cosmicreach.ui.widgets.CRLabel;
import finalforeach.cosmicreach.ui.widgets.CRSlider;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SoundControllerMenu extends GameState {
    private final GameState previousGameState;
    private final NumberFormat intFormat = new DecimalFormat("#");
    private final NumberFormat percentFormat = Lang.getPercentFormatter();
    private Table soundListTable;
    private ScrollPane scrollPane;
    private TextField searchField;
    private List<String> allSoundIdsSorted;
    private CRButton backButton;
    private CRSlider soundSlider;
    private CRSlider musicSlider;
    private CRSlider musicFreqSlider;
    private CRLabel searchLabel;
    private static final float PADDING = 10f;
    private static final float TOP_BAR_HEIGHT = 50f;
    private static final float SEARCH_BAR_HEIGHT = 30f;
    private static final float BUTTON_WIDTH = 150f;
    private static final float SLIDER_WIDTH = 200f;
    private static final float INDIVIDUAL_SLIDER_WIDTH = 250f;
    private static final float ELEMENT_SPACING = 5f;
    private static final float BACK_BUTTON_RIGHT_MARGIN = 20f;

    public SoundControllerMenu(GameState previousGameState) {
        this.previousGameState = previousGameState;
    }

    @Override
    public void create() {
        super.create();
        Gdx.input.setInputProcessor(this.stage);

        SettingsManager.initialize();

        float currentX = PADDING;
        float topY = stage.getHeight() - PADDING - TOP_BAR_HEIGHT;

        backButton = new CRButton("Back") {
            @Override
            public void onClick() {
                super.onClick();
                GameState.switchToGameState(previousGameState);
            }
        };
        backButton.setBounds(currentX, topY, BUTTON_WIDTH, TOP_BAR_HEIGHT);
        stage.addActor(backButton);
        currentX += BUTTON_WIDTH + BACK_BUTTON_RIGHT_MARGIN;

        soundSlider = this.createSettingsCRSlider(SoundSettings.soundVolume, "Global: ", 1.0f, 0.01f, this.percentFormat);
        soundSlider.setBounds(currentX, topY, SLIDER_WIDTH, TOP_BAR_HEIGHT);
        stage.addActor(soundSlider);
        currentX += SLIDER_WIDTH + ELEMENT_SPACING;

        musicSlider = this.createSettingsCRSlider(SoundSettings.musicVolume, "Music: ", 1.0f, 0.01f, this.percentFormat);
        musicSlider.setBounds(currentX, topY, SLIDER_WIDTH, TOP_BAR_HEIGHT);
        stage.addActor(musicSlider);
        currentX += SLIDER_WIDTH + ELEMENT_SPACING;

        musicFreqSlider = this.createSettingsCRSlider(SoundSettings.musicFrequency, "Music Freq (min): ", 20.0f, 1.0f, this.intFormat);
        musicFreqSlider.setBounds(currentX, topY, SLIDER_WIDTH, TOP_BAR_HEIGHT);
        stage.addActor(musicFreqSlider);

        float searchY = topY - PADDING - SEARCH_BAR_HEIGHT;
        currentX = PADDING;

        searchLabel = new CRLabel("Search:");
        searchLabel.pack();
        Vector2 searchLabelDim = FontRenderer.getTextDimensions(this.newUiViewport, String.valueOf(searchLabel.getText()), new Vector2(0f, 0f));
        searchLabel.setPosition(currentX, searchY + (SEARCH_BAR_HEIGHT - searchLabel.getHeight()) / 2f - searchLabelDim.y / 2);
        stage.addActor(searchLabel);
        currentX += searchLabel.getWidth() + ELEMENT_SPACING;

        searchField = new TextField("", GameStyles.textstyle);
        searchField.setMessageText("Search Sounds...");
        searchField.setTextFieldListener((textField, c) -> filterAndRebuildSoundList(textField.getText()));
        float searchFieldWidth = stage.getWidth() - currentX - PADDING;
        searchField.setBounds(currentX, searchY, searchFieldWidth, SEARCH_BAR_HEIGHT);
        stage.addActor(searchField);

        soundListTable = new Table();
        soundListTable.top().left();

        scrollPane = new ScrollPane(soundListTable, GameStyles.styleTooltip.background == null
                ? new ScrollPane.ScrollPaneStyle()
                : new ScrollPane.ScrollPaneStyle(GameStyles.styleTooltip.background, null, null, null, null));
        scrollPane.setFadeScrollBars(false);
        scrollPane.setScrollingDisabled(true, false);
        scrollPane.setForceScroll(false, true);
        scrollPane.setFlickScroll(false);

        float scrollPaneTop = searchY - PADDING;
        float scrollPaneBottom = PADDING;
        float scrollPaneHeight = scrollPaneTop - scrollPaneBottom;
        scrollPane.setBounds(PADDING, scrollPaneBottom, stage.getWidth() - 2 * PADDING, scrollPaneHeight);

        scrollPane.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                if (stage != null && stage.getKeyboardFocus() == searchField) {
                    stage.setKeyboardFocus(null);
                    return true;
                }
                return false;
            }

            @Override
            public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY) {
                return false;
            }
        });

        stage.addActor(scrollPane);

        try {
            allSoundIdsSorted = new ArrayList<>(SoundHelper.getAllSoundIdentifiers().keySet());
            Collections.sort(allSoundIdsSorted);
        } catch (Exception e) {
            Mod.LOGGER.error("Error getting sound identifiers: {}", e.getMessage(), e);
            allSoundIdsSorted = new ArrayList<>();
            if (soundListTable != null) {
                soundListTable.clearChildren();
                soundListTable.add(new CRLabel("Error loading sound list.", GameStyles.styleText)).pad(10);
            }
        }

        filterAndRebuildSoundList("");

        this.stage.setScrollFocus(scrollPane);

        stage.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                if (stage.getKeyboardFocus() == searchField) {
                    Actor target = event.getTarget();
                    if (target != searchField && !target.isDescendantOf(searchField)) {
                        stage.setKeyboardFocus(null);
                        Gdx.input.setOnscreenKeyboardVisible(false);
                        return false;
                    }
                }
                return false;
            }
        });
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);

        if (backButton == null) return;

        float currentX = PADDING;
        float topY = stage.getHeight() - PADDING - TOP_BAR_HEIGHT;

        backButton.setBounds(currentX, topY, BUTTON_WIDTH, TOP_BAR_HEIGHT);
        currentX += BUTTON_WIDTH + BACK_BUTTON_RIGHT_MARGIN;

        soundSlider.setBounds(currentX, topY, SLIDER_WIDTH, TOP_BAR_HEIGHT);
        currentX += SLIDER_WIDTH + ELEMENT_SPACING;

        musicSlider.setBounds(currentX, topY, SLIDER_WIDTH, TOP_BAR_HEIGHT);
        currentX += SLIDER_WIDTH + ELEMENT_SPACING;

        musicFreqSlider.setBounds(currentX, topY, SLIDER_WIDTH, TOP_BAR_HEIGHT);

        float searchY = topY - PADDING - SEARCH_BAR_HEIGHT;
        currentX = PADDING;

        searchLabel.pack();
        Vector2 searchLabelDim = FontRenderer.getTextDimensions(this.newUiViewport, String.valueOf(searchLabel.getText()), new Vector2(0f, 0f));
        searchLabel.setPosition(currentX, searchY + (SEARCH_BAR_HEIGHT - searchLabel.getHeight()) / 2f - searchLabelDim.y / 2);
        currentX += searchLabel.getWidth() + ELEMENT_SPACING;

        float searchFieldWidth = stage.getWidth() - currentX - PADDING;
        searchField.setBounds(currentX, searchY, searchFieldWidth, SEARCH_BAR_HEIGHT);

        float scrollPaneTop = searchY - PADDING;
        float scrollPaneBottom = PADDING;
        float scrollPaneHeight = scrollPaneTop - scrollPaneBottom;
        scrollPane.setBounds(PADDING, scrollPaneBottom, stage.getWidth() - 2 * PADDING, scrollPaneHeight);

        soundListTable.invalidateHierarchy();
        scrollPane.layout();
    }

    @Override
    public void render() {
        super.render();
        this.stage.act(Gdx.graphics.getDeltaTime());

        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) || Gdx.input.isButtonJustPressed(Input.Buttons.BACK)) {
            if (this.stage.getKeyboardFocus() == searchField) this.stage.setKeyboardFocus(null);
            else GameState.switchToGameState(previousGameState);
        }

        ScreenUtils.clear(0.145F, 0.078F, 0.153F, 1.0F, true);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);

        this.stage.draw();

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glEnable(GL20.GL_CULL_FACE);
    }

    private CRSlider createSettingsCRSlider(final INumberSetting setting, final String prefix, float max, float stepSize, final NumberFormat valueTextFormat) {
        float initialValue = setting.getValueAsFloat();
        String initialText = prefix + formatValue(initialValue, valueTextFormat, setting);

        CRSlider slider = new CRSlider(initialText, 0.0f, max, stepSize, false) {
            @Override
            protected void onChangeEvent(ChangeListener.ChangeEvent event) {
                float currentValue = this.getValue();
                if (Math.abs(setting.getValueAsFloat() - currentValue) > stepSize / 10.0f) {
                    setting.setValue(currentValue);
                    this.setText(prefix + formatValue(setting.getValueAsFloat(), valueTextFormat, setting));
                } else {
                    this.setText(prefix + formatValue(setting.getValueAsFloat(), valueTextFormat, setting));
                }
            }
        };
        slider.setValue(initialValue);
        return slider;
    }

    private String formatValue(float value, NumberFormat format, INumberSetting setting) {
        if (format == null) {
            if (setting instanceof IntSetting) return "" + (int) value;
            else return String.format("%.2f", value);
        } else {
            float epsilon = 0.0001f;
            if (format == percentFormat && Math.abs(value - 1.0f) < epsilon) return "100%";
            if (format == percentFormat && Math.abs(value - 0.0f) < epsilon) return "0%";
            return format.format(value);
        }
    }

    private void filterAndRebuildSoundList(String filterText) {
        soundListTable.clearChildren(true);

        if (allSoundIdsSorted == null || allSoundIdsSorted.isEmpty()) {
            soundListTable.add(new CRLabel(allSoundIdsSorted == null ? "Sound list error." : "No sounds found.", GameStyles.styleText)).pad(10);
            return;
        }

        String filterLower = filterText.toLowerCase().trim();
        boolean hasFilter = !filterLower.isEmpty();

        final float stepSize = 0.01f;

        for (final String soundId : allSoundIdsSorted) {
            if (hasFilter && !soundId.toLowerCase().contains(filterLower)) continue;

            Table soundRowTable = new Table();

            CRLabel soundLabel = new CRLabel(soundId);
            soundLabel.setEllipsis("...");
            soundRowTable.add(soundLabel).growX().left().padRight(ELEMENT_SPACING);

            float initialVolume = SettingsManager.getVolume(soundId);

            CRSlider individualSlider = new CRSlider("", 0.0f, 1.0f, stepSize, false) {
                @Override
                protected void onChangeEvent(ChangeListener.ChangeEvent event) {
                    float currentValue = this.getValue();
                    float storedValue = SettingsManager.getVolume(soundId);

                    if (Math.abs(currentValue - storedValue) > stepSize / 10.0f) {
                        SettingsManager.setVolume(soundId, currentValue);
                        this.setText(formatValue(SettingsManager.getVolume(soundId), percentFormat, null));
                    } else {
                        this.setText(formatValue(storedValue, percentFormat, null));
                    }
                }
            };

            individualSlider.setValue(initialVolume);
            individualSlider.setText(formatValue(initialVolume, percentFormat, null));

            soundRowTable.add(individualSlider).width(INDIVIDUAL_SLIDER_WIDTH).right();

            soundListTable.add(soundRowTable).expandX().fillX().pad(2f).row();
        }

        soundListTable.invalidateHierarchy();

        if (scrollPane != null) {
            float scrollPercent = scrollPane.getScrollPercentY();
            scrollPane.layout();
            scrollPane.setScrollPercentY(scrollPercent);
        }
    }
}
