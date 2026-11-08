package com.vrgames.gravitysling.core

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class AuditRegressionTest {
    @Test fun everyLevelMatchesLiveFlightAtVariableFrameRates() {
        for (level in Levels.all) for (hz in listOf(30, 60, 72, 90, 120)) {
            val s = GameSession(); s.load(level.id)
            val answer = level.solution!!
            answer.placements.forEach { (id, p) -> assertTrue(s.place(id, p)) }
            s.setPull(answer.pull)
            val expected = simulate(level, answer.pull, answer.placements, burnAt = answer.burnAt)
            s.scheduledBurn = answer.burnAt
            assertTrue(s.release())
            val events = ArrayList<FlightEvent>()
            var frames = 0
            while (s.phase == Phase.FLYING && frames++ < 5000) s.tick(1.0 / hz, events)
            assertEquals("${level.id} at $hz Hz", expected.status, s.lastStatus)
            assertEquals(expected.stars, s.lastStars)
            assertEquals(expected.time, s.flight!!.time, 1e-10)
            val case = "${level.id} at $hz Hz"
            assertEquals("$case burn count", if (answer.burnAt == null) 0 else 1, events.count { it == FlightEvent.Burned })
            assertEquals("$case delivery count", 1, events.count { it == FlightEvent.Delivered })
            assertEquals(3, events.filterIsInstance<FlightEvent.StarCollected>().map { it.index }.distinct().size)
        }
    }

    @Test fun seededStressNeverProducesInvalidFlightState() {
        val rng = Random(20261017)
        for (level in Levels.all) repeat(300) {
            val pull = Solver.randomDirection(rng) * (Physics.MIN_PULL + rng.nextDouble() * 0.3)
            val f = Flight(level, level.solution!!.placements); f.launch(pull)
            val events = ArrayList<FlightEvent>()
            var steps = 0
            while (f.status == FlightStatus.FLYING && steps++ < 10000) {
                f.step(events)
                assertTrue("${level.id} finite", listOf(f.x,f.y,f.z,f.vx,f.vy,f.vz).all { it.isFinite() })
            }
            assertNotEquals("${level.id} terminates", FlightStatus.FLYING, f.status)
            assertTrue(f.starCount in 0..3)
        }
    }

    @Test fun pauseHitchDoesNotTeleportAndResetCannotResumeFlight() {
        val s = GameSession(); s.setPull(s.level.solution!!.pull); s.release()
        s.tick(30.0, ArrayList())
        assertTrue(s.flight!!.time <= GameSession.MAX_FRAME + Physics.DT)
        s.reset(); s.tick(1.0, ArrayList())
        assertEquals(Phase.AIMING, s.phase); assertNull(s.flight)
        assertEquals(Vec3.ZERO, s.pull)
    }

    @Test fun failedAndShortShotsNeverUnlockNextLevel() {
        val s = GameSession()
        assertFalse(s.release())
        s.setPull(Vec3(0.0,0.0,-0.25)); s.release()
        repeat(1000) { s.tick(0.05, ArrayList()) }
        assertNotEquals(FlightStatus.SUCCESS, s.lastStatus)
        assertFalse(s.progress.unlocked(Levels.all[1]))
    }

    @Test fun flightFreezesPlacementAndIgnoresRepeatedRelease() {
        val s = GameSession(); s.load("1-3")
        val a = s.level.solution!!; a.placements.forEach { (id,p) -> s.place(id,p) }
        s.setPull(a.pull); assertTrue(s.release())
        assertFalse(s.release()); assertEquals(1,s.attempts)
        assertFalse(s.place("ball1", s.level.movables[0].pos))
        s.setPull(Vec3.ZERO); assertEquals(a.pull,s.pull)
    }

    @Test fun saveAcceptsZeroStarClearAndRejectsMalformedEntries() {
        val p = Progress.parse("1-1:0,garbage:3,1-2:no,1-3:999,1-4:-5")
        assertTrue(p.cleared("1-1")); assertTrue(p.unlocked(Levels.all[1]))
        assertFalse(p.cleared("1-2")); assertEquals(3,p.stars("1-3"))
        assertEquals(0,p.stars("1-4")); assertEquals(p.serialize(),Progress.parse(p.serialize()).serialize())
    }
}
