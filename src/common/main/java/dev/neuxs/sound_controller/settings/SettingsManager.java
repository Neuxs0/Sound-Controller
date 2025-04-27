package dev.neuxs.sound_controller.settings;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import dev.neuxs.sound_controller.Mod;
import dev.neuxs.sound_controller.utils.SoundHelper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class SettingsManager {
    private static final String CONFIG_DIR_NAME = "config";
    private static final String CONFIG_FILE_NAME = "sound_controller.json";
    private static final String CORRUPTED_CONFIG_FILE_NAME = "sound_controller.corrupted.json";
    private static final Path CONFIG_DIRECTORY_PATH = Paths.get(CONFIG_DIR_NAME);
    private static final Path CONFIG_FILE_PATH = CONFIG_DIRECTORY_PATH.resolve(CONFIG_FILE_NAME);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type SOUND_VOLUME_MAP_TYPE = new TypeToken<Map<String, Float>>() {}.getType();

    private static final float DEFAULT_VOLUME = 1.0f;
    private static final float MIN_VOLUME = 0.0f;
    private static final float MAX_VOLUME = 1.0f;
    private static final long FILE_READ_DELAY_MS = 150;
    private static final long RELOAD_DEBOUNCE_MS = 500;

    private static final SettingsManager instance = new SettingsManager();
    private volatile SoundConfig currentConfig;

    private WatchService watchService = null;
    private final AtomicBoolean watcherRunning = new AtomicBoolean(false);
    private volatile long lastReloadAttemptTime = 0;

    private SettingsManager() {
        this.currentConfig = loadConfigInternal();
        startWatching();
    }

    public static SettingsManager getInstance() {
        return instance;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void initialize() {
        getInstance();
    }

    private SoundConfig loadConfigInternal() {
        SoundConfig loadedConfig = null;
        boolean needsSave = false;
        Path targetPath = CONFIG_FILE_PATH;

        try {
            Files.createDirectories(CONFIG_DIRECTORY_PATH);

            if (Files.exists(targetPath)) {
                try { Thread.sleep(50); }
                catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }

                try (BufferedReader reader = Files.newBufferedReader(targetPath, StandardCharsets.UTF_8)) {
                    Map<String, Float> volumes = GSON.fromJson(reader, SOUND_VOLUME_MAP_TYPE);
                    if (volumes != null) loadedConfig = new SoundConfig(new TreeMap<>(volumes));
                    else Mod.LOGGER.warn("Configuration file {} was empty or null. Using default.", targetPath);
                } catch (JsonSyntaxException | JsonIOException e) {
                    Mod.LOGGER.error("Failed to parse JSON in {}: {}. Backing up.", targetPath, e.getMessage());
                    backupCorruptedConfig(targetPath);
                } catch (IOException e) {
                    Mod.LOGGER.error("I/O error reading {}: {}. Using default.", targetPath, e.getMessage(), e);
                } catch (Exception e) {
                    Mod.LOGGER.error("Unexpected error reading/parsing {}: {}. Using default.", targetPath, e.getMessage(), e);
                }
            } else needsSave = true;

        } catch (IOException e) {
            Mod.LOGGER.error("Fatal: Failed to create config directory {}: {}. Using in-memory default.", CONFIG_DIRECTORY_PATH, e.getMessage(), e);
            loadedConfig = createDefaultConfig();
            return loadedConfig;
        } catch (Exception e) {
            Mod.LOGGER.error("Fatal: Unexpected error during config loading setup for {}: {}. Using in-memory default.", targetPath, e.getMessage(), e);
            loadedConfig = createDefaultConfig();
            return loadedConfig;
        }

        if (loadedConfig == null) loadedConfig = createDefaultConfig();

        boolean syncMadeChanges = syncConfigWithGameSounds(loadedConfig);

        if (needsSave || syncMadeChanges) saveConfigToFile(loadedConfig);

        return loadedConfig;
    }

    private void saveConfigToFile(SoundConfig configToSave) {
        if (configToSave == null) {
            Mod.LOGGER.error("Attempted to save a null config.");
            return;
        }

        Path targetPath = CONFIG_FILE_PATH;

        try {
            Files.createDirectories(CONFIG_DIRECTORY_PATH);
            Path tempPath = targetPath.resolveSibling(targetPath.getFileName().toString() + ".tmp");
            try (BufferedWriter writer = Files.newBufferedWriter(tempPath, StandardCharsets.UTF_8)) {
                GSON.toJson(configToSave.getSoundVolumes(), writer);
            }
            Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (JsonIOException e) {
            Mod.LOGGER.error("Gson error writing config to {}: {}", targetPath, e.getMessage(), e);
        } catch (IOException e) {
            Mod.LOGGER.error("I/O error saving config to {}: {}", targetPath, e.getMessage(), e);
        } catch (Exception e) {
            Mod.LOGGER.error("An unexpected error occurred during config save to {}: {}", targetPath, e.getMessage(), e);
        }
    }

    private SoundConfig createDefaultConfig() {
        return new SoundConfig(new TreeMap<>());
    }

    private boolean syncConfigWithGameSounds(SoundConfig config) {
        if (config == null) return false;

        boolean modified = false;
        Map<String, Float> currentVolumes = config.getSoundVolumes();
        Set<String> gameSoundIds;

        try { gameSoundIds = SoundHelper.getAllSoundIdentifiers().keySet(); }
        catch (Exception e) {
            Mod.LOGGER.error("Failed to get sound IDs from SoundHelper during sync", e);
            return false;
        }

        for (String gameSoundId : gameSoundIds) {
            if (!currentVolumes.containsKey(gameSoundId)) {
                currentVolumes.put(gameSoundId, DEFAULT_VOLUME);
                modified = true;
            }
        }

        Set<String> configuredSounds = Set.copyOf(currentVolumes.keySet());
        for (String configuredSoundId : configuredSounds) {
            if (gameSoundIds.contains(configuredSoundId)) {
                float currentVol = currentVolumes.get(configuredSoundId);
                float clampedVol = clampVolume(currentVol);
                if (Math.abs(currentVol - clampedVol) > 0.0001f) {
                    currentVolumes.put(configuredSoundId, clampedVol);
                    modified = true;
                }
            }
        }
        return modified;
    }

    private static float clampVolume(float volume) {
        return Math.max(MIN_VOLUME, Math.min(MAX_VOLUME, volume));
    }

    private void backupCorruptedConfig(Path originalPath) {
        Path backupPath = originalPath.resolveSibling(CORRUPTED_CONFIG_FILE_NAME);
        try {
            Files.move(originalPath, backupPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            Mod.LOGGER.warn("Backed up potentially corrupted config {} to {}", originalPath.getFileName(), backupPath.getFileName());
        } catch (NoSuchFileException e) {
            Mod.LOGGER.warn("Could not backup config {}: File did not exist.", originalPath);
        } catch (IOException e) {
            Mod.LOGGER.error("Could not backup corrupted config file {}: {}", originalPath, e.getMessage(), e);
        }
    }

    public static float getVolume(String soundId) {
        SoundConfig config = getInstance().currentConfig;
        if (config == null) {
            Mod.LOGGER.error("getVolume called but currentConfig is NULL! ID='{}'", soundId);
            return 1.0f;
        }

        return config.getVolume(soundId);
    }

    public static void setVolume(String soundId, float volume) {
        SettingsManager manager = getInstance();
        if (manager.currentConfig == null) {
            Mod.LOGGER.error("setVolume called but currentConfig is NULL! Cannot set Volume for ID='{}'", soundId);
            return;
        }

//        if (soundId.contains("music")) GameMusicManager.forceSongChange(); // Any proper implementation is just taking far too long

        if (manager.currentConfig.setVolume(soundId, volume)) saveSettings();
    }

    public static void saveSettings() {
        getInstance().saveConfigToFile(getInstance().currentConfig);
    }

    private synchronized void performReload() {
        long now = System.currentTimeMillis();
        if ((now - lastReloadAttemptTime < RELOAD_DEBOUNCE_MS)) return;
        lastReloadAttemptTime = now;
        this.currentConfig = loadConfigInternal();
    }

    private void startWatching() {
        try {
            Files.createDirectories(CONFIG_DIRECTORY_PATH);
        } catch (IOException e) {
            Mod.LOGGER.error("Cannot start file watcher: Failed to ensure config directory exists: {}", CONFIG_DIRECTORY_PATH, e);
            return;
        } catch (Exception e) {
            Mod.LOGGER.error("Cannot start file watcher: Unexpected error checking config directory: {}", CONFIG_DIRECTORY_PATH, e);
            return;
        }

        if (!watcherRunning.compareAndSet(false, true)) return;

        try {
            watchService = FileSystems.getDefault().newWatchService();
            CONFIG_DIRECTORY_PATH.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);

            ExecutorService watchExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread t = Executors.defaultThreadFactory().newThread(r);
                t.setDaemon(true);
                t.setName("SoundController-ConfigWatcher");
                return t;
            });

            watchExecutor.submit(this::watchLoop);

        } catch (IOException e) {
            Mod.LOGGER.error("Failed to initialize configuration file watcher for {}: {}", CONFIG_DIRECTORY_PATH, e.getMessage(), e);
            watcherRunning.set(false);
            closeWatchService();
        } catch (Exception e) {
            Mod.LOGGER.error("Unexpected error starting configuration file watcher: {}", e.getMessage(), e);
            watcherRunning.set(false);
            closeWatchService();
        }
    }

    @SuppressWarnings("BusyWait")
    private void watchLoop() {
        WatchKey key;
        while (watcherRunning.get()) {
            try {
                key = watchService.take();
            } catch (InterruptedException | ClosedWatchServiceException e) {
                watcherRunning.set(false);
                continue;
            } catch(Exception e){
                Mod.LOGGER.error("Unexpected error waiting for watch key: {}", e.getMessage(), e);
                watcherRunning.set(false);
                continue;
            }

            try {
                Thread.sleep(FILE_READ_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                watcherRunning.set(false);
                key.pollEvents();
                key.reset();
                continue;
            }

            boolean relevantChange = false;
            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();

                if (kind == StandardWatchEventKinds.OVERFLOW) {
                    Mod.LOGGER.warn("Config watcher detected filesystem event overflow. Triggering reload.");
                    relevantChange = true;
                    continue;
                }

                if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                    Path changedFile = (Path) event.context();
                    if (changedFile != null && changedFile.toString().equals(CONFIG_FILE_NAME)) {
                        relevantChange = true;
                    }
                }
            }

            if (relevantChange) performReload();

            boolean valid = key.reset();
            if (!valid) {
                Mod.LOGGER.warn("Config watch key became invalid. Stopping watcher.");
                watcherRunning.set(false);
            }
        }
    }

    private void closeWatchService() {
        if (watchService != null) {
            try { watchService.close();}
            catch (IOException e) { Mod.LOGGER.error("Error closing watch service: {}", e.getMessage(), e); }
            finally { watchService = null; }
        }
    }
}
