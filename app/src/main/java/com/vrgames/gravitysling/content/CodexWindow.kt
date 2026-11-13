package com.vrgames.gravitysling.content

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.ViewCoordinateSpace
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.content.SpatialView
import com.vrgames.gravitysling.core.Codex
import com.vrgames.gravitysling.core.CodexCard
import com.vrgames.gravitysling.core.Route
import com.vrgames.gravitysling.core.Shelf
import com.vrgames.gravitysling.game.Game
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val Cyan = Color(0xFF6FE3FF)
private val Dim = Color(0xFFB8C2D6)
private val Faint = Color(0xFF8C96AA)
private val Gold = Color(0xFFFFC94D)
private val Chip = Color(0x22FFFFFF)
private val ChipOn = Color(0x556FE3FF)

/**
 * 航天图鉴 (volume window), rebuilt 2026-11-12 after the review asked for a stronger encyclopedia:
 * three shelves (bodies, craft and journeys, ideas); named cards; on the chosen one its model turns
 * on the left, and on the right its key figures, a drawing of its route through the solar system
 * that draws itself, the text, its narration, and a way to see the model large beside the player.
 * Every card collected turns 小驿号 gold.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CodexWindow() {
    @Suppress("UNUSED_VARIABLE") val pv = Game.progressVersion
    val unlocked = Game.progress().unlockedCards().toSet()
    val selected = Game.selectedCard?.takeIf { it in unlocked } ?: unlocked.firstOrNull()
    val card = selected?.let { Codex.byId(it) }
    var shelf by remember { mutableStateOf(card?.shelf ?: Shelf.BODY) }
    DisposableEffect(Unit) { onDispose { Game.narrate(null) } }

    Row(Modifier.fillMaxSize()) {
        CodexModel(selected, Modifier.weight(1f).fillMaxHeight())
        Column(
            Modifier.width(520.dp).fillMaxHeight().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("航天图鉴", fontSize = 26.sp)
                val all = Codex.cards.size
                Text(
                    if (unlocked.size >= all) "全部 $all 张收齐！小驿号已镀金" else "已收集 ${unlocked.size} / $all",
                    fontSize = 15.sp, color = if (unlocked.size >= all) Gold else Dim,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (sh in Shelf.entries) {
                    val have = Codex.onShelf(sh).count { it.id in unlocked }
                    Tab("${sh.title} $have/${Codex.onShelf(sh).size}", sh == shelf) { shelf = sh }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (c in Codex.onShelf(shelf)) {
                    val open = c.id in unlocked
                    Tab(if (open) c.title else "？？？", c.id == selected, small = true, enabled = open) {
                        Game.selectedCard = c.id
                        Game.narrate(c.id)
                    }
                }
            }
            if (card == null) {
                Text("完成第一单，解锁第一张图鉴卡。", fontSize = 17.sp)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(card.title, fontSize = 24.sp, color = Cyan)
                        Text(card.subtitle, fontSize = 14.sp, color = Dim)
                    }
                    Tab(if (Game.narrating == card.id) "■ 停止讲解" else "▶ 听讲解", Game.narrating == card.id, small = true) {
                        Game.narrate(if (Game.narrating == card.id) null else card.id)
                    }
                    Tab(if (Game.showcase == card.id) "收回模型" else "放到身边看", Game.showcase == card.id, small = true) {
                        Game.showcase = if (Game.showcase == card.id) null else card.id
                    }
                }
                Figures(card)
                if (card.route.isNotEmpty()) RouteDrawing(card)
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                    Text(card.body, fontSize = 16.sp)
                }
            }
            Text(DISCLAIMER, fontSize = 11.sp, color = Faint)
        }
    }
}

@Composable
private fun Tab(text: String, on: Boolean, small: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Text(
        text,
        Modifier.clip(shape).background(if (on) ChipOn else Chip).border(1.dp, if (on) Cyan else Color(0x33FFFFFF), shape)
            .clickable(enabled = enabled) { onClick() }.padding(horizontal = if (small) 10.dp else 14.dp, vertical = if (small) 6.dp else 8.dp),
        fontSize = if (small) 14.sp else 16.sp, color = if (enabled) Color.White else Faint,
    )
}

/** The card's key figures as labelled tiles. */
@Composable
private fun Figures(card: CodexCard) {
    if (card.stats.isEmpty()) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((k, v) in card.stats) {
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(Color(0x1A6FE3FF)).padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(k, fontSize = 12.sp, color = Dim, textAlign = TextAlign.Center)
                Text(v, fontSize = 15.sp, color = Gold, textAlign = TextAlign.Center)
            }
        }
    }
}

/**
 * The journey from above: the sun in the middle, the orbits of the bodies it passes, and the route
 * drawing itself from stop to stop with the probe at its head, over and over.
 */
@Composable
internal fun RouteDrawing(card: CodexCard) {
    val t by rememberInfiniteTransition(label = "route").animateFloat(
        0f, 1f, infiniteRepeatable(tween(card.route.size * 1400 + 1200, easing = LinearEasing), RepeatMode.Restart), label = "routeT",
    )
    val stops = card.route.mapNotNull { Route.ORBITS[it]?.let { o -> it to o } }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Canvas(Modifier.width(150.dp).height(150.dp)) {
            val c = Offset(size.width / 2, size.height / 2)
            val R = size.minDimension / 2 * 0.95f
            fun at(o: Pair<Double, Double>, k: Int): Offset {
                // A body met twice is drawn a little further round its orbit the second time.
                val a = Math.toRadians(o.second + 28.0 * k)
                return Offset(c.x + (R * o.first * cos(a)).toFloat(), c.y - (R * o.first * sin(a)).toFloat())
            }
            drawCircle(Color(0xFFFFB347), R * 0.06f, c)
            for ((_, o) in stops.distinctBy { it.first }) if (o.first > 0) drawCircle(Color(0x33FFFFFF), (R * o.first).toFloat(), c, style = Stroke(1.dp.toPx()))
            val seen = HashMap<String, Int>()
            val pts = stops.map { (n, o) -> at(o, seen.merge(n, 1, Int::plus)!! - 1) }
            if (pts.size < 2) return@Canvas
            // Each leg bends round the sun a little, as transfer orbits do.
            val path = Path().apply {
                moveTo(pts[0].x, pts[0].y)
                for (i in 1 until pts.size) {
                    val a = pts[i - 1]; val b = pts[i]
                    val m = Offset((a.x + b.x) / 2, (a.y + b.y) / 2)
                    val out = Offset(m.x - c.x, m.y - c.y)
                    val ctrl = Offset(m.x + out.y * 0.35f, m.y - out.x * 0.35f)
                    quadraticTo(ctrl.x, ctrl.y, b.x, b.y)
                }
            }
            val measure = PathMeasure().apply { setPath(path, false) }
            val drawn = Path()
            val len = measure.length * min(1f, t * 1.15f)
            measure.getSegment(0f, len, drawn, true)
            drawPath(path, Color(0x22FFFFFF), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))))
            drawPath(drawn, Gold, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
            for (p in pts) drawCircle(Cyan, 4.dp.toPx(), p)
            val head = measure.getPosition(len)
            drawCircle(Color.White, 3.5.dp.toPx(), head)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("航线", fontSize = 13.sp, color = Dim)
            Text(card.route.joinToString(" → "), fontSize = 15.sp, color = Color.White)
            Text("示意图：距离按比例压缩，金线是探测器飞过的路。", fontSize = 12.sp, color = Faint)
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
        // Spacecraft models come as files, loaded the first time they are shown.
        if (!assets.loadCodexModel(key)) return@LaunchedEffect
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
