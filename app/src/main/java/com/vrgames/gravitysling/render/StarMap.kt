package com.vrgames.gravitysling.render

import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import com.vrgames.gravitysling.core.BodyKind
import com.vrgames.gravitysling.core.BodySpec
import com.vrgames.gravitysling.core.Level
import com.vrgames.gravitysling.core.Levels
import com.vrgames.gravitysling.core.Progress
import com.vrgames.gravitysling.core.Vec3
import kotlin.math.cos
import kotlin.math.sin

/**
 * 3D level select floating above the playfield: one cluster per chapter around its planet, one
 * star per level; pinch a star to fly that level. Locked stars are grey, open ones glow cyan,
 * cleared ones turn gold with a pip per star earned. A violet ring marks a blind-challenge badge
 * and a white ring the current level. The diamond under each cluster is the chapter challenge.
 * While the map is being coached, the next story level to fly pulses harder.
 */
class StarMap(private val assets: Assets) {
    val root = Entity().apply { enabled = false }
    /** Pinchable star entity → level id. */
    val starOf = HashMap<Entity, String>()
    var visible = false; private set

    private var content: Entity? = null
    private val pulsing = ArrayList<Entity>()
    private var beacon: Entity? = null
    private var age = -1.0

    /** [suggest]: make the next story level stand out (map coaching). */
    fun show(progress: Progress, currentId: String, suggest: Boolean = false) {
        rebuild(progress, currentId, if (suggest) Levels.main.firstOrNull { progress.unlocked(it) && !progress.cleared(it.id) }?.id else null)
        if (!visible) {
            age = 0.0
            root.setScale(0.01f)
        }
        visible = true
        root.enabled = true
    }

    fun hide() {
        visible = false
        root.enabled = false
    }

    fun update(dt: Double, time: Double) {
        if (!visible) return
        if (age >= 0) {
            age += dt
            val x = (age / POP_SECONDS).coerceAtMost(1.0)
            // Ease out with a small overshoot, like the level entrance.
            val c = 1.70158
            root.setScale((1 + (c + 1) * (x - 1) * (x - 1) * (x - 1) + c * (x - 1) * (x - 1)).toFloat().coerceAtLeast(0.01f))
            if (x >= 1.0) age = -1.0
        }
        val pulse = (1.0 + 0.2 * sin(time * 4.0)).toFloat()
        pulsing.forEach { it.setScale(pulse) }
        beacon?.setScale((1.0 + 0.5 * (0.5 + 0.5 * sin(time * 6.0))).toFloat())
    }

    private fun rebuild(progress: Progress, currentId: String, suggestId: String?) {
        content?.let {
            root.removeChild(it)
            it.destroy(true)
        }
        starOf.clear()
        pulsing.clear()
        beacon = null
        val c = Entity()
        content = c
        root.addChild(c)
        root.setPos(CENTER)
        var prev: Vec3? = null
        var prevDone = false
        for (ch in 1..3) {
            val center = Vec3((ch - 2) * SPACING, 0.0, 0.0)
            c.addChild(assets.newBodyVisual(BodySpec("map$ch", "", BodyKind.PLANET, Vec3.ZERO, PLANET_R, 0.0, look = LOOKS[ch - 1])).apply {
                setPos(center)
            })
            val levels = Levels.all.filter { it.chapter == ch }
            for (lv in levels) {
                val pos = center + slot(lv)
                val done = progress.cleared(lv.id)
                if (lv.challenge) {
                    val last = levels.last { !it.challenge }
                    link(c, center + slot(last), pos, done)
                } else {
                    prev?.let { link(c, it, pos, prevDone) }
                    prev = pos
                    prevDone = done
                }
                c.addChild(star(lv, progress, lv.id == currentId, lv.id == suggestId).apply { setPos(pos) })
            }
        }
    }

    /** Main levels sit on an arc over the planet, from lower left to lower right; the challenge below. */
    private fun slot(lv: Level): Vec3 {
        val (deg, r) = if (lv.challenge) -90.0 to RING * 1.3 else 200.0 - (lv.number - 1) * 55.0 to RING
        val a = Math.toRadians(deg)
        return Vec3(cos(a) * r, sin(a) * r, 0.0)
    }

    private fun star(lv: Level, p: Progress, isCurrent: Boolean, isSuggested: Boolean): Entity {
        val e = Entity()
        val open = p.unlocked(lv)
        val done = p.cleared(lv.id)
        val core = when {
            !open -> assets.mapLocked
            lv.challenge -> assets.mapChallenge
            done -> assets.mapCleared
            else -> assets.mapOpen
        }
        if (lv.challenge) {
            e.addChild(model(assets.mapDiamond, core).apply {
                transform().eulerAngles = EulerAngles(35f, 45f, 0f)
                if (!open) setScale(0.7f)
            })
        } else {
            e.addChild(model(assets.unitSphere, core).apply { setScale(if (open) STAR_R else STAR_R * 0.7f) })
        }
        if (open && !done) {
            // Not yet delivered: a breathing halo.
            val halo = Entity().apply { addChild(model(assets.unitSphere, assets.mapOpenHalo).apply { setScale(STAR_R * 1.9f) }) }
            e.addChild(halo)
            if (isSuggested) beacon = halo else pulsing.add(halo)
        }
        if (done) {
            for (i in 0 until 3) {
                e.addChild(model(assets.unitSphere, if (i < p.stars(lv.id)) assets.mapPipOn else assets.mapPipOff).apply {
                    setScale(0.0045f)
                    setPos(Vector3((i - 1) * 0.013f, -0.027f, 0f))
                })
            }
        }
        val facing = quatFromY(Vec3(0.0, 0.0, 1.0))
        if (p.badge(lv.id)) e.addChild(model(assets.mapBadgeRing, assets.mapBadge).apply { transform().quaternion = facing })
        if (isCurrent) {
            val ring = Entity().apply { addChild(model(assets.mapRing, assets.mapCurrent).apply { transform().quaternion = facing }) }
            e.addChild(ring)
            pulsing.add(ring)
        }
        if (open) {
            e.components.set(CollisionComponent(listOf(ShapeResource.createSphere(0.032f)), PhysicsMaterialResource()))
            e.components.set(InteractableComponent())
            e.components.set(HoverEffectComponent())
            starOf[e] = lv.id
        }
        return e
    }

    private fun link(parent: Entity, a: Vec3, b: Vec3, done: Boolean) {
        val d = b - a
        parent.addChild(model(assets.bandMesh, if (done) assets.mapLinkDone else assets.mapLink).apply {
            setPos((a + b) * 0.5)
            transform().quaternion = quatFromY(d)
            transform().setScaleVector(Vector3(0.5f, d.length().toFloat(), 0.5f))
        })
    }

    companion object {
        /** Playfield-local center: above the launcher, a little further away than the probe. */
        val CENTER = Vec3(0.0, 0.42, -0.2)
        const val SPACING = 0.36
        const val RING = 0.11
        const val STAR_R = 0.013f
        const val PLANET_R = 0.035
        const val POP_SECONDS = 0.35
        private val LOOKS = listOf("earth", "mars", "jupiter")
    }
}
