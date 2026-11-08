package com.vrgames.gravitysling.game

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.pico.spatial.core.ecs.DirectionalLightComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.EulerAngles
import com.vrgames.gravitysling.core.FlightStatus
import com.vrgames.gravitysling.core.Levels

/**
 * Test hook, debug builds only: plays levels with their intended solution so a full run can be
 * checked on the emulator without hand tracking. Driven over adb; results go to logcat (tag
 * GravityTest), one "result" line per flight and a "done" summary.
 *
 *   adb shell am broadcast -a com.vrgames.gravitysling.TEST -p com.vrgames.gravitysling --es cmd all
 *
 * cmd: all (1-1 to 3-6, challenges included) · level --es id 2-3 · shot (current level) · reset (wipe save) · stop ·
 * mute --es id hum|sfx|music|all|none (to find which sound is wrong while someone listens) ·
 * view --es id "x,y,z,yaw,scale"|reset (bring the playfield in front of the emulator camera) ·
 * slow --es id 0.05 (time scale, to catch short effects in a screenshot) · ibl --es id 9 (IBL exponent) ·
 * miss (fly into the nearest planet: crash effect) · sky --es id 0|1 (room / star sky) ·
 * map --es id 0|1 (star map) · pick --es id 2-3 (pinch that star on the map) · blind --es id 0|1 ·
 * burn (fire the engine now, as a pinch during the flight does) ·
 * coach [--es id hints] (show the tutorial and every coaching hint again; hints: skip the slingshot tutorial) · guide --es id 0|1 (how-to-play card) ·
 * coachshot (fire the current level like a player: burn coaching slows the flight, no automatic burn) ·
 * pull --es id sol|alt|off (hold the probe at a pull as a hand would; logs the aim line 1.5 s later)
 */
object AutoPilot {
    const val ACTION = "com.vrgames.gravitysling.TEST"
    private const val TAG = "GravityTest"
    /** Time for the stage to rebuild a level before shooting. */
    private const val LOAD_DELAY_MS = 2500L
    /** Time to watch the delivery (confetti, chapter card) before the next level. */
    private const val NEXT_DELAY_MS = 3500L

    private val main = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<String>()
    private var running = false
    private val results = ArrayList<Pair<String, Int>>()

    // Frame timing since the last shot; written from the frame callback.
    @Volatile private var frames = 0
    @Volatile private var frameTime = 0.0
    @Volatile private var worstFrame = 0.0

    fun register(context: Context) {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = command(i.getStringExtra("cmd") ?: "", i.getStringExtra("id"))
        }
        ContextCompat.registerReceiver(context.applicationContext, receiver, IntentFilter(ACTION), ContextCompat.RECEIVER_EXPORTED)
        Log.i(TAG, "ready")
    }

    private fun command(cmd: String, id: String?) {
        Log.i(TAG, "cmd $cmd ${id.orEmpty()}")
        when (cmd) {
            "all" -> start(Levels.all.map { it.id })
            "level" -> if (Levels.all.any { it.id == id }) start(listOf(id!!)) else Log.w(TAG, "unknown level $id")
            "shot" -> shoot()
            "pull" -> Game.requestDebugPull(id ?: "off")
            "miss" -> { frames = 0; frameTime = 0.0; worstFrame = 0.0; Game.requestAutoShot(miss = true) }
            "sky" -> Game.skyOn = id != "0"
            "nrm" -> id?.toFloatOrNull()?.let { Game.assets?.setNormalScale(it) }
            "reset" -> { stop(); Game.resetSave() }
            "stop" -> stop()
            "mute" -> mute(id.orEmpty())
            "view" -> Game.debugView = id?.takeIf { it != "reset" }
            "slow" -> Game.timeScale = id?.toDoubleOrNull() ?: 1.0
            "ibl" -> id?.toFloatOrNull()?.let { Game.lighting?.exponent = it }
            "sun" -> sun(id.orEmpty())
            "sph" -> Game.assets?.tangentSphere = id != "0"
            "map" -> Game.mapShown = id != "0"
            "pick" -> Log.i(TAG, "pick ${id.orEmpty()} ${if (Game.pickFromMap(id.orEmpty())) "opened" else "locked"}")
            "blind" -> if (Game.blind != (id != "0")) Game.toggleBlind()
            "burn" -> Log.i(TAG, "burn ${Game.requestBurn()}")
            "coach" -> Game.replayCoaching(slingshot = id != "hints")
            "guide" -> Game.showGuide = id != "0"
            "coachshot" -> { frames = 0; frameTime = 0.0; worstFrame = 0.0; Game.requestAutoShot(coached = true) }
            else -> Log.w(TAG, "unknown cmd $cmd")
        }
    }

    /** "lux" or "lux,pitch,yaw": retunes the key light live. */
    private fun sun(arg: String) {
        val v = arg.split(',').mapNotNull { it.trim().toFloatOrNull() }
        val e = Game.sun ?: return
        v.getOrNull(0)?.let { e.components[DirectionalLightComponent::class.java]?.intensity = it }
        if (v.size >= 3) e.components[TransformComponent::class.java]?.setEulerAngles(EulerAngles(v[1], v[2], 0f))
    }

    private fun mute(what: String) {
        val all = what == "all"
        Game.sound?.let {
            it.humsMuted = all || what == "hum"
            it.enabled = !(all || what == "sfx")
        }
        Game.music?.muted = all || what == "music"
        Log.i(TAG, "muted $what")
    }

    private fun start(ids: List<String>) {
        stop()
        queue.addAll(ids)
        results.clear()
        running = true
        Game.startGame()
        next()
    }

    private fun stop() {
        running = false
        queue.clear()
        main.removeCallbacksAndMessages(null)
    }

    private fun next() {
        if (!running) return
        val id = queue.removeFirstOrNull()
        if (id == null) {
            running = false
            val passed = results.count { it.second > 0 }
            Log.i(TAG, "done passed=$passed/${results.size} stars=${results.sumOf { it.second }}/${results.size * 3}")
            return
        }
        Game.dismissCelebration()
        Game.selectLevel(id)
        main.postDelayed({ if (running) shoot() }, LOAD_DELAY_MS)
    }

    private fun shoot() {
        frames = 0; frameTime = 0.0; worstFrame = 0.0
        Game.requestAutoShot()
    }

    /** Called by the stage after it tried to fire the intended shot. */
    fun onShot(fired: Boolean) {
        if (fired) return
        Log.w(TAG, "result ${Game.levelId} NOT_FIRED")
        if (running) { results += Game.levelId to 0; main.postDelayed(::next, NEXT_DELAY_MS) }
    }

    fun onFrame(dt: Double) {
        frames++
        frameTime += dt
        if (dt > worstFrame) worstFrame = dt
    }

    fun onFlightFinished(levelId: String, status: FlightStatus?, stars: Int) {
        val fps = if (frameTime > 0) frames / frameTime else 0.0
        Log.i(TAG, "result $levelId $status stars=$stars fps=%.1f worst=%.0fms".format(fps, worstFrame * 1000))
        if (!running) return
        results += levelId to if (status == FlightStatus.SUCCESS) stars else 0
        main.postDelayed(::next, NEXT_DELAY_MS)
    }
}
