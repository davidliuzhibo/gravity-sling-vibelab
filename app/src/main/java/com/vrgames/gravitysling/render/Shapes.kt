package com.vrgames.gravitysling.render

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.Material
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.MeshInstancesResource
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.ecs.resource.Resource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Transform
import com.pico.spatial.core.math.Vector3
import com.vrgames.gravitysling.core.Vec3
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

fun Vec3.toVector3() = Vector3(x.toFloat(), y.toFloat(), z.toFloat())

fun Entity.transform(): TransformComponent = components[TransformComponent::class.java]!!

fun Entity.setPos(p: Vector3) { transform().setPosition(p) }
fun Entity.setPos(p: Vec3) { transform().setPosition(p.toVector3()) }
fun Entity.setScale(s: Float) { transform().setScaleVector(Vector3(s)) }

fun color(r: Float, g: Float, b: Float, a: Float = 1f) = Color4(r, g, b, a)

fun unlit(c: Color4, blending: BlendingMode = BlendingMode.OPAQUE): UnlitMaterial =
    UnlitMaterial.create(blending).apply { setBaseColor(c) }

fun pbr(
    base: Color4,
    metallic: Float = 0f,
    roughness: Float = 0.6f,
    emissive: Color4? = null,
    blending: BlendingMode = BlendingMode.OPAQUE,
): PhysicallyBasedMaterial = PhysicallyBasedMaterial.create(blending).apply {
    setBaseColor(base)
    setMetallic(metallic)
    setRoughness(roughness)
    if (emissive != null) setEmissiveColor(emissive)
}

/** Additive light: brightens what is behind it, never darkens. [c]'s alpha scales the strength. */
fun glow(c: Color4): UnlitMaterial =
    UnlitMaterial.create(BlendingMode.ADD).apply {
        setBaseColor(Color4(c.red * c.alpha, c.green * c.alpha, c.blue * c.alpha, c.alpha))
    }

/**
 * Additive back faces only. On a shell slightly larger than a planet, the planet hides the middle,
 * so only a thin ring at the silhouette shows: a cheap atmosphere/halo without a custom shader.
 */
fun rim(c: Color4): UnlitMaterial = glow(c).apply { setCullingMode(MaterialCullingMode.FRONT) }

fun model(mesh: MeshResource, material: Material) = ModelEntity(mesh, material)

/** Makes a resource app-owned, so destroying an entity that uses it does not release it. */
fun <T : Resource> T.shared(): T = apply { toGlobal() }

/** Rotation that turns +Y onto [dir] (used for bands and rings). */
fun quatFromY(dir: Vec3): Quat {
    val d = dir.normalized()
    val dot = d.y.coerceIn(-1.0, 1.0)
    if (dot > 0.99999) return Quat(0f, 0f, 0f, 1f)
    if (dot < -0.99999) return Quat(1f, 0f, 0f, 0f)
    // axis = Y x d = (d.z, 0, -d.x)
    val ax = d.z
    val az = -d.x
    val len = sqrt(ax * ax + az * az)
    val half = acos(dot) / 2
    val s = sin(half) / len
    return Quat((ax * s).toFloat(), 0f, (az * s).toFloat(), cos(half).toFloat())
}

/**
 * A pool of identical small meshes drawn with instancing (one draw call), used for the aiming
 * line, the flight trail and the hint path. Unused slots are collapsed to scale 0.
 */
class DotLine(
    private val name: String,
    val capacity: Int,
    mesh: MeshResource,
    material: Material,
    private val dotScale: Float = 1f,
) {
    val entity: Entity = ModelEntity(mesh, material)
    private val ids = Array(capacity) { "$name$it" }
    private val instances = MeshInstancesResource.create(
        name,
        ids.map { MeshInstancesResource.Instance(it, hidden()) },
    )
    private var shown = 0
    /** Debug: how many dots are showing and where the last one is. */
    val count get() = shown
    var last: Vec3? = null; private set

    init {
        entity.components[ModelComponent::class.java]!!.meshInstances = instances
    }

    private fun hidden() = Transform(Vector3(0f, -100f, 0f), EulerAngles(0f, 0f, 0f), Vector3(0f))

    /** Shows the given points (fading the size toward the end if [taper]). */
    fun show(points: List<Vec3>, taper: Boolean = false) {
        val n = minOf(points.size, capacity)
        for (i in 0 until n) {
            val s = if (taper) dotScale * (1f - 0.6f * i / n.coerceAtLeast(1)) else dotScale
            instances.update(ids[i], Transform(points[i].toVector3(), EulerAngles(0f, 0f, 0f), Vector3(s)))
        }
        for (i in n until shown) instances.update(ids[i], hidden())
        shown = n
        last = if (n > 0) points[n - 1] else null
    }

    fun clear() = show(emptyList())

    /** Sets a single slot (for ring-buffer style trails); cheaper than [show]. */
    fun set(slot: Int, p: Vec3) {
        instances.update(ids[slot], Transform(p.toVector3(), EulerAngles(0f, 0f, 0f), Vector3(dotScale)))
        if (slot >= shown) shown = slot + 1
    }
}
