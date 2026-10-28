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
    }

    fun nextLevel(): Level? {
        val i = Levels.all.indexOf(level)
        return Levels.all.getOrNull(i + 1)
    }

    // ---- Aiming --------------------------------------------------------------------------------
    fun setPull(p: Vec3) {
        if (phase != Phase.AIMING) return
        pull = p.clampLength(level.maxPull)
        previewCache = null
    }

    val probeAimPosition get() = level.launch + pull

    /** Moves a gravity ball; returns false (and leaves it) if the spot is not allowed. */
    fun place(id: String, pos: Vec3): Boolean {
        if (phase != Phase.AIMING) return false
        if (!Placement.isValid(level, id, pos, placements - id)) return false
        placements[id] = pos
        previewCache = null
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

    fun invalidatePreview() { previewCache = null }

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
            return false
        }
        val f = Flight(level, placements)
        f.launch(pull)
        flight = f
        attempts++
        phase = Phase.FLYING
        accumulator = 0.0
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
    fun burnPreview(): List<Vec3> {
        val f = flight ?: return emptyList()
        if (blind || phase != Phase.FLYING || !f.canBurn || burnRequested) return emptyList()
        val g = f.fork()
        g.burn()
        return continueFlight(g, sampleEvery = PREVIEW_SAMPLE, maxTime = g.time + BURN_PREVIEW_SECONDS).path
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
        }
        if (f.status != FlightStatus.FLYING) finish(f)
    }

    private fun finish(f: Flight) {
        phase = Phase.RESULT
        lastStatus = f.status
        lastStars = f.starCount
        val before = progress.unlockedCards().toSet()
        improved = f.status == FlightStatus.SUCCESS && progress.record(level.id, f.starCount, blind)
        newCards = progress.unlockedCards().filter { it !in before }
    }

    companion object {
        const val PREVIEW_SECONDS = 1.6
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
