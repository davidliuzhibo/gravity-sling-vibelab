package com.vrgames.gravitysling.render

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.LoadType
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.ecs.resource.TextureColorSpace
import com.pico.spatial.core.ecs.resource.TextureCreateOption
import com.pico.spatial.core.ecs.resource.TextureEncoding
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import com.vrgames.gravitysling.core.BodyKind
import com.vrgames.gravitysling.core.BodySpec

/**
 * Shared meshes, materials, textures and AI-generated model templates. Created once on the
 * stage's first frame; level rebuilds reuse them. Everything shared is marked [shared]: the SDK
 * releases a resource together with the entity that owns it, and a level rebuild destroys the
 * previous level's entities.
 */
/** Encyclopedia models that come as files (assets/models/codex/<key>.glb). */
val CODEX_MODELS = setOf("dish_probe", "sun_probe", "wing_probe", "capsule", "rover")

class Assets {
    val unitSphere: MeshResource = MeshResource.createSphere(1f).shared()
    val dotMesh: MeshResource = MeshResource.createSphere(0.006f).shared()
    val bandMesh: MeshResource = MeshResource.createCylinder(1f, 0.0035f).shared()
    val launcherMesh: MeshResource = MeshResource.createTorus(0.1f, 0.088f).shared()

    val aimMaterial = unlit(color(0.45f, 0.9f, 1f)).shared()
    val aimGoldMaterial = unlit(color(1f, 0.82f, 0.25f)).shared()
    val assistMaterial = unlit(color(1f, 0.8f, 0.25f, 0.55f), BlendingMode.TRANSPARENT).shared()
    val trailMaterial = unlit(color(1f, 0.62f, 0.25f)).shared()
    val hintMaterial = unlit(color(1f, 0.85f, 0.3f, 0.55f), BlendingMode.TRANSPARENT).shared()
    val bandMaterial = unlit(color(1f, 0.78f, 0.35f)).shared()
    val launcherMaterial = pbr(color(0.25f, 0.8f, 0.85f), metallic = 0.8f, roughness = 0.3f, emissive = color(0.05f, 0.35f, 0.4f)).shared()
    // The goal zone glows at its edge only, so the station inside stays clear.
    val goalMaterial = rim(color(0.35f, 0.95f, 1f, 0.45f)).shared()
    val goalLockedMaterial = rim(color(0.6f, 0.6f, 0.65f, 0.2f)).shared()
    val checkpointMaterial = pbr(color(0.2f, 0.8f, 1f), roughness = 0.3f, emissive = color(0.1f, 0.6f, 0.9f)).shared()
    val checkpointDoneMaterial = pbr(color(0.3f, 1f, 0.5f), roughness = 0.3f, emissive = color(0.15f, 0.8f, 0.3f)).shared()
    val ghostMaterial = unlit(color(0.75f, 0.45f, 1f, 0.28f), BlendingMode.TRANSPARENT).shared()
    val ghostProbeMaterial = unlit(color(0.55f, 0.95f, 1f, 0.35f), BlendingMode.TRANSPARENT).shared()
    /** "If you burn now" line during a flight, and the station's orbit when it moves. */
    val burnMaterial = unlit(color(1f, 0.55f, 0.2f, 0.85f), BlendingMode.TRANSPARENT).shared()
    val orbitMaterial = unlit(color(0.4f, 0.9f, 1f, 0.3f), BlendingMode.TRANSPARENT).shared()

    // Star map (level select): locked / open / cleared stars, earned-star pips, badges, links.
    val mapLocked = unlit(color(0.32f, 0.34f, 0.4f)).shared()
    val mapOpen = glow(color(0.45f, 0.95f, 1f)).shared()
    val mapOpenHalo = rim(color(0.45f, 0.95f, 1f, 0.5f)).shared()
    val mapCleared = pbr(color(1f, 0.82f, 0.3f), metallic = 0.6f, roughness = 0.25f, emissive = color(1f, 0.7f, 0.2f)).shared()
    val mapChallenge = pbr(color(0.85f, 0.45f, 1f), metallic = 0.5f, roughness = 0.25f, emissive = color(0.6f, 0.25f, 0.95f)).shared()
    val mapPipOn = glow(color(1f, 0.8f, 0.3f)).shared()
    val mapPipOff = unlit(color(0.25f, 0.27f, 0.32f)).shared()
    val mapBadge = glow(color(0.8f, 0.5f, 1f, 0.9f)).shared()
    val mapCurrent = unlit(color(1f, 1f, 1f, 0.7f), BlendingMode.TRANSPARENT).shared()
    val mapLink = unlit(color(0.45f, 0.65f, 0.9f, 0.3f), BlendingMode.TRANSPARENT).shared()
    val mapLinkDone = unlit(color(1f, 0.8f, 0.35f, 0.55f), BlendingMode.TRANSPARENT).shared()
    val mapRing: MeshResource = MeshResource.createTorus(0.026f, 0.023f).shared()
    val mapBadgeRing: MeshResource = MeshResource.createTorus(0.021f, 0.018f).shared()
    val mapDiamond: MeshResource = MeshResource.createBox(Vector3(0.022f, 0.022f, 0.022f)).shared()

    val confettiMesh: MeshResource = MeshResource.createBox(Vector3(0.012f, 0.012f, 0.003f)).shared()
    val confettiMaterial = pbr(color(1f, 0.8f, 0.3f), metallic = 0.7f, roughness = 0.3f, emissive = color(0.8f, 0.55f, 0.1f)).shared()
    private val starCore = pbr(color(1f, 0.82f, 0.3f), metallic = 0.6f, roughness = 0.25f, emissive = color(1f, 0.7f, 0.2f)).shared()
    private val starRingMat = unlit(color(1f, 0.9f, 0.55f, 0.7f), BlendingMode.TRANSPARENT).shared()
    private val starRing = MeshResource.createTorus(0.03f, 0.027f).shared()
    private val ballCore = pbr(color(0.55f, 0.3f, 1f), roughness = 0.2f, emissive = color(0.45f, 0.2f, 0.95f)).shared()
    private val ballHalo = glow(color(0.6f, 0.4f, 1f, 0.12f)).shared()
    private val rockMat by lazy {
        pbr(color(0.62f, 0.56f, 0.5f), roughness = 0.95f).apply { setBaseColorTexture(texture("textures/moon.jpg")) }.shared()
    }

    /**
     * Decoded textures by asset path. [loadTextures] fills it off the main thread at startup (a
     * 4K sky decoded on the main thread stalls the app long enough for the system to flag it);
     * anything missing is loaded on demand.
     */
    private val textures = java.util.concurrent.ConcurrentHashMap<String, TextureResource>()

    /** Call from a background dispatcher, before the first level is built. */
    fun loadTextures() {
        // Only the sky in use: each 4K panorama is about 45 MB of texture memory. [preloadSky] brings in the others.
        texture(firstSky)
        for (p in listOf("earth", "mars", "jupiter", "moon")) texture("textures/$p.jpg")
        normalMaps.values.forEach(::texture)
    }

    /** The sky of the chapter the game resumes in; set before [loadTextures]. */
    var firstSky = ChapterLook.of(1).sky

    /** Decode a chapter's sky ahead of [setSky]; call from a background dispatcher. */
    fun preloadSky(look: ChapterLook) { texture(look.sky) }

    private fun texture(path: String): TextureResource = textures.getOrPut(path) {
        val normal = path in normalMaps.values
        TextureResource.load(
            path,
            LoadType.FROM_ASSETS,
            TextureCreateOption().apply {
                if (normal) {
                    colorSpace = TextureColorSpace.RAW
                    textureEncoding = TextureEncoding.LINEAR
                }
            },
        ).shared()
    }

    // Atmospheres and halos: additive back-face shells (see [rim]), inner layers brighter.
    private val earthRims = listOf(rim(color(0.45f, 0.75f, 1f, 0.6f)), rim(color(0.33f, 0.56f, 1f, 0.28f))).map { it.shared() }
    private val marsRims = listOf(rim(color(1f, 0.6f, 0.4f, 0.3f)), rim(color(1f, 0.55f, 0.35f, 0.1f))).map { it.shared() }
    private val jupiterRims = listOf(rim(color(1f, 0.85f, 0.6f, 0.22f)), rim(color(1f, 0.8f, 0.55f, 0.08f))).map { it.shared() }
    private val ballRims = listOf(rim(color(0.7f, 0.45f, 1f, 0.5f)), rim(color(0.6f, 0.35f, 1f, 0.2f))).map { it.shared() }
    private val starHalo = rim(color(1f, 0.8f, 0.35f, 0.35f)).shared()
    private val beadMaterial = glow(color(0.5f, 0.95f, 1f, 0.9f)).shared()
    private val goldBead = glow(color(1f, 0.8f, 0.35f, 0.9f)).shared()
    private val violetBead = glow(color(0.75f, 0.45f, 1f, 0.9f)).shared()

    // Particles. Instances are scaled to the particle radius in meters.
    val sparkGold = glow(color(1f, 0.78f, 0.3f)).shared()
    val sparkFire = glow(color(1f, 0.5f, 0.15f)).shared()
    val sparkCyan = glow(color(0.4f, 0.95f, 1f)).shared()
    val sparkViolet = glow(color(0.75f, 0.45f, 1f)).shared()
    val exhaustMaterial = glow(color(0.55f, 0.85f, 1f, 0.8f)).shared()
    val dustMesh: MeshResource = MeshResource.createSphere(0.003f).shared()
    val dustMaterial = glow(color(0.7f, 0.8f, 1f, 0.6f)).shared()
    val flashMaterial = glow(color(1f, 0.6f, 0.25f, 0.8f)).shared()
    private val boosterMesh = MeshResource.createTorus(0.07f, 0.058f).shared()
    val boosterMaterial = pbr(color(1f, 0.75f, 0.2f), metallic = 0.9f, roughness = 0.2f, emissive = color(0.9f, 0.55f, 0.1f)).shared()
    private val boosterUsedMat = pbr(color(0.5f, 0.45f, 0.35f), metallic = 0.6f, roughness = 0.5f).shared()
    private val boosterCoreMat = unlit(color(1f, 0.8f, 0.3f, 0.14f), BlendingMode.TRANSPARENT).shared()
    private val portalInMat = unlit(color(0.7f, 0.35f, 1f, 0.35f), BlendingMode.TRANSPARENT).shared()
    private val portalOutMat = unlit(color(0.35f, 0.9f, 1f, 0.3f), BlendingMode.TRANSPARENT).shared()
    private val portalRingMat = pbr(color(0.6f, 0.3f, 1f), roughness = 0.2f, emissive = color(0.5f, 0.2f, 0.9f)).shared()
    private val portalOutRingMat = pbr(color(0.3f, 0.85f, 1f), roughness = 0.2f, emissive = color(0.15f, 0.6f, 0.9f)).shared()

    /** Textured planet materials, loaded on first use and cached. */
    private val bodyMats = HashMap<String, PhysicallyBasedMaterial>()

    private fun bodyMaterial(look: String): PhysicallyBasedMaterial = bodyMats.getOrPut(look) {
        // Small moons reuse the lunar texture with a tint.
        val (tex, tint, rough) = when (look) {
            "earth" -> Triple("earth", color(1f, 1f, 1f), 0.8f)
            "mars" -> Triple("mars", color(1f, 1f, 1f), 0.9f)
            "jupiter" -> Triple("jupiter", color(1f, 1f, 1f), 0.7f)
            "venus" -> Triple("venus", color(1f, 1f, 1f), 0.8f)
            "saturn" -> Triple("saturn", color(1f, 1f, 1f), 0.75f)
            "phobos", "deimos" -> Triple("moon", color(0.75f, 0.6f, 0.5f), 0.95f)
            "io" -> Triple("moon", color(1f, 0.9f, 0.45f), 0.9f)
            "europa" -> Triple("moon", color(0.95f, 0.9f, 0.8f), 0.6f)
            "ganymede" -> Triple("moon", color(0.75f, 0.7f, 0.65f), 0.9f)
            else -> Triple("moon", color(1f, 1f, 1f), 0.95f)
        }
        pbr(tint, roughness = rough).apply {
            setBaseColorTexture(texture("textures/$tex.jpg"))
            normalMaps[tex]?.let { setNormalTexture(texture(it)); setNormalScale(1f) }
        }.shared()
    }

    /** Debug: relief strength of every textured body that has a normal map. */
    fun setNormalScale(s: Float) {
        bodyMats.values.forEach { if (it.getNormalTexture() != null) it.setNormalScale(s) }
    }

    /** Relief for the rocky bodies, derived from their textures (tools/normal_from_height.py). */
    private val normalMaps = mapOf("moon" to "textures/moon_n.png", "mars" to "textures/mars_n.png")

    /**
     * Unit sphere (tools/make_sphere.py) for bodies with a normal map. On the built-in sphere the
     * moon and Mars textures came out smeared into streaks; this one maps them cleanly and carries
     * tangents for the relief. [tangentSphere] is a debug switch (takes effect on the next level).
     */
    var sphereTemplate: Entity? = null
        set(v) { field = v?.also(::shareResources) }
    var tangentSphere = true

    /** A unit sphere in the body's material, with tangents when its material has a normal map. */
    private fun bodySphere(look: String): Entity {
        val m = bodyMaterial(look)
        val template = sphereTemplate?.takeIf { tangentSphere && m.getNormalTexture() != null }
        return template?.clone(Entity.CloneOptions(true, true))?.also { setMaterials(it, m) } ?: model(unitSphere, m)
    }

    /** Irregular asteroid shapes (tools/make_rocks.py); spheres stand in until they load. */
    var rockTemplates: List<Entity> = emptyList()
        set(v) { field = v.onEach(::shareResources) }

    /**
     * Loaded asynchronously; null until ready (primitives are used meanwhile). Clones share the
     * template's meshes and materials, so those are made app-owned too.
     */
    var probeTemplate: Entity? = null
        set(v) { field = v?.also(::shareResources) }
    var stationTemplate: Entity? = null
        set(v) { field = v?.also(::shareResources) }

    private fun shareResources(e: Entity) {
        e.components[ModelComponent::class.java]?.let { m ->
            m.mesh?.shared()
            m.materials?.forEach { it.shared() }
        }
        e.getChildren().forEach(::shareResources)
    }

    fun newBodyVisual(b: BodySpec): Entity {
        val r = b.radius.toFloat()
        return when (b.kind) {
            BodyKind.PLANET, BodyKind.STAR -> Entity().apply {
                addChild(bodySphere(b.look).apply { setScale(r) })
                val rims = when (b.look) {
                    "earth" -> earthRims
                    "mars" -> marsRims
                    "jupiter" -> jupiterRims
                    else -> emptyList()
                }
                rims.forEachIndexed { i, m -> addChild(model(unitSphere, m).apply { setScale(r * (1.025f + 0.04f * i)) }) }
            }
            BodyKind.MOON -> bodySphere(b.look).apply { setScale(r) }
            BodyKind.GRAVITY_BALL -> Entity().apply {
                addChild(model(unitSphere, ballCore).apply { setScale(r * 0.7f) })
                addChild(model(unitSphere, ballHalo).apply { setScale(r * 1.6f) })
                ballRims.forEachIndexed { i, m -> addChild(model(unitSphere, m).apply { setScale(r * (0.8f + 0.25f * i)) }) }
            }
            BodyKind.ASTEROID -> newRock(b.id, r)
        }
    }

    private fun newRock(id: String, r: Float): Entity {
        val h = id.hashCode() and 0x7fffffff
        val template = rockTemplates.getOrNull(h % rockTemplates.size.coerceAtLeast(1))
        val rock = template?.clone(Entity.CloneOptions(true, true))?.also { setMaterials(it, rockMat) }
            ?: return model(unitSphere, rockMat).apply { transform().setScaleVector(Vector3(r * 1.2f, r * 0.85f, r)) }
        return rock.apply {
            setScale(r * 2.3f)
            transform().eulerAngles = EulerAngles((h % 360).toFloat(), (h / 360 % 360).toFloat(), 0f)
        }
    }

    private val goldMaterial by lazy { pbr(color(1f, 0.78f, 0.32f), metallic = 1f, roughness = 0.22f, emissive = color(0.18f, 0.12f, 0.02f)).shared() }

    /** Turns [e] (a probe) to gold: the reward for a full encyclopedia. */
    fun gild(e: Entity) = setMaterials(e, goldMaterial)

    private fun setMaterials(e: Entity, m: com.pico.spatial.core.ecs.resource.Material) {
        e.components[ModelComponent::class.java]?.materials?.let { mats -> for (i in 0 until mats.size) mats[i] = m }
        e.getChildren().forEach { setMaterials(it, m) }
    }

    /** A few glowing beads on a circle; spinning their parent makes a smooth ring visibly turn. */
    private fun beads(radius: Float, count: Int, size: Float, material: com.pico.spatial.core.ecs.resource.Material) = Entity().apply {
        for (i in 0 until count) {
            val a = i * 2 * Math.PI / count
            addChild(model(unitSphere, material).apply {
                setScale(size)
                setPos(Vector3((radius * kotlin.math.cos(a)).toFloat(), 0f, (radius * kotlin.math.sin(a)).toFloat()))
            })
        }
    }

    /** Launcher dock: the ring plus a turning bead ring (child 1, spun by the playfield). */
    fun newLauncher(): Entity = Entity().apply {
        addChild(model(launcherMesh, launcherMaterial))
        addChild(beads(0.094f, 6, 0.007f, beadMaterial))
    }

    /** Golden ring with a faint core; the ring axis is set by the caller. */
    fun newBooster(): Entity = Entity().apply {
        addChild(model(boosterMesh, boosterMaterial))
        addChild(model(unitSphere, boosterCoreMat).apply { setScale(0.055f) })
        addChild(beads(0.064f, 4, 0.008f, goldBead))
    }

    fun boosterUsed(e: Entity) {
        e.getChildren().firstOrNull()?.components?.get(com.pico.spatial.core.ecs.ModelComponent::class.java)?.materials?.set(0, boosterUsedMat)
    }

    /** Wormhole mouth: glowing ring around a translucent disk-like sphere. */
    fun newPortal(radius: Float, entry: Boolean): Entity = Entity().apply {
        addChild(model(MeshResource.createTorus(radius, radius - 0.012f), if (entry) portalRingMat else portalOutRingMat))
        addChild(model(unitSphere, if (entry) portalInMat else portalOutMat).apply {
            transform().setScaleVector(Vector3(radius * 0.92f, radius * 0.25f, radius * 0.92f))
        })
        addChild(beads(radius * 0.7f, 6, 0.008f, if (entry) violetBead else beadMaterial))
    }

    /** 3D model shown on an encyclopedia card. */
    fun newCodexModel(key: String): Entity? = when (key) {
        "probe" -> newProbeVisual().apply { setScale(3.2f) }
        "station" -> newStationVisual(0.28f)
        "booster" -> newBooster().apply { setScale(2.2f); transform().eulerAngles = EulerAngles(70f, 0f, 0f) }
        "wormhole" -> newPortal(0.12f, true).apply { transform().eulerAngles = EulerAngles(70f, 0f, 0f) }
        "asteroid" -> newBodyVisual(BodySpec("a", "", BodyKind.ASTEROID, com.vrgames.gravitysling.core.Vec3.ZERO, 0.09, 0.0))
        "gravity_ball" -> newBodyVisual(BodySpec("b", "", BodyKind.GRAVITY_BALL, com.vrgames.gravitysling.core.Vec3.ZERO, 0.1, 0.0))
        "earth", "mars", "jupiter", "venus" -> newBodyVisual(BodySpec(key, "", BodyKind.PLANET, com.vrgames.gravitysling.core.Vec3.ZERO, 0.13, 0.0, look = key))
        "saturn" -> newSaturn(0.1f).apply { transform().eulerAngles = EulerAngles(20f, 0f, 8f) }
        "sun" -> newSun(0.13f)
        "comet" -> newComet()
        "sail" -> newSail()
        in CODEX_MODELS -> codexTemplates[key]?.clone(Entity.CloneOptions(true, true))
        else -> newBodyVisual(BodySpec(key, "", BodyKind.MOON, com.vrgames.gravitysling.core.Vec3.ZERO, 0.12, 0.0, look = key))
    }

    // ---- The encyclopedia's newer models (2026-11-12) ----

    /** Original spacecraft for the journey cards (Seed3D, see docs/素材来源); loaded when first shown. */
    private val codexTemplates = java.util.concurrent.ConcurrentHashMap<String, Entity>()

    /** Loads model [key] of [CODEX_MODELS] once (main thread, suspending); true when it can be shown. */
    suspend fun loadCodexModel(key: String): Boolean {
        if (key !in CODEX_MODELS) return true
        if (codexTemplates.containsKey(key)) return true
        val e = runCatching { Entity.loadSuspend("asset://models/codex/$key.glb") }.getOrNull() ?: return false
        shareResources(e)
        codexTemplates[key] = e
        return true
    }

    private val sunMaterial by lazy { unlit(color(1f, 1f, 1f)).apply { setBaseColorTexture(texture("textures/sun.jpg")) }.shared() }
    private val sunHalo by lazy { listOf(rim(color(1f, 0.7f, 0.25f, 0.6f)), rim(color(1f, 0.55f, 0.15f, 0.25f))).map { it.shared() } }

    /** The sun: its own light (unlit), with a warm corona. */
    fun newSun(r: Float) = Entity().apply {
        addChild(model(unitSphere, sunMaterial).apply { setScale(r) })
        sunHalo.forEachIndexed { i, m -> addChild(model(unitSphere, m).apply { setScale(r * (1.04f + 0.08f * i)) }) }
    }

    private val ringMaterial by lazy {
        pbr(color(1f, 1f, 1f), roughness = 0.8f, blending = BlendingMode.TRANSPARENT).apply {
            setBaseColorTexture(texture("textures/saturn_ring.png"))
            setCullingMode(MaterialCullingMode.NONE)
        }.shared()
    }
    private val ringMesh by lazy { annulus(1.24f, 2.3f, 96).shared() }

    /** Saturn: the banded globe and its ring (a flat band textured across its width). */
    fun newSaturn(r: Float) = Entity().apply {
        addChild(newBodyVisual(BodySpec("saturn", "", BodyKind.PLANET, com.vrgames.gravitysling.core.Vec3.ZERO, r.toDouble(), 0.0, look = "saturn")))
        addChild(model(ringMesh, ringMaterial).apply { setScale(r) })
    }

    /** A flat ring from [inner] to [outer] in the xz plane; u runs from the inner edge to the outer. */
    private fun annulus(inner: Float, outer: Float, seg: Int): MeshResource {
        val pos = ArrayList<Vector3>(); val nor = ArrayList<Vector3>(); val tan = ArrayList<com.pico.spatial.core.math.Vector4>(); val uv = ArrayList<com.pico.spatial.core.math.Vector2>()
        val idx = ArrayList<Int>()
        for (i in 0..seg) {
            val a = 2 * Math.PI * i / seg
            val c = kotlin.math.cos(a).toFloat(); val sn = kotlin.math.sin(a).toFloat()
            for ((k, rr) in listOf(inner, outer).withIndex()) {
                pos += Vector3(rr * c, 0f, rr * sn); nor += Vector3(0f, 1f, 0f)
                tan += com.pico.spatial.core.math.Vector4(-sn, 0f, c, 1f); uv += com.pico.spatial.core.math.Vector2(k.toFloat(), 0.5f)
            }
        }
        for (i in 0 until seg) {
            val a = i * 2; val b = a + 1; val c = a + 2; val d = a + 3
            idx += listOf(a, c, b, b, c, d)
        }
        return MeshResource.createWithMeshModel(com.pico.spatial.core.ecs.resource.MeshModel(pos, idx, nor, tan, uv), null, "saturn_ring")
    }

    private val cometTail by lazy { glow(color(0.55f, 0.85f, 1f, 0.35f)).shared() }
    private val cometDust by lazy { glow(color(1f, 0.9f, 0.7f, 0.25f)).shared() }

    /** A comet: a dark icy nucleus and a long tail of fading glow (pointing away from where the sun would be). */
    fun newComet() = Entity().apply {
        addChild(newRock("comet", 0.06f))
        for (k in 1..14) {
            val t = k / 14f
            addChild(model(unitSphere, cometTail).apply { setScale(0.05f * (1f + 1.6f * t)); setPos(Vector3(0.05f + 0.32f * t, 0.01f * t, 0f)) })
            addChild(model(unitSphere, cometDust).apply { setScale(0.03f * (1f + t)); setPos(Vector3(0.04f + 0.26f * t, -0.05f * t * t, 0f)) })
        }
    }

    private val sailMaterial by lazy { pbr(color(0.95f, 0.85f, 0.55f), metallic = 1f, roughness = 0.15f).apply { setCullingMode(MaterialCullingMode.NONE) }.shared() }

    /** A square solar sail on four booms round a small hub. */
    fun newSail() = Entity().apply {
        val boom = pbr(color(0.85f, 0.85f, 0.88f), metallic = 0.6f, roughness = 0.4f)
        addChild(model(MeshResource.createBox(Vector3(0.3f, 0.0008f, 0.3f)), sailMaterial).apply { transform().eulerAngles = EulerAngles(80f, 45f, 0f) })
        addChild(model(MeshResource.createCylinder(0.04f, 0.035f), pbr(color(0.8f, 0.65f, 0.3f), metallic = 0.9f, roughness = 0.3f)).apply { transform().eulerAngles = EulerAngles(80f, 45f, 0f) })
        for (k in 0 until 2) addChild(model(MeshResource.createBox(Vector3(0.43f, 0.004f, 0.004f)), boom).apply { transform().eulerAngles = EulerAngles(80f, 45f + 90f * k, 0f) })
    }

    fun newStar(): Entity = Entity().apply {
        addChild(model(unitSphere, starCore).apply { setScale(0.016f) })
        addChild(model(starRing, starRingMat).apply { transform().eulerAngles = EulerAngles(70f, 0f, 0f) })
        addChild(model(unitSphere, starHalo).apply { setScale(0.03f) })
    }

    fun newProbeVisual(): Entity {
        probeTemplate?.clone(Entity.CloneOptions(true, true))?.let { return it }
        // Primitive stand-in: white body, orange band, blue panels.
        return Entity().apply {
            addChild(model(unitSphere, pbr(color(0.95f, 0.95f, 0.97f), roughness = 0.35f)).apply { setScale(0.03f) })
            addChild(model(MeshResource.createTorus(0.032f, 0.026f), pbr(color(1f, 0.55f, 0.15f), roughness = 0.4f)).apply {
                transform().eulerAngles = EulerAngles(90f, 0f, 0f)
            })
            val panel = pbr(color(0.15f, 0.3f, 0.85f), metallic = 0.5f, roughness = 0.3f).shared()
            addChild(model(MeshResource.createBox(Vector3(0.05f, 0.002f, 0.025f)), panel).apply { setPos(Vector3(-0.058f, 0f, 0f)) })
            addChild(model(MeshResource.createBox(Vector3(0.05f, 0.002f, 0.025f)), panel).apply { setPos(Vector3(0.058f, 0f, 0f)) })
        }
    }

    fun newStationVisual(size: Float): Entity {
        stationTemplate?.clone(Entity.CloneOptions(true, true))?.let { return it.apply { setScale(size) } }
        return Entity().apply {
            addChild(model(MeshResource.createTorus(size * 0.5f, size * 0.4f), pbr(color(0.92f, 0.95f, 0.97f), metallic = 0.3f, roughness = 0.4f)))
            addChild(model(unitSphere, pbr(color(0.3f, 0.9f, 0.9f), emissive = color(0.1f, 0.6f, 0.6f))).apply { setScale(size * 0.15f) })
        }
    }

    private val skyMaterials = HashMap<String, com.pico.spatial.core.ecs.resource.Material>()

    private fun skyMaterial(path: String) = skyMaterials.getOrPut(path) {
        UnlitMaterial.create().apply {
            setBaseColorTexture(texture(path))
            setCullingMode(MaterialCullingMode.FRONT)
        }.shared()
    }

    /** Inside-out sphere with the first chapter's panorama (the Milky Way). */
    fun newSky(): Entity = model(MeshResource.createSphere(40f), skyMaterial(firstSky))

    /**
     * A big world far beyond the playfield, so the sky has scale. It never takes part in the flight.
     * One sphere with a soft atmosphere; [setBackdrop] changes its look per chapter.
     */
    fun newBackdrop(): Entity = Entity().apply {
        addChild(model(unitSphere, bodyMaterial("jupiter")))
        addChild(model(unitSphere, jupiterRims[0]).apply { setScale(1.03f) })
        setPos(Vector3(-13f, 2.5f, -24f))
        setScale(8f)
    }

    fun setBackdrop(backdrop: Entity, chapter: Int) {
        val look = when (chapter) { 1 -> "earth"; 2 -> "mars"; else -> "jupiter" }
        backdrop.getChildren().firstOrNull()?.let { setMaterials(it, bodyMaterial(look)) }
        val rims = when (chapter) { 1 -> earthRims; 2 -> marsRims; else -> jupiterRims }
        backdrop.getChildren().getOrNull(1)?.let { setMaterials(it, rims[0]) }
    }

    /**
     * A field of distant rocks around the play volume, one instanced draw. Clearly beyond the bodies
     * of the level, so it reads as scenery and never as an obstacle.
     */
    fun newDebrisField(count: Int = 36): Entity? {
        val rock = rockTemplates.firstOrNull() ?: return null
        val mesh = firstMesh(rock) ?: return null
        var seed = 11
        fun rnd(): Float { seed = seed * 1103515245 + 12345; return ((seed ushr 8) and 0xFFFF) / 65536f }
        val instances = (0 until count).map { i ->
            val a = rnd() * 6.2832f
            val r = 3.2f + rnd() * 3.5f
            val y = (rnd() - 0.5f) * 2.2f
            val s = .05f + rnd() * rnd() * .28f
            com.pico.spatial.core.ecs.resource.MeshInstancesResource.Instance(
                "debris$i",
                com.pico.spatial.core.math.Transform(
                    Vector3(r * kotlin.math.cos(a), y, r * kotlin.math.sin(a)),
                    EulerAngles(rnd() * 360f, rnd() * 360f, rnd() * 360f),
                    Vector3(s),
                ),
            )
        }
        return com.pico.spatial.core.ecs.ModelEntity(mesh, rockMat).also {
            it.components[ModelComponent::class.java]!!.meshInstances = com.pico.spatial.core.ecs.resource.MeshInstancesResource.create("debris", instances)
        }
    }

    private fun firstMesh(e: Entity): MeshResource? =
        e.components[ModelComponent::class.java]?.mesh ?: e.getChildren().firstNotNullOfOrNull { firstMesh(it) }

    /** Swaps the panorama on a sphere made by [newSky]. */
    fun setSky(sky: Entity, look: ChapterLook) = setMaterials(sky, skyMaterial(look.sky))

    companion object {
        /** Scales a loaded model so its longest side is [size] meters and centers it. */
        fun normalize(model: Entity, size: Float): Entity {
            val b = model.getVisualBounds(null, recursive = true, enabledOnly = false)
            val longest = maxOf(b.size.x, b.size.y, b.size.z).coerceAtLeast(1e-4f)
            val s = size / longest
            model.components[TransformComponent::class.java]?.apply {
                scaleVector = Vector3(s)
                setPosition(Vector3(-b.center.x * s, -b.center.y * s, -b.center.z * s))
            }
            return Entity().apply { addChild(model) }
        }
    }
}
