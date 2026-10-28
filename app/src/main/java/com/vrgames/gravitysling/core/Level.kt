package com.vrgames.gravitysling.core

/** Visual role of a body. The renderer picks a model/texture by [BodySpec.look]. */
enum class BodyKind { STAR, PLANET, MOON, ASTEROID, GRAVITY_BALL }

/**
 * Circular orbit around a fixed point, so a body's position is a pure function of flight time.
 * Time is 0 at launch, so aiming always sees the same layout.
 */
data class Orbit(
    val center: Vec3,
    val radius: Double,
    /** Seconds per revolution; negative values orbit clockwise. */
    val period: Double,
    /** Angle at t = 0, in degrees. */
    val phaseDeg: Double = 0.0,
    val normal: Vec3 = Vec3.UP,
) {
    /** Axes of the orbit plane, scaled by [radius]: position = center + u cos(a) + w sin(a). */
    fun axes(): Pair<Vec3, Vec3> {
        val nrm = normal.normalized()
        // Pick any vector not parallel to the normal to build the orbit plane.
        val ref = if (kotlin.math.abs(nrm.x) < 0.9) Vec3(1.0, 0.0, 0.0) else Vec3(0.0, 0.0, 1.0)
        val u = nrm.cross(ref).cross(nrm).normalized()
        return u * radius to nrm.cross(u) * radius
    }

    fun positionAt(t: Double): Vec3 {
        val (u, w) = axes()
        val a = Math.toRadians(phaseDeg) + 2 * kotlin.math.PI / period * t
        return center + u * kotlin.math.cos(a) + w * kotlin.math.sin(a)
    }
}

/**
 * A gravitating (mu > 0) or inert (mu == 0, e.g. asteroid) sphere. Touching it ends the flight.
 * Bodies with [movable] = true are placed by the player before launch.
 */
data class BodySpec(
    val id: String,
    val name: String,
    val kind: BodyKind,
    val pos: Vec3,
    val radius: Double,
    /** Gravitational parameter G*M in m^3/s^2 (playfield scale). */
    val mu: Double,
    val look: String = id,
    val orbit: Orbit? = null,
    /** Movable bodies are switched off (no pull, no collision) while they sit in their dock. */
    val movable: Boolean = false,
)

/** A spherical trigger zone. */
data class Zone(val pos: Vec3, val radius: Double)

/** Passing through multiplies speed by [factor] (once per booster per flight). */
data class Booster(val pos: Vec3, val radius: Double, val factor: Double)

/** Entering [a] moves the probe to [b], keeping its velocity. One-way. */
data class Portal(val a: Zone, val b: Vec3)

/** Axis-aligned flight bounds in playfield space; leaving them loses the probe. */
data class Bounds(val min: Vec3, val max: Vec3) {
    fun contains(x: Double, y: Double, z: Double) =
        x >= min.x && x <= max.x && y >= min.y && y <= max.y && z >= min.z && z <= max.z

    fun clamp(p: Vec3) = Vec3(
        p.x.coerceIn(min.x, max.x),
        p.y.coerceIn(min.y, max.y),
        p.z.coerceIn(min.z, max.z),
    )
}

/**
 * A known good answer, used by tests and by the in-game hint. [burnAt] is the flight time of the
 * mid-flight burn, if the answer uses one.
 */
data class Solution(val pull: Vec3, val placements: Map<String, Vec3> = emptyMap(), val burnAt: Double? = null)

data class Level(
    val id: String,
    val chapter: Int,
    val number: Int,
    val title: String,
    /** Mission briefing shown in the console window. */
    val brief: String,
    /** One-line teaching tip for the mechanic this level introduces. */
    val tip: String,
    val launch: Vec3,
    val bodies: List<BodySpec>,
    val goal: Zone,
    val stars: List<Vec3> = emptyList(),
    /** Must be passed in order before the goal counts. */
    val checkpoints: List<Zone> = emptyList(),
    val boosters: List<Booster> = emptyList(),
    val portals: List<Portal> = emptyList(),
    val bounds: Bounds = DEFAULT_BOUNDS,
    val timeLimit: Double = 14.0,
    val maxPull: Double = Physics.MAX_PULL,
    /** Cards unlocked in the encyclopedia window when this level is cleared. */
    val unlocks: List<String> = emptyList(),
    val solution: Solution? = null,
    /** The probe may fire its engine once during the flight (see [Physics.BURN_FACTOR]). */
    val burn: Boolean = false,
    /** Optional bonus level at the end of a chapter; it does not gate the next chapter. */
    val challenge: Boolean = false,
    /** The station circles a planet instead of waiting at [goal] (which is then its spot at launch). */
    val goalOrbit: Orbit? = null,
) {
    val movables get() = bodies.filter { it.movable }

    companion object {
        val DEFAULT_BOUNDS = Bounds(Vec3(-1.3, -0.75, -1.3), Vec3(1.3, 0.85, 0.7))
        const val STAR_RADIUS = 0.05
    }
}
