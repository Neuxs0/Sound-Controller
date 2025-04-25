package dev.neuxs.sound_controller.utils;

import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ObjectSet;
import dev.neuxs.sound_controller.Mod;
import finalforeach.cosmicreach.GameAssetLoader;
import finalforeach.cosmicreach.util.Identifier;

import java.util.HashMap;
import java.util.Map;

public class SoundHelper {

    public static Map<String, Identifier> getAllSoundIdentifiers() {
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
                        Mod.LOGGER.error("Error during forEachAsset for sounds in namespace '{}' with prefix '{}'", ns, prefix, e);
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
                                    Mod.LOGGER.error("Failed to parse music JSON definition: {}", assetPathStr, e);
                                }
                            }
                        });
                    } catch (Exception e) {
                        Mod.LOGGER.error("Error during forEachAsset for music in namespace '{}' with prefix '{}'", ns, prefix, e);
                    }
                }
            }
        }

        Map<String, Identifier> resultMap = new HashMap<>();
        for (Identifier id : soundIds) {
            resultMap.put(id.toString(), id);
        }

        return resultMap;
    }

    private static void parseMusicJsonForSoundFiles(JsonValue musicJson, ObjectSet<Identifier> soundIds) {
        if (musicJson == null) return;

        if (musicJson.has("fileName")) {
            String songFileName = musicJson.getString("fileName", null);
            if (songFileName != null && !songFileName.isEmpty()) {
                try {
                    soundIds.add(Identifier.of(songFileName));
                } catch (Exception e) {
                    Mod.LOGGER.error("Error creating Identifier from music fileName: {}", songFileName, e);
                }
            }
        }

        if (musicJson.isArray()) {
            for (JsonValue songEntry : musicJson) {
                if (songEntry.isObject() && songEntry.has("fileName")) {
                    String songFileName = songEntry.getString("fileName", null);
                    if (songFileName != null && !songFileName.isEmpty()) {
                        try {
                            soundIds.add(Identifier.of(songFileName));
                        } catch (Exception e) {
                            Mod.LOGGER.error("Error creating Identifier from music fileName in array: {}", songFileName, e);
                        }
                    }
                }
            }
        }
        else if (musicJson.isObject() && !musicJson.has("fileName")) {
            for (JsonValue child = musicJson.child; child != null; child = child.next) {
                if (child.isObject()) {
                    parseMusicJsonForSoundFiles(child, soundIds);
                }
            }
        }
    }
}
