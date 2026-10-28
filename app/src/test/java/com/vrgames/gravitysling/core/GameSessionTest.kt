package com.vrgames.gravitysling.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameSessionTest {

    private fun flyToEnd(s: GameSession): List<FlightEvent> {
        val events = ArrayList<FlightEvent>()
        var guard = 0
        while (s.phase == Phase.FLYING && guard++ < 10_000) s.tick(1 / 72.0, events)
        return events
    }

    @Test
    fun intendedShotPlayedFrameByFrameMatchesPreview() {
        for (level in Levels.all) {
            val s = GameSession()
            s.load(level.id)
            val sol = level.solution!!
            sol.placements.forEach { (id, p) -> assertTrue(s.place(id, p)) }
            s.setPull(sol.pull)
            assertTrue(s.release())
            s.scheduledBurn = sol.burnAt
            val events = flyToEnd(s)
            assertEquals(level.id, FlightStatus.SUCCESS, s.lastStatus)
            assertEquals(level.id, 3, s.lastStars)
            assertTrue(events.last() is FlightEvent.Delivered)
            assertEquals(3, events.count { it is FlightEvent.StarCollected })
            assertEquals(level.checkpoints.size, events.count { it is FlightEvent.CheckpointPassed })
            assertTrue(s.progress.cleared(level.id))
        }
    }

    @Test
    fun shortPullCancelsInsteadOfFiring() {
        val s = GameSession()
        s.setPull(v(0.0, 0.0, 0.01))
        assertFalse(s.release())
        assertEquals(Phase.AIMING, s.phase)
        assertEquals(0, s.attempts)
        assertTrue(s.preview().isEmpty())
    }

    @Test
    fun pullIsClampedAndPreviewIsFullByDefault() {
        val s = GameSession()
        s.setPull(v(0.0, 0.0, 2.0))
        assertEquals(Physics.MAX_PULL, s.pull.length(), 1e-9)
        assertTrue(s.fullPreview)
        val full = s.preview()
        s.fullPreview = false
        s.invalidatePreview()
        val short = s.preview()
        // The full line reaches further along the flight (it is sampled more sparsely).
        assertTrue(full.last().distanceTo(s.level.launch) > short.last().distanceTo(s.level.launch))
    }

    @Test
    fun crashThenRetryKeepsPlacementsAndCountsAttempts() {
        val s = GameSession()
        s.load("1-2")
        s.setPull(v(0.0, -0.002, 0.15)) // straight into the moon
        s.release()
        flyToEnd(s)
        assertEquals(FlightStatus.CRASHED, s.lastStatus)
        assertFalse(s.progress.cleared("1-2"))
        s.reset()
        assertEquals(Phase.AIMING, s.phase)
        assertEquals(1, s.attempts)
    }

    @Test
    fun ballMayGoBesideTheLauncherButNotIntoThePull() {
        val s = GameSession()
        s.load("1-3")
        val l = s.level.launch
        // Beside and in front of the launcher: allowed (the device test found the old keep-out
        // threw the ball back to its dock all the time).
        assertTrue(s.canPlace("ball1", l + v(0.3, 0.05, -0.15)))
        assertTrue(s.canPlace("ball1", l + v(-0.25, 0.1, -0.2)))
        // On the launcher, and where the probe is pulled back toward the player: refused, with a reason.
        assertEquals("离发射架太近", Placement.blocked(s.level, "ball1", l + v(0.05, 0.0, 0.0), emptyMap()))
        assertEquals("挡住了拉弓的位置", Placement.blocked(s.level, "ball1", l + v(0.0, -0.05, 0.18), emptyMap()))
    }

    @Test
    fun illegalPlacementIsRejected() {
        val s = GameSession()
        s.load("1-3")
        val moon = s.level.bodies.first { it.id == "moon" }
        assertFalse(s.place("ball1", moon.pos))
        assertFalse(s.place("ball1", s.level.launch))
        assertTrue(s.place("ball1", s.level.solution!!.placements.getValue("ball1")))
    }

    @Test
    fun progressRoundTripsAndUnlocksInOrder() {
        val p = Progress.parse("1-1:3,1-2:1,bogus:3,1-3:x")
        assertEquals(3, p.stars("1-1"))
        assertEquals(1, p.stars("1-2"))
        assertFalse(p.cleared("1-3"))
        assertTrue(p.unlocked(Levels.byId("1-3")))
        assertFalse(p.unlocked(Levels.byId("1-4")))
        assertFalse(p.record("1-1", 2))
        assertTrue(p.record("1-2", 2))
        assertEquals("1-1:3,1-2:2", p.serialize())
        assertEquals(listOf("slingshot", "earth", "moon"), p.unlockedCards())
    }

    @Test
    fun everyUnlockHasACodexCard() {
        for (l in Levels.all) {
            assertTrue(l.id, l.unlocks.isNotEmpty())
            l.unlocks.forEach { assertNotNull("${l.id} $it", Codex.byId(it)) }
        }
    }
}
