package com.vrgames.gravitysling

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vrgames.gravitysling.core.*
import com.vrgames.gravitysling.game.Game
import com.vrgames.gravitysling.game.RuntimeProbe
import com.vrgames.gravitysling.platform.LaunchActivity
import com.vrgames.gravitysling.render.Grab
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Uses real scene frame callbacks and render objects; it does NOT emulate a physical hand. */
@RunWith(AndroidJUnit4::class)
class RuntimeAuditTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private fun main(block: () -> Unit) = inst.runOnMainSync(block)
    private fun waitFor(label: String, seconds: Int = 40, check: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + seconds * 1000
        while (SystemClock.uptimeMillis() < end) {
            var ok = false; main { ok = check() }
            if (ok) return
            SystemClock.sleep(100)
        }
        fail("Timed out: $label")
    }
    @Test fun allLevelsCancelRetryPersistenceAndNativeScene() {
        val context = inst.targetContext
        val prefs = context.getSharedPreferences("gravity_sling", Context.MODE_PRIVATE)
        val backup = HashMap(prefs.all)
        try {
            context.startActivity(Intent(context, LaunchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            waitFor("initial scene") { RuntimeProbe.current() != null }
            main { assertFalse("silent startup", Game.audioEnabled); Game.startGame() }
            val startAt = InstrumentationRegistry.getArguments().getString("startAt") ?: Levels.all.first().id
            val startIndex = Levels.all.indexOfFirst { it.id == startAt }
            require(startIndex >= 0) { "Unknown startAt: $startAt" }
            // For resuming a slow-device test run only. Earlier levels must have separate PASS evidence.
            main { Levels.all.take(startIndex).forEach { Game.progress().record(it.id, 3) } }
            for (level in Levels.all.drop(startIndex)) {
                main { Game.selectLevel(level.id) }
                waitFor("scene ${level.id}") { RuntimeProbe.readyLevel == level.id && RuntimeProbe.readyVersion == Game.levelVersion }
                main {
                    val pf = RuntimeProbe.current()!!
                    assertFalse(pf.sound!!.enabled)
                    val attempts = Game.session.attempts
                    pf.dragStart(Grab.Probe, Game.session)
                    pf.drag(level.solution!!.pull, Game.session)
                    pf.dragCancel(Game.session)
                    assertEquals(Phase.AIMING,Game.session.phase)
                    assertEquals(attempts,Game.session.attempts)
                    assertEquals(Vec3.ZERO,Game.session.pull)
                    // Actual render/input methods, then normal SceneEvents.Update advances time.
                    level.solution!!.placements.forEach { (id,p) ->
                        pf.dragStart(Grab.Ball(id),Game.session)
                        pf.drag(p - Game.session.placements.getValue(id), Game.session)
                        pf.dragEnd(Game.session)
                        assertTrue(p.distanceTo(Game.session.placements.getValue(id)) < 1e-9)
                    }
                    Game.toggleHint(); Game.toggleFullPreview()
                    pf.dragStart(Grab.Probe,Game.session)
                    pf.drag(level.solution!!.pull,Game.session)
                    assertTrue(pf.dragEnd(Game.session)); Game.refresh()
                }
                // Headless PICO 6.1 runs substantially slower than real time on a 4 GB laptop GPU.
                waitFor("delivery ${level.id}",180) { Game.phase == Phase.RESULT }
                main {
                    assertEquals(level.id,FlightStatus.SUCCESS,Game.lastStatus)
                    assertEquals(3,Game.lastStars)
                    assertTrue(Game.progress().cleared(level.id))
                    android.util.Log.i("GravityAudit","PASS ${level.id} stars=3 silent=${!Game.audioEnabled}")
                }
            }
            main {
                assertEquals(45,Game.progress().totalStars())
                assertEquals(15,Game.progress().unlockedCards().size)
                assertTrue(Game.celebration!!.allDone)
            }
            waitFor("persisted 45 stars") { Progress.parse(prefs.getString("progress",null)).totalStars() == 45 }
            // Level changes invalidate an in-progress drag before the next scene rebuild.
            main {
                Game.selectLevel("1-1")
            }
            waitFor("revisit first") { RuntimeProbe.readyLevel == "1-1" }
            main {
                val pf=RuntimeProbe.current()!!
                pf.dragStart(Grab.Probe,Game.session); pf.drag(Vec3(0.0,0.0,0.15),Game.session)
                Game.selectLevel("3-5")
                assertFalse(pf.dragEnd(Game.session))
                assertFalse(pf.frame(0.02,Game.session))
            }
            waitFor("final revisit") { RuntimeProbe.readyLevel == "3-5" }
            repeat(12) {
                main { Game.retry() }
                SystemClock.sleep(150)
            }
            SystemClock.sleep(15000)
            main { assertNotNull(RuntimeProbe.current()); assertFalse(Game.audioEnabled) }
        } finally {
            val edit = prefs.edit().clear()
            backup.forEach { (k,v) -> when(v) {
                is String -> edit.putString(k,v)
                is Boolean -> edit.putBoolean(k,v)
                is Int -> edit.putInt(k,v)
                is Long -> edit.putLong(k,v)
                is Float -> edit.putFloat(k,v)
                is Set<*> -> @Suppress("UNCHECKED_CAST") edit.putStringSet(k,v as Set<String>)
            } }
            assertTrue("restore original progress",edit.commit())
        }
    }
}
