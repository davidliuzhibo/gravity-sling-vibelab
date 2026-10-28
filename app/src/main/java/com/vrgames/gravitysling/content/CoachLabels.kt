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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.platform.LengthUnit
import com.pico.spatial.ui.platform.PhysicalLengthConverter
import com.vrgames.gravitysling.core.Levels
import com.vrgames.gravitysling.core.Tutorial
import com.vrgames.gravitysling.game.Game

/** Attachment panel ids for the in-scene coaching labels (placed by the playfield). */
const val LABEL_BURN = "coach_burn"
const val LABEL_BALL = "coach_ball"
const val LABEL_MAP = "coach_map"
const val LABEL_INTRO = "intro"
const val LABEL_PULL = "coach_pull"
const val LABEL_RESULT = "result"
const val LABEL_MENU = "menu"
const val LABEL_BALL_INTRO = "ball_intro"
const val LABEL_LEVEL = "level_card"

/** Text height in the scene, so the labels read at arm's length to about 1.5 m. */
private const val TEXT_METERS = 0.022f

private val Ink = Color(0xFFEAF6FF)
private val Panel = Color(0xCC0B1426)

/**
 * A short instruction floating in the scene next to what it is about, with an animated pinch
 * icon. Sized in meters through [converter], so it stays the same physical size on any panel
 * density. [onPinch]: the label itself is pinchable (the burn label fires the engine).
 */
@Composable
fun CoachLabel(text: String, accent: Color, converter: PhysicalLengthConverter, onPinch: (() -> Unit)? = null) {
    fun m(meters: Float): Dp = converter.lengthToDp(meters, LengthUnit.Meters)
    val shape = RoundedCornerShape(m(0.02f))
    var mod = Modifier
        .widthIn(max = m(0.4f))
        .clip(shape)
        .background(Panel)
        .border(m(0.002f), accent, shape)
    if (onPinch != null) mod = mod.clickable { onPinch() }
    Row(
        mod.padding(horizontal = m(0.016f), vertical = m(0.01f)),
        horizontalArrangement = Arrangement.spacedBy(m(0.012f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PinchIcon(accent, m(0.034f))
        Text(text, fontSize = m(TEXT_METERS).value.sp, color = Ink)
    }
}

/** Thumb and index tip closing on each other, with a flash when they meet. */
@Composable
private fun PinchIcon(accent: Color, size: Dp) {
    val t by rememberInfiniteTransition(label = "pinch").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "pinchT",
    )
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        // Close over the first half, touch, then open again.
        val close = when {
            t < 0.45f -> t / 0.45f
            t < 0.7f -> 1f
            else -> 1f - (t - 0.7f) / 0.3f
        }
        val gap = w * (0.32f - 0.27f * close)
        val tip = Offset(w * 0.62f, w * 0.5f)
        val palm = Offset(w * 0.12f, w * 0.5f)
        val index = Offset(tip.x, tip.y - gap)
        val thumb = Offset(tip.x, tip.y + gap)
        val stroke = w * 0.07f
        drawLine(accent.copy(alpha = 0.7f), palm, index, strokeWidth = stroke)
        drawLine(accent.copy(alpha = 0.7f), palm, thumb, strokeWidth = stroke)
        drawCircle(accent, w * 0.1f, index)
        drawCircle(accent, w * 0.1f, thumb)
        if (close >= 1f) drawCircle(accent.copy(alpha = 0.6f), w * 0.3f, tip, style = Stroke(stroke))
    }
}

/** The three labels, as the stage's attachment panels. */
@Composable
fun BurnLabel(converter: PhysicalLengthConverter) =
    CoachLabel("捏一下手指 = 点火", Color(0xFFFFA45C), converter) { if (Game.requestBurn()) Game.refresh() }

@Composable
fun BallLabel(converter: PhysicalLengthConverter) =
    CoachLabel(Game.ballNotice ?: "引力球：会把飞过的探测器吸弯\n两指捏住它 → 拖到空中 → 张开手指放下", Color(0xFFB98CFF), converter)

@Composable
fun MapLabel(converter: PhysicalLengthConverter) =
    CoachLabel("捏住一颗星，去那一关", Color(0xFF6FE3FF), converter)

@Composable
fun PullLabel(converter: PhysicalLengthConverter) {
    val text = when (Game.tutorialStep) {
        Tutorial.Step.GRAB -> "① 用拇指和食指捏住它（别整只手抓）"
        Tutorial.Step.PULL -> "② 保持捏住，往身前拉"
        Tutorial.Step.RELEASE -> "③ 松开手指 = 发射！"
        else -> "捏住 · 往身前拉 · 松手发射"
    }
    CoachLabel(text, Color(0xFF6FE3FF), converter)
}

/**
 * The title card, floating in the scene about 0.75 m in front of the player (the console window
 * sits wherever the system puts it, often too far to read): the game in one line, the slingshot
 * in three steps, and a pinchable start.
 */
@Composable
fun IntroPanel(converter: PhysicalLengthConverter) {
    fun m(meters: Float): Dp = converter.lengthToDp(meters, LengthUnit.Meters)
    fun size(meters: Float) = m(meters).value.sp
    @Suppress("UNUSED_VARIABLE") val pv = Game.progressVersion
    val progress = Game.progress()
    val fresh = progress.totalStars() == 0 && !progress.cleared(Levels.all.first().id)
    val cyan = Color(0xFF6FE3FF)
    val gold = Color(0xFFFFD36B)
    val shape = RoundedCornerShape(m(0.025f))
    Column(
        Modifier.width(m(0.52f)).clip(shape).background(Panel).border(m(0.002f), cyan, shape)
            .padding(horizontal = m(0.03f), vertical = m(0.024f)),
        verticalArrangement = Arrangement.spacedBy(m(0.012f)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("引力弹弓 · 星驿快递", fontSize = size(0.042f), color = cyan)
        Text("借行星的引力拐弯，把包裹送进空间站。", fontSize = size(0.022f), color = Ink)
        Column(verticalArrangement = Arrangement.spacedBy(m(0.006f))) {
            Text("怎么玩：", fontSize = size(0.022f), color = gold)
            Text("① 看着发射架上的小探测器，用拇指和食指捏住它（两根手指捏，不要整只手去抓）", fontSize = size(0.02f), color = Ink)
            Text("② 保持捏住，把手往身前拉：拉得越远飞得越快，蓝线是预测航线", fontSize = size(0.02f), color = Ink)
            Text("③ 两根手指张开，就发射。收集星星，飞进空间站就算送达", fontSize = size(0.02f), color = Ink)
            Text("随时可以捏左下方的「菜单」：打开调度台、图鉴，或者退出（进度自动保存）", fontSize = size(0.017f), color = Color(0xFFB8C2D6))
        }
        CoachLabel(if (fresh) "捏一下这里：开始第一单" else "捏一下这里：继续派送（★ ${progress.totalStars()}）", gold, converter) {
            Game.startGame()
        }
        Text(DISCLAIMER, fontSize = size(0.0125f), color = Color(0xFFB8C2D6))
    }
}

/**
 * After a delivery, in front of the player: the stars, then "next delivery" and "again" as big
 * pinchable lines (the console window, which has the same buttons, may be far away).
 */
@Composable
fun ResultPanel(converter: PhysicalLengthConverter) {
    fun m(meters: Float): Dp = converter.lengthToDp(meters, LengthUnit.Meters)
    fun size(meters: Float) = m(meters).value.sp
    @Suppress("UNUSED_VARIABLE") val pv = Game.progressVersion
    val gold = Color(0xFFFFD36B)
    val cyan = Color(0xFF6FE3FF)
    val level = Levels.byId(Game.levelId)
    val next = Levels.all.getOrNull(Levels.all.indexOf(level) + 1)?.takeIf { Game.progress().unlocked(it) }
    val shape = RoundedCornerShape(m(0.025f))
    Column(
        Modifier.width(m(0.46f)).clip(shape).background(Panel).border(m(0.002f), gold, shape)
            .padding(horizontal = m(0.026f), vertical = m(0.02f)),
        verticalArrangement = Arrangement.spacedBy(m(0.012f)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("送达！${"★".repeat(Game.lastStars)}${"☆".repeat(3 - Game.lastStars)}" + if (Game.improved) "  新纪录" else "",
            fontSize = size(0.036f), color = gold)
        Game.celebration?.let { c ->
            Text(if (c.allDone) "全部航线完成！你已经是星驿快递的金牌调度员了。" else "${Levels.chapterTitles[c.chapter]} 完成！本章 ★ ${c.stars} / ${c.maxStars}",
                fontSize = size(0.02f), color = gold)
        }
        val cards = Game.newCards.mapNotNull { com.vrgames.gravitysling.core.Codex.byId(it)?.title }
        if (cards.isNotEmpty()) Text("新图鉴：${cards.joinToString("、")}（菜单 → 航天图鉴）", fontSize = size(0.018f), color = cyan)
        if (next != null) {
            CoachLabel("下一单：${if (next.challenge) "挑战" else next.id}「${next.title}」 →", cyan, converter) { Game.next() }
        } else {
            Text("这一章的航线都送完了！捏住上方星图里的星星，可以去任意一关。", fontSize = size(0.02f), color = Ink)
        }
        CoachLabel(if (Game.lastStars < 3) "再送一次（去拿满星星）" else "再送一次", Color(0xFFB8C2D6), converter) { Game.retry() }
        Text("也可以捏住上方星图里的任意一颗星，直接去那一关。", fontSize = size(0.016f), color = Color(0xFFB8C2D6))
    }
}

/**
 * The way back to everything once the windows are closed: a small "menu" tab beside the
 * launcher that unfolds into the console, the codex, the how-to and quitting.
 */
@Composable
fun MenuPanel(converter: PhysicalLengthConverter, openConsole: () -> Unit, openCodex: () -> Unit, quit: () -> Unit) {
    fun m(meters: Float): Dp = converter.lengthToDp(meters, LengthUnit.Meters)
    val cyan = Color(0xFF6FE3FF)
    val dim = Color(0xFFB8C2D6)
    if (!Game.menuOpen) {
        CoachLabel("菜单", dim, converter) { Game.menuOpen = true }
        return
    }
    val shape = RoundedCornerShape(m(0.02f))
    Column(
        Modifier.width(m(0.36f)).clip(shape).background(Panel).border(m(0.002f), dim, shape).padding(m(0.016f)),
        verticalArrangement = Arrangement.spacedBy(m(0.01f)),
    ) {
        CoachLabel("调度台：选关、辅助、高度", cyan, converter) { openConsole(); Game.menuOpen = false }
        CoachLabel("航天图鉴", cyan, converter) { openCodex(); Game.menuOpen = false }
        CoachLabel("本关说明", cyan, converter) { Game.levelCardDismissed = false; Game.menuOpen = false }
        CoachLabel("重看新手引导", cyan, converter) { Game.replayCoaching(); Game.menuOpen = false }
        Text("进度自动保存：每送达一单都会记下来。", fontSize = m(0.016f).value.sp, color = dim)
        CoachLabel("退出游戏", Color(0xFFFF8A7A), converter) { quit() }
        CoachLabel("收起菜单", dim, converter) { Game.menuOpen = false }
    }
}

/** What the gravity ball is, what it is for and how to move it; in front of the player the first time. */
@Composable
fun BallIntroPanel(converter: PhysicalLengthConverter) {
    fun m(meters: Float): Dp = converter.lengthToDp(meters, LengthUnit.Meters)
    fun size(meters: Float) = m(meters).value.sp
    val violet = Color(0xFFB98CFF)
    val gold = Color(0xFFFFD36B)
    val shape = RoundedCornerShape(m(0.025f))
    Column(
        Modifier.width(m(0.5f)).clip(shape).background(Panel).border(m(0.002f), violet, shape)
            .padding(horizontal = m(0.028f), vertical = m(0.022f)),
        verticalArrangement = Arrangement.spacedBy(m(0.01f)),
    ) {
        Text("新道具：引力球", fontSize = size(0.036f), color = violet)
        Text("是什么", fontSize = size(0.018f), color = gold)
        Text("发射架旁边那颗会发光的紫色小球，是一颗能用手搬动的小星球。它和地球、月球一样有引力。", fontSize = size(0.02f), color = Ink)
        Text("有什么用", fontSize = size(0.018f), color = gold)
        Text("探测器从它旁边飞过，会被它吸得拐弯。把它放在航线旁边，就能把航线「掰」向空间站。留在原位时它不起作用。", fontSize = size(0.02f), color = Ink)
        Text("怎么用", fontSize = size(0.018f), color = gold)
        Text("① 用拇指和食指捏住紫色小球（两根手指捏，不要整只手抓）\n② 保持捏住，拖到空中：半透明的球在示范放哪儿\n③ 两根手指张开，球就放下了，蓝色瞄准线会马上变弯\n④ 线对准空间站了，再去拉弹弓发射", fontSize = size(0.02f), color = Ink)
        Text("有些地方放不下（贴着发射架、挡住拉弓的位置、压住星星）：球上方会说明原因，松手后它会退回上一个能放的位置。", fontSize = size(0.016f), color = Color(0xFFB8C2D6))
        CoachLabel("知道了，试试看", violet, converter) { Game.ballIntroDismissed = true }
    }
}

/** The level's mission, at the start of each level: its name, the goal and the one tip. */
@Composable
fun LevelCard(converter: PhysicalLengthConverter) {
    fun m(meters: Float): Dp = converter.lengthToDp(meters, LengthUnit.Meters)
    fun size(meters: Float) = m(meters).value.sp
    val level = Levels.byId(Game.levelId)
    val cyan = Color(0xFF6FE3FF)
    val shape = RoundedCornerShape(m(0.022f))
    Column(
        Modifier.width(m(0.4f)).clip(shape).background(Panel).border(m(0.002f), cyan, shape)
            .padding(horizontal = m(0.022f), vertical = m(0.018f)),
        verticalArrangement = Arrangement.spacedBy(m(0.008f)),
    ) {
        Text((if (level.challenge) "挑战" else "任务 ${level.id}") + "「${level.title}」", fontSize = size(0.026f), color = cyan)
        Text(level.brief, fontSize = size(0.018f), color = Ink)
        Text("提示：${level.tip}", fontSize = size(0.016f), color = Color(0xFFB8C2D6))
        CoachLabel("知道了", cyan, converter) { Game.levelCardDismissed = true }
    }
}
