package com.vrgames.gravitysling.render

import com.pico.spatial.core.ecs.EnvironmentLightingSettingsComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ImageBasedLightComponent
import com.pico.spatial.core.ecs.ImageBasedLightReceiverComponent
import com.pico.spatial.core.ecs.ImageBasedLightSource
import com.pico.spatial.core.ecs.LoadType
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.resource.TextureCreateOption
import com.pico.spatial.core.ecs.resource.TextureEncoding
import com.pico.spatial.core.ecs.resource.TextureResource

/**
 * Image-based light from the Milky Way panorama (tools/ibl_source.py + Filament cmgen).
 *
 * The stage runs in Mixed style so the player can switch to their room, and in Mixed style the
 * system lights PBR models from the camera view of the real room: a metal probe floating among
 * the stars would reflect the bedroom. A local IBL fixes that. Each model opts in with a receiver
 * component, and [roomWeight] decides how much of the room's light is still mixed in
 * (0 under the star sky, more when the player switches to their room).
 *
 * Each chapter has its own sky ([ChapterLook]); [chapter] swaps in the IBL made from that sky.
 */
class Lighting(private val textures: List<TextureResource?>) {
    val source = Entity()
    private val roots = ArrayList<Entity>()

    var roomWeight = SKY_ROOM_WEIGHT
        set(v) {
            field = v
            roots.forEach(::apply)
        }

    var exponent = EXPONENT
        set(v) {
            field = v
            source.components[ImageBasedLightComponent::class.java]?.intensityExponent = v
        }

    var chapter = 1
        set(v) {
            if (v == field) return
            field = v
            val tex = textures.getOrNull(v - 1) ?: return
            source.components[ImageBasedLightComponent::class.java]?.source = ImageBasedLightSource.Single(tex)
        }

    init {
        textures.firstOrNull()?.let { source.components.set(ImageBasedLightComponent(ImageBasedLightSource.Single(it), EXPONENT)) }
    }

    /** Lights every model under [root] with the space IBL, now and whenever the weight changes. */
    fun receive(root: Entity) {
        if (roots.none { it === root }) roots.add(root)
        apply(root)
    }

    /** Stops tracking a subtree that is about to be destroyed. */
    fun forget(root: Entity) {
        roots.removeAll { it === root }
    }

    private fun apply(e: Entity) {
        if (e.components[ModelComponent::class.java] != null) {
            e.components.set(ImageBasedLightReceiverComponent(source))
            e.components.set(EnvironmentLightingSettingsComponent(roomWeight))
        }
        e.getChildren().forEach(::apply)
    }

    companion object {
        /** Loads each chapter's prefiltered IBL cubemap; call from a background dispatcher. Null where one fails. */
        fun loadTextures(): List<TextureResource?> = ChapterLook.all.map { look ->
            runCatching {
                TextureResource.load(
                    look.ibl,
                    LoadType.FROM_ASSETS,
                    TextureCreateOption().apply { textureEncoding = TextureEncoding.LINEAR },
                ).shared()
            }.onFailure { android.util.Log.w("GravityLight", "${look.ibl}: ${it.message}") }.getOrNull()
        }

        /** Brightness of the space IBL (SDK default 8; the panorama is dark, so a bit higher). */
        const val EXPONENT = 9f
        const val SKY_ROOM_WEIGHT = 0f
        const val ROOM_ROOM_WEIGHT = 0.7f
    }
}

/**
 * A chapter's surroundings: its sky panorama, the IBL made from it (tools/ibl_source.py + cmgen),
 * and the colors of the key and rim lights (ARGB).
 */
class ChapterLook(val sky: String, val ibl: String, val key: Long, val rim: Long) {
    companion object {
        val all = listOf(
            // Moon port: the blue Milky Way, a white sun.
            ChapterLook("textures/sky.jpg", "textures/space_ibl.ktx", 0xFFFFF1DC, 0xFF9DB8FF),
            // Red route: a rust-colored nebula, warmer sun and a red rim.
            ChapterLook("textures/sky_ch2.jpg", "textures/space_ibl_ch2.ktx", 0xFFFFD9BC, 0xFFFF9278),
            // Deep-space mail: amber nebulae and a violet cluster, golden sun and a violet rim.
            ChapterLook("textures/sky_ch3.jpg", "textures/space_ibl_ch3.ktx", 0xFFFFE6B8, 0xFFC0A4FF),
        )

        fun of(chapter: Int) = all[(chapter - 1).coerceIn(0, all.size - 1)]
    }
}
