package dev.neuxs.sound_controller.settings;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public class SoundConfig {
    private final Map<String, Float> soundVolumes;

    private static final float DEFAULT_VOLUME = 1.0f;
    private static final float MIN_VOLUME = 0.0f;
    private static final float MAX_VOLUME = 1.0f;

    public SoundConfig(Map<String, Float> initialVolumes) {
        this.soundVolumes = (initialVolumes != null) ? new TreeMap<>(initialVolumes) : new TreeMap<>();
    }

    public float getVolume(String soundId) {
        Objects.requireNonNull(soundId, "soundId cannot be null");
        return soundVolumes.getOrDefault(soundId, DEFAULT_VOLUME);
    }

    public boolean setVolume(String soundId, float volume) {
        Objects.requireNonNull(soundId, "soundId cannot be null");
        float clampedVolume = clampVolume(volume);
        Float previousValue = soundVolumes.put(soundId, clampedVolume);
        return previousValue == null || Math.abs(previousValue - clampedVolume) > 0.0001f;
    }

    Map<String, Float> getSoundVolumes() {
        return soundVolumes;
    }

    private static float clampVolume(float volume) {
        return Math.max(MIN_VOLUME, Math.min(MAX_VOLUME, volume));
    }
}
