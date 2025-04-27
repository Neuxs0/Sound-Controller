package dev.neuxs.sound_controller.utils;

import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ObjectSet;
import de.pottgames.tuningfork.SoundBuffer;
import de.pottgames.tuningfork.jukebox.song.Song;
import dev.neuxs.sound_controller.Mod;
import finalforeach.cosmicreach.GameAssetLoader;
import finalforeach.cosmicreach.audio.GameSong;
import finalforeach.cosmicreach.sounds.GameSound;
import finalforeach.cosmicreach.util.Identifier;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SoundHelper {
    private static final Map<SoundBuffer, Identifier> soundBufferToIdentifierMap = new ConcurrentHashMap<>();

    private static volatile Map<String, Identifier> soundStringToIdentifierMap = null;
    private static final Object stringMapLock = new Object();
    public static volatile GameSong lastChosenGameSong = null;
    public static volatile Song currentJukeboxSong = null;

    public static void registerSoundBuffer(Identifier id, SoundBuffer buffer) {
        if (id != null && buffer != null) soundBufferToIdentifierMap.put(buffer, id);
        else Mod.LOGGER.warn("Attempted to register null SoundBuffer or Identifier. ID: {}, Buffer: {}", id, buffer);
    }

    public static Map<SoundBuffer, Identifier> getSoundBufferMap() {
        return Collections.unmodifiableMap(soundBufferToIdentifierMap);
    }

    public static void initializeStringIdentifierMap() {
        if (soundStringToIdentifierMap == null) {
            synchronized (stringMapLock) {
                if (soundStringToIdentifierMap == null) {
                    try {
                        ObjectSet<Identifier> allIds = getAllIdentifiers();
                        Map<String, Identifier> stringMap = new HashMap<>();
                        for (Identifier id : allIds) if (id != null) stringMap.put(id.toString(), id);
                        soundStringToIdentifierMap = Collections.unmodifiableMap(stringMap);
                    } catch (Exception e) {
                        Mod.LOGGER.error("Failed to initialize String Identifier map!", e);
                        soundStringToIdentifierMap = Collections.emptyMap();
                    }
                }
            }
        }
    }

    public static Map<String, Identifier> getAllSoundIdentifiers() {
        initializeStringIdentifierMap();
        return soundStringToIdentifierMap != null ? soundStringToIdentifierMap : Collections.emptyMap();
    }

    private static ObjectSet<Identifier> getAllIdentifiers() {
        ObjectSet<Identifier> soundIds = new ObjectSet<>();

        String[] soundExtensions = {".ogg", ".wav"};
        String[] soundPrefixes = {"sounds/"};

        String[] musicExtensions = {".json"};
        String[] musicPrefixes = {"music/"};

        ObjectSet<String> namespaces = GameAssetLoader.getAllNamespaces();

        for (String ns : namespaces) {
            String namespacePrefix = ns + ":";

            for (String prefix : soundPrefixes) {
                for (String ext : soundExtensions) {
                    String searchPath = namespacePrefix + prefix;
                    try {
                        GameAssetLoader.forEachAsset(searchPath, ext, (assetPathStr, fileHandle) -> {
                            if (fileHandle != null && fileHandle.exists()) {
                                try {
                                    Identifier id = Identifier.of(assetPathStr);
                                    soundIds.add(id);
                                } catch (Exception e) {
                                    Mod.LOGGER.error("Error processing sound asset path: {}", assetPathStr, e);
                                }
                            }
                        });
                    } catch (Exception e) {
                        Mod.LOGGER.error("Error during forEachAsset for sounds in namespace '{}' with prefix '{}', ext '{}'", ns, prefix, ext, e);
                    }
                }
            }

            for (String prefix : musicPrefixes) {
                for (String ext : musicExtensions) {
                    String searchPath = namespacePrefix + prefix;
                    try {
                        GameAssetLoader.forEachAsset(searchPath, ext, (assetPathStr, fileHandle) -> {
                            if (fileHandle != null && fileHandle.exists()) {
                                try {
                                    JsonValue musicJson = GameAssetLoader.loadJson(fileHandle);
                                    parseMusicJsonForSoundFiles(musicJson, soundIds);
                                } catch (Exception e) {
                                    Mod.LOGGER.error("Failed to parse music JSON definition or add sound from {}: {}", assetPathStr, e.getMessage());
                                }
                            }
                        });
                    } catch (Exception e) {
                        Mod.LOGGER.error("Error during forEachAsset for music in namespace '{}' with prefix '{}', ext '{}'", ns, prefix, ext, e);
                    }
                }
            }
        }

        return soundIds;
    }

    private static void parseMusicJsonForSoundFiles(JsonValue musicJson, ObjectSet<Identifier> soundIds) {
        if (musicJson == null) return;

        if (musicJson.isObject() && musicJson.has("fileName")) {
            String songFileName = musicJson.getString("fileName", null);
            if (songFileName != null && !songFileName.isEmpty()) {
                try {
                    Identifier id = Identifier.of(songFileName);
                    soundIds.add(id);
                } catch (Exception e) {
                    Mod.LOGGER.error("Error creating Identifier from music fileName (object): {}", songFileName, e);
                }
            }
        }

        if (musicJson.isArray()) for (JsonValue songEntry : musicJson) parseMusicJsonForSoundFiles(songEntry, soundIds);
        else if (musicJson.isObject() && !musicJson.has("fileName")) {
            for (JsonValue child = musicJson.child; child != null; child = child.next) {
                if (child.isObject() || child.isArray()) parseMusicJsonForSoundFiles(child, soundIds);
            }
        }
    }
}
