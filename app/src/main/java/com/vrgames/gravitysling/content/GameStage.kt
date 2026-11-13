package com.vrgames.gravitysling.content

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.pico.spatial.tracking.DataProvider
import com.pico.spatial.tracking.hand.HandTrackingData
import com.pico.spatial.tracking.hand.HandTrackingProvider
import com.vrgames.gravitysling.core.FlightStatus
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.pico.spatial.core.ecs.DirectionalLightComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.event.SceneEvents
import com.pico.spatial.core.lifecycle.Cancellable
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.content.SpatialViewAttachments
import com.pico.spatial.ui.foundation.content.toColor4
import com.pico.spatial.ui.foundation.gesture.TargetEntity
import com.pico.spatial.ui.foundation.gesture.detectSpatialDragGesture
import com.pico.spatial.ui.platform.LengthUnit
import com.pico.spatial.ui.platform.LocalPhysicalLengthConverter
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import com.vrgames.gravitysling.CODEX_ID
import com.vrgames.gravitysling.CONSOLE_ID
import com.vrgames.gravitysling.core.Levels
import com.vrgames.gravitysling.core.Phase
import com.vrgames.gravitysling.core.Tutorial
import com.vrgames.gravitysling.core.Vec3
import com.vrgames.gravitysling.game.AutoPilot
import com.vrgames.gravitysling.game.Game
import com.vrgames.gravitysling.render.Assets
import com.vrgames.gravitysling.render.ChapterLook
import com.vrgames.gravitysling.render.Grab
import com.vrgames.gravitysling.render.Lighting
import com.vrgames.gravitysling.render.Music
import com.vrgames.gravitysling.render.Playfield
import com.vrgames.gravitysling.render.Sound
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope

/**
 * Full-space stage: Milky Way sky, the level's bodies, and the slingshot. Pinch the probe and pull
 * it toward you to aim; pinch a gravity ball to move it. Opens the console and codex windows.
 */
@Composable
fun GameStage() {
    val context = LocalContext.current
    val navigator = LocalSpatialNavigator.current
    val converter = LocalPhysicalLengthConverter.current
    var playfield by remember { mutableStateOf<Playfield?>(null) }
    var sky by remember { mutableStateOf<Entity?>(null) }
    val subscription = remember { arrayOfNulls<Cancellable>(1) }
    val showcase = remember { arrayOfNulls<Entity>(1) }
    val showcaseYaw = remember { floatArrayOf(0f) }
    val main = remember { Handler(Looper.getMainLooper()) }
    val grabbingProbe = remember { booleanArrayOf(false) }
    // Last gesture callback (uptime ms), for the stuck-gesture check in the frame loop.
    val lastInput = remember { longArrayOf(0L) }
    // Hand tracking, to see the fingers open even when the system never ends the drag.
    val hands = remember { HandTrackingProvider() }
    // The hand that pinched the current drag (0 left, 1 right, -1 unknown) and open frames seen.
    val pinch = remember { intArrayOf(-1, 0) }
    val backdrop = remember { arrayOfNulls<Entity>(1) }

    DisposableEffect(Unit) {
        // The windows wait in the scene menu: the title, mission and result cards cover a run,
        // and two windows open at once crowd the view.
        Game.init(context)
        val started = runCatching { hands.start() }
        Log.i(INPUT_TAG, "hand tracking ${started.getOrNull() ?: started.exceptionOrNull()?.message} support=${runCatching { hands.supportState }.getOrNull()}")
        onDispose {
            runCatching { hands.stop() }
            subscription[0]?.cancel()
            navigator.closeWindowContainer(CONSOLE_ID)
            navigator.closeWindowContainer(CODEX_ID)
        }
    }

    // Background music plays while the app is in front.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val music = Music(context).also { Game.music = it }
        val observer = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) music.resume() else if (e == Lifecycle.Event.ON_PAUSE) music.pause()
            // Home hides the stage but not the windows it opened: close them and fold the menu when the game is no
            // longer seen at all (ON_STOP, not ON_PAUSE, which a window of our own opening also causes).
            if (e == Lifecycle.Event.ON_STOP) {
                // The codex narration stops too (device test 2026-11-12 in 掌心星球: a voice kept talking after Home).
                Game.narrate(null)
                Game.menuOpen = false
                runCatching { navigator.closeWindowContainer(CONSOLE_ID) }
                runCatching { navigator.closeWindowContainer(CODEX_ID) }
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            Game.narrate(null)
            music.release()
            Game.music = null
        }
    }

    // Rebuild entities when the level changes; reset them when the player presses retry.
    LaunchedEffect(Game.levelVersion, playfield) {
        val pf = playfield ?: return@LaunchedEffect
        synchronized(Game.lock) { pf.build(Game.session) }
    }
    LaunchedEffect(Game.resetVersion) {
        val pf = playfield ?: return@LaunchedEffect
        synchronized(Game.lock) { pf.resetVisuals(Game.session) }
    }
    LaunchedEffect(Game.debugPullVersion) {
        val pf = playfield ?: return@LaunchedEffect
        if (Game.debugPullVersion == 0) return@LaunchedEffect
        val mode = Game.debugPullMode
        synchronized(Game.lock) { pf.debugPull(Game.session, mode) }
        kotlinx.coroutines.delay(1500)
        Log.i("GravityTest", "aim after pull $mode: ${synchronized(Game.lock) { pf.debugAim() }} full=${Game.fullPreview}")
    }
    LaunchedEffect(Game.autoShotVersion) {
        val pf = playfield ?: return@LaunchedEffect
        if (Game.autoShotVersion == 0) return@LaunchedEffect
        val fired = synchronized(Game.lock) { pf.autoShot(Game.session, Game.autoShotMiss, Game.autoShotCoached) }
        Game.refresh()
        AutoPilot.onShot(fired)
    }
    // Each chapter has its own sky, and the IBL and light colors that go with it.
    LaunchedEffect(Game.levelId, sky, playfield) {
        val look = ChapterLook.of(Levels.all.firstOrNull { it.id == Game.levelId }?.chapter ?: 1)
        sky?.let { s ->
            // Decode a 4K panorama off the main thread, on first entering its chapter.
            withContext(Dispatchers.IO) { Game.assets?.preloadSky(look) }
            Game.assets?.setSky(s, look)
        }
        Game.lighting?.chapter = ChapterLook.all.indexOf(look) + 1
        Game.sun?.components?.get(DirectionalLightComponent::class.java)?.color = Color(look.key).toColor4()
        backdrop[0]?.let { Game.assets?.setBackdrop(it, ChapterLook.all.indexOf(look) + 1) }
    }
    LaunchedEffect(Game.skyOn, sky, playfield) {
        sky?.enabled = Game.skyOn
        // In the room, let the room's own light onto the models too.
        playfield?.lighting?.roomWeight = if (Game.skyOn) Lighting.SKY_ROOM_WEIGHT else Lighting.ROOM_ROOM_WEIGHT
    }
    // The 3D level map: rebuilt whenever progress or the current level changes while it is up.
    LaunchedEffect(Game.mapShown, Game.progressVersion, Game.levelId, Game.coachMap, playfield) {
        val pf = playfield ?: return@LaunchedEffect
        synchronized(Game.lock) { if (Game.mapShown) pf.map.show(Game.progress(), Game.levelId, Game.coachMap) else pf.map.hide() }
    }
    // Demonstrate the pull until the player has pulled once; float the steps over the launcher
    // until the first launch.
    LaunchedEffect(Game.tutorialStep, playfield) {
        playfield?.demoPull = Game.tutorialStep == Tutorial.Step.GRAB || Game.tutorialStep == Tutorial.Step.PULL
        playfield?.pullCoach = Game.tutorialStep.let { it != null && it != Tutorial.Step.DONE }
    }
    // The title card floats in front of the player; the probe waits until it is dismissed.
    LaunchedEffect(Game.showTitle, playfield) { playfield?.titleUp = Game.showTitle }
    LaunchedEffect(Game.progressVersion, playfield) { if (Game.allCardsFound) playfield?.gildProbe() }
    // "放到身边看": the chosen card's model, three times its window size, turning beside the player.
    LaunchedEffect(Game.codexOpen, playfield) { playfield?.codexUp = Game.codexOpen }
    LaunchedEffect(Game.showcase, Game.codexOpen, playfield) {
        val pf = playfield ?: return@LaunchedEffect
        showcase[0]?.let { pf.root.removeChild(it) }
        showcase[0] = null
        val card = Game.showcase?.let { com.vrgames.gravitysling.core.Codex.byId(it) } ?: return@LaunchedEffect
        val assets = Game.assets ?: return@LaunchedEffect
        if (!assets.loadCodexModel(card.model)) return@LaunchedEffect
        val model = assets.newCodexModel(card.model) ?: return@LaunchedEffect
        val holder = Entity().apply { addChild(model) }
        holder.components[TransformComponent::class.java]?.apply {
            // Beside the codex panel when it is open; otherwise at the side of the field, as before.
            val at = if (Game.codexOpen) com.vrgames.gravitysling.render.Playfield.CODEX_MODEL_POS else com.vrgames.gravitysling.core.Vec3(-0.5, 0.12, -0.42)
            setPosition(Vector3(at.x.toFloat(), at.y.toFloat(), at.z.toFloat()))
            setScaleVector(Vector3(if (Game.codexOpen) 1.6f else 2.4f))
        }
        pf.root.addChild(holder)
        showcase[0] = holder
    }
    LaunchedEffect(Game.coachBall, Game.ballIntroDismissed, playfield) {
        playfield?.ballIntroUp = Game.coachBall && !Game.ballIntroDismissed
    }
    // The mission card, until the player starts on the level (the first-run steps explain level 1).
    LaunchedEffect(Game.levelCardDismissed, Game.tutorialStep, playfield) {
        playfield?.levelCardUp = !Game.levelCardDismissed && Game.tutorialStep == null
    }
    // After a delivery, "next / again" float in front of the player.
    LaunchedEffect(Game.phase, Game.lastStatus, playfield) {
        playfield?.resultUp = Game.phase == Phase.RESULT && Game.lastStatus == FlightStatus.SUCCESS
    }
    LaunchedEffect(Game.missText, playfield) { playfield?.missUp = Game.missText != null }
    // Coach the gravity ball and the star map in the scene until the player has used each once.
    LaunchedEffect(Game.coachBall, Game.coachMap, playfield) {
        playfield?.coachBall = Game.coachBall
        playfield?.coachMap = Game.coachMap
    }
    LaunchedEffect(Game.heightOffset, playfield, Game.debugView) {
        val pf = playfield ?: return@LaunchedEffect
        val v = Game.debugView?.split(",")?.mapNotNull { it.trim().toFloatOrNull() }
        if (v != null && v.size == 5) pf.debugPose(v[0], v[1], v[2], v[3], v[4]) else pf.placeInRoom(Game.heightOffset)
    }

    SpatialView(
        Modifier.pointerInput(Unit) {
            fun meters(px: Float) = with(density) { converter.dpToLength(px.toDp(), LengthUnit.Meters) }.toDouble()
            detectSpatialDragGesture(
                context = context,
                targetedToEntity = TargetEntity.any(),
                onDragStart = { _, value ->
                    val pf = playfield
                    val g = pf?.grabOf(value.targetEntity)
                    val phase = synchronized(Game.lock) { Game.session.phase }
                    val flying = phase == Phase.FLYING
                    lastInput[0] = SystemClock.uptimeMillis()
                    val h = latestHands(hands)
                    val (l, r) = pinchValues(h)
                    pinch[0] = when {
                        l == null && r == null -> -1
                        (r ?: 0f) >= (l ?: 0f) -> if ((r ?: 0f) > PINCH_HELD) 1 else -1
                        else -> if ((l ?: 0f) > PINCH_HELD) 0 else -1
                    }
                    pinch[1] = 0
                    Log.i(INPUT_TAG, "start ${g ?: "none"} target=${value.targetEntity?.getName()} phase=$phase kind=${value.interactionKind} " +
                        "pinchL=$l pinchR=$r hand=${pinch[0]} stuck=${pf?.isDragging}")
                    // A start while a drag is still open means its end never came: close it first.
                    if (pf?.isDragging == true) endDrag(pf, grabbingProbe, "restart")
                    grabbingProbe[0] = false
                    when {
                        pf == null || g == null -> Unit
                        // The title card asks to start first (the probe is hidden until then).
                        Game.showTitle -> Unit
                        // Any pinch during a flight fires the engine (once, where the level allows it).
                        flying -> if (Game.requestBurn()) Game.refresh()
                        g is Grab.MapStar -> Game.pickFromMap(g.id)
                        g == Grab.Burn -> Unit
                        else -> {
                            val aiming = synchronized(Game.lock) {
                                pf.dragStart(g, Game.session)
                                Game.session.phase == Phase.AIMING
                            }
                            grabbingProbe[0] = g == Grab.Probe
                            if (g == Grab.Probe) Game.onProbeGrabbed() else if (aiming) Game.onBallGrabbed() else Game.mapShown = false
                            Game.refresh()
                        }
                    }
                },
                onDragEnd = { endDrag(playfield, grabbingProbe, "end") },
                onDragCancel = { endDrag(playfield, grabbingProbe, "cancel") },
            ) { value ->
                val pf = playfield ?: return@detectSpatialDragGesture
                lastInput[0] = SystemClock.uptimeMillis()
                // Compose y points down; space y points up.
                val d = Vec3(meters(value.dragAmount.x), -meters(value.dragAmount.y), meters(value.dragAmount.z))
                val pull = synchronized(Game.lock) {
                    pf.drag(d, Game.session)
                    Game.session.pull.length()
                }
                if (grabbingProbe[0]) Game.onPullChanged(pull)
            }
        },
        // Coaching labels: 2D panels that the playfield places next to what they explain.
        attachments = {
            AttachmentPanel(LABEL_BURN) { BurnLabel(converter) }
            AttachmentPanel(LABEL_BALL) { BallLabel(converter) }
            AttachmentPanel(LABEL_MAP) { MapLabel(converter) }
            AttachmentPanel(LABEL_INTRO) { IntroPanel(converter) }
            AttachmentPanel(LABEL_PULL) { PullLabel(converter) }
            AttachmentPanel(LABEL_RESULT) { ResultPanel(converter) }
            AttachmentPanel(LABEL_BALL_INTRO) { BallIntroPanel(converter) }
            AttachmentPanel(LABEL_LEVEL) { LevelCard(converter) }
            AttachmentPanel(LABEL_MISS) { MissPanel(converter) }
            AttachmentPanel(LABEL_CODEX) { if (Game.codexOpen) CodexPanel(converter) }
            AttachmentPanel(LABEL_MENU) {
                MenuPanel(
                    converter,
                    openConsole = { navigator.openWindowContainer(CONSOLE_ID) },
                    openCodex = { Game.openCodex() },
                    quit = { context.findActivity()?.finishAndRemoveTask() },
                )
            }
        },
        update = { _, attachments ->
            playfield?.let { pf -> if (!pf.hasLabels) attachLabels(pf, attachments) }
        },
        initial = { content, attachments ->
            // One sun, no real-time shadows: nothing in open space falls on anything for long, and
            // a shadow pass redraws the whole scene every frame. The IBL supplies the cool fill.
            content.addEntity(Entity().apply {
                components.set(DirectionalLightComponent(Color(0xFFFFF1DC).toColor4(), 1100f, false))
                components[TransformComponent::class.java]?.setEulerAngles(EulerAngles(-35f, 50f, 0f))
                Game.sun = this
            })

            val assets = Assets()
            assets.firstSky = ChapterLook.of(Levels.all.firstOrNull { it.id == Game.levelId }?.chapter ?: 1).sky
            // Textures decode off the main thread; AI-generated models load in parallel, and
            // primitives stand in if one fails.
            val iblTextures = withContext(Dispatchers.IO) {
                assets.loadTextures()
                Lighting.loadTextures()
            }
            coroutineScope {
                val probe = async { loadModel("asset://models/probe.glb", 0.1f) }
                val station = async { loadModel("asset://models/station.glb", 1f) }
                val rocks = (0 until 3).map { async { loadModel("asset://models/rock$it.glb", 1f) } }
                // Diameter 2, to match the radius-1 unit sphere it replaces.
                val sphere = async { loadModel("asset://models/sphere.glb", 2f) }
                assets.probeTemplate = probe.await()
                assets.stationTemplate = station.await()
                assets.rockTemplates = rocks.mapNotNull { it.await() }
                assets.sphereTemplate = sphere.await()
            }
            Game.assets = assets
            Game.assetsReady = true

            val skyEntity = assets.newSky().apply { enabled = Game.skyOn }
            content.addEntity(skyEntity)
            sky = skyEntity
            assets.newBackdrop().also { content.addEntity(it); backdrop[0] = it }
            assets.newDebrisField()?.let { content.addEntity(it) }

            val lighting = Lighting(iblTextures)
            content.addEntity(lighting.source)
            Game.lighting = lighting

            val pf = Playfield(assets)
            pf.lighting = lighting
            pf.placeInRoom(Game.heightOffset)
            content.addEntity(pf.root)
            lighting.receive(pf.root)
            backdrop[0]?.let { lighting.receive(it) }
            // Each card sits in the recess of a thick console frame.
            val frameFiles = mapOf(LABEL_INTRO to "intro", LABEL_RESULT to "result", LABEL_BALL_INTRO to "ball_intro", LABEL_LEVEL to "level", LABEL_MISS to "miss")
            frameFiles.forEach { (id, file) ->
                runCatching { Entity.loadSuspend("asset://panels/panel_$file.glb") }.getOrNull()?.let { pf.addFrame(id, it) }
            }
            pf.onAimGold = { gold -> main.post { Game.aimDelivers = gold } }
            // Spatial audio: effects are emitted from the playfield.
            pf.sound = Sound(pf.root).also { it.load(); Game.sound = it }
            pf.post = { r -> main.post(r) }
            pf.onBallNotice = { text -> main.post { Game.ballNotice = text } }
            attachLabels(pf, attachments)
            playfield = pf

            subscription[0] = content.subscribe(SceneEvents.Update::class.java) { e ->
                AutoPilot.onFrame(e.deltaTime.toDouble())
                // A pinched hand never holds perfectly still, so a drag that has been silent this
                // long has lost its end (hand tracking dropped the release): treat it as let go.
                if (pf.isDragging && lastInput[0] > 0 && SystemClock.uptimeMillis() - lastInput[0] > STUCK_DRAG_MS) {
                    lastInput[0] = 0L
                    main.post { if (pf.isDragging) endDrag(pf, grabbingProbe, "watchdog") }
                }
                // The pinching hand's fingers have opened: that is the release, whatever the
                // gesture system says (it sometimes keeps the drag alive after the fingers part).
                if (pf.isDragging && pinch[0] >= 0) {
                    val v = pinchValues(latestHands(hands)).toList()[pinch[0]]
                    pinch[1] = if (v != null && v < PINCH_OPEN) pinch[1] + 1 else 0
                    if (pinch[1] >= PINCH_OPEN_FRAMES) {
                        pinch[0] = -1
                        main.post { if (pf.isDragging) endDrag(pf, grabbingProbe, "fingers-open") }
                    }
                }
                showcase[0]?.components?.get(TransformComponent::class.java)?.let { t ->
                    showcaseYaw[0] = (showcaseYaw[0] + e.deltaTime * 25f) % 360f
                    t.eulerAngles = EulerAngles(10f, showcaseYaw[0], 0f)
                }
                val (changed, phase) = synchronized(Game.lock) {
                    pf.frame(e.deltaTime.toDouble() * Game.timeScale, Game.session) to Game.session.phase
                }
                if (changed) main.post {
                    if (phase == Phase.RESULT) Game.onFlightFinished() else Game.refresh()
                }
            }
        },
    )
}

private fun attachLabels(pf: Playfield, attachments: SpatialViewAttachments) {
    pf.attachLabels(
        attachments.entity(LABEL_BURN), attachments.entity(LABEL_BALL), attachments.entity(LABEL_MAP),
        attachments.entity(LABEL_INTRO), attachments.entity(LABEL_PULL),
        attachments.entity(LABEL_RESULT), attachments.entity(LABEL_MENU), attachments.entity(LABEL_BALL_INTRO), attachments.entity(LABEL_LEVEL),
        attachments.entity(LABEL_MISS), attachments.entity(LABEL_CODEX),
    )
}

/** Logcat tag for every pinch the stage sees (kept in release builds, for on-device reports). */
private const val INPUT_TAG = "GravityInput"
/** A drag with no callback for this long is taken as released. */
private const val STUCK_DRAG_MS = 2000L
/** Pinch strength (0 open .. 1 closed) above which a hand counts as the one pinching. */
private const val PINCH_HELD = 0.5f
/** Below this the fingers have parted; it must hold for a few frames (tracking jitters). */
private const val PINCH_OPEN = 0.3f
private const val PINCH_OPEN_FRAMES = 3

private fun latestHands(hands: HandTrackingProvider): HandTrackingData? =
    runCatching { if (hands.state == DataProvider.State.STARTED) hands.latestData else null }.getOrNull()

/** Left and right pinch strength, null where the hand is not tracked. */
private fun pinchValues(h: HandTrackingData?): Pair<Float?, Float?> {
    fun v(p: com.pico.spatial.tracking.hand.HandPinch?) = p?.takeIf { it.isReady }?.pinchValue
    return v(h?.leftPinch) to v(h?.rightPinch)
}

private fun android.content.Context.findActivity(): android.app.Activity? {
    var c: android.content.Context? = this
    while (c is android.content.ContextWrapper) {
        if (c is android.app.Activity) return c
        c = c.baseContext
    }
    return null
}

private fun endDrag(pf: Playfield?, grabbingProbe: BooleanArray, why: String) {
    if (pf == null) return
    val (pull, fired) = synchronized(Game.lock) { Game.session.pull.length() to pf.dragEnd(Game.session) }
    Log.i(INPUT_TAG, "$why probe=${grabbingProbe[0]} fired=$fired pull=${"%.3f".format(pull)}")
    if (grabbingProbe[0]) Game.onProbeReleased(fired) else Game.onDragFinished()
    grabbingProbe[0] = false
    Game.refresh()
}

private suspend fun loadModel(uri: String, size: Float): Entity? =
    runCatching { Entity.loadSuspend(uri) }.getOrNull()?.let { Assets.normalize(it, size) }
