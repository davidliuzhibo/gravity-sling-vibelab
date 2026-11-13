package com.vrgames.gravitysling.game

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vrgames.gravitysling.core.Coach
import com.vrgames.gravitysling.core.FlightStatus
import com.vrgames.gravitysling.core.GameSession
import com.vrgames.gravitysling.core.Levels
import com.vrgames.gravitysling.core.Tutorial
import com.vrgames.gravitysling.core.Phase
import com.vrgames.gravitysling.core.Progress

/**
 * App-wide game state shared by the stage and both windows (they run in one process).
 * [session] holds the rules; the Compose fields mirror what the windows display and are only
 * refreshed on discrete events, never per frame.
 */
object Game {
    private const val PREFS = "gravity_sling"
    private const val KEY_PROGRESS = "progress"
    private const val KEY_TUTORIAL = "tutorial_done"
    private const val KEY_COACH = "coach"
    private const val KEY_COACH_VERSION = "coach_version"
    /** A ball this far (m) from its dock counts as placed, for the coaching. */
    private const val BALL_PLACED = 0.05

    /** Guards [session]: gestures, the frame callback and window buttons all touch it. */
    val lock = Any()
    var session = GameSession(); private set
    private var prefs: android.content.SharedPreferences? = null

    // ---- Mirrored UI state ---------------------------------------------------------------------
    var levelId by mutableStateOf(session.level.id); private set
    var phase by mutableStateOf(Phase.AIMING); private set
    var attempts by mutableIntStateOf(0); private set
    var starsThisFlight by mutableIntStateOf(0); private set
    var checkpointsThisFlight by mutableIntStateOf(0); private set
    var lastStatus by mutableStateOf<FlightStatus?>(null); private set
    var lastStars by mutableIntStateOf(0); private set
    var improved by mutableStateOf(false); private set
    var fullPreview by mutableStateOf(true); private set
    var showHint by mutableStateOf(false); private set
    var blind by mutableStateOf(false); private set
    /** The current flight can still fire its engine (shown as a hint in the console). */
    var canBurn by mutableStateOf(false); private set
    var burnedThisFlight by mutableStateOf(false); private set
    /** The 3D star map (level select) floats in the playfield. */
    var mapShown by mutableStateOf(false)
    var skyOn by mutableStateOf(true)
    /** Bumps whenever saved progress changes, so level buttons and the codex recompose. */
    var progressVersion by mutableIntStateOf(0); private set
    /** Bumps when the stage must rebuild the level's entities. */
    var levelVersion by mutableIntStateOf(0); private set
    /** Playfield height offset chosen by the player (meters). */
    var heightOffset by mutableStateOf(0f)
    var selectedCard by mutableStateOf<String?>(null)
    /** 航天图鉴 is open in the scene (it used to be a system window, which opened too far away). */
    var codexOpen by mutableStateOf(false); private set

    fun openCodex() {
        codexOpen = true
        menuOpen = false
        // The chosen card's model turns beside the panel.
        if (showcase == null) showcase = selectedCard ?: progress().unlockedCards().firstOrNull()
    }

    fun closeCodex() {
        codexOpen = false
        showcase = null
        narrate(null)
    }
    /** The card whose model stands large beside the player in the main scene ("放到身边看"), or null. */
    var showcase by mutableStateOf<String?>(null)
    /** The card whose narration is playing, or null. */
    var narrating by mutableStateOf<String?>(null); private set
    private var appContext: Context? = null
    private var voice: android.media.MediaPlayer? = null

    /** Plays card [id]'s narration (assets/narration/codex_<id>.ogg), stopping any other; null stops. */
    fun narrate(id: String?) {
        runCatching { voice?.release() }
        voice = null
        narrating = null
        val ctx = appContext ?: return
        if (id == null) return
        voice = runCatching {
            ctx.assets.openFd("narration/codex_$id.ogg").use { fd ->
                android.media.MediaPlayer().apply {
                    setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                    setOnCompletionListener { if (voice === it) narrating = null }
                    prepare()
                    start()
                }
            }
        }.getOrNull()
        if (voice != null) narrating = id
    }

    /** Every card of the encyclopedia found: the probe turns gold. */
    val allCardsFound get() = progress().unlockedCards().toSet().size >= com.vrgames.gravitysling.core.Codex.cards.size
    /** Cards the last delivery unlocked for the first time (shown as a note in the console). */
    var newCards by mutableStateOf<List<String>>(emptyList())
    /** Bumps when the stage must put the probe back on the launcher (retry button). */
    var resetVersion by mutableIntStateOf(0); private set
    /** Bumps when the stage must fire the intended solution ([AutoPilot], debug only). */
    var autoShotVersion by mutableIntStateOf(0); private set
    /** Shared meshes/materials, created by the stage; the codex window reuses them. */
    var assets: com.vrgames.gravitysling.render.Assets? = null
    /** The stage's sound, so test commands can mute parts of it. */
    var sound: com.vrgames.gravitysling.render.Sound? = null
    var music: com.vrgames.gravitysling.render.Music? = null
    var lighting: com.vrgames.gravitysling.render.Lighting? = null
    /** The key (sun) light, for the debug `sun` command. */
    var sun: com.pico.spatial.core.ecs.Entity? = null

    /** Debug only (AutoPilot): playfield pose override "x,y,z,yaw,scale" for screenshots, and slow motion. */
    var debugView by mutableStateOf<String?>(null)
    @Volatile var timeScale = 1.0
    var assetsReady by mutableStateOf(false)

    /** Title card in the console; the stage already shows the resumed level behind it. */
    var showTitle by mutableStateOf(true); private set
    private var tutorial = Tutorial(done = true)
    var tutorialStep by mutableStateOf<Tutorial.Step?>(null); private set
    private var coach = Coach()
    /** Coaching still to give (until the player has done it once); see [Coach]. */
    var coachBall by mutableStateOf(false); private set
    var coachBurn by mutableStateOf(false); private set
    var coachMap by mutableStateOf(false); private set
    /** The flight is in its burn-coaching slow motion right now. */
    var burnCue by mutableStateOf(false); private set
    /** Why the last flight failed, in front of the player until the next grab. */
    var missText by mutableStateOf<String?>(null); private set
    /** The player has missed often enough to be offered the aim assist. */
    var assistOffered by mutableStateOf(false); private set
    var aimAssist by mutableStateOf(false); private set
    /** The aim line reaches the station right now. */
    var aimDelivers by mutableStateOf(false)
    /** The "how to play" card in the console. */
    var showGuide by mutableStateOf(false)
    /** The gravity-ball explainer has been put away (this run); it shows while the ball is coached. */
    var ballIntroDismissed by mutableStateOf(false)
    /** The level's mission card has been put away (it comes back with each new level). */
    var levelCardDismissed by mutableStateOf(false)
    /** Note over a dragged gravity ball (why it cannot go there), or null. */
    var ballNotice by mutableStateOf<String?>(null)
    /** The in-scene menu (next to the launcher) is unfolded. */
    var menuOpen by mutableStateOf(false)
    /** Shown in the console after the last level of a chapter is delivered. */
    var celebration by mutableStateOf<Celebration?>(null); private set

    data class Celebration(val chapter: Int, val stars: Int, val maxStars: Int, val allDone: Boolean)

    fun init(context: Context) {
        if (prefs != null) return
        appContext = context.applicationContext
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        synchronized(lock) {
            session = GameSession(Progress.parse(p.getString(KEY_PROGRESS, null)))
            // Resume at the first story level that is not yet cleared, then at an open challenge.
            val resume = Levels.main.firstOrNull { !session.progress.cleared(it.id) }
                ?: Levels.all.firstOrNull { !session.progress.cleared(it.id) }
                ?: Levels.main.last()
            session.load(resume.id)
        }
        tutorial = Tutorial(done = p.getBoolean(KEY_TUTORIAL, false))
        tutorialStep = tutorial.step
        // Saves before v1.2 counted a mere grab of a ball as learned; coach the ball once more.
        val saved = p.getString(KEY_COACH, null)
        coach = Coach.parse(if (p.getInt(KEY_COACH_VERSION, 1) < 2) saved?.split(',')?.filter { it != "ball" }?.joinToString(",") else saved)
        p.edit().putInt(KEY_COACH_VERSION, 2).putString(KEY_COACH, coach.serialize()).apply()
        refresh(levelChanged = true)
        progressVersion++
        AutoPilot.register(context)
    }

    /** Test hook: forgets all progress so the first-run flow shows again. */
    fun resetSave() {
        prefs?.edit()?.clear()?.apply()
        synchronized(lock) { session = GameSession() }
        tutorial = Tutorial(done = false)
        tutorialStep = tutorial.step
        coach = Coach()
        showTitle = true
        celebration = null
        selectedCard = null
        newCards = emptyList()
        mapShown = false
        refresh(levelChanged = true)
        progressVersion++
    }

    /** Debug: the next auto shot flies into the nearest planet instead of the solution. */
    @Volatile var autoShotMiss = false
    /** Debug: the next auto shot flies like a player's (burn coaching on, no scheduled burn). */
    @Volatile var autoShotCoached = false

    /** Debug only (AutoPilot "pull"): hold the probe at a pull ("sol", "alt") or let go ("off"). */
    @Volatile var debugPullMode = "off"
    var debugPullVersion by mutableIntStateOf(0); private set
    fun requestDebugPull(mode: String) {
        debugPullMode = mode
        debugPullVersion++
    }

    fun requestAutoShot(miss: Boolean = false, coached: Boolean = false) {
        autoShotMiss = miss
        autoShotCoached = coached
        autoShotVersion++
    }

    fun startGame() {
        showTitle = false
        // Returning players pick up from the star map; new ones go straight into the first level.
        if (session.progress.cleared(Levels.all.first().id)) mapShown = true
    }

    fun dismissCelebration() {
        celebration = null
    }

    // ---- Tutorial hooks (called from the stage on the main thread) -----------------------------
    fun onProbeGrabbed() {
        mapShown = false
        missText = null
        levelCardDismissed = true
        tutorialChanged(tutorial.onGrab())
    }
    fun onPullChanged(length: Double) { if (tutorial.active) tutorialChanged(tutorial.onPull(length)) }
    fun onProbeReleased(fired: Boolean) = tutorialChanged(if (fired) tutorial.onLaunch() else tutorial.onCancel())

    fun onBallGrabbed() {
        mapShown = false
        missText = null
        ballIntroDismissed = true
        levelCardDismissed = true
    }

    /** After any drag: the ball coaching ends once a ball has really been moved out of its dock. */
    fun onDragFinished() {
        val placed = synchronized(lock) {
            session.level.movables.any { (session.placements[it.id] ?: it.pos).distanceTo(it.pos) > BALL_PLACED }
        }
        if (placed) coached(Coach.Topic.BALL)
    }

    /**
     * Shows the slingshot tutorial and every coaching hint again (from the "how to play" card).
     * [slingshot] false: the hints only (the test hook, which cannot pinch through the tutorial).
     */
    fun replayCoaching(slingshot: Boolean = true) {
        tutorial = Tutorial(done = !slingshot)
        tutorialStep = tutorial.step
        coach = Coach()
        prefs?.edit()?.putBoolean(KEY_TUTORIAL, !slingshot)?.remove(KEY_COACH)?.apply()
        showGuide = false
        refresh()
    }

    private fun coached(t: Coach.Topic) {
        if (!coach.complete(t)) return
        prefs?.edit()?.putString(KEY_COACH, coach.serialize())?.apply()
        refresh()
    }

    private fun tutorialChanged(changed: Boolean) {
        if (!changed) return
        tutorialStep = tutorial.step
        if (tutorial.step == Tutorial.Step.DONE) prefs?.edit()?.putBoolean(KEY_TUTORIAL, true)?.apply()
    }

    fun progress() = session.progress

    // ---- Commands from windows -----------------------------------------------------------------
    fun selectLevel(id: String) {
        celebration = null
        newCards = emptyList()
        mapShown = false
        levelCardDismissed = false
        missText = null
        synchronized(lock) { session.load(id) }
        refresh(levelChanged = true)
    }

    /** A star on the 3D map was pinched: open that level if it is unlocked. */
    fun pickFromMap(id: String): Boolean {
        val level = Levels.all.firstOrNull { it.id == id } ?: return false
        if (!session.progress.unlocked(level)) return false
        selectLevel(id)
        coached(Coach.Topic.MAP)
        return true
    }

    /** Pinch during a flight: fire the engine once (if this level allows it). */
    fun requestBurn(): Boolean = synchronized(lock) { session.requestBurn() }

    /** From the miss card: show where to pull, and lock on when the pull comes near it. */
    fun enableAssist() {
        missText = null
        synchronized(lock) { session.enableAssist() }
        resetVersion++
        refresh()
    }

    fun dismissMiss() { missText = null }

    fun toggleBlind() {
        synchronized(lock) {
            session.blind = !session.blind
            if (session.phase == Phase.AIMING) session.invalidatePreview()
        }
        refresh()
    }

    fun retry() {
        mapShown = false
        newCards = emptyList()
        synchronized(lock) { session.reset() }
        resetVersion++
        refresh()
    }

    fun next() {
        val n = synchronized(lock) { session.nextLevel() } ?: return
        selectLevel(n.id)
    }

    fun toggleFullPreview() {
        synchronized(lock) {
            session.fullPreview = !session.fullPreview
            session.invalidatePreview()
        }
        refresh()
    }

    fun toggleHint() {
        synchronized(lock) { if (!session.blind) session.showHint = !session.showHint }
        refresh()
    }

    /** Called by the stage after a flight ends or anything else changes. */
    fun refresh(levelChanged: Boolean = false) {
        val s = session
        synchronized(lock) {
            levelId = s.level.id
            phase = s.phase
            attempts = s.attempts
            starsThisFlight = s.flight?.starCount ?: 0
            checkpointsThisFlight = s.flight?.nextCheckpoint ?: 0
            lastStatus = s.lastStatus
            lastStars = s.lastStars
            improved = s.improved
            fullPreview = s.fullPreview
            showHint = s.showHint
            blind = s.blind
            canBurn = s.phase == Phase.FLYING && s.flight?.canBurn == true
            burnedThisFlight = s.flight?.burnUsed == true
            burnCue = s.inBurnCue
            assistOffered = s.assistOffered
            aimAssist = s.aimAssist
            // Coach the burn only once the slingshot itself has been learned.
            s.coachBurn = !coach.done(Coach.Topic.BURN) && tutorial.step == null
        }
        coachBall = !coach.done(Coach.Topic.BALL) && tutorialStep == null && session.level.movables.isNotEmpty()
        coachBurn = !coach.done(Coach.Topic.BURN)
        coachMap = !coach.done(Coach.Topic.MAP)
        if (s.playerBurned) coached(Coach.Topic.BURN)
        if (levelChanged) levelVersion++
    }

    /** Persists progress after a successful delivery. */
    fun onFlightFinished() {
        refresh()
        missText = synchronized(lock) { session.lastMiss?.describe(session.level.goal.radius) }
        if (lastStatus == FlightStatus.SUCCESS) {
            val text = synchronized(lock) { session.progress.serialize() }
            prefs?.edit()?.putString(KEY_PROGRESS, text)?.apply()
            progressVersion++
            val cards = synchronized(lock) { session.newCards }
            newCards = cards
            cards.firstOrNull()?.let { selectedCard = it }
            val lv = session.level
            val p = session.progress
            if (!lv.challenge && lv == Levels.main.last { it.chapter == lv.chapter } && p.chapterCleared(lv.chapter)) {
                val max = Levels.main.count { it.chapter == lv.chapter } * 3
                celebration = Celebration(lv.chapter, p.chapterStars(lv.chapter), max, p.allCleared())
            }
            // Pick the next delivery from the star map (the console offers "next" as well).
            mapShown = true
        }
        // The "well done" coaching card goes away after the first flight.
        if (tutorial.step == Tutorial.Step.DONE) {
            tutorial.dismiss()
            tutorialStep = null
        }
        AutoPilot.onFlightFinished(levelId, lastStatus, lastStars)
    }
}
