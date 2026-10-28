package com.vrgames.gravitysling.render

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ObjectAudioComponent
import com.pico.spatial.core.ecs.audio.AudioPlayerController
import com.pico.spatial.core.ecs.resource.AudioResource
import com.pico.spatial.core.ecs.LoadType
import com.vrgames.gravitysling.core.Vec3

enum class Sfx(val file: String, val volume: Float) {
    STRETCH("stretch", 0.5f),
    LAUNCH("launch", 0.9f),
    STAR("star", 0.8f),
    CHECKPOINT("checkpoint", 0.8f),
    DELIVERED("delivered", 1f),
    CRASH("crash", 0.9f),
    PLACE("place", 0.6f),
}

/**
 * Spatial sound for the playfield. Each effect has one long-lived emitter entity with a prepared
 * controller: playing an effect only moves the emitter and restarts it, which avoids creating
 * native audio players per event (a known frame-drop source). Must be used on the main thread.
 */
class Sound(private val parent: Entity) {
    private val emitters = HashMap<Sfx, Entity>()
    private val controllers = HashMap<Sfx, AudioPlayerController>()
    private var hum: AudioResource? = null
    private val humControllers = ArrayList<AudioPlayerController>()
    var enabled = true
    var humsMuted = false
        set(v) {
            field = v
            humControllers.forEach { runCatching { if (v) it.stop() else it.play() } }
        }

    /** Loads every clip. Failures are logged and ignored (the game then plays silently). */
    fun load() {
        for (s in Sfx.entries) {
            runCatching {
                val res = AudioResource.load(s.file, "asset://audio/${s.file}.wav", LoadType.FROM_ASSETS)
                val e = Entity()
                e.components.set(ObjectAudioComponent(s.volume))
                parent.addChild(e)
                val c = e.prepareAudio(res)
                if (c.valid) {
                    emitters[s] = e
                    controllers[s] = c
                }
            }.onFailure { android.util.Log.w(TAG, "sfx ${s.file}: ${it.message}") }
        }
        // Planets come and go with levels, so the hum clip must outlive them.
        runCatching { hum = AudioResource.load("hum", "asset://audio/hum.wav", LoadType.FROM_ASSETS).shared() }
            .onFailure { android.util.Log.w(TAG, "hum: ${it.message}") }
    }

    fun play(s: Sfx, at: Vec3) {
        if (!enabled) return
        val c = controllers[s] ?: return
        emitters[s]?.setPos(at)
        runCatching {
            if (c.isPlaying()) c.stop()
            c.seekTo(0)
            c.play()
        }
    }

    /** Gives a planet a quiet looping hum so you can hear where the big masses are. */
    fun attachHum(body: Entity, volume: Float) {
        val res = hum ?: return
        runCatching {
            body.components.set(ObjectAudioComponent(volume))
            val c = body.playAudio(res)
            if (c.valid) {
                c.setLoop(true)
                if (humsMuted) c.stop()
                humControllers.add(c)
            }
        }
    }

    /** Stops hums of entities about to be destroyed (level change). */
    fun releaseHums() {
        humControllers.forEach { runCatching { it.stop() } }
        humControllers.clear()
    }

    companion object {
        private const val TAG = "GravitySound"
    }
}
