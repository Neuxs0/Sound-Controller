package dev.neuxs.sound_controller.mixins;

import com.badlogic.gdx.Files;
import com.badlogic.gdx.files.FileHandle;
import de.pottgames.tuningfork.SoundBuffer;
import dev.neuxs.sound_controller.Mod;
import dev.neuxs.sound_controller.settings.SettingsManager;
import dev.neuxs.sound_controller.utils.SoundHelper;
import finalforeach.cosmicreach.audio.SoundManager;
import finalforeach.cosmicreach.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@SuppressWarnings("unused")
@Mixin(value = SoundManager.class, priority = 1100)
public abstract class SoundManagerMixin {

    @Inject(
            method = "loadSound(Lcom/badlogic/gdx/files/FileHandle;)Lde/pottgames/tuningfork/SoundBuffer;",
            at = @At("RETURN"),
            remap = false
    )
    private void sound_controller$captureLoadedSoundBuffer(FileHandle loadAsset, CallbackInfoReturnable<SoundBuffer> cir) {
        SoundBuffer returnedSoundBuffer = cir.getReturnValue();

        if (loadAsset == null || returnedSoundBuffer == null) {
            Mod.LOGGER.warn("Cannot register sounds, FileHandle or returned SoundBuffer is null. FileHandle: {}", loadAsset);
            return;
        }

        Identifier soundId = null;
        try {
            String path = loadAsset.path().replace("\\", "/");
            String potentialIdStr;

            if (path.contains("assets/")) {
                potentialIdStr = path.substring(path.indexOf("assets/") + "assets/".length());
                int firstSlash = potentialIdStr.indexOf('/');
                if (firstSlash != -1) {
                    String namespace = potentialIdStr.substring(0, firstSlash);
                    String name = potentialIdStr.substring(firstSlash + 1);
                    soundId = Identifier.of(namespace, name);
                } else {
                    Mod.LOGGER.warn("Ambiguous classpath path: {}", path);
                }
            } else if (path.contains("/mods/")) {
                potentialIdStr = path.substring(path.indexOf("/mods/") + "/mods/".length());
                int firstSlash = potentialIdStr.indexOf('/');
                if (firstSlash != -1) {
                    String namespace = potentialIdStr.substring(0, firstSlash);
                    String name = potentialIdStr.substring(firstSlash + 1);
                    soundId = Identifier.of(namespace, name);
                } else {
                    Mod.LOGGER.warn("Ambiguous mod path: {}", path);
                }
            } else if (loadAsset.type() == Files.FileType.Internal || path.startsWith("base/")) {
                potentialIdStr = path.startsWith("base/") ? path.substring("base/".length()) : path;
                soundId = Identifier.of("base", potentialIdStr);
            } else {
                Mod.LOGGER.warn("Unrecognized FileHandle path type/structure: {}", path);
            }


            if (soundId != null) SoundHelper.registerSoundBuffer(soundId, returnedSoundBuffer);
            else Mod.LOGGER.warn("Intercepted loadSound() for SoundBuffer, but failed to deduce Identifier from path: {}", loadAsset.path());
        } catch (Exception e) {
            Mod.LOGGER.error("Error occurred while deducing Identifier or registering sound for {}: {}", loadAsset.path(), e.getMessage(), e);
        }
    }

    @ModifyVariable(
            method = "playSound(Lde/pottgames/tuningfork/SoundBuffer;FFF)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private float sound_controller$modifyPlaySoundBufferPanVolumeArg(float originalVolume, SoundBuffer sound) {
        String context = "playSound(SB,FFF)";
        float modMultiplier = getCustomVolumeMultiplier(sound, context);
        return originalVolume * modMultiplier;
    }

    @ModifyVariable(
            method = "playSound(Lde/pottgames/tuningfork/SoundBuffer;FF)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private float sound_controller$modifyPlaySoundBufferNoPanVolumeArg(float originalVolume, SoundBuffer sound) {
        String context = "playSound(SB,FF)";
        float modMultiplier = getCustomVolumeMultiplier(sound, context);
        return originalVolume * modMultiplier;
    }

    @ModifyVariable(
            method = "playSound3D(Lde/pottgames/tuningfork/SoundBuffer;Lcom/badlogic/gdx/math/Vector3;FF)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private float sound_controller$modifyPlaySound3DBufferVolPitchVolumeArg(float originalVolume, SoundBuffer sound) {
        String context = "playSound3D(SB,V3,FF)";
        float modMultiplier = getCustomVolumeMultiplier(sound, context);
        return originalVolume * modMultiplier;
    }

    @ModifyVariable(
            method = "playSound3D(Lde/pottgames/tuningfork/SoundBuffer;Lcom/badlogic/gdx/math/Vector3;F)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private float sound_controller$modifyPlaySound3DBufferVolVolumeArg(float originalVolume, SoundBuffer sound) {
        String context = "playSound3D(SB,V3,F)";
        float modMultiplier = getCustomVolumeMultiplier(sound, context);
        return originalVolume * modMultiplier;
    }

    private float getCustomVolumeMultiplier(SoundBuffer sound, String context) {
        Map<SoundBuffer, Identifier> map = SoundHelper.getSoundBufferMap();

        if (map.isEmpty()) {
            Mod.LOGGER.warn("SoundBuffer map is currently empty. Context: {}", context);
            return 1.0f;
        }

        Identifier soundId = map.get(sound);

        if (soundId != null) return SettingsManager.getVolume(soundId.toString());
        else {
            Mod.LOGGER.warn("Identifier NOT FOUND in map for SoundBuffer instance: {}. Returning default 1.0f. Context: {}", sound, context);
            return 1.0f;
        }
    }
}
