package com.vrgames.gravitysling.core

import org.junit.Test
import kotlin.random.Random

/**
 * Search harness for 1-3 (the level that introduces the gravity ball); run with -Dlab=ball.
 * Looks for a shot that is pulled straight toward the player (no awkward downward pull), a ball
 * spot within easy reach of its dock, and a station at about launcher height that the plain shot
 * misses, so the ball is what makes the delivery.
 */
class BallLab {
    @Test
    fun search() {
        if (System.getProperty("lab") != "ball") return
        val dock = Vec3(0.45, -0.3, 0.2)
        data class Hit(val score: Double, val text: String)
        val hits = ArrayList<Hit>()
        for (mu in listOf(0.025, 0.035)) {
            val bodies = listOf(
                BodySpec("earth", "地球", BodyKind.PLANET, Vec3(-0.85, -0.4, -0.2), 0.2, 0.03, look = "earth"),
                BodySpec("moon", "月球", BodyKind.MOON, Vec3(0.45, -0.25, -0.75), 0.08, 0.012, look = "moon"),
                BodySpec("ball1", "引力球", BodyKind.GRAVITY_BALL, dock, 0.05, mu, look = "gravity_ball", movable = true),
            )
            for (bx in listOf(-0.1, 0.0, 0.1, 0.2, 0.3, 0.4)) for (by in listOf(-0.2, -0.1, 0.0, 0.1, 0.2)) for (bz in listOf(-0.5, -0.4, -0.3, -0.2, -0.1)) {
                val spot = Vec3(bx, by, bz)
                val reach = spot.distanceTo(dock)
                if (reach > 0.6) continue
                for (px in listOf(-0.04, -0.02, 0.0, 0.02, 0.04)) for (py in listOf(-0.04, -0.02, 0.0)) for (pz in listOf(0.11, 0.14, 0.17)) {
                    val pull = Vec3(px, py, pz)
                    for (gt in listOf(2.4, 2.8, 3.2)) {
                        val level = runCatching {
                            Levels.authored(
                                id = "lab", chapter = 1, number = 3, title = "", brief = "", tip = "",
                                bodies = bodies, pull = pull, placements = mapOf("ball1" to spot),
                                goalTime = gt, starTimes = listOf(gt * 0.3, gt * 0.6, gt * 0.85),
                            )
                        }.getOrNull() ?: continue
                        val g = level.goal.pos
                        if (g.y < -0.05 || g.y > 0.2 || g.z > -0.75 || kotlin.math.abs(g.x) > 0.6) continue
                        if (!Placement.isValid(level, "ball1", spot, emptyMap())) continue
                        // The plain shot (ball left in its dock) must not deliver.
                        if (simulate(level, pull).status == FlightStatus.SUCCESS) continue
                        // Nor should a nearby plain shot: the ball has to matter.
                        val rng = Random(3)
                        var plainOk = 0
                        repeat(300) {
                            val p = Solver.randomDirection(rng) * (Physics.MIN_PULL + rng.nextDouble() * (level.maxPull - Physics.MIN_PULL))
                            if (simulate(level, p).status == FlightStatus.SUCCESS) plainOk++
                        }
                        if (plainOk > 0) continue
                        val deliver = Solver.robustness(level, level.solution!!, requireAllStars = false, samples = 60)
                        if (deliver < 0.5) continue
                        val three = Solver.robustness(level, level.solution!!, samples = 60)
                        val score = deliver + 0.5 * three - 0.4 * reach
                        hits.add(Hit(score, "mu=$mu ball=${spot.f()} reach=${"%.2f".format(reach)} pull=${pull.f()} t=$gt goal=${g.f()} deliver=${"%.2f".format(deliver)} three=${"%.2f".format(three)}"))
                    }
                }
            }
        }
        hits.sortedByDescending { it.score }.take(15).forEach { println("%.3f %s".format(it.score, it.text)) }
        println("hits ${hits.size}")
    }

    private fun Vec3.f() = "v(${"%.3f".format(x)}, ${"%.3f".format(y)}, ${"%.3f".format(z)})"
}
