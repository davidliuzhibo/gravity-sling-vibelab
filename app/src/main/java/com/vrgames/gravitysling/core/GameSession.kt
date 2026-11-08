package com.vrgames.gravitysling.core

/**
 * Saved progress: best stars per cleared level, plus a badge for levels delivered without the aim
 * line. Serialized as "1-1:3,1-2:2b" (b = badge).
 */
class Progress(
    private val best: MutableMap<String, Int> = LinkedHashMap(),
    private val badges: MutableSet<String> = LinkedHashSet(),
) {
    fun stars(id: String) = best[id] ?: 0
    fun cleared(id: String) = best.containsKey(id)
    fun badge(id: String) = id in badges
    fun totalStars() = best.values.sum()
    fun badgeCount() = badges.size

    /** Records a clear; returns true if it improved the best result or earned a new badge. */
    fun record(id: String, stars: Int, blind: Boolean = false): Boolean {
        val newBadge = blind && badges.add(id)
        val old = best[id]
        if (old != null && old >= stars) return newBadge
        best[id] = stars
        return true
    }

    /**
     * The first level is always open. A main level opens once the previous main level is cleared;
     * a chapter's challenge opens once the chapter's main levels are all cleared.
     */
    fun unlocked(level: Level): Boolean {
        if (level.challenge) return chapterCleared(level.chapter)
        val i = Levels.main.indexOf(level)
        return i <= 0 || cleared(Levels.main[i - 1].id)
    }

    fun unlockedCards(): List<String> = Levels.all.filter { cleared(it.id) }.flatMap { it.unlocks }

    fun chapterStars(chapter: Int) = Levels.main.filter { it.chapter == chapter }.sumOf { stars(it.id) }
    fun chapterCleared(chapter: Int) = Levels.main.filter { it.chapter == chapter }.all { cleared(it.id) }
    fun allCleared() = Levels.main.all { cleared(it.id) }

    fun serialize() = best.entries.joinToString(",") { "${it.key}:${it.value}" + if (it.key in badges) "b" else "" }

    companion object {
        fun parse(s: String?): Progress {
            val m = LinkedHashMap<String, Int>()
            val b = LinkedHashSet<String>()
            s?.split(',')?.forEach { part ->
                val kv = part.split(':')
                if (kv.size != 2 || Levels.all.none { it.id == kv[0] }) return@forEach
                val stars = kv[1].removeSuffix("b").toIntOrNull() ?: return@forEach
                m[kv[0]] = stars.coerceIn(0, 3)
                if (kv[1].endsWith("b")) b.add(kv[0])
            }
            return Progress(m, b)
        }
    }
}

enum class Phase { AIMING, FLYING, RESULT }

/** How a failed flight went wrong: how close it came to the goal and on which side, from the player's seat. */
data class MissReport(val status: FlightStatus, val closest: Double, val offset: Vec3) {
    /** One line a player can act on: how far, which way, and what to change. */
    fun describe(goalRadius: Double): String {
        val gap = ((closest - goalRadius).coerceAtLeast(0.0) * 100).toInt()
        val side = buildList {
            if (kotlin.math.abs(offset.x) >= .03) add(if (offset.x > 0) "右边" else "左边")
            if (kotlin.math.abs(offset.y) >= .03) add(if (offset.y > 0) "上方" else "下方")
            if (kotlin.math.abs(offset.z) >= .05) add(if (offset.z > 0) "近处" else "远处")
        }
        val where = if (side.isEmpty()) "旁边" else side.joinToString("偏")
        val fix = buildList {
            // A pull sends the probe the opposite way, so the hand moves towards the side it missed on.
            if (kotlin.math.abs(offset.x) >= .03) add(if (offset.x > 0) "手再往右拉一点" else "手再往左拉一点")
            if (kotlin.math.abs(offset.y) >= .03) add(if (offset.y > 0) "手再往上抬一点" else "手再往下压一点")
        }.joinToString("，")
        val head = when (status) {
            FlightStatus.CRASHED -> "撞上天体了，离空间站还差 $gap 厘米。"
            FlightStatus.TIMEOUT -> "飞得太久，包裹超时了。最近时差 $gap 厘米，在空间站${where}。"
            else -> "飞出投递区了。最近时差 $gap 厘米，从空间站${where}飞过。"
        }
        return head + if (fix.isEmpty()) "拉得再用力一点试试。" else "下次$fix。"
    }
}

/** Snapshot of everything the aim line depends on; computed off the render thread. */
class AimRequest(val level: Level, val pull: Vec3, val placements: Map<String, Vec3>, val sampleEvery: Int, val maxTime: Double) {
    fun run(): AimResult = simulate(level, pull, placements, sampleEvery = sampleEvery, maxTime = maxTime).let {
        AimResult(it.path, it.status == FlightStatus.SUCCESS)
    }
}
class AimResult(val path: List<Vec3>, val delivers: Boolean)

/**
 * Game rules independent of rendering: aiming, placement, live flight and scoring.
 * The renderer reads positions from here every frame and forwards input to it.
 */
class GameSession(val progress: Progress = Progress()) {
    var level: Level = Levels.all.first(); private set
    var phase = Phase.AIMING; private set
    /** Current slingshot pull (probe offset from the launcher). */
    var pull: Vec3 = Vec3.ZERO; private set
    val placements = LinkedHashMap<String, Vec3>()
    var attempts = 0; private set
    var flight: Flight? = null; private set
    var lastStatus: FlightStatus? = null; private set
    var lastStars = 0; private set
    var improved = false; private set
    /** Encyclopedia cards the last flight unlocked for the first time. */
    var newCards: List<String> = emptyList(); private set
    /** Show the whole predicted path (default) instead of only the opening segment. */
    var fullPreview = true
    var showHint = false
    /** Blind challenge: no aim line, no hint; a delivery earns the level's badge. */
    var blind = false
        set(v) { field = v; if (v) showHint = false; previewCache = null }
    /** Flight time at which to fire the engine on its own (test hook: replays a solution's burn). */
    var scheduledBurn: Double? = null
    private var burnRequested = false
    /** The player hasn't burned yet: flights on burn levels slow down near a planet ([Coach]). */
    var coachBurn = false
    /** Flight time this flight's burn coaching centres on (null: no coaching this flight). */
    var burnCue: Double? = null; private set
    /** The player (not a test replay) fired the engine on this flight. */
    var playerBurned = false; private set
    /** Why the last flight failed, for the card in front of the player. */
    var lastMiss: MissReport? = null; private set
    /** Failed flights on this level since it was loaded. */
    var failsOnLevel = 0; private set
    /** Aim assist: a gold marker shows a pull that delivers, and a pull near it locks on. */
    var aimAssist = false; private set
    /** Bumps whenever the aim line must be recomputed. */
    var aimVersion = 0; private set
    private var closest = Double.MAX_VALUE
    private var closestOffset = Vec3.ZERO

    private var accumulator = 0.0
    private val stepEvents = ArrayList<FlightEvent>(4)
    private var previewCache: List<Vec3>? = null
    private var hintCache: List<Vec3>? = null

    init { load(level.id) }

    fun load(id: String) {
        level = Levels.byId(id)
        placements.clear()
        level.movables.forEach { placements[it.id] = it.pos }
        attempts = 0
        showHint = false
        hintCache = null
        failsOnLevel = 0
        aimAssist = false
        lastMiss = null
        reset()
    }

    /** Back to aiming on the same level, keeping placements. */
    fun reset() {
        phase = Phase.AIMING
        pull = Vec3.ZERO
        flight = null
        lastStatus = null
        lastStars = 0
        improved = false
        newCards = emptyList()
        accumulator = 0.0
        previewCache = null
        scheduledBurn = null
        burnRequested = false
        burnCue = null
        playerBurned = false
        aimVersion++
    }

    fun nextLevel(): Level? {
        val i = Levels.all.indexOf(level)
        return Levels.all.getOrNull(i + 1)
    }

    // ---- Aiming --------------------------------------------------------------------------------
    fun setPull(p: Vec3) {
        if (phase != Phase.AIMING) return
        var next = p.clampLength(level.maxPull)
        assistPull?.let { if (next.distanceTo(it) <= ASSIST_LOCK) next = it }
        pull = next
        previewCache = null
        aimVersion++
    }

    /** The pull the assist marker shows, or null while it is off. */
    val assistPull: Vec3? get() = if (aimAssist) level.solution?.pull else null

    /** The assist is offered after repeated misses, on levels with a known solution. */
    val assistOffered get() = failsOnLevel >= ASSIST_AFTER_FAILS && level.solution != null && !aimAssist && !blind

    /** Turns the assist on and puts the gravity balls where the known solution needs them. */
    fun enableAssist() {
        val s = level.solution ?: return
        if (phase != Phase.AIMING) reset()
        s.placements.forEach { (id, p) -> placements[id] = p }
        aimAssist = true
        previewCache = null
        aimVersion++
    }

    /** What the render side should compute for the aim line now; null when no line is due. */
    fun aimRequest(): AimRequest? {
        if (blind || pull.length() < Physics.MIN_PULL) return null
        return if (fullPreview) AimRequest(level, pull, LinkedHashMap(placements), PREVIEW_SAMPLE * 2, FULL_PREVIEW_SECONDS)
        else AimRequest(level, pull, LinkedHashMap(placements), PREVIEW_SAMPLE, PREVIEW_SECONDS)
    }

    val probeAimPosition get() = level.launch + pull

    /** Moves a gravity ball; returns false (and leaves it) if the spot is not allowed. */
    fun place(id: String, pos: Vec3): Boolean {
        if (phase != Phase.AIMING) return false
        if (!Placement.isValid(level, id, pos, placements - id)) return false
        placements[id] = pos
        previewCache = null
        aimVersion++
        return true
    }

    fun canPlace(id: String, pos: Vec3) = Placement.isValid(level, id, pos, placements - id)

    /** Predicted path for the current pull, or empty if the pull is too short to fire. */
    fun preview(): List<Vec3> {
        previewCache?.let { return it }
        val p = if (blind || pull.length() < Physics.MIN_PULL) emptyList() else
            if (fullPreview) simulate(level, pull, placements, sampleEvery = PREVIEW_SAMPLE * 2, maxTime = FULL_PREVIEW_SECONDS).path
            else simulate(level, pull, placements, sampleEvery = PREVIEW_SAMPLE, maxTime = PREVIEW_SECONDS).path
        previewCache = p
        return p
    }

    fun invalidatePreview() { previewCache = null; aimVersion++ }

    /** Full intended path of the level's reference solution. */
    fun hintPath(): List<Vec3> {
        hintCache?.let { return it }
        val s = level.solution ?: return emptyList()
        val p = simulate(level, s.pull, s.placements, sampleEvery = PREVIEW_SAMPLE, burnAt = s.burnAt).path
        hintCache = p
        return p
    }

    /** Releases the slingshot. Returns false if the pull was too short (treated as cancel). */
    fun release(): Boolean {
        if (phase != Phase.AIMING) return false
        if (pull.length() < Physics.MIN_PULL) {
            pull = Vec3.ZERO
            previewCache = null
            aimVersion++
            return false
        }
        val f = Flight(level, placements)
        f.launch(pull)
        flight = f
        attempts++
        phase = Phase.FLYING
        accumulator = 0.0
        closest = Double.MAX_VALUE
        burnCue = if (coachBurn && level.burn) Coach.burnCueTime(level, pull, placements) else null
        return true
    }

    /** Test replays fly at full speed. */
    fun skipBurnCue() { burnCue = null }

    // ---- Flight --------------------------------------------------------------------------------
    /** Asks for the mid-flight burn; it fires at the next simulation step. False if not available. */
    fun requestBurn(): Boolean {
        val f = flight ?: return false
        if (phase != Phase.FLYING || !f.canBurn || burnRequested) return false
        burnRequested = true
        return true
    }

    /** Burn coaching is on for this flight and the engine is still available. */
    val burnCoaching get() = burnCue != null && phase == Phase.FLYING && flight?.canBurn == true && !burnRequested

    /**
     * Simulation speed for this frame: slows to [CUE_SLOW] around [burnCue] (easing in and out)
     * until the player burns, 1.0 otherwise.
     */
    fun timeScale(): Double {
        val cue = burnCue ?: return 1.0
        val f = flight ?: return 1.0
        if (!burnCoaching) return 1.0
        val t = f.time - cue
        return when {
            t <= -CUE_RAMP || t >= CUE_HOLD + CUE_RAMP -> 1.0
            t < 0 -> 1.0 + (CUE_SLOW - 1.0) * (t + CUE_RAMP) / CUE_RAMP
            t <= CUE_HOLD -> CUE_SLOW
            else -> CUE_SLOW + (1.0 - CUE_SLOW) * (t - CUE_HOLD) / CUE_RAMP
        }
    }

    /** In the slow-motion window right now. */
    val inBurnCue get() = timeScale() < 1.0

    /** Where the probe would go if it burned now (empty when the burn isn't available). */
    fun burnPreview(): List<Vec3> = burnPreviewFork()?.let(::runBurnPreview) ?: emptyList()

    /** A private copy of the flight for [runBurnPreview]; safe to run on another thread. */
    fun burnPreviewFork(): Flight? {
        val f = flight ?: return null
        if (blind || phase != Phase.FLYING || !f.canBurn || burnRequested) return null
        return f.fork()
    }

    fun runBurnPreview(fork: Flight): List<Vec3> {
        fork.burn()
        return continueFlight(fork, sampleEvery = PREVIEW_SAMPLE, maxTime = fork.time + BURN_PREVIEW_SECONDS).path
    }

    /**
     * Advances the live flight by real time [dt] seconds (fixed sub-steps, so it replays exactly
     * what the preview showed). Events are appended to [out].
     */
    fun tick(dt: Double, out: MutableList<FlightEvent>) {
        val f = flight ?: return
        if (phase != Phase.FLYING) return
        accumulator += dt.coerceIn(0.0, MAX_FRAME)
        while (accumulator >= Physics.DT && f.status == FlightStatus.FLYING) {
            accumulator -= Physics.DT
            // Same order as simulate(): the burn applies just before a step.
            val due = burnRequested || scheduledBurn?.let { f.time >= it } == true
            if (due && f.burn()) {
                out.add(FlightEvent.Burned)
                if (burnRequested) playerBurned = true
            }
            if (due) { burnRequested = false; scheduledBurn = null }
            f.step(stepEvents)
            out.addAll(stepEvents)
            val gap = f.position() - f.goalPosition()
            val d = gap.length()
            if (d < closest) { closest = d; closestOffset = gap }
        }
        if (f.status != FlightStatus.FLYING) finish(f)
    }

    private fun finish(f: Flight) {
        phase = Phase.RESULT
        lastStatus = f.status
        lastStars = f.starCount
        val before = progress.unlockedCards().toSet()
        improved = f.status == FlightStatus.SUCCESS && progress.record(level.id, f.starCount, blind)
        if (f.status == FlightStatus.SUCCESS) lastMiss = null
        else {
            lastMiss = MissReport(f.status, closest, closestOffset)
            failsOnLevel++
        }
        newCards = progress.unlockedCards().filter { it !in before }
    }

    companion object {
        const val PREVIEW_SECONDS = 1.6
        /** Misses before the assist is offered. */
        const val ASSIST_AFTER_FAILS = 2
        /** A pull this close to the assist marker locks onto it. */
        const val ASSIST_LOCK = 0.03
        /** Full preview: 20 dots per second, up to 9 s (fits the 200-dot aim line). */
        const val FULL_PREVIEW_SECONDS = 9.0
        /** How far ahead the "burn now" line looks. */
        const val BURN_PREVIEW_SECONDS = 1.2
        /** One preview dot every 9 steps = 40 dots per second of flight. */
        const val PREVIEW_SAMPLE = 9
        /** Burn coaching: slowest simulation speed, how long it holds and eases (flight seconds). */
        const val CUE_SLOW = 0.15
        const val CUE_HOLD = 0.5
        const val CUE_RAMP = 0.2
        /** Cap catch-up after a hitch so the probe does not teleport. */
        const val MAX_FRAME = 0.1
    }
}
