package com.vrgames.gravitysling.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Every level must be beatable with 3 stars and forgiving enough for a real pinch-release. */
class LevelsTest {

    @Test
    fun intendedShotEarnsThreeStars() {
        for (level in Levels.all) {
            val s = level.solution!!
            val r = simulate(level, s.pull, s.placements, burnAt = s.burnAt)
            assertEquals("${level.id} status", FlightStatus.SUCCESS, r.status)
            assertEquals("${level.id} stars", level.stars.size, r.stars)
        }
    }

    @Test
    fun intendedPlacementsAreLegal() {
        for (level in Levels.all) {
            val s = level.solution!!
            for ((id, p) in s.placements) {
                assertTrue("${level.id} $id placement", Placement.isValid(level, id, p, s.placements - id))
            }
            for (b in level.movables) {
                assertTrue("${level.id} ${b.id} dock", Placement.isValid(level, b.id, b.pos, emptyMap()))
            }
        }
    }

    @Test
    fun deliveryIsForgivingToHandJitter() {
        for (level in Levels.all) {
            val deliver = Solver.robustness(level, level.solution!!, requireAllStars = false)
            // Chapter finales and challenges are allowed to be harder.
            val min = if (level.challenge || (level.number == 5 && level.chapter > 1)) 0.25 else 0.35
            assertTrue("${level.id} delivery robustness $deliver", deliver >= min)
        }
    }

    @Test
    fun movablesMatterWhereIntended() {
        // Where the intended answer places a ball, leaving it in the dock must not earn 3 stars.
        for (level in Levels.all.filter { it.solution!!.placements.isNotEmpty() }) {
            val r = simulate(level, level.solution!!.pull, burnAt = level.solution!!.burnAt)
            assertTrue("${level.id} works without placing", r.status != FlightStatus.SUCCESS || r.stars < level.stars.size)
        }
    }

    @Test
    fun intendedShotUsesEveryBoosterAndPortal() {
        for (level in Levels.all.filter { it.boosters.isNotEmpty() || it.portals.isNotEmpty() }) {
            val s = level.solution!!
            val f = Flight(level, s.placements)
            f.launch(s.pull)
            val events = ArrayList<FlightEvent>()
            val all = ArrayList<FlightEvent>()
            while (f.status == FlightStatus.FLYING) {
                if (s.burnAt != null && f.time >= s.burnAt) f.burn()
                f.step(events); all.addAll(events)
            }
            assertEquals("${level.id} boosts", level.boosters.size, all.count { it is FlightEvent.Boosted })
            assertEquals("${level.id} warps", level.portals.size, all.count { it is FlightEvent.Warped })
        }
    }

    @Test
    fun boostersAndPortalsAreNecessary() {
        // Without the mechanic the intended pull must not earn 3 stars.
        for (level in Levels.all.filter { it.boosters.isNotEmpty() || it.portals.isNotEmpty() }) {
            val s = level.solution!!
            val bare = level.copy(boosters = emptyList(), portals = emptyList())
            val r = simulate(bare, s.pull, s.placements, burnAt = s.burnAt)
            assertTrue("${level.id} works without mechanics", r.status != FlightStatus.SUCCESS || r.stars < level.stars.size)
        }
    }

    @Test
    fun everyChapterHasFiveLevelsAndAChallenge() {
        assertEquals(listOf(1, 2, 3), Levels.all.map { it.chapter }.distinct())
        Levels.main.groupBy { it.chapter }.forEach { (c, ls) -> assertEquals("chapter $c", 5, ls.size) }
        Levels.all.groupBy { it.chapter }.forEach { (c, ls) ->
            assertEquals("chapter $c challenge", 1, ls.count { it.challenge })
            assertTrue("chapter $c challenge comes last", ls.last().challenge)
        }
    }

    @Test
    fun levelsHaveUniqueIdsAndOrder() {
        assertEquals(Levels.all.size, Levels.all.map { it.id }.toSet().size)
        Levels.all.groupBy { it.chapter }.forEach { (_, ls) ->
            assertEquals((1..ls.size).toList(), ls.map { it.number })
        }
    }

    /** Design report; run with -Dlab=true to print difficulty numbers for tuning. */
    @Test
    fun lab() {
        if (System.getProperty("lab") != "true") return
        for (level in Levels.all) {
            val s = level.solution!!
            val r = simulate(level, s.pull, s.placements, sampleEvery = 90, burnAt = s.burnAt)
            val deliver = Solver.robustness(level, s, requireAllStars = false)
            val three = Solver.robustness(level, s)
            val deliver3 = Solver.robustness(level, s, jitter = 0.03, requireAllStars = false)
            val rng = Random(1)
            var randomOk = 0
            repeat(2000) {
                val pull = Solver.randomDirection(rng) * (Physics.MIN_PULL + rng.nextDouble() * (level.maxPull - Physics.MIN_PULL))
                if (simulate(level, pull, s.placements, burnAt = s.burnAt).status == FlightStatus.SUCCESS) randomOk++
            }
            println("== ${level.id} ${level.title}: ${r.status} stars=${r.stars} t=${"%.2f".format(r.time)} " +
                "deliver±1.5cm=${"%.2f".format(deliver)} deliver±3cm=${"%.2f".format(deliver3)} 3star±1.5cm=${"%.2f".format(three)} random=${randomOk / 20.0}%")
            println("   path: " + r.path.joinToString(" ") { "(${"%.2f".format(it.x)},${"%.2f".format(it.y)},${"%.2f".format(it.z)})" })
            println("   goal=${level.goal.pos.fmt()} stars=${level.stars.joinToString { it.fmt() }}")
            level.bodies.forEachIndexed { i, b ->
                val minD = r.path.minOf { it.distanceTo(b.pos) }
                println("   ${b.id}: minDist≈${"%.2f".format(minD)} (r=${b.radius})")
            }
        }
    }

    private fun Vec3.fmt() = "(${"%.2f".format(x)},${"%.2f".format(y)},${"%.2f".format(z)})"
}
