package com.vrgames.gravitysling.core

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Placement rules shared by the game (drag & drop) and the solver. */
object Placement {
    const val CLEARANCE = 0.04
    /** Free space kept around the launcher ring. */
    const val LAUNCHER_CLEARANCE = 0.1
    /** The pull zone starts this far in front of the launcher (toward the player it is all pull zone). */
    const val PULL_ZONE_FRONT = 0.05

    /** True if [id] may sit at [pos] given the other movables' [placements]. */
    fun isValid(level: Level, id: String, pos: Vec3, placements: Map<String, Vec3>) = blocked(level, id, pos, placements) == null

    /** Why [id] may not sit at [pos] (a few words for the player), or null if it may. */
    fun blocked(level: Level, id: String, pos: Vec3, placements: Map<String, Vec3>): String? {
        val me = level.bodies.first { it.id == id }
        if (!level.bounds.contains(pos.x, pos.y, pos.z)) return "出了投递区"
        for (b in level.bodies) {
            if (b.id == id || b.orbit != null) continue
            val p = if (b.movable) placements[b.id] ?: b.pos else b.pos
            if (p.distanceTo(pos) < me.radius + b.radius + CLEARANCE) return "离${b.name}太近"
        }
        // Orbiting bodies sweep a ring; keep clear of the whole ring.
        for (b in level.bodies) {
            val o = b.orbit ?: continue
            val d = pos.distanceTo(o.center)
            if (d > o.radius - b.radius - me.radius - CLEARANCE && d < o.radius + b.radius + me.radius + CLEARANCE) return "挡住了${b.name}的轨道"
        }
        val keepOut = me.radius + 0.08
        // Off the launcher itself, and out of the space the probe is pulled back through (on the
        // player's side of it); the rest of the launcher's surroundings is fair game (on the
        // device a wider keep-out made the ball spring back to its dock without saying why).
        val fromLaunch = pos - level.launch
        if (fromLaunch.length() < me.radius + LAUNCHER_CLEARANCE) return "离发射架太近"
        if (fromLaunch.z > -PULL_ZONE_FRONT && fromLaunch.length() < me.radius + Physics.MAX_PULL) return "挡住了拉弓的位置"
        if (pos.distanceTo(level.goal.pos) < me.radius + CLEARANCE + level.goal.radius) return "离空间站太近"
        if (level.stars.any { it.distanceTo(pos) < me.radius + Level.STAR_RADIUS }) return "压住了星星"
        if (level.checkpoints.any { it.pos.distanceTo(pos) < keepOut + it.radius }) return "挡住了中继环"
        return null
    }
}

/**
 * Brute-force search over pulls (and movable placements) to prove a level is solvable and to
 * measure how forgiving a solution is to hand jitter.
 */
object Solver {
    data class Found(val solution: Solution, val result: FlightResult)

    fun randomDirection(rng: Random): Vec3 {
        val u = rng.nextDouble() * 2 - 1
        val t = rng.nextDouble() * 2 * PI
        val s = kotlin.math.sqrt(1 - u * u)
        return Vec3(s * cos(t), s * sin(t), u)
    }

    private fun randomPull(level: Level, rng: Random): Vec3 {
        val m = Physics.MIN_PULL + rng.nextDouble() * (level.maxPull - Physics.MIN_PULL)
        return randomDirection(rng) * m
    }

    private fun randomPlacements(level: Level, rng: Random): Map<String, Vec3>? {
        val out = HashMap<String, Vec3>()
        for (b in level.movables) {
            var ok = false
            repeat(40) {
                if (ok) return@repeat
                val mn = level.bounds.min
                val mx = level.bounds.max
                val p = Vec3(
                    mn.x + rng.nextDouble() * (mx.x - mn.x),
                    mn.y + rng.nextDouble() * (mx.y - mn.y),
                    mn.z + rng.nextDouble() * (mx.z - mn.z),
                )
                if (Placement.isValid(level, b.id, p, out)) {
                    out[b.id] = p
                    ok = true
                    return@repeat
                }
            }
            if (!ok) return null
        }
        return out
    }

    private fun better(a: FlightResult, b: FlightResult?): Boolean {
        if (b == null) return true
        val sa = a.status == FlightStatus.SUCCESS
        val sb = b.status == FlightStatus.SUCCESS
        if (sa != sb) return sa
        if (a.stars != b.stars) return a.stars > b.stars
        return a.time < b.time
    }

    /** Random search; stops early once a success with [targetStars] is found. */
    fun search(level: Level, samples: Int, rng: Random = Random(7), targetStars: Int = level.stars.size): Found? {
        var best: Found? = null
        repeat(samples) {
            val placements = if (level.movables.isEmpty()) emptyMap() else randomPlacements(level, rng) ?: return@repeat
            val pull = randomPull(level, rng)
            val r = simulate(level, pull, placements)
            if (better(r, best?.result)) best = Found(Solution(pull, placements), r)
            val b = best!!
            if (b.result.status == FlightStatus.SUCCESS && b.result.stars >= targetStars) return b
        }
        return best?.takeIf { it.result.status == FlightStatus.SUCCESS }
    }

    /** Hill-climbs a solution toward the middle of its success region (more robust to jitter). */
    fun refine(level: Level, start: Solution, rounds: Int = 300, rng: Random = Random(11)): Solution {
        var cur = start
        var curScore = robustness(level, cur, samples = 40, rng = Random(3))
        repeat(rounds) {
            val step = 0.01
            val pull = (cur.pull + randomDirection(rng) * (rng.nextDouble() * step)).clampLength(level.maxPull)
            val placements = cur.placements.mapValues { (id, p) ->
                val cand = p + randomDirection(rng) * (rng.nextDouble() * step * 2)
                if (Placement.isValid(level, id, cand, cur.placements - id)) cand else p
            }
            val cand = Solution(pull, placements, cur.burnAt)
            val r = simulate(level, cand.pull, cand.placements, burnAt = cand.burnAt)
            if (r.status != FlightStatus.SUCCESS || r.stars < level.stars.size) return@repeat
            val s = robustness(level, cand, samples = 40, rng = Random(3))
            if (s >= curScore) {
                cur = cand
                curScore = s
            }
        }
        return cur
    }

    /**
     * Fraction of pulls within [jitter] meters of the solution's pull that still deliver with all
     * stars. ~1.5 cm is a realistic pinch-release error. A solution's burn is also moved by up to
     * [burnJitter] seconds either way (a pinch that comes a little early or late).
     */
    fun robustness(
        level: Level,
        solution: Solution,
        jitter: Double = 0.015,
        samples: Int = 200,
        rng: Random = Random(5),
        requireAllStars: Boolean = true,
        burnJitter: Double = 0.1,
    ): Double {
        var ok = 0
        repeat(samples) {
            // Uniform in a ball: radius ~ cbrt(u).
            val r = jitter * Math.cbrt(rng.nextDouble())
            val pull = solution.pull + randomDirection(rng) * r
            val burnAt = solution.burnAt?.let { it + (rng.nextDouble() * 2 - 1) * burnJitter }
            val res = simulate(level, pull, solution.placements, burnAt = burnAt)
            val pass = res.status == FlightStatus.SUCCESS && (!requireAllStars || res.stars == level.stars.size)
            if (pass) ok++
        }
        return ok.toDouble() / samples
    }

    /** Angle in degrees between two pulls; used to tell distinct solutions apart. */
    fun angleBetween(a: Vec3, b: Vec3) = Math.toDegrees(acos((a.normalized().dot(b.normalized())).coerceIn(-1.0, 1.0)))
}
