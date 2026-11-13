package com.vrgames.gravitysling.render

import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import com.vrgames.gravitysling.core.BodyKind
import com.vrgames.gravitysling.core.BodySpec
import com.vrgames.gravitysling.core.FlightEvent
import com.vrgames.gravitysling.core.FlightStatus
import com.vrgames.gravitysling.core.GameSession
import com.vrgames.gravitysling.core.Level
import com.vrgames.gravitysling.core.Phase
import com.vrgames.gravitysling.core.Vec3
import com.vrgames.gravitysling.core.simulate
import kotlin.math.sin

/** What a grabbed entity is, so the stage knows how to route a drag. */
sealed class Grab {
    data object Probe : Grab()
    data class Ball(val id: String) : Grab()
    /** The invisible pinch area around the playfield during a flight: fire the engine. */
    data object Burn : Grab()
    /** A star on the 3D level map. */
    data class MapStar(val id: String) : Grab()
}

/**
 * Owns every entity of the current level and keeps them in sync with [GameSession].
 * All positions are playfield-local; [root] places the playfield in front of the player.
 */
class Playfield(private val assets: Assets) {
    val root = Entity()
    private var levelRoot: Entity? = null

    // Persistent pieces (survive level changes).
    private val probe = Entity()
    private val aim = DotLine("aim", 200, assets.dotMesh, assets.aimMaterial, 1f)
    /** Physics for the aim and burn lines runs here, never on the render thread; only the newest request matters. */
    private val worker = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "aim-line").apply { isDaemon = true } }
    private val aimBusy = java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile private var aimResult: com.vrgames.gravitysling.core.AimResult? = null
    private var aimAskedFor = -1
    private var aimLastAsk = Double.NEGATIVE_INFINITY
    private val burnBusy = java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile private var burnResult: List<Vec3>? = null
    private var burnLastAsk = 0.0
    private var aimGold = false
    /** Gold marker where the aim assist wants the probe pulled to. */
    private val assistMark = model(assets.unitSphere, assets.assistMaterial).apply { enabled = false; setScale(0.03f) }
    private val trail = DotLine("trail", 240, assets.dotMesh, assets.trailMaterial, 0.7f)
    private val hint = DotLine("hint", 320, assets.dotMesh, assets.hintMaterial, 0.8f)
    /** During a flight: where the probe would go if it burned now. */
    private val burnLine = DotLine("burn", 60, assets.dotMesh, assets.burnMaterial, 0.8f)
    /** The station's orbit, for levels where it moves. */
    private val goalOrbitLine = DotLine("goalOrbit", 72, assets.dotMesh, assets.orbitMaterial, 0.6f)
    /** Level select in 3D; shown between flights. */
    val map = StarMap(assets)
    /**
     * Invisible box in front of the player, covering the playfield. It is only on during a flight
     * (while the engine is still available), so a pinch anywhere over the playfield fires it.
     */
    private val burnShell = Entity().apply {
        components.set(CollisionComponent(listOf(ShapeResource.createBox(Vector3(2.6f, 1.6f, 1.4f))), PhysicsMaterialResource()))
        components.set(InteractableComponent())
        setPos(Vector3(0f, 0.05f, -0.6f))
        enabled = false
    }

    /** Spatial sound (main thread only). [post] hops from the frame callback to the main thread. */
    var sound: Sound? = null
    var post: (Runnable) -> Unit = { it.run() }
    private val bandL = model(assets.bandMesh, assets.bandMaterial)
    private val bandR = model(assets.bandMesh, assets.bandMaterial)
    private val burst = model(assets.unitSphere, assets.flashMaterial)
    // Effects (playfield-local, survive level changes).
    private val goldSparks = Sparks("gold", 96, assets.unitSphere, assets.sparkGold)
    private val fireSparks = Sparks("fire", 96, assets.unitSphere, assets.sparkFire)
    private val cyanSparks = Sparks("cyan", 128, assets.unitSphere, assets.sparkCyan)
    private val violetSparks = Sparks("violet", 64, assets.unitSphere, assets.sparkViolet)
    private val exhaust = Sparks("exhaust", 48, assets.unitSphere, assets.exhaustMaterial)
    private val dust = Dust(assets.dustMesh, assets.dustMaterial)
    /** Space image-based light for the models; set by the stage. */
    var lighting: Lighting? = null
    /** Translucent "ghost" that demonstrates the pull while the tutorial asks for it. */
    private val ghost = model(assets.unitSphere, assets.ghostProbeMaterial)
    private val confetti = DotLine("confetti", 90, assets.confettiMesh, assets.confettiMaterial, 1f)
    private var confettiAge = -1.0
    private var confettiOrigin = Vec3.ZERO
    private val confettiVel = List(90) { i ->
        // Deterministic spread over a sphere, biased upward.
        val u = ((i * 0.618034) % 1.0) * 2 - 1
        val t = i * 2.399963
        val r = kotlin.math.sqrt(1 - u * u)
        Vec3(r * kotlin.math.cos(t), kotlin.math.abs(u) * 0.8 + 0.3, r * kotlin.math.sin(t)) * (0.35 + (i % 7) * 0.04)
    }
    private val confettiPoints = ArrayList<Vec3>(90)

    /** Set by the stage while the tutorial wants the pull demonstrated. */
    var demoPull = false
    /** Set by the stage while the title card is up: the probe waits off the launcher. */
    var titleUp = false
    /** Set by the stage while the slingshot steps should float over the launcher. */
    var pullCoach = false
    /** Set by the stage after a delivery: the "next / again" card floats in front of the player. */
    var resultUp = false
    /** Set by the stage while the gravity-ball explainer should show. */
    var ballIntroUp = false
    /** Set by the stage while the level's mission card should show. */
    var levelCardUp = false
    /** 航天图鉴 open in front of the player: the other cards step aside while it is up. */
    var codexUp = false
    private var codexLabel: Entity? = null
    /** Called (render thread) when the aim line starts or stops reaching the station. */
    var onAimGold: ((Boolean) -> Unit)? = null
    /** Called (render thread) when the note over a dragged ball changes: why it cannot sit there, or null. */
    var onBallNotice: ((String?) -> Unit)? = null
    private var ballNotice: String? = null
    /** Playfield time until which the "it went back" note stays up after a refused drop. */
    private var ballNoticeUntil = -1.0
    private var ballNoticeAt = Vec3.ZERO

    private fun noticeBall(text: String?, at: Vec3) {
        ballNoticeAt = at
        if (text == ballNotice) return
        ballNotice = text
        onBallNotice?.invoke(text)
    }
    /** Whether the probe shows (it never gets disabled; see init). */
    private var probeShown = true
        set(v) {
            field = v
            probe.getChildren().firstOrNull()?.let { if (it.enabled != (v && !titleUp)) it.enabled = v && !titleUp }
        }
    /** Set by the stage while the player has yet to move a gravity ball / pick from the star map. */
    var coachBall = false
    var coachMap = false
    /** In-scene coaching labels (Compose panels from the stage); see [attachLabels]. */
    private var burnLabel: Entity? = null
    private var introLabel: Entity? = null
    private var pullLabel: Entity? = null
    private var resultLabel: Entity? = null
    private var menuLabel: Entity? = null
    private var ballIntroLabel: Entity? = null
    private var levelLabel: Entity? = null
    private var missLabel: Entity? = null
    /** Thick console frames for the card panels, by attachment id; each is added to its panel once both exist. */
    private val frames = HashMap<String, Entity>()
    /** Set by the stage after a miss: the card that says how far off it was. */
    var missUp = false
    private var ballLabel: Entity? = null
    private var mapLabel: Entity? = null
    /** Translucent ball that demonstrates dragging a gravity ball out of its dock. */
    private val ballGhost = model(assets.unitSphere, assets.ghostMaterial).apply { enabled = false }
    private var ballPulsing = -1
    private var cueShown = false

    // Per-level pieces.
    private val bodyEntities = ArrayList<Entity>()
    private val bodySpecs = ArrayList<BodySpec>()
    private val starEntities = ArrayList<Entity>()
    private val checkpointEntities = ArrayList<Entity>()
    private val boosterEntities = ArrayList<Entity>()
    private val portalEntities = ArrayList<Entity>()
    private var launcherSpin: Entity? = null
    private var introAge = 0.0
    private var goalGlow: Entity? = null
    private var goalEntity: Entity? = null
    private val ghostBalls = ArrayList<Entity>()
    private val grabs = HashMap<Entity, Grab>()

    private var level: Level? = null
    private var time = 0.0
    private var aimShownFor: List<Vec3>? = null
    private var hintShown = false
    private var trailSlot = 0
    private var stepCounter = 0
    private var burstAge = -1.0
    private var deliveredAge = -1.0
    private var resultAge = -1.0
    private val events = ArrayList<FlightEvent>(8)

    // Drag state.
    private var dragging: Grab? = null
    private var handOffset = Vec3.ZERO
    /** Recent pulls (uptime ms, offset): opening the fingers jerks the hand, so release uses an earlier one. */
    private val pullHistory = ArrayDeque<Pair<Long, Vec3>>()
    private var ballDragPos = Vec3.ZERO

    private var gilded = false

    /** The encyclopedia is complete: 小驿号 is gold from now on. */
    fun gildProbe() {
        if (gilded) return
        gilded = true
        probe.getChildren().firstOrNull()?.let { assets.gild(it) }
    }

    init {
        probe.addChild(assets.newProbeVisual())
        probe.components.set(
            CollisionComponent(listOf(ShapeResource.createSphere(PROBE_GRAB_RADIUS)), PhysicsMaterialResource()),
        )
        probe.components.set(InteractableComponent())
        probe.components.set(HoverEffectComponent())
        grabs[probe] = Grab.Probe
        // The probe stays on the root and stays enabled: a grab target that is disabled or moved to
        // another parent may stop receiving pinches. It is hidden through its visual instead.
        root.addChild(probe)
        root.addChild(aim.entity)
        root.addChild(trail.entity)
        root.addChild(hint.entity)
        root.addChild(bandL)
        root.addChild(bandR)
        burst.enabled = false
        root.addChild(burst)
        ghost.enabled = false
        ghost.setScale(0.035f)
        root.addChild(ghost)
        root.addChild(confetti.entity)
        listOf(goldSparks, fireSparks, cyanSparks, violetSparks, exhaust).forEach { root.addChild(it.entity) }
        root.addChild(dust.entity)
        root.addChild(burnLine.entity)
        root.addChild(goalOrbitLine.entity)
        root.addChild(map.root)
        root.addChild(burnShell)
        grabs[burnShell] = Grab.Burn
        root.addChild(ballGhost)
        root.addChild(assistMark)
    }

    val hasLabels get() = burnLabel != null && ballLabel != null && mapLabel != null && introLabel != null && pullLabel != null && resultLabel != null && menuLabel != null && ballIntroLabel != null && levelLabel != null && missLabel != null

    /** Gives a card panel its frame, sitting just behind the text. */
    fun addFrame(id: String, frame: Entity) { frames[id] = frame }
    private fun fit(id: String, panel: Entity?) {
        val frame = frames.remove(id) ?: return
        val p = panel ?: run { frames[id] = frame; return }
        frame.setPos(Vector3(0f, 0f, FRAME_BEHIND))
        p.addChild(frame)
        lighting?.receive(p)
    }

    /**
     * Takes the stage's coaching label panels (any still null are taken on a later call); they
     * stay hidden until their coaching is due.
     */
    fun attachLabels(burn: Entity?, ball: Entity?, map: Entity?, intro: Entity?, pull: Entity?, result: Entity?, menu: Entity?, ballIntro: Entity?, level: Entity?, miss: Entity?, codex: Entity? = null) {
        fun take(e: Entity?, old: Entity?): Entity? {
            if (e == null || old != null) return old
            e.enabled = false
            root.addChild(e)
            return e
        }
        burnLabel = take(burn, burnLabel)
        ballLabel = take(ball, ballLabel)
        mapLabel = take(map, mapLabel)
        introLabel = take(intro, introLabel)
        pullLabel = take(pull, pullLabel)
        resultLabel = take(result, resultLabel)
        menuLabel = take(menu, menuLabel)
        ballIntroLabel = take(ballIntro, ballIntroLabel)
        levelLabel = take(level, levelLabel)
        missLabel = take(miss, missLabel)
        codexLabel = take(codex, codexLabel)
        fit(com.vrgames.gravitysling.content.LABEL_INTRO, introLabel)
        fit(com.vrgames.gravitysling.content.LABEL_RESULT, resultLabel)
        fit(com.vrgames.gravitysling.content.LABEL_BALL_INTRO, ballIntroLabel)
        fit(com.vrgames.gravitysling.content.LABEL_LEVEL, levelLabel)
        fit(com.vrgames.gravitysling.content.LABEL_MISS, missLabel)
    }

    /** Golden shower above [at] (chapter finished). */
    fun celebrate(at: Vec3) {
        confettiOrigin = at
        confettiAge = 0.0
    }

    /** Height of the playfield origin above the floor, plus the player's offset. */
    fun placeInRoom(heightOffset: Float) {
        root.setPos(Vector3(0f, BASE_HEIGHT + heightOffset, BASE_Z))
        root.transform().eulerAngles = EulerAngles(0f, 0f, 0f)
        root.setScale(1f)
    }

    /** Debug: arbitrary placement, to frame the playfield for the emulator's fixed camera. */
    fun debugPose(x: Float, y: Float, z: Float, yaw: Float, scale: Float) {
        root.setPos(Vector3(x, y, z))
        root.transform().eulerAngles = EulerAngles(0f, yaw, 0f)
        root.setScale(scale)
    }

    // ---- Building ------------------------------------------------------------------------------
    fun build(session: GameSession) {
        val lv = session.level
        val started = android.os.SystemClock.elapsedRealtime()
        sound?.releaseHums()
        levelRoot?.let {
            lighting?.forget(it)
            root.removeChild(it)
            it.destroy(true)
        }
        grabs.keys.removeAll { it != probe && it != burnShell }
        ballPulsing = -1
        bodyEntities.clear(); bodySpecs.clear(); starEntities.clear(); checkpointEntities.clear(); ghostBalls.clear(); boosterEntities.clear(); portalEntities.clear()
        level = lv
        val lr = Entity()
        levelRoot = lr
        root.addChild(lr)

        // Launcher ring facing the player.
        lr.addChild(assets.newLauncher().apply {
            setPos(lv.launch)
            transform().quaternion = quatFromY(Vec3(0.0, 0.0, 1.0))
            launcherSpin = getChildren().getOrNull(1)
        })

        val tClear = android.os.SystemClock.elapsedRealtime()
        for (b in lv.bodies) {
            val e = makeBody(b, session.placements[b.id] ?: b.pos)
            lr.addChild(e)
            bodyEntities.add(e)
            bodySpecs.add(b)
            if (b.movable) grabs[e] = Grab.Ball(b.id)
            if (b.kind == BodyKind.PLANET) sound?.attachHum(e, (b.mu * 6).toFloat().coerceIn(0.1f, 0.45f))
        }
        val tBodies = android.os.SystemClock.elapsedRealtime()
        for (s in lv.stars) {
            val e = assets.newStar().apply { setPos(s) }
            lr.addChild(e)
            starEntities.add(e)
        }
        // Orient checkpoint rings along the intended path so you fly through them.
        val sol = lv.solution
        val path = if (sol != null) simulate(lv, sol.pull, sol.placements, sampleEvery = 3).path else emptyList()
        for (c in lv.checkpoints) {
            val i = path.indices.minByOrNull { path[it].distanceTo(c.pos) } ?: 0
            val dir = if (path.size > 2) path[(i + 1).coerceAtMost(path.lastIndex)] - path[(i - 1).coerceAtLeast(0)] else Vec3.UP
            val ring = model(MeshResource.createTorus(c.radius.toFloat(), (c.radius - 0.014).toFloat()), assets.checkpointMaterial).apply {
                setPos(c.pos)
                transform().quaternion = quatFromY(dir)
            }
            lr.addChild(ring)
            checkpointEntities.add(ring)
        }
        fun dirAt(p: Vec3): Vec3 {
            if (path.size < 3) return Vec3(0.0, 0.0, -1.0)
            val i = path.indices.minByOrNull { path[it].distanceTo(p) } ?: 0
            return path[(i + 1).coerceAtMost(path.lastIndex)] - path[(i - 1).coerceAtLeast(0)]
        }
        for (b in lv.boosters) {
            val e = assets.newBooster().apply {
                setPos(b.pos)
                transform().quaternion = quatFromY(dirAt(b.pos))
            }
            lr.addChild(e)
            boosterEntities.add(e)
        }
        for (pt in lv.portals) {
            val r = pt.a.radius.toFloat()
            lr.addChild(assets.newPortal(r, true).apply {
                setPos(pt.a.pos)
                transform().quaternion = quatFromY(dirAt(pt.a.pos))
                portalEntities.add(this)
            })
            lr.addChild(assets.newPortal(r * 0.8f, false).apply {
                setPos(pt.b)
                transform().quaternion = quatFromY(dirAt(pt.b))
                portalEntities.add(this)
            })
        }
        val goal = Entity().apply { setPos(lv.goal.pos) }
        goalEntity = goal
        // A moving station shows the orbit it follows.
        val go = lv.goalOrbit
        if (go != null) goalOrbitLine.show(List(goalOrbitLine.capacity) { go.positionAt(go.period * it / goalOrbitLine.capacity) })
        else goalOrbitLine.clear()
        goal.addChild(assets.newStationVisual(lv.goal.radius.toFloat() * 2.2f))
        val glow = model(assets.unitSphere, assets.goalMaterial).apply { setScale(lv.goal.radius.toFloat()) }
        goal.addChild(glow)
        goalGlow = glow
        lr.addChild(goal)

        // Ghost balls for the hint.
        sol?.placements?.forEach { (id, p) ->
            val r = lv.bodies.first { it.id == id }.radius.toFloat()
            val g = model(assets.unitSphere, assets.ghostMaterial).apply { setPos(p); setScale(r); enabled = false }
            lr.addChild(g)
            ghostBalls.add(g)
        }

        val tRest = android.os.SystemClock.elapsedRealtime()
        lighting?.receive(lr)
        val tLight = android.os.SystemClock.elapsedRealtime()
        introAge = 0.0
        resetVisuals(session)
        val end = android.os.SystemClock.elapsedRealtime()
        android.util.Log.i("GravityTest", "build ${lv.id} ${end - started}ms (clear ${tClear - started}, bodies ${tBodies - tClear}, rest ${tRest - tBodies}, light ${tLight - tRest}, reset ${end - tLight})")
    }

    private fun makeBody(b: BodySpec, pos: Vec3): Entity {
        val holder = Entity().apply { setPos(pos) }
        // The middle entity carries the entrance animation's scale.
        holder.addChild(Entity().apply { addChild(assets.newBodyVisual(b)) })
        if (b.movable) {
            holder.components.set(
                CollisionComponent(
                    listOf(ShapeResource.createSphere(b.radius.toFloat() + 0.03f)),
                    PhysicsMaterialResource(),
                ),
            )
            holder.components.set(InteractableComponent())
            holder.components.set(HoverEffectComponent())
        }
        return holder
    }

    /** Puts everything back to the aiming layout (after a retry or a level load). */
    fun resetVisuals(session: GameSession) {
        val lv = session.level
        time = 0.0
        probeShown = true
        probe.setPos(lv.launch)
        goalEntity?.setPos(lv.goal.pos)
        burnLine.clear()
        burnResult = null
        aimResult = null
        aimAskedFor = -1
        // The playfield clock just went back to 0: the throttle's last ask must go with it, or no aim line is asked for
        // until the new clock catches up with the old one (seconds to minutes with no line at all).
        aimLastAsk = Double.NEGATIVE_INFINITY
        setAimGold(false)
        burnShell.enabled = false
        updateBallGlow(session)
        starEntities.forEach { it.enabled = true }
        boosterEntities.forEach { it.getChildren().firstOrNull()?.components?.get(com.pico.spatial.core.ecs.ModelComponent::class.java)?.materials?.set(0, assets.boosterMaterial) }
        checkpointEntities.forEach { it.components[com.pico.spatial.core.ecs.ModelComponent::class.java]?.materials?.set(0, assets.checkpointMaterial) }
        for (i in bodySpecs.indices) {
            val b = bodySpecs[i]
            if (b.orbit != null) bodyEntities[i].setPos(orbitPosition(session, i, 0.0))
            else bodyEntities[i].setPos(session.placements[b.id] ?: b.pos)
        }
        trailSlot = 0
        trail.clear()
        aimShownFor = null
        aim.clear()
        hintShown = false
        burstAge = -1.0
        deliveredAge = -1.0
        resultAge = -1.0
        burst.enabled = false
        exhaust.clear()
        updateBands(lv.launch, false)
        goalGlow?.components?.get(com.pico.spatial.core.ecs.ModelComponent::class.java)?.materials?.set(
            0, if (lv.checkpoints.isEmpty()) assets.goalMaterial else assets.goalLockedMaterial,
        )
    }

    private fun orbitPosition(session: GameSession, i: Int, t: Double): Vec3 {
        // A throwaway flight gives the exact same orbit math as the simulation.
        val f = com.vrgames.gravitysling.core.Flight(session.level, session.placements)
        return if (t == 0.0) f.bodyPosition(i) else session.flight?.bodyPosition(i) ?: f.bodyPosition(i)
    }

    // ---- Input ---------------------------------------------------------------------------------
    fun grabOf(e: Entity?): Grab? {
        var cur = e
        while (cur != null) {
            grabs[cur]?.let { return it }
            map.starOf[cur]?.let { return Grab.MapStar(it) }
            cur = if (cur.hasParent()) parentOf(cur) else null
        }
        return null
    }

    private fun parentOf(e: Entity): Entity? = runCatching { e.getParent() }.getOrNull()

    val isDragging get() = dragging != null

    fun dragStart(g: Grab, session: GameSession) {
        if (session.phase == Phase.RESULT && session.lastStatus != FlightStatus.SUCCESS) {
            session.reset()
            resetVisuals(session)
        }
        if (session.phase != Phase.AIMING || g == Grab.Burn || g is Grab.MapStar) return
        dragging = g
        handOffset = Vec3.ZERO
        pullHistory.clear()
        if (g == Grab.Probe) sfx(Sfx.STRETCH, session.level.launch)
        if (g is Grab.Ball) ballDragPos = session.placements[g.id] ?: Vec3.ZERO
    }

    /**
     * Debug only (AutoPilot "pull"): holds the probe at a pull as a hand would, through the same [GameSession.setPull] a
     * drag uses, so the aim line can be checked without hands. "sol": the level's reference pull; "alt": a shorter pull
     * off to the side; anything else lets go without firing.
     */
    fun debugPull(session: GameSession, mode: String) {
        val sol = session.level.solution?.pull ?: Vec3(0.0, 0.0, 0.12)
        val v = when (mode) {
            "sol" -> sol
            "alt" -> sol * 0.7 + Vec3(0.03, 0.0, 0.0)
            else -> null
        }
        if (v == null) {
            dragging = null
            handOffset = Vec3.ZERO
            session.setPull(Vec3.ZERO)
        } else {
            dragging = Grab.Probe
            handOffset = v
            session.setPull(v)
        }
    }

    /** Debug: what the aim line shows now. */
    fun debugAim() = "points=${aim.count} end=${aim.last?.let { "%.3f,%.3f,%.3f".format(it.x, it.y, it.z) }} gold=$aimGold"

    /** [delta] is the hand's movement since the last callback, in meters (playfield axes). */
    fun drag(delta: Vec3, session: GameSession) {
        when (val g = dragging ?: return) {
            Grab.Probe -> {
                handOffset += delta
                session.setPull(handOffset)
                val now = android.os.SystemClock.uptimeMillis()
                pullHistory.addLast(now to handOffset)
                while (pullHistory.size > 2 && pullHistory.first().first < now - 600) pullHistory.removeFirst()
            }
            is Grab.Ball -> {
                ballDragPos = session.level.bounds.clamp(ballDragPos + delta)
                val i = bodySpecs.indexOfFirst { it.id == g.id }
                if (i >= 0) bodyEntities[i].setPos(ballDragPos)
                // Live preview follows legal positions only; say why a spot is refused.
                val why = com.vrgames.gravitysling.core.Placement.blocked(session.level, g.id, ballDragPos, session.placements - g.id)
                if (why == null) session.place(g.id, ballDragPos)
                noticeBall(why?.let { "这里放不下：$it\n再挪一挪" }, ballDragPos)
                updateBallGlow(session)
            }
            Grab.Burn, is Grab.MapStar -> Unit
        }
    }

    /** Returns true if the probe was launched. */
    fun dragEnd(session: GameSession): Boolean {
        val g = dragging ?: return false
        dragging = null
        return when (g) {
            Grab.Probe -> {
                // The pull as it was just before the fingers started to open.
                val cut = android.os.SystemClock.uptimeMillis() - RELEASE_SETTLE_MS
                pullHistory.lastOrNull { it.first <= cut }?.let { session.setPull(it.second) }
                val fired = session.release()
                updateBands(session.level.launch, false)
                if (!fired) probe.setPos(session.level.launch) else sfx(Sfx.LAUNCH, session.level.launch)
                fired
            }
            Grab.Burn, is Grab.MapStar -> false
            is Grab.Ball -> {
                // Snap back to the last legal spot, and say so for a moment.
                val why = com.vrgames.gravitysling.core.Placement.blocked(session.level, g.id, ballDragPos, session.placements - g.id)
                val back = session.placements[g.id] ?: bodySpecs.firstOrNull { it.id == g.id }?.pos ?: Vec3.ZERO
                if (why != null) {
                    noticeBall("${why}，退回了上一个能放的位置", back)
                    ballNoticeUntil = time + BALL_NOTICE_SECONDS
                } else noticeBall(null, back)
                val i = bodySpecs.indexOfFirst { it.id == g.id }
                if (i >= 0) bodyEntities[i].setPos(session.placements[g.id] ?: bodySpecs[i].pos)
                sfx(Sfx.PLACE, session.placements[g.id] ?: Vec3.ZERO)
                false
            }
        }
    }

    /**
     * Test hook: places the balls and fires the level's intended solution. [coached] flies it like
     * a player would (burn coaching on, no automatic burn).
     */
    fun autoShot(session: GameSession, miss: Boolean = false, coached: Boolean = false): Boolean {
        val sol = session.level.solution ?: return false
        dragging = null
        if (session.phase != Phase.AIMING) session.reset()
        sol.placements.forEach { (id, p) -> session.place(id, p) }
        resetVisuals(session)
        val lv = session.level
        val target = lv.bodies.filter { it.mu > 0 && it.orbit == null }.minByOrNull { it.pos.distanceTo(lv.launch) }
        // Pulling away from a body launches straight at it (to see the crash effect).
        session.setPull(if (miss && target != null) (lv.launch - target.pos).normalized() * 0.12 else sol.pull)
        val fired = session.release()
        if (fired) {
            if (!coached) {
                session.skipBurnCue()
                session.scheduledBurn = sol.burnAt
            }
            sfx(Sfx.LAUNCH, session.level.launch)
        }
        return fired
    }

    /** A docked gravity ball is switched off: show only its core until it is placed. */
    private fun updateBallGlow(session: GameSession) {
        for (i in bodySpecs.indices) {
            val b = bodySpecs[i]
            if (!b.movable) continue
            val docked = (session.placements[b.id] ?: b.pos).distanceTo(b.pos) < com.vrgames.gravitysling.core.Physics.DOCK_EPS
            val visual = bodyEntities[i].getChildren().firstOrNull()?.getChildren()?.firstOrNull() ?: continue
            visual.getChildren().drop(1).forEach { it.enabled = !docked }
        }
    }

    // ---- Per frame -----------------------------------------------------------------------------
    /**
     * Advances the flight and animations by [dt] seconds. Returns true if something the windows
     * show has changed (so the caller can refresh Compose state off the hot path).
     */
    fun frame(dt: Double, session: GameSession): Boolean {
        val lv = level ?: return false
        time += dt
        var uiChanged = false
        when (session.phase) {
            Phase.AIMING -> {
                val p = session.probeAimPosition
                if (dragging == Grab.Probe) {
                    probe.setPos(p)
                    updateBands(p, true)
                }
                requestAimLine(session)
                val result = aimResult
                if (result != null) {
                    aimResult = null
                    aim.show(result.path, taper = !session.fullPreview)
                    setAimGold(result.delivers)
                }
                val mark = session.assistPull
                assistMark.enabled = mark != null && !titleUp
                if (mark != null) assistMark.setPos(session.level.launch + mark)
            }
            Phase.FLYING -> {
                // Burn coaching slows the flight itself; effects and pulses keep real time.
                session.tick(dt * session.timeScale(), events)
                val f = session.flight!!
                probe.setPos(f.position())
                if (lv.goalOrbit != null) goalEntity?.setPos(f.goalPosition())
                // The engine is still available: pinch area on, "burn now" line every other frame.
                val burnable = f.canBurn && session.phase == Phase.FLYING
                if (burnShell.enabled != burnable) burnShell.enabled = burnable
                assistMark.enabled = false
                if (!burnable) burnLine.clear() else {
                    if (time - burnLastAsk >= BURN_LINE_PERIOD && burnBusy.compareAndSet(false, true)) {
                        burnLastAsk = time
                        val fork = session.burnPreviewFork()
                        if (fork == null) burnBusy.set(false) else worker.execute {
                            try { burnResult = session.runBurnPreview(fork) } finally { burnBusy.set(false) }
                        }
                    }
                    burnResult?.let { burnLine.show(it, taper = true); burnResult = null }
                }
                for (i in bodySpecs.indices) if (bodySpecs[i].orbit != null) bodyEntities[i].setPos(f.bodyPosition(i))
                // Ion exhaust streaming back from the probe.
                if (probeShown) exhaust.emit(f.position(), 1, 0.02, 0.5, 0.008f, f.velocity() * -0.25)
                if (stepCounter++ % 2 == 0) {
                    trail.set(trailSlot, f.position())
                    trailSlot = (trailSlot + 1) % trail.capacity
                }
                for (e in events) {
                    when (e) {
                        is FlightEvent.StarCollected -> {
                            starEntities[e.index].enabled = false
                            goldSparks.emit(lv.stars[e.index], 26, 0.32, 0.7, 0.009f)
                            sfx(Sfx.STAR, lv.stars[e.index])
                            uiChanged = true
                        }
                        is FlightEvent.Boosted -> {
                            assets.boosterUsed(boosterEntities[e.index])
                            goldSparks.emit(lv.boosters[e.index].pos, 30, 0.4, 0.6, 0.009f, f.velocity() * 0.3)
                            sfx(Sfx.LAUNCH, lv.boosters[e.index].pos)
                        }
                        FlightEvent.Burned -> {
                            // Engine burn: a short jet of flame and ions out of the back.
                            fireSparks.emit(f.position(), 34, 0.18, 0.5, 0.008f, f.velocity() * -0.7)
                            exhaust.emit(f.position(), 24, 0.04, 0.7, 0.012f, f.velocity() * -0.9)
                            sfx(Sfx.LAUNCH, f.position())
                            uiChanged = true
                        }
                        is FlightEvent.Warped -> {
                            violetSparks.emit(lv.portals[e.index].a.pos, 24, 0.3, 0.6, 0.009f)
                            violetSparks.emit(lv.portals[e.index].b, 24, 0.3, 0.6, 0.009f)
                            sfx(Sfx.CHECKPOINT, lv.portals[e.index].b)
                        }
                        is FlightEvent.CheckpointPassed -> {
                            checkpointEntities[e.index].components[com.pico.spatial.core.ecs.ModelComponent::class.java]?.materials?.set(0, assets.checkpointDoneMaterial)
                            if (e.index == lv.checkpoints.lastIndex) {
                                goalGlow?.components?.get(com.pico.spatial.core.ecs.ModelComponent::class.java)?.materials?.set(0, assets.goalMaterial)
                            }
                            cyanSparks.emit(lv.checkpoints[e.index].pos, 22, 0.3, 0.6, 0.008f)
                            sfx(Sfx.CHECKPOINT, lv.checkpoints[e.index].pos)
                            uiChanged = true
                        }
                        FlightEvent.Delivered -> {
                            deliveredAge = 0.0
                            probeShown = false
                            cyanSparks.emit(lv.goal.pos, 70, 0.45, 1.0, 0.011f)
                            sfx(Sfx.DELIVERED, lv.goal.pos)
                            // Confetti for a perfect run.
                            if (f.starCount == lv.stars.size) celebrate(lv.goal.pos + Vec3(0.0, 0.08, 0.0))
                        }
                        is FlightEvent.Crashed, FlightEvent.Lost, FlightEvent.TimedOut -> {
                            showBurst(f.position()); probeShown = false
                            if (e is FlightEvent.Crashed) {
                                fireSparks.emit(f.position(), 60, 0.5, 0.9, 0.011f)
                                goldSparks.emit(f.position(), 20, 0.25, 0.5, 0.007f)
                            }
                            sfx(Sfx.CRASH, f.position())
                        }
                    }
                }
                if (session.phase == Phase.RESULT) {
                    uiChanged = true
                    resultAge = 0.0
                    aim.clear()
                    burnLine.clear()
                    burnShell.enabled = false
                }
                events.clear()
                updateBands(lv.launch, false)
            }
            Phase.RESULT -> {
                if (resultAge >= 0) resultAge += dt
                // After a miss, put a fresh probe back on the launcher automatically.
                if (session.lastStatus != FlightStatus.SUCCESS && resultAge > AUTO_RETRY_SECONDS) {
                    session.reset()
                    resetVisuals(session)
                    uiChanged = true
                }
            }
        }
        // Hint path.
        if (session.showHint != hintShown) {
            hintShown = session.showHint
            if (hintShown) hint.show(session.hintPath()) else hint.clear()
            ghostBalls.forEach { it.enabled = hintShown }
        }
        animateDemo(session)
        if (animateCoaching(session)) uiChanged = true
        animateConfetti(dt)
        animate(dt)
        animateIntro(dt)
        map.update(dt, time)
        goldSparks.update(dt); fireSparks.update(dt); cyanSparks.update(dt); violetSparks.update(dt)
        exhaust.update(dt, drag = 0.5)
        dust.update(time)
        return uiChanged
    }

    /** Asks the worker for the aim line when the aim changed, at most about 20 times a second. */
    private fun requestAimLine(session: GameSession) {
        val version = session.aimVersion
        if (version == aimAskedFor || time - aimLastAsk < AIM_LINE_PERIOD) return
        val request = session.aimRequest()
        if (request == null) {
            aimAskedFor = version
            aim.clear()
            setAimGold(false)
            return
        }
        if (!aimBusy.compareAndSet(false, true)) return
        aimAskedFor = version
        aimLastAsk = time
        worker.execute {
            try { aimResult = request.run() } finally { aimBusy.set(false) }
        }
    }

    /** The aim line turns gold when it really reaches the station: no guessing whether the shot is good. */
    private fun setAimGold(gold: Boolean) {
        if (gold == aimGold) return
        aimGold = gold
        aim.entity.components[com.pico.spatial.core.ecs.ModelComponent::class.java]?.materials?.set(0, if (gold) assets.aimGoldMaterial else assets.aimMaterial)
        onAimGold?.invoke(gold)
    }

    private fun animate(dt: Double) {
        val spin = (time * 8.0 % 360.0).toFloat()
        for (i in bodySpecs.indices) {
            val k = bodySpecs[i].kind
            if (k == BodyKind.PLANET || k == BodyKind.MOON) {
                bodyEntities[i].transform().eulerAngles = EulerAngles(0f, spin * if (k == BodyKind.PLANET) 1f else 0.4f, 0f)
            }
        }
        val starSpin = (time * 90.0 % 360.0).toFloat()
        val bob = (sin(time * 2.0) * 0.004).toFloat()
        starEntities.forEachIndexed { i, s ->
            if (s.enabled) {
                s.transform().eulerAngles = EulerAngles(0f, starSpin + i * 40f, 0f)
                val base = level!!.stars[i]
                s.setPos(Vector3(base.x.toFloat(), base.y.toFloat() + bob, base.z.toFloat()))
            }
        }
        goalGlow?.let { g ->
            val r = level!!.goal.radius.toFloat()
            val pulse = if (deliveredAge >= 0) {
                deliveredAge += dt
                (1f + 1.5f * (deliveredAge / 0.6).toFloat().coerceAtMost(1f))
            } else 1f + 0.06f * sin(time * 3.0).toFloat()
            g.setScale(r * pulse)
        }
        if (burstAge >= 0) {
            burstAge += dt
            if (burstAge > 0.5) { burst.enabled = false; burstAge = -1.0 }
            else burst.setScale((0.06 * kotlin.math.sin(burstAge / 0.5 * Math.PI)).toFloat())
        }
        // Rings turn so they read as machinery rather than static props.
        launcherSpin?.transform()?.eulerAngles = EulerAngles(0f, (time * 40.0 % 360.0).toFloat(), 0f)
        boosterEntities.forEach { it.getChildren().getOrNull(2)?.transform()?.eulerAngles = EulerAngles(0f, (time * 120.0 % 360.0).toFloat(), 0f) }
        portalEntities.forEachIndexed { i, p ->
            p.getChildren().getOrNull(2)?.transform()?.eulerAngles = EulerAngles(0f, ((if (i % 2 == 0) 1 else -1) * time * 90.0 % 360.0).toFloat(), 0f)
        }
        if (dragging == null && level != null) {
            // Idle probe gently breathes on the launcher.
            probe.transform().eulerAngles = EulerAngles(0f, (time * 20.0 % 360.0).toFloat(), 0f)
        }
    }

    /** Level entrance: bodies grow into place one after another, then the stars pop in. */
    private fun animateIntro(dt: Double) {
        if (introAge < 0) return
        introAge += dt
        fun pop(delay: Double): Float {
            val x = ((introAge - delay) / INTRO_GROW).coerceIn(0.0, 1.0)
            // Ease out with a small overshoot.
            val c = 1.70158
            return (1 + (c + 1) * (x - 1) * (x - 1) * (x - 1) + c * (x - 1) * (x - 1)).toFloat().coerceAtLeast(0f)
        }
        bodyEntities.forEachIndexed { i, e -> e.getChildren().firstOrNull()?.setScale(pop(i * INTRO_STAGGER)) }
        val starsAt = bodyEntities.size.coerceAtMost(12) * INTRO_STAGGER + 0.2
        starEntities.forEach { it.setScale(pop(starsAt)) }
        if (introAge > starsAt + INTRO_GROW) {
            bodyEntities.forEach { it.getChildren().firstOrNull()?.setScale(1f) }
            starEntities.forEach { it.setScale(1f) }
            introAge = -1.0
        }
    }

    private fun animateDemo(session: GameSession) {
        val show = demoPull && !titleUp && dragging == null && session.phase == Phase.AIMING
        if (!show) {
            if (ghost.enabled) { ghost.enabled = false; updateBands(session.level.launch, false) }
            return
        }
        // 2.4 s loop: ease the ghost 18 cm toward the player, hold, snap back.
        val cycle = (time % 2.4) / 2.4
        val k = when {
            cycle < 0.5 -> { val x = cycle / 0.5; x * x * (3 - 2 * x) }
            cycle < 0.75 -> 1.0
            else -> 0.0
        }
        val p = session.level.launch + DEMO_PULL * k
        ghost.enabled = true
        ghost.setPos(p)
        updateBands(p, k > 0.02)
    }

    /**
     * Coaching in the scene: a label over the probe and a flashing "burn now" line while the burn is
     * coached; a breathing gravity ball with a ghost dragging it out; a label over the star map.
     * Returns true when the burn slow motion starts or ends (the console says so).
     */
    private fun animateCoaching(session: GameSession): Boolean {
        val lv = level ?: return false
        val burnOn = session.burnCoaching
        burnLabel?.let {
            showIf(it, burnOn)
            if (burnOn) it.setPos(session.flight!!.position() + LABEL_OVER_PROBE)
        }
        val cue = session.inBurnCue
        val lineOn = !cue || (time * 3.0) % 1.0 < 0.65
        if (burnLine.entity.enabled != lineOn) burnLine.entity.enabled = lineOn

        val i = bodySpecs.indexOfFirst { it.movable }
        val ballOn = coachBall && i >= 0 && session.phase == Phase.AIMING && dragging == null && introAge < 0
        if (ballOn) {
            val b = bodySpecs[i]
            val from = session.placements[b.id] ?: b.pos
            val to = lv.solution?.placements?.get(b.id) ?: (from + BALL_DEMO_MOVE)
            bodyEntities[i].setScale((1.0 + 0.12 * sin(time * 5.0)).toFloat())
            ballPulsing = i
            // 2.8 s loop: the ghost eases out of the dock to its spot, holds, then starts over.
            val cycle = (time % 2.8) / 2.8
            val k = when {
                cycle < 0.55 -> { val x = cycle / 0.55; x * x * (3 - 2 * x) }
                cycle < 0.85 -> 1.0
                else -> -1.0
            }
            ballGhost.enabled = k >= 0
            if (k >= 0) {
                ballGhost.setPos(from + (to - from) * k)
                ballGhost.setScale(b.radius.toFloat())
            }
            ballLabel?.setPos(from + Vec3(0.0, b.radius + LABEL_GAP, 0.0))
        } else {
            if (ballPulsing >= 0) bodyEntities.getOrNull(ballPulsing)?.setScale(1f)
            ballPulsing = -1
            if (ballGhost.enabled) ballGhost.enabled = false
        }
        // One explanation at a time: the big explainer stands in for the label over the ball. A
        // refused spot is always explained, over the ball.
        if (ballNoticeUntil >= 0 && time > ballNoticeUntil && dragging !is Grab.Ball) {
            ballNoticeUntil = -1.0
            noticeBall(null, ballNoticeAt)
        }
        val noticeOn = ballNotice != null
        if (noticeOn) ballLabel?.setPos(ballNoticeAt + Vec3(0.0, BALL_NOTICE_GAP, 0.0))
        ballLabel?.let { showIf(it, noticeOn || (ballOn && !ballIntroUp)) }

        introLabel?.let {
            showIf(it, titleUp)
            if (titleUp) it.setPos(INTRO_PANEL_POS)
        }
        val pullOn = pullCoach && !titleUp && session.phase == Phase.AIMING
        pullLabel?.let {
            showIf(it, pullOn)
            if (pullOn) it.setPos(lv.launch + LABEL_OVER_LAUNCHER)
        }
        resultLabel?.let {
            val on = resultUp && !titleUp && !codexUp
            showIf(it, on)
            if (on) it.setPos(SIDE_CARD_POS)
        }
        ballIntroLabel?.let {
            val on = ballIntroUp && !titleUp && !codexUp && session.phase == Phase.AIMING
            showIf(it, on)
            if (on) it.setPos(INTRO_PANEL_POS)
        }
        levelLabel?.let {
            val on = levelCardUp && !missUp && !titleUp && !ballIntroUp && !codexUp && session.phase == Phase.AIMING
            showIf(it, on)
            if (on) it.setPos(SIDE_CARD_POS)
        }
        missLabel?.let {
            val on = missUp && !titleUp && !codexUp && dragging == null
            showIf(it, on)
            if (on) it.setPos(SIDE_CARD_POS)
        }
        menuLabel?.let {
            showIf(it, !titleUp)
            if (!titleUp) it.setPos(MENU_POS)
        }
        codexLabel?.let {
            showIf(it, codexUp && !titleUp)
            if (codexUp && !titleUp) it.setPos(CODEX_POS)
        }
        probeShown = probeShown // re-applies the title-card hiding

        val mapOn = coachMap && map.visible
        mapLabel?.let {
            showIf(it, mapOn)
            if (mapOn) it.setPos(StarMap.CENTER + LABEL_OVER_MAP)
        }

        if (cue == cueShown) return false
        cueShown = cue
        return true
    }

    private fun showIf(e: Entity, on: Boolean) { if (e.enabled != on) e.enabled = on }

    private fun animateConfetti(dt: Double) {
        if (confettiAge < 0) return
        confettiAge += dt
        if (confettiAge > 2.6) { confetti.clear(); confettiAge = -1.0; return }
        val t = confettiAge
        confettiPoints.clear()
        for (v in confettiVel) confettiPoints.add(confettiOrigin + v * t + Vec3(0.0, -0.35 * t * t, 0.0))
        confetti.show(confettiPoints)
    }

    private fun sfx(s: Sfx, at: Vec3) {
        val snd = sound ?: return
        post { snd.play(s, at) }
    }

    private fun showBurst(p: Vec3) {
        burst.setPos(p)
        burst.setScale(0.02f)
        burst.enabled = true
        burstAge = 0.0
    }

    /** Draws the two slingshot bands from the launcher ring to [to]. */
    private fun updateBands(to: Vec3, visible: Boolean) {
        bandL.enabled = visible
        bandR.enabled = visible
        if (!visible) return
        val l = level ?: return
        stretch(bandL, l.launch + Vec3(-LAUNCHER_RADIUS, 0.0, 0.0), to)
        stretch(bandR, l.launch + Vec3(LAUNCHER_RADIUS, 0.0, 0.0), to)
    }

    private fun stretch(band: Entity, a: Vec3, b: Vec3) {
        val d = b - a
        val len = d.length().coerceAtLeast(0.001)
        band.setPos((a + b) * 0.5)
        band.transform().quaternion = quatFromY(d)
        band.transform().setScaleVector(Vector3(1f, len.toFloat(), 1f))
    }

    companion object {
        /** Playfield origin: about chest height, ~1 m in front of where the player stands. */
        const val BASE_HEIGHT = 1.15f
        const val BASE_Z = -1.0f
        const val LAUNCHER_RADIUS = 0.095
        const val AUTO_RETRY_SECONDS = 1.4
        private const val AIM_LINE_PERIOD = 0.05
        private const val BURN_LINE_PERIOD = 0.12
        /** How far back (ms) the release looks for a steady pull. */
        private const val RELEASE_SETTLE_MS = 150L
        private val DEMO_PULL = Vec3(0.0, -0.04, 0.18)
        /** Where the ghost drags a ball whose level has no reference placement for it. */
        private val BALL_DEMO_MOVE = Vec3(-0.2, 0.15, -0.2)
        private const val FRAME_BEHIND = -0.006f
        private val LABEL_OVER_PROBE = Vec3(0.0, 0.09, 0.0)
        private val LABEL_OVER_LAUNCHER = Vec3(0.0, 0.14, 0.0)
        /** Title card and explainers: about 0.75 m from a seated player, just above the launcher ring. */
        private val INTRO_PANEL_POS = Vec3(0.0, 0.16, 0.25)
        /** Mission and result cards: front right, clear of the launcher and the star map above. */
        private val SIDE_CARD_POS = Vec3(0.36, 0.02, 0.28)
        /** The menu tab: low on the left, beside the launcher, within reach. */
        private val MENU_POS = Vec3(-0.42, -0.2, 0.3)
        /** 航天图鉴: straight ahead about 0.8 m from the eyes, half as far as the system window used to open. */
        private val CODEX_POS = Vec3(0.06, 0.06, 0.2)
        /** The codex card's model, turning to the left of the panel. */
        val CODEX_MODEL_POS = Vec3(-0.5, 0.02, 0.18)
        /** Pinch target around the probe: generous, so the eye-hand pinch finds it easily. */
        private const val PROBE_GRAB_RADIUS = 0.09f
        private val LABEL_OVER_MAP = Vec3(0.0, 0.2, 0.0)
        private const val LABEL_GAP = 0.07
        private const val BALL_NOTICE_GAP = 0.12
        private const val BALL_NOTICE_SECONDS = 3.0
        private const val INTRO_GROW = 0.45
        private const val INTRO_STAGGER = 0.05
    }
}
