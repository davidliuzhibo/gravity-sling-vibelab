package com.vrgames.gravitysling.core

import org.junit.Test
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.random.Random

/**
 * Search harness used to design the chapter challenges; run with -Dlab=design. It tries random
 * shots and keeps the ones where the level's twist (a mid-flight burn, or a placed gravity ball)
 * is really needed: thousands of random shots without it never reach the station.
 */
class DesignLab {
    private fun on() = System.getProperty("lab") == "design"

    /** Positions of a flight every [every] steps, with their times. */
    private class Track(val t: DoubleArray, val p: Array<Vec3>, val status: FlightStatus)

    private fun track(level: Level, pull: Vec3, placements: Map<String, Vec3>, burnAt: Double?, maxTime: Double, every: Int = 3): Track {
        val f = Flight(level, placements)
        f.launch(pull)
        val ts = ArrayList<Double>(); val ps = ArrayList<Vec3>()
        ts.add(0.0); ps.add(f.position())
        val ev = ArrayList<FlightEvent>()
        var n = 0
        while (f.status == FlightStatus.FLYING && f.time < maxTime) {
            if (burnAt != null && f.time >= burnAt) f.burn()
            f.step(ev)
            if (++n % every == 0) { ts.add(f.time); ps.add(f.position()) }
        }
        return Track(ts.toDoubleArray(), ps.toTypedArray(), f.status)
    }

    private fun randomPull(rng: Random, maxPull: Double): Vec3 {
        // Pull toward the player (+z) half-space: shots go away from the player.
        var d: Vec3
        do { d = Solver.randomDirection(rng) } while (d.z < 0.2)
        return d * (Physics.MIN_PULL + rng.nextDouble() * (maxPull - Physics.MIN_PULL))
    }

    /**
     * Every place (and, with [timeBin], every time) the given flights went through, on a coarse
     * grid. [near] says conservatively whether any of them came within [r] of a point.
     */
    private class Visited(tracks: List<Track>, val cell: Double = 0.04, val timeBin: Double? = null) {
        private val keys = HashSet<Long>()
        init {
            for (tr in tracks) for (i in tr.p.indices) keys.add(key(ix(tr.p[i].x), ix(tr.p[i].y), ix(tr.p[i].z), tb(tr.t[i])))
        }
        private fun ix(c: Double) = kotlin.math.floor(c / cell).toInt()
        private fun tb(t: Double) = if (timeBin == null) 0 else kotlin.math.floor(t / timeBin).toInt()
        private fun key(x: Int, y: Int, z: Int, t: Int) =
            ((x + 512).toLong() shl 45) or ((y + 512).toLong() shl 34) or ((z + 512).toLong() shl 23) or (t + 1024).toLong()

        fun near(p: Vec3, r: Double, t: Double = 0.0): Boolean {
            val k = kotlin.math.ceil(r / cell).toInt() + 1
            val cx = ix(p.x); val cy = ix(p.y); val cz = ix(p.z); val ct = tb(t)
            val dt = if (timeBin == null) 0 else 1
            for (a in -k..k) for (b in -k..k) for (c in -k..k) {
                if ((a * a + b * b + c * c) * cell * cell > (r + cell * 1.8) * (r + cell * 1.8)) continue
                for (d in -dt..dt) if (key(cx + a, cy + b, cz + c, ct + d) in keys) return true
            }
            return false
        }
    }

    private fun base(id: String, chapter: Int, bodies: List<BodySpec>, maxPull: Double, burn: Boolean) = Level(
        id = id, chapter = chapter, number = 6, title = "", brief = "", tip = "", launch = Levels.LAUNCH,
        bodies = bodies, goal = Zone(v(99.0, 99.0, 99.0), 0.0), timeLimit = 9.0, maxPull = maxPull, burn = burn,
    )

    private fun clearance(level: Level, tr: Track, upto: Int): Double {
        var m = Double.MAX_VALUE
        for (b in level.bodies) if (b.mu > 0 && b.orbit == null) for (i in 0..upto) m = minOf(m, tr.p[i].distanceTo(b.pos) - b.radius)
        return m
    }

    /** Burn-required search: the goal must be unreachable without the burn. */
    private fun burnSearch(name: String, level: Level, tMin: Double, tMax: Double, samples: Int, seed: Int) {
        val rng = Random(seed)
        // Without a burn, energy caps how far from the (single, fixed) planet the probe can get.
        val planet = level.bodies.first { it.kind == BodyKind.PLANET }
        val v0 = level.maxPull * Physics.PULL_GAIN
        val e0 = v0 * v0 / 2 - planet.mu / level.launch.distanceTo(planet.pos)
        val rMax = planet.mu / -e0
        println("== $name: without a burn the probe stays within ${"%.2f".format(rMax)} m of the planet")
        data class Cand(val pull: Vec3, val burnAt: Double, val t: Double, val goal: Vec3, val score: Double, val clear: Double)
        val found = ArrayList<Cand>()
        repeat(samples) {
            val pull = Solver.randomDirection(rng) * (level.maxPull * (0.5 + 0.5 * rng.nextDouble()))
            val burnAt = 0.4 + rng.nextDouble() * (tMax - 1.5)
            val tr = track(level, pull, emptyMap(), burnAt, tMax)
            // Candidate goal times along this path.
            for (i in tr.t.indices) {
                if (tr.t[i] < tMin || i % 30 != 0) continue
                val g = tr.p[i]
                if (g.distanceTo(level.launch) < 0.5) continue
                // Keep the station where it is easy to see and reach for.
                if (kotlin.math.abs(g.x) > 0.95 || g.y < -0.45 || g.y > 0.6 || g.z < -1.15 || g.z > 0.2) continue
                // A close pass by the planet, so the burn has something to work with.
                val c = clearance(level, tr, i)
                if (c < 0.03 || c > 0.15) continue
                if (g.distanceTo(planet.pos) < rMax + 0.15) continue
                found += Cand(pull, burnAt, tr.t[i], g, 0.0, c)
            }
        }
        println("   ${found.size} candidates beyond that")
        // Robustness to a 1.5 cm hand error and a 0.1 s late/early pinch.
        val scored = found.shuffled(Random(1)).take(400).map { c ->
            val lv = level.copy(goal = Zone(c.goal, 0.1), timeLimit = c.t + 3)
            val r2 = Random(5)
            var ok = 0
            repeat(60) {
                val p = c.pull + Solver.randomDirection(r2) * (0.015 * Math.cbrt(r2.nextDouble()))
                val b = c.burnAt + (r2.nextDouble() * 2 - 1) * 0.1
                if (simulate(lv, p, emptyMap(), burnAt = b).status == FlightStatus.SUCCESS) ok++
            }
            c.copy(score = ok / 60.0)
        }.sortedByDescending { it.score }
        scored.take(8).forEach { c ->
            println("  pull=${c.pull.f()} burnAt=${"%.2f".format(c.burnAt)} goalT=${"%.2f".format(c.t)} goal=${c.goal.f()} robust=${"%.2f".format(c.score)} clear=${"%.2f".format(c.clear)}")
        }
    }

    /** Ball-required search with a station circling [center]: unreachable without placing the ball. */
    private fun ballSearch(name: String, level: Level, center: Vec3, period: Double, tMin: Double, tMax: Double, samples: Int, seed: Int) {
        val rng = Random(seed)
        val ball = level.movables.first()
        val b = level.bounds
        data class Cand(val pull: Vec3, val place: Vec3, val t: Double, val goal: Vec3, val sweep: Double, var robust: Double = 0.0, val orbit: Orbit, var noBallRate: Double = 1.0)
        val found = ArrayList<Cand>()
        repeat(samples) {
            val place = Vec3(
                b.min.x + rng.nextDouble() * (b.max.x - b.min.x),
                -0.5 + rng.nextDouble() * 1.1,
                -1.2 + rng.nextDouble() * 1.0,
            )
            if (!Placement.isValid(level.copy(goal = Zone(v(99.0, 99.0, 99.0), 0.0)), ball.id, place, emptyMap())) return@repeat
            val pull = randomPull(rng, level.maxPull)
            val pl = mapOf(ball.id to place)
            val tr = track(level, pull, pl, null, tMax)
            var sweep = 0.0
            var prev = atan2(tr.p[0].z - center.z, tr.p[0].x - center.x)
            for (i in 1 until tr.t.size) {
                val a = atan2(tr.p[i].z - center.z, tr.p[i].x - center.x)
                var d = a - prev
                if (d > PI) d -= 2 * PI
                if (d < -PI) d += 2 * PI
                sweep += d; prev = a
                if (tr.t[i] < tMin || i % 30 != 0) continue
                val g = tr.p[i]
                val r = g.distanceTo(center)
                if (kotlin.math.abs(sweep) < 2.6 || r < 0.3 || r > 0.7) continue
                if (clearance(level, tr, i) < 0.05) continue
                val d0 = g - center
                val n = (Vec3.UP - d0.normalized() * Vec3.UP.dot(d0.normalized())).normalized()
                val o0 = Orbit(center, r, period, 0.0, n)
                val (u, w) = o0.axes()
                val orbit = o0.copy(phaseDeg = Math.toDegrees(atan2(d0.dot(w), d0.dot(u)) - 2 * PI / period * tr.t[i]))
                found += Cand(pull, place, tr.t[i], g, sweep, orbit = orbit)
            }
        }
        println("== $name: ${found.size} candidates")
        // Without the ball, (almost) no shot may meet the station.
        val noBallTracks = List(4000) { track(level, randomPull(Random(77 + it), level.maxPull), emptyMap(), null, tMax + 1) }
        val kept = found.shuffled(Random(2)).take(600).filter { c ->
            var hit = 0
            for (tr in noBallTracks) if (tr.p.indices.any { tr.p[it].distanceTo(c.orbit.positionAt(tr.t[it])) < 0.1 }) hit++
            c.noBallRate = hit.toDouble() / noBallTracks.size
            c.noBallRate <= 0.02
        }.sortedBy { it.noBallRate }
        println("   ${kept.size} of them deliver without the ball in at most 2% of random shots")
        kept.take(200).onEach { c ->
            val r2 = Random(5)
            var ok = 0
            repeat(50) {
                val p = c.pull + Solver.randomDirection(r2) * (0.015 * Math.cbrt(r2.nextDouble()))
                val tr = track(level, p, mapOf(ball.id to c.place), null, c.t + 2, every = 1)
                // Same station orbit as the candidate.
                val d0 = c.goal - center
                val n = (Vec3.UP - d0.normalized() * Vec3.UP.dot(d0.normalized())).normalized()
                val o0 = Orbit(center, d0.length(), period, 0.0, n)
                val (u, w) = o0.axes()
                val orbit = o0.copy(phaseDeg = Math.toDegrees(atan2(d0.dot(w), d0.dot(u)) - 2 * PI / period * c.t))
                if (tr.p.indices.any { tr.p[it].distanceTo(orbit.positionAt(tr.t[it])) < 0.085 }) ok++
            }
            c.robust = ok / 50.0
        }.sortedByDescending { it.robust - it.noBallRate * 10 }.take(8).forEach { c ->
            println("  pull=${c.pull.f()} ball=${c.place.f()} goalT=${"%.2f".format(c.t)} goal=${c.goal.f()} sweep=${"%.0f".format(Math.toDegrees(c.sweep))} robust=${"%.2f".format(c.robust)} noBall=${"%.4f".format(c.noBallRate)}")
        }
    }

    @Test
    fun design() {
        if (!on()) return
        val which = System.getProperty("which") ?: "all"
        if (which == "all" || which == "1") {
            val earthC = v(0.0, -0.1, -0.6)
            val lv = base("1-6", 1, listOf(
                BodySpec("earth", "地球", BodyKind.PLANET, earthC, 0.17, 0.03, look = "earth"),
                BodySpec("ball1", "引力球", BodyKind.GRAVITY_BALL, v(0.6, -0.35, 0.25), 0.05, 0.025, look = "gravity_ball", movable = true),
            ), Physics.MAX_PULL, burn = false)
            ballSearch("1-6 ball + orbiting station", lv, earthC, -7.0, 3.0, 6.0, 30000, 11)
        }
        if (which == "all" || which == "2") {
            val lv = base("2-6", 2, listOf(
                BodySpec("mars", "火星", BodyKind.PLANET, v(0.05, -0.1, -0.45), 0.15, 0.12, look = "mars"),
            ), 0.1, burn = true)
            burnSearch("2-6 low-power launcher", lv, 2.5, 6.0, 30000, 21)
        }
        if (which == "all" || which == "3") {
            val jc = v(-0.1, -0.05, -0.5)
            val lv = base("3-6", 3, listOf(
                BodySpec("jupiter", "木星", BodyKind.PLANET, jc, 0.19, 0.15, look = "jupiter"),
                BodySpec("europa", "木卫二", BodyKind.MOON, Vec3.ZERO, 0.035, 0.004, look = "europa", orbit = Orbit(jc, 0.42, 6.0, 90.0)),
            ), 0.1, burn = true)
            burnSearch("3-6 Oberth", lv, 2.5, 6.0, 30000, 31)
        }
    }

    private fun Vec3.f() = "v(%.3f, %.3f, %.3f)".format(x, y, z)
}
