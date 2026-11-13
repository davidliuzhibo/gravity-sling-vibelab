package com.vrgames.gravitysling.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Mid-flight burn, docked/optional gravity balls, challenges, blind badges, codex unlocks. */
class FeaturesTest {

    private fun flyToEnd(s: GameSession): List<FlightEvent> {
        val events = ArrayList<FlightEvent>()
        var guard = 0
        while (s.phase == Phase.FLYING && guard++ < 10_000) s.tick(1 / 72.0, events)
        return events
    }

    @Test
    fun burnSpeedsUpOnceAndKeepsDirection() {
        val level = Levels.byId("2-1")
        val f = Flight(level)
        f.launch(level.solution!!.pull)
        val ev = ArrayList<FlightEvent>()
        repeat(200) { f.step(ev) }
        val before = f.velocity()
        assertTrue(f.canBurn)
        assertTrue(f.burn())
        val after = f.velocity()
        assertEquals(Physics.BURN_FACTOR, after.length() / before.length(), 1e-9)
        assertEquals(1.0, after.normalized().dot(before.normalized()), 1e-9)
        assertFalse(f.burn())
        assertFalse(f.canBurn)
    }

    @Test
    fun noBurnInChapterOne() {
        for (level in Levels.all.filter { it.chapter == 1 }) assertFalse(level.id, level.burn)
        val f = Flight(Levels.byId("1-1"))
        f.launch(Levels.byId("1-1").solution!!.pull)
        assertFalse(f.burn())
    }

    @Test
    fun forkedBurnPredictsTheRealBurn() {
        val level = Levels.byId("2-2")
        val f = Flight(level)
        f.launch(level.solution!!.pull)
        val ev = ArrayList<FlightEvent>()
        repeat(150) { f.step(ev) }
        val g = f.fork()
        g.burn()
        val predicted = continueFlight(g, maxTime = f.time + 1.0)
        f.burn()
        while (f.status == FlightStatus.FLYING && f.time < predicted.time - 1e-9) f.step(ev)
        assertEquals(0.0, f.position().distanceTo(g.position()), 1e-12)
    }

    @Test
    fun pinchDuringFlightFiresTheEngineOnce() {
        val s = GameSession()
        s.load("2-1")
        s.setPull(s.level.solution!!.pull)
        assertTrue(s.release())
        val events = ArrayList<FlightEvent>()
        s.tick(0.3, events)
        assertTrue(s.burnPreview().isNotEmpty())
        assertTrue(s.requestBurn())
        assertFalse(s.requestBurn())
        s.tick(0.05, events)
        assertEquals(1, events.count { it is FlightEvent.Burned })
        assertTrue(s.flight!!.burnUsed)
        assertTrue(s.burnPreview().isEmpty())
    }

    @Test
    fun dockedBallDoesNothing() {
        for (level in Levels.all.filter { it.movables.isNotEmpty() }) {
            val pull = level.solution!!.pull
            val bare = level.copy(bodies = level.bodies.filter { !it.movable })
            val a = simulate(level, pull, sampleEvery = 20, burnAt = level.solution!!.burnAt)
            val b = simulate(bare, pull, sampleEvery = 20, burnAt = level.solution!!.burnAt)
            assertEquals(level.id, b.path, a.path)
        }
    }

    @Test
    fun optionalBallsOpenOtherRoutes() {
        val optional = Levels.all.filter { it.movables.isNotEmpty() && it.solution!!.placements.isEmpty() }
        assertEquals(listOf("2-1", "3-1"), optional.map { it.id })
        for (level in optional) {
            val rng = Random(9)
            val ball = level.movables.first()
            var found = false
            var tries = 0
            while (!found && tries++ < 30_000) {
                val b = level.bounds
                val p = Vec3(
                    b.min.x + rng.nextDouble() * (b.max.x - b.min.x),
                    b.min.y + rng.nextDouble() * (b.max.y - b.min.y),
                    b.min.z + rng.nextDouble() * (b.max.z - b.min.z),
                )
                if (!Placement.isValid(level, ball.id, p, emptyMap())) continue
                val pull = Solver.randomDirection(rng) * (Physics.MIN_PULL + rng.nextDouble() * (level.maxPull - Physics.MIN_PULL))
                if (simulate(level, pull, mapOf(ball.id to p)).status != FlightStatus.SUCCESS) continue
                // The ball has to be what makes this route work.
                found = simulate(level, pull).status != FlightStatus.SUCCESS
            }
            assertTrue("${level.id}: no delivery that needs the ball", found)
        }
    }

    @Test
    fun challengesNeedTheirTwist() {
        for (level in Levels.all.filter { it.challenge }) {
            val sol = level.solution!!
            if (sol.burnAt != null) {
                // No shot without the burn delivers.
                val rng = Random(3)
                var ok = 0
                repeat(4000) {
                    val pull = Solver.randomDirection(rng) * (Physics.MIN_PULL + rng.nextDouble() * (level.maxPull - Physics.MIN_PULL))
                    if (simulate(level, pull, sol.placements).status == FlightStatus.SUCCESS) ok++
                }
                assertEquals("${level.id} delivers without a burn", 0, ok)
            } else {
                assertTrue("${level.id} needs a burn or a ball", sol.placements.isNotEmpty())
            }
        }
    }

    @Test
    fun movingStationMustBeLed() {
        val moving = Levels.all.filter { it.goalOrbit != null }
        assertTrue(moving.isNotEmpty())
        for (level in moving) {
            val sol = level.solution!!
            val r = simulate(level, sol.pull, sol.placements, sampleEvery = 1, burnAt = sol.burnAt)
            assertEquals(level.id, FlightStatus.SUCCESS, r.status)
            // Where you meet the station is far from where it waits at launch: aiming at it misses.
            assertTrue(level.id, r.path.last().distanceTo(level.goal.pos) > 0.25)
        }
    }

    @Test
    fun challengesUnlockAfterTheirChapterAndGateNothing() {
        val p = Progress.parse("1-1:3,1-2:3,1-3:3,1-4:3")
        assertFalse(p.unlocked(Levels.byId("1-6")))
        p.record("1-5", 1)
        assertTrue(p.unlocked(Levels.byId("1-6")))
        assertTrue(p.unlocked(Levels.byId("2-1")))
        assertTrue(p.chapterCleared(1))
        val all = Progress.parse(Levels.main.joinToString(",") { "${it.id}:1" })
        assertTrue(all.allCleared())
        assertTrue(Levels.all.filter { it.challenge }.all { all.unlocked(it) })
    }

    @Test
    fun blindBadgesAreSavedAndKept() {
        val p = Progress()
        assertTrue(p.record("1-1", 2, blind = true))
        assertTrue(p.badge("1-1"))
        assertFalse(p.record("1-1", 1))
        assertTrue(p.record("1-1", 3))
        assertEquals("1-1:3b", p.serialize())
        val q = Progress.parse("1-1:3b,1-2:2")
        assertTrue(q.badge("1-1"))
        assertFalse(q.badge("1-2"))
        assertEquals(5, q.totalStars())
        assertEquals("1-1:3b,1-2:2", q.serialize())
    }

    @Test
    fun blindHidesTheAimLineAndHint() {
        val s = GameSession()
        s.showHint = true
        s.blind = true
        assertFalse(s.showHint)
        s.setPull(v(0.0, 0.0, 0.15))
        assertTrue(s.preview().isEmpty())
        s.blind = false
        assertTrue(s.preview().isNotEmpty())
    }

    @Test
    fun everyCardIsUnlockedExactlyOnce() {
        assertEquals(Codex.cards.size, Codex.cards.map { it.id }.toSet().size)
        val unlocks = Levels.all.flatMap { it.unlocks }
        assertEquals(unlocks.size, unlocks.toSet().size)
        assertEquals(Codex.cards.map { it.id }.toSet(), unlocks.toSet())
        Codex.cards.forEach { assertTrue(it.id, it.fact.isNotBlank() && it.fact.length <= 40) }
    }

    @Test
    fun everyCardHasItsShelfFiguresAndAKnownRoute() {
        // Every shelf has cards; every card shows at least one figure; every stop of a route can be drawn.
        Shelf.entries.forEach { assertTrue(it.title, Codex.onShelf(it).isNotEmpty()) }
        Codex.cards.forEach { c ->
            assertTrue(c.id, c.stats.isNotEmpty())
            c.route.forEach { stop -> assertNotNull("${c.id} $stop", Route.ORBITS[stop]) }
        }
        assertEquals(37, Codex.cards.size)
    }

    @Test
    fun newCardsAreReportedOnlyTheFirstTime() {
        val s = GameSession()
        val sol = s.level.solution!!
        s.setPull(sol.pull)
        s.release()
        flyToEnd(s)
        assertEquals(listOf("slingshot", "earth", "cosmic_speed"), s.newCards)
        s.reset()
        s.setPull(sol.pull)
        s.release()
        flyToEnd(s)
        assertTrue(s.newCards.isEmpty())
    }
}
