package com.vrgames.gravitysling.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** First-time coaching: saved topics and the burn slow motion. */
class CoachTest {

    /** Advances a frame the way the playfield does: real time scaled by the coaching. */
    private fun frame(s: GameSession, events: MutableList<FlightEvent>) = s.tick(FRAME * s.timeScale(), events)

    private fun coachedFlight(id: String): GameSession {
        val s = GameSession()
        s.load(id)
        s.coachBurn = true
        s.setPull(s.level.solution!!.pull)
        assertTrue(s.release())
        return s
    }

    @Test
    fun topicsAreSavedAndParsed() {
        val c = Coach()
        assertFalse(c.done(Coach.Topic.BURN))
        assertTrue(c.complete(Coach.Topic.MAP))
        assertFalse(c.complete(Coach.Topic.MAP))
        assertTrue(c.complete(Coach.Topic.BALL))
        assertEquals("ball,map", c.serialize())
        val back = Coach.parse(c.serialize())
        assertTrue(back.done(Coach.Topic.BALL))
        assertTrue(back.done(Coach.Topic.MAP))
        assertFalse(back.done(Coach.Topic.BURN))
        assertFalse(Coach.parse(null).done(Coach.Topic.BALL))
        assertFalse(Coach.parse("junk,,").done(Coach.Topic.BALL))
    }

    @Test
    fun aFirstBurnAtTheCueKeepsTheReferenceShotDelivering() {
        // The reference shot without its own burn: a player who burns when told still delivers.
        // Where every burn would miss (2-4's station), that flight is not coached.
        val coached = ArrayList<String>()
        for (level in Levels.all.filter { it.burn && it.solution!!.burnAt == null }) {
            val sol = level.solution!!
            val cue = Coach.burnCueTime(level, sol.pull, sol.placements) ?: continue
            coached += level.id
            val plain = simulate(level, sol.pull, sol.placements)
            assertTrue("${level.id} cue $cue in flight ${plain.time}", cue!! >= Coach.CUE_EARLIEST && cue <= plain.time - Coach.CUE_MARGIN)
            assertEquals(level.id, FlightStatus.SUCCESS, simulate(level, sol.pull, sol.placements, burnAt = cue).status)
        }
        // The first burn level always coaches.
        assertTrue(coached.toString(), "2-1" in coached)
    }

    @Test
    fun cueSitsOnACloseFlybyWhenBurningThereIsSafe() {
        // 3-6 is the near-Jupiter burn: its shot swings close past the planet.
        val level = Levels.byId("3-6")
        val sol = level.solution!!
        val cue = Coach.burnCueTime(level, sol.pull, sol.placements)!!
        val jupiter = level.bodies.first { it.kind == BodyKind.PLANET && it.orbit == null }
        val r = simulate(level, sol.pull, sol.placements, sampleEvery = 1)
        val atCue = r.path[(cue / Physics.DT).toInt()].distanceTo(jupiter.pos)
        assertTrue("at cue $atCue, radius ${jupiter.radius}", atCue < jupiter.radius * Coach.CUE_CLOSE)
    }


    @Test
    fun flightSlowsAroundTheCueUntilThePlayerBurns() {
        val s = coachedFlight("2-1")
        val cue = s.burnCue!!
        assertEquals(1.0, s.timeScale(), 0.0)
        val events = ArrayList<FlightEvent>()
        var slowest = 1.0
        var frames = 0
        while (s.flight!!.time < cue + GameSession.CUE_HOLD / 2) {
            frame(s, events)
            slowest = minOf(slowest, s.timeScale())
            frames++
        }
        assertEquals(GameSession.CUE_SLOW, slowest, 1e-9)
        assertTrue(s.inBurnCue)
        assertTrue(s.burnCoaching)
        // The slow window lasts several seconds of real time.
        assertTrue("frames $frames", frames > 72 * 2)
        assertTrue(s.requestBurn())
        assertEquals(1.0, s.timeScale(), 0.0)
        frame(s, events)
        assertTrue(s.playerBurned)
        assertFalse(s.burnCoaching)
        assertEquals(1, events.count { it is FlightEvent.Burned })
    }

    @Test
    fun slowMotionEndsOnItsOwnIfThePlayerWaits() {
        val s = coachedFlight("2-1")
        val cue = s.burnCue!!
        val events = ArrayList<FlightEvent>()
        while (s.phase == Phase.FLYING && s.flight!!.time < cue + GameSession.CUE_HOLD + GameSession.CUE_RAMP + 0.05) frame(s, events)
        assertEquals(1.0, s.timeScale(), 0.0)
        assertFalse(s.inBurnCue)
        assertFalse(s.playerBurned)
        // Same physics as a flight without coaching: slow motion only stretches real time.
        while (s.phase == Phase.FLYING) frame(s, events)
        val plain = simulate(s.level, s.level.solution!!.pull, s.level.solution!!.placements)
        assertEquals(plain.status, s.lastStatus)
        assertEquals(plain.time, s.flight!!.time, 1e-9)
    }

    @Test
    fun noCoachingWhenOffOnLevelsWithoutBurnOrForReplays() {
        val off = GameSession()
        off.load("2-1")
        off.setPull(off.level.solution!!.pull)
        off.release()
        assertNull(off.burnCue)
        assertEquals(1.0, off.timeScale(), 0.0)

        val chapter1 = coachedFlight("1-3")
        assertNull(chapter1.burnCue)

        // A test replay: no slow motion, and its scheduled burn does not count as the player's.
        val replay = coachedFlight("2-6")
        replay.skipBurnCue()
        replay.scheduledBurn = replay.level.solution!!.burnAt
        val events = ArrayList<FlightEvent>()
        while (replay.phase == Phase.FLYING) frame(replay, events)
        assertEquals(1, events.count { it is FlightEvent.Burned })
        assertFalse(replay.playerBurned)

        // A retry starts clean.
        replay.reset()
        assertNull(replay.burnCue)
        assertFalse(replay.playerBurned)
    }

    companion object {
        private const val FRAME = 1 / 72.0
    }
}
