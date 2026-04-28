package main.components.audio

import lib.ISoundOptions
import lib.Scene
import lib.Sound
import lib.TransformNode

/**
 * Manager for BabylonJS spatial sounds.
 */
object SpatialSoundManager {
    /**
     * Creates a spatial sound attached to a mesh.
     * 
     * @param name The name of the sound
     * @param url The URL of the sound file
     * @param scene The BabylonJS scene
     * @param mesh The mesh to attach the sound to
     * @param loop Whether the sound should loop
     * @param volume The volume of the sound
     * @param maxDistance The maximum distance at which the sound can be heard
     * @return The created Sound object
     */
    fun createWalkingSound(
        name: String,
        url: String,
        scene: Scene,
        mesh: TransformNode,
        loop: Boolean = true,
        volume: Double = 0.5,
        maxDistance: Double = 50.0
    ): Sound {
        return createSound(
            name = name,
            url = url,
            scene = scene,
            mesh = mesh,
            loop = loop,
            volume = volume,
            maxDistance = maxDistance
        )
    }

    /**
     * Creates a spatial sound.
     * 
     * @param name The name of the sound
     * @param url The URL of the sound file
     * @param scene The BabylonJS scene
     * @param mesh Optional mesh to attach the sound to
     * @param loop Whether the sound should loop
     * @param volume The volume of the sound
     * @param maxDistance The maximum distance at which the sound can be heard
     * @param autoplay Whether to play the sound immediately when ready
     * @return The created Sound object
     */
    fun createSound(
        name: String,
        url: String,
        scene: Scene,
        mesh: TransformNode? = null,
        loop: Boolean = false,
        volume: Double = 0.5,
        maxDistance: Double = 50.0,
        autoplay: Boolean = false,
    ): Sound {
        val options = object : ISoundOptions {
            override var loop: Boolean? = loop
            override var autoplay: Boolean? = autoplay
            override var spatialSound: Boolean? = true
            override var maxDistance: Number? = maxDistance
            override var volume: Number? = volume
            override var rolloffFactor: Number? = 1.0
            override var refDistance: Number? = 1.0
            override var distanceModel: String? = "linear"
        }
        
        val sound = Sound(
            name = name,
            urlOrArrayBuffer = url,
            scene = scene,
            readyCallback = {
                console.log("[SpatialSoundManager] Sound $name is ready (url: $url)")
            },
            options = options
        )
        
        // Explicitly set loop and volume to ensure they are applied correctly
        sound.loop = loop
        sound.setVolume(volume)
        
        console.log("[SpatialSoundManager] Creating sound $name for url: $url (maxDist: $maxDistance, vol: $volume, loop: ${sound.loop}, autoplay: $autoplay)")
        
        if (mesh != null) {
            sound.attachToMesh(mesh)
        }
        
        return sound
    }

    /**
     * Plays a one-shot sound attached to a mesh.
     */
    fun playOneShot(
        name: String,
        url: String,
        scene: Scene,
        mesh: TransformNode,
        volume: Double = 0.5,
        maxDistance: Double = 50.0,
    ): Sound {
        return createSound(
            name = name,
            url = url,
            scene = scene,
            mesh = mesh,
            loop = false,
            volume = volume,
            maxDistance = maxDistance,
            autoplay = true,
        )
    }

    /**
     * Safely plays a sound only if it's ready.
     */
    fun play(sound: Sound?) {
        if (sound != null && sound.isReady) {
            if (!sound.isPlaying) {
                try {
                    // Ensure loop is still correct
                    if (!sound.loop) sound.loop = true
                    
                    console.log("[SpatialSoundManager] Playing sound: ${sound.name} (loop=${sound.loop}, volume=${sound.getVolume()})")
                    sound.play()
                } catch (t: Throwable) {
                    console.warn("[SpatialSoundManager] Failed to play sound: ${t.message}")
                }
            }
        } else if (sound != null && !sound.isReady) {
            // Optional: log occasionally that it's not ready yet
        }
    }

    /**
     * Safely pauses a sound.
     */
    fun pause(sound: Sound?) {
        if (sound != null && sound.isPlaying) {
            try {
                console.log("[SpatialSoundManager] Pausing sound: ${sound.name}")
                sound.pause()
            } catch (t: Throwable) {
                console.warn("[SpatialSoundManager] Failed to pause sound: ${t.message}")
            }
        }
    }

    /**
     * Safely disposes a sound, handling potential BabylonJS internal errors.
     */
    fun safeDispose(sound: Sound?) {
        if (sound == null) return
        try {
            // Stop and detach before disposing
            if (sound.isPlaying) {
                try { sound.stop() } catch (_: Throwable) {}
            }
            try { sound.detachFromMesh() } catch (_: Throwable) {}
            
            // Critical fix for "this._soundV2 is undefined"
            // We use asDynamic to check if the internal _soundV2 exists before calling dispose.
            // This avoids the TypeError when BabylonJS attempts to call dispose() on an uninitialized internal node.
            val dynSound = sound.asDynamic()
            val hasSoundV2 = dynSound._soundV2 != null
            val hasSoundSource = dynSound._soundSource != null
            
            if (hasSoundV2 || hasSoundSource || sound.isReady) {
                sound.dispose()
            } else {
                console.warn("[SpatialSoundManager] Sound ${sound.name} is not fully initialized (isReady=${sound.isReady}, _soundV2=$hasSoundV2), attempting safe manual cleanup")
                // If it's not ready and has no soundV2, calling dispose() is risky in some Babylon versions.
                // However, we still want to remove it from the scene if possible.
                try {
                    val scene = dynSound._scene
                    if (scene != null && scene.mainSoundTrack != null) {
                        scene.mainSoundTrack.removeSound(sound)
                    }
                } catch (_: Throwable) {}
            }
        } catch (e: Throwable) {
            console.warn("[SpatialSoundManager] Error disposing sound: ${e.message}")
        }
    }
}
