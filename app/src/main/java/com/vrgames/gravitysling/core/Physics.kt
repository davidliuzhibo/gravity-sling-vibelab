package com.vrgames.gravitysling.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class FlightStatus { AIMING, FLYING, SUCCESS, CRASHED, LOST, TIMEOUT }

sealed class FlightEvent {
    data class StarCollected(val index: Int) : FlightEvent()
    data class CheckpointPassed(val index: Int) : FlightEvent()
    data class Boosted(val index: Int) : FlightEvent()
    data class Warped(val index: Int) : FlightEvent()
    data object Burned : FlightEvent()
    data object Delivered : FlightEvent()
    data class Crashed(val bodyIndex: Int) : FlightEvent()
    data object Lost : FlightEvent()
    data object TimedOut : FlightEvent()
}

object Physics {
    /** Fixed simulation step. Preview and live flight use the same step, so they match exactly. */
    const val DT = 1.0 / 360.0
    /** Launch speed per meter of pull (1/s). */
    const val PULL_GAIN = 2.4
    const val MAX_PULL = 0.25
    /** Pulls shorter than this are treated as "let go without firing". */
    const val MIN_PULL = 0.03
    const val PROBE_RADIUS = 0.018
    /** The mid-flight burn multiplies the probe's speed by this, keeping its direction. */
    const val BURN_FACTOR = 1.35
    /** A movable body this close to its dock counts as docked (switched off). */
    const val DOCK_EPS = 1e-4

    fun launchVelocity(pull: Vec3, maxPull: Double = MAX_PULL): Vec3 = -(pull.clampLength(maxPull)) * PULL_GAIN
}

/**
 * Deterministic point-mass flight through a [Level]. Hot loop works on primitive arrays to avoid
 * per-step allocation (the live flight steps ~360 times per second on device).
 */
class Flight(val level: Level, private val placements: Map<String, Vec3> = emptyMap()) {
    private val n = level.bodies.size
    private val mu = DoubleArray(n) { level.bodies[it].mu }
    /** Negative for docked movables: they can't be hit. */
    private val hitR = DoubleArray(n) { level.bodies[it].radius + Physics.PROBE_RADIUS }
    private val baseX = DoubleArray(n)
    private val baseY = DoubleArray(n)
    private val baseZ = DoubleArray(n)
    // Orbit basis (u, w) scaled by radius, angular speed and phase.
    private val orbiting = BooleanArray(n)
    private val ux = DoubleArray(n); private val uy = DoubleArray(n); private val uz = DoubleArray(n)
    private val wx = DoubleArray(n); private val wy = DoubleArray(n); private val wz = DoubleArray(n)
    private val omega = DoubleArray(n)
    private val phase = DoubleArray(n)

    /** Current body positions (valid for [time]). */
    val bx = DoubleArray(n)
    val by = DoubleArray(n)
    val bz = DoubleArray(n)

    var time = 0.0; private set
    var x = 0.0; private set
    var y = 0.0; private set
    var z = 0.0; private set
    var vx = 0.0; private set
    var vy = 0.0; private set
    var vz = 0.0; private set
    private var ax = 0.0
    private var ay = 0.0
    private var az = 0.0

    var status = FlightStatus.AIMING; private set
    /** Where the station is now (it moves when the level has a [Level.goalOrbit]). */
    var goalX = level.goal.pos.x; private set
    var goalY = level.goal.pos.y; private set
    var goalZ = level.goal.pos.z; private set
    var burnUsed = false; private set
    val canBurn get() = level.burn && !burnUsed && status == FlightStatus.FLYING
    val starsCollected = BooleanArray(level.stars.size)
    val starCount get() = starsCollected.count { it }
    var nextCheckpoint = 0; private set
    private val boosterUsed = BooleanArray(level.boosters.size)
    private val portalUsed = BooleanArray(level.portals.size)

    init {
        for (i in 0 until n) {
            val b = level.bodies[i]
            val p = if (b.movable) placements[b.id] ?: b.pos else b.pos
            if (b.movable && p.distanceTo(b.pos) < Physics.DOCK_EPS) {
                mu[i] = 0.0
                hitR[i] = -1.0
            }
            val o = b.orbit
            if (o == null) {
                baseX[i] = p.x; baseY[i] = p.y; baseZ[i] = p.z
            } else {
                orbiting[i] = true
                baseX[i] = o.center.x; baseY[i] = o.center.y; baseZ[i] = o.center.z
                val (u, w) = o.axes()
                ux[i] = u.x; uy[i] = u.y; uz[i] = u.z
                wx[i] = w.x; wy[i] = w.y; wz[i] = w.z
                omega[i] = 2 * PI / o.period
                phase[i] = Math.toRadians(o.phaseDeg)
            }
        }
        updateBodies(0.0)
        x = level.launch.x; y = level.launch.y; z = level.launch.z
    }

    fun bodyPosition(i: Int) = Vec3(bx[i], by[i], bz[i])
    fun position() = Vec3(x, y, z)
    fun velocity() = Vec3(vx, vy, vz)
    fun goalPosition() = Vec3(goalX, goalY, goalZ)

    /** True if movable body [i] sits in its dock (and so does nothing). */
    fun docked(i: Int) = hitR[i] < 0

    /** Fires the engine once: speed times [Physics.BURN_FACTOR], same direction. */
    fun burn(): Boolean {
        if (!canBurn) return false
        burnUsed = true
        vx *= Physics.BURN_FACTOR; vy *= Physics.BURN_FACTOR; vz *= Physics.BURN_FACTOR
        return true
    }

    /** An independent copy of this flight at its current state (for "what if I burn now"). */
    fun fork(): Flight = Flight(level, placements).also { f ->
        f.time = time; f.x = x; f.y = y; f.z = z
        f.vx = vx; f.vy = vy; f.vz = vz; f.ax = ax; f.ay = ay; f.az = az
        f.status = status; f.burnUsed = burnUsed; f.nextCheckpoint = nextCheckpoint
        starsCollected.copyInto(f.starsCollected)
        boosterUsed.copyInto(f.boosterUsed)
        portalUsed.copyInto(f.portalUsed)
        f.updateBodies(time)
    }

    fun launch(pull: Vec3) {
        check(status == FlightStatus.AIMING) { "already launched" }
        val v0 = Physics.launchVelocity(pull, level.maxPull)
        vx = v0.x; vy = v0.y; vz = v0.z
        status = FlightStatus.FLYING
        computeAccel()
    }

    /** Advances one fixed step. Returns events in [out] (cleared first). */
    fun step(out: MutableList<FlightEvent>) {
        out.clear()
        if (status != FlightStatus.FLYING) return
        val dt = Physics.DT
        // Velocity Verlet: x += v dt + a dt^2/2; a' = a(x, t+dt); v += (a + a') dt/2
        x += vx * dt + 0.5 * ax * dt * dt
        y += vy * dt + 0.5 * ay * dt * dt
        z += vz * dt + 0.5 * az * dt * dt
        val oax = ax; val oay = ay; val oaz = az
        time += dt
        updateBodies(time)
        computeAccel()
        vx += 0.5 * (oax + ax) * dt
        vy += 0.5 * (oay + ay) * dt
        vz += 0.5 * (oaz + az) * dt
        checkTriggers(out)
    }

    private fun updateBodies(t: Double) {
        level.goalOrbit?.positionAt(t)?.let { goalX = it.x; goalY = it.y; goalZ = it.z }
        for (i in 0 until n) {
            if (orbiting[i]) {
                val a = phase[i] + omega[i] * t
                val c = cos(a); val s = sin(a)
                bx[i] = baseX[i] + ux[i] * c + wx[i] * s
                by[i] = baseY[i] + uy[i] * c + wy[i] * s
                bz[i] = baseZ[i] + uz[i] * c + wz[i] * s
            } else {
                bx[i] = baseX[i]; by[i] = baseY[i]; bz[i] = baseZ[i]
            }
        }
    }

    private fun computeAccel() {
        ax = 0.0; ay = 0.0; az = 0.0
        for (i in 0 until n) {
            if (mu[i] == 0.0) continue
            val dx = bx[i] - x; val dy = by[i] - y; val dz = bz[i] - z
            val r2 = dx * dx + dy * dy + dz * dz
            val r = sqrt(r2)
            val k = mu[i] / (r2 * r)
            ax += dx * k; ay += dy * k; az += dz * k
        }
    }

    private fun near(px: Double, py: Double, pz: Double, r: Double): Boolean {
        val dx = px - x; val dy = py - y; val dz = pz - z
        return dx * dx + dy * dy + dz * dz <= r * r
    }

    private fun checkTriggers(out: MutableList<FlightEvent>) {
        for (i in 0 until n) {
            if (hitR[i] > 0 && near(bx[i], by[i], bz[i], hitR[i])) {
                status = FlightStatus.CRASHED
                out.add(FlightEvent.Crashed(i))
                return
            }
        }
        val pr = Physics.PROBE_RADIUS
        level.stars.forEachIndexed { i, s ->
            if (!starsCollected[i] && near(s.x, s.y, s.z, Level.STAR_RADIUS + pr)) {
                starsCollected[i] = true
                out.add(FlightEvent.StarCollected(i))
            }
        }
        level.boosters.forEachIndexed { i, b ->
            if (!boosterUsed[i] && near(b.pos.x, b.pos.y, b.pos.z, b.radius)) {
                boosterUsed[i] = true
                vx *= b.factor; vy *= b.factor; vz *= b.factor
                out.add(FlightEvent.Boosted(i))
            }
        }
        level.portals.forEachIndexed { i, p ->
            if (!portalUsed[i] && near(p.a.pos.x, p.a.pos.y, p.a.pos.z, p.a.radius)) {
                portalUsed[i] = true
                x = p.b.x; y = p.b.y; z = p.b.z
                computeAccel()
                out.add(FlightEvent.Warped(i))
            }
        }
        if (nextCheckpoint < level.checkpoints.size) {
            val c = level.checkpoints[nextCheckpoint]
            if (near(c.pos.x, c.pos.y, c.pos.z, c.radius)) {
                out.add(FlightEvent.CheckpointPassed(nextCheckpoint))
                nextCheckpoint++
            }
        } else {
            if (near(goalX, goalY, goalZ, level.goal.radius)) {
                // Stars inside the station's catch area count as picked up with the delivery.
                level.stars.forEachIndexed { i, s ->
                    val dx = s.x - goalX; val dy = s.y - goalY; val dz = s.z - goalZ
                    val r = level.goal.radius + Level.STAR_RADIUS
                    if (!starsCollected[i] && dx * dx + dy * dy + dz * dz <= r * r) {
                        starsCollected[i] = true
                        out.add(FlightEvent.StarCollected(i))
                    }
                }
                status = FlightStatus.SUCCESS
                out.add(FlightEvent.Delivered)
                return
            }
        }
        if (!level.bounds.contains(x, y, z)) {
            status = FlightStatus.LOST
            out.add(FlightEvent.Lost)
        } else if (time >= level.timeLimit) {
            status = FlightStatus.TIMEOUT
            out.add(FlightEvent.TimedOut)
        }
    }
}

/** Result of a full simulated flight (used by preview, hints and the solver). */
data class FlightResult(
    val status: FlightStatus,
    val stars: Int,
    val time: Double,
    /** Sampled probe positions, every [sampleEvery] steps. */
    val path: List<Vec3>,
)

/**
 * Runs a whole flight. [maxTime] cuts it short (used for the partial aiming preview). [burnAt]
 * fires the engine at the first step at or after that flight time, as a live flight does.
 */
fun simulate(
    level: Level,
    pull: Vec3,
    placements: Map<String, Vec3> = emptyMap(),
    sampleEvery: Int = 0,
    maxTime: Double = Double.MAX_VALUE,
    burnAt: Double? = null,
): FlightResult {
    val f = Flight(level, placements)
    f.launch(pull)
    return continueFlight(f, sampleEvery, maxTime, burnAt)
}

/** Flies [f] on from wherever it is; see [simulate]. */
fun continueFlight(f: Flight, sampleEvery: Int = 0, maxTime: Double = Double.MAX_VALUE, burnAt: Double? = null): FlightResult {
    val path = ArrayList<Vec3>()
    if (sampleEvery > 0) path.add(f.position())
    val events = ArrayList<FlightEvent>(4)
    var steps = 0
    while (f.status == FlightStatus.FLYING && f.time < maxTime) {
        if (burnAt != null && f.time >= burnAt) f.burn()
        f.step(events)
        steps++
        if (sampleEvery > 0 && steps % sampleEvery == 0) path.add(f.position())
    }
    if (sampleEvery > 0 && steps % sampleEvery != 0) path.add(f.position())
    return FlightResult(f.status, f.starCount, f.time, path)
}
