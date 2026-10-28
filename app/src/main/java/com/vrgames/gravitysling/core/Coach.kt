package com.vrgames.gravitysling.core

/**
 * One-time coaching for the moves that come after the slingshot: moving a gravity ball, the
 * mid-flight burn and picking a level on the 3D star map. Each topic is coached until the player
 * has done it once. Saved as "ball,burn,map".
 */
class Coach(done: Set<Topic> = emptySet()) {
    enum class Topic { BALL, BURN, MAP }

    private val done = done.toMutableSet()

    fun done(t: Topic) = t in done

    /** Returns true the first time [t] is done (so the caller saves). */
    fun complete(t: Topic) = done.add(t)

    fun serialize() = Topic.entries.filter { it in done }.joinToString(",") { it.name.lowercase() }

    companion object {
        fun parse(s: String?) = Coach(
            s.orEmpty().split(',').mapNotNull { n -> Topic.entries.firstOrNull { it.name.equals(n.trim(), ignoreCase = true) } }.toSet(),
        )

        /** Earliest flight time to slow down at (the probe has left the launcher). */
        const val CUE_EARLIEST = 0.4
        /** Without a close planet pass, slow down this far into the flight. */
        const val CUE_DEFAULT = 1.0
        /** Leave the player this much flight after the cue to see the burn work. */
        const val CUE_MARGIN = 0.5
        /** A pass counts as close within this many planet radii. */
        const val CUE_CLOSE = 4.0
        /** Spacing of the fallback cue times tried when the preferred one would spoil the shot. */
        const val CUE_STEP = 0.1

        /**
         * When the burn coaching should slow time down for this shot. Preferred: the probe's first
         * close pass of a planet (the low point of the swing, where a burn adds the most energy),
         * else [CUE_DEFAULT]. A first burn should not wreck the shot, so the nearest time at which
         * burning ends no worse than not burning wins. Null (coach a later flight) if the flight ends
         * too soon or every burn would spoil it.
         */
        fun burnCueTime(level: Level, pull: Vec3, placements: Map<String, Vec3>): Double? {
            val every = 9
            val plain = simulate(level, pull, placements, sampleEvery = every)
            val latest = plain.time - CUE_MARGIN
            if (latest < CUE_EARLIEST) return null
            val preferred = (closePass(level, plain.path, every * Physics.DT) ?: CUE_DEFAULT).coerceIn(CUE_EARLIEST, latest)
            val n = ((latest - CUE_EARLIEST) / CUE_STEP).toInt()
            val tries = listOf(preferred) + (0..n).map { CUE_EARLIEST + it * CUE_STEP }.sortedBy { kotlin.math.abs(it - preferred) }
            val bar = rank(plain.status)
            return tries.firstOrNull { rank(simulate(level, pull, placements, burnAt = it).status) >= bar }
        }

        /** Time of the first close pass of a fixed planet along [path] (sampled every [dt]). */
        private fun closePass(level: Level, path: List<Vec3>, dt: Double): Double? {
            val first = (CUE_EARLIEST / dt).toInt().coerceAtLeast(1)
            var best: Double? = null
            for (p in level.bodies) {
                if (p.kind != BodyKind.PLANET || p.movable || p.orbit != null) continue
                for (i in first until path.size - 1) {
                    val d = path[i].distanceTo(p.pos)
                    if (d <= path[i - 1].distanceTo(p.pos) && d < path[i + 1].distanceTo(p.pos) && d < p.radius * CUE_CLOSE) {
                        val t = i * dt
                        if (best == null || t < best) best = t
                        break
                    }
                }
            }
            return best
        }

        private fun rank(s: FlightStatus) = when (s) {
            FlightStatus.SUCCESS -> 2
            FlightStatus.CRASHED -> 0
            else -> 1
        }
    }
}
