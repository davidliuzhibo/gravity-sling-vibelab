package com.vrgames.gravitysling.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.ViewCoordinateSpace
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.content.SpatialView
import com.vrgames.gravitysling.core.Codex
import com.vrgames.gravitysling.game.Game
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.min

/**
 * Volumetric encyclopedia: the selected card's 3D model turns on the left, its text on the right.
 * Cards unlock as levels are cleared.
 */
@Composable
fun CodexWindow() {
    @Suppress("UNUSED_VARIABLE") val pv = Game.progressVersion
    val unlocked = Game.progress().unlockedCards().toSet()
    val selected = Game.selectedCard?.takeIf { it in unlocked } ?: unlocked.firstOrNull()

    Row(Modifier.fillMaxSize()) {
        CodexModel(selected, Modifier.weight(1f).fillMaxHeight())
        Column(
            Modifier.width(380.dp).fillMaxHeight().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("航天图鉴", fontSize = 26.sp)
            Text("已解锁 ${unlocked.size} / ${Codex.cards.size}", fontSize = 15.sp, color = Color(0xFFB8C2D6))
            // 20 cards don't fit one row in this width.
            Codex.cards.chunked(7).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { c ->
                        val open = c.id in unlocked
                        Button(onClick = { if (open) Game.selectedCard = c.id }) {
                            Text(if (!open) "？" else if (c.id == selected) "●" else "○", fontSize = 14.sp)
                        }
                    }
                }
            }
            val card = selected?.let { Codex.byId(it) }
            if (card == null) {
                Text("完成第一单，解锁第一张图鉴卡。", fontSize = 17.sp)
            } else {
                Text(card.title, fontSize = 24.sp, color = Color(0xFF6FE3FF))
                Text(card.subtitle, fontSize = 15.sp, color = Color(0xFFB8C2D6))
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                    Text(card.body, fontSize = 17.sp)
                }
            }
            Text(DISCLAIMER, fontSize = 12.sp, color = Color(0xFF8C96AA))
        }
    }
}

@Composable
private fun CodexModel(cardId: String?, modifier: Modifier) {
    val turntable = remember { Entity() }
    val slot = remember { Entity() }

    LaunchedEffect(cardId, Game.assetsReady) {
        val assets = Game.assets ?: return@LaunchedEffect
        slot.getChildren().forEach { it.destroy(true) }
        val key = Codex.byId(cardId ?: return@LaunchedEffect)?.model ?: return@LaunchedEffect
        val model = assets.newCodexModel(key)
        model?.let { slot.addChild(it) }
    }
    LaunchedEffect(Unit) {
        var yaw = 0f
        while (true) {
            delay(33)
            yaw = (yaw + 0.8f) % 360f
            turntable.components[TransformComponent::class.java]?.eulerAngles = EulerAngles(12f, yaw, 0f)
        }
    }

    SpatialView(modifier) { content, _ ->
        turntable.addChild(slot)
        content.addEntity(turntable)
        // Keep the model inside the volume: center it and fit to the smaller half extent.
        val corner = content.convertPosition(Vector3.ZERO, ViewCoordinateSpace.Local, content.localSpatialCoordinateSpace)
        val half = min(abs(corner.x), abs(corner.z)).takeIf { it > 0f } ?: 0.2f
        slot.components[TransformComponent::class.java]?.setScaleVector(Vector3(min(1f, half / 0.16f)))
    }
}
