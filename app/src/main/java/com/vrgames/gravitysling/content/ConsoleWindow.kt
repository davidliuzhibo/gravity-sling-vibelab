package com.vrgames.gravitysling.content

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import com.vrgames.gravitysling.CODEX_ID
import com.vrgames.gravitysling.core.Codex
import com.vrgames.gravitysling.core.FlightStatus
import com.vrgames.gravitysling.core.Levels
import com.vrgames.gravitysling.core.Phase
import com.vrgames.gravitysling.core.Tutorial
import com.vrgames.gravitysling.game.Game

private val Accent = Color(0xFF6FE3FF)
private val Gold = Color(0xFFFFD36B)
private val Dim = Color(0xFFB8C2D6)


const val DISCLAIMER = "本作品为虚构，与任何航天机构无关。美术由 AI 生成，音效由程序合成，背景音乐为 Synth-thetic 的《Cosmic Navigation》（CC0）。"

/** Mission control: title card first, then level select, briefing, flight status and assists. */
@Composable
fun ConsoleWindow() {
    if (Game.showTitle) TitleCard() else MissionControl()
}

@Composable
private fun TitleCard() {
    @Suppress("UNUSED_VARIABLE") val pv = Game.progressVersion
    val progress = Game.progress()
    val fresh = progress.totalStars() == 0 && !progress.cleared(Levels.all.first().id)
    Column(
        modifier = Modifier.fillMaxSize().padding(40.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("引力弹弓", fontSize = 60.sp, color = Accent)
        Text("星 驿 快 递", fontSize = 30.sp, color = Gold)
        Spacer(Modifier.height(8.dp))
        Text("把房间变成太阳系。\n拉开弹弓，借天体的引力弯曲航线，\n把包裹从月球送到木星。", fontSize = 23.sp)
        Spacer(Modifier.height(8.dp))
        Button(onClick = { Game.startGame() }) {
            Text(if (fresh) "开始第一单" else "继续派送（★ ${progress.totalStars()} / ${Levels.all.size * 3}）", fontSize = 25.sp)
        }
        Spacer(Modifier.height(24.dp))
        Text(DISCLAIMER, fontSize = 18.sp, color = Dim)
    }
}

@Composable
private fun MissionControl() {
    val navigator = LocalSpatialNavigator.current
    @Suppress("UNUSED_VARIABLE") val pv = Game.progressVersion // recompose on progress changes
    val progress = Game.progress()
    val level = Levels.byId(Game.levelId)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("星驿快递 · 调度台", fontSize = 30.sp)
            Spacer(Modifier.weight(1f))
            Text("★ ${progress.totalStars()} / ${Levels.all.size * 3}", fontSize = 23.sp, color = Gold)
            Button(onClick = { Game.showGuide = !Game.showGuide }) { Text(if (Game.showGuide) "收起说明" else "玩法说明") }
        }
        if (Game.showGuide) GuideCard()

        // Chapter tabs: a chapter opens once its first level is unlocked.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Levels.chapterTitles.forEach { (c, title) ->
                val first = Levels.all.first { it.chapter == c }
                val open = progress.unlocked(first)
                Button(onClick = {
                    if (open && c != level.chapter) {
                        val target = Levels.main.filter { it.chapter == c && progress.unlocked(it) }.lastOrNull() ?: first
                        Game.selectLevel(target.id)
                    }
                }) {
                    Text(if (open) (if (c == level.chapter) "● " else "") + title.substringAfter("· ") else "🔒", fontSize = 18.sp)
                }
            }
        }
        Text(Levels.chapterTitles[level.chapter] ?: "", fontSize = 21.sp, color = Dim)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Levels.all.filter { it.chapter == level.chapter }.forEach { l ->
                val open = progress.unlocked(l)
                val stars = progress.stars(l.id)
                val mark = if (l.id == level.id) "▶" else ""
                val name = if (l.challenge) "挑战" else "${l.number}"
                val badge = if (progress.badge(l.id)) "◆" else ""
                Button(onClick = { if (open) Game.selectLevel(l.id) }) {
                    Text(
                        if (open) "$mark$name$badge\n${"★".repeat(stars)}${"☆".repeat(3 - stars)}" else "$name\n🔒",
                        fontSize = 18.sp,
                    )
                }
            }
        }

        Game.celebration?.let { CelebrationCard(it) }
        Game.tutorialStep?.let { TutorialCard(it) }
        // A new move gets its own card the first time a level needs it (the scene shows it too).
        if (Game.tutorialStep == null) {
            if (Game.coachBall) CoachCard("新技能 · 引力球", BALL_COACH, Violet)
            else if (Game.coachBurn && level.burn) CoachCard("新技能 · 点火", BURN_COACH, Burn)
        }
        if (Game.newCards.isNotEmpty()) NewCardNote(Game.newCards)

        // Mission card.
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0x33223355)).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text((if (level.challenge) "挑战任务" else "任务 ${level.id}") + "「${level.title}」", fontSize = 25.sp, color = Accent)
            Text(level.brief, fontSize = 20.sp)
            Text("提示：${level.tip}", fontSize = 18.sp, color = Dim)
            if (level.burn) Text(BURN_TIP, fontSize = 18.sp, color = Burn)
        }

        // Live status from the stage.
        val status = when (Game.phase) {
            Phase.AIMING -> if (Game.blind) "盲射挑战：没有瞄准线，凭感觉拉弓" else "捏住探测器向身前拉，松手发射"
            Phase.FLYING -> buildString {
                append("飞行中… 已收集 ★${Game.starsThisFlight}")
                if (level.checkpoints.isNotEmpty()) append("，中继 ${Game.checkpointsThisFlight}/${level.checkpoints.size}")
                if (Game.burnCue) append("。慢动作：现在捏一下手指，点火！")
                else if (Game.canBurn) append("。捏一下手指：点火！") else if (Game.burnedThisFlight) append("。已点火")
            }
            Phase.RESULT -> when (Game.lastStatus) {
                FlightStatus.SUCCESS -> "送达！获得 ${"★".repeat(Game.lastStars)}${"☆".repeat(3 - Game.lastStars)}" +
                    (if (Game.blind) " ◆ 盲射徽章" else "") + if (Game.improved) "（新纪录）" else ""
                FlightStatus.CRASHED -> "撞上天体了，马上换一个探测器…"
                FlightStatus.LOST -> "飞出了投递区，马上换一个探测器…"
                FlightStatus.TIMEOUT -> "飞得太久，包裹超时了…"
                else -> ""
            }
        }
        Text(status, fontSize = 23.sp, color = if (Game.lastStatus == FlightStatus.SUCCESS) Gold else Color.White)
        Text("本关尝试 ${Game.attempts} 次", fontSize = 18.sp, color = Dim)

        if (Game.phase == Phase.RESULT && Game.lastStatus == FlightStatus.SUCCESS) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { Game.retry() }) { Text("再送一次") }
                val next = Levels.all.getOrNull(Levels.all.indexOf(level) + 1)
                if (next != null && progress.unlocked(next)) Button(onClick = { Game.next() }) { Text("下一单 →") }
            }
            Text("也可以在星图上捏住一颗星，直接去那一关。", fontSize = 18.sp, color = Dim)
        }

        Spacer(Modifier.height(4.dp))
        Text("辅助", fontSize = 19.sp, color = Dim)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { Game.toggleFullPreview() }) { Text(if (Game.fullPreview) "瞄准线：完整" else "瞄准线：开头") }
            Button(onClick = { Game.toggleHint() }) { Text(if (Game.blind) "盲射中不可看航线" else if (Game.showHint) "隐藏航线" else "看标准航线") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { Game.mapShown = !Game.mapShown }) { Text(if (Game.mapShown) "收起星图" else "打开星图") }
            // Blind challenge opens once the story is finished.
            if (progress.allCleared()) {
                Button(onClick = { Game.toggleBlind() }) { Text(if (Game.blind) "盲射挑战：开" else "盲射挑战：关") }
            }
        }
        if (progress.allCleared()) {
            Text("盲射挑战：不显示瞄准线和点火航线，送达就能得到这一关的 ◆ 徽章（已得 ${progress.badgeCount()} / ${Levels.all.size}）。",
                fontSize = 17.sp, color = Dim)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { Game.skyOn = !Game.skyOn }) { Text(if (Game.skyOn) "环境：星空" else "环境：房间") }
            Button(onClick = { Game.heightOffset -= 0.1f }) { Text("降低") }
            Button(onClick = { Game.heightOffset += 0.1f }) { Text("升高") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { navigator.openWindowContainer(CODEX_ID) }) { Text("打开航天图鉴") }
        }
    }
}

private val Burn = Color(0xFFFFA45C)
private val Violet = Color(0xFFB98CFF)

const val BALL_COACH = "紫色的引力球是一颗可以搬动的小行星：它和行星一样有引力，从它旁边飞过的探测器会被拉得拐弯。" +
    "留在原位时它不起作用。捏住它，拖到空中再松手，蓝色瞄准线会马上显示拐弯后的新航线；场景里的半透明球在示范该拖到哪。"
const val BURN_COACH = "发射之后还能操作：飞行途中时间会放慢一下，" +
    "这时捏一下手指（在哪儿捏都行），探测器就沿当前方向加速一次。橙色虚线是「现在点火」会走的航线。"

/** Every gesture in one place, for players who skipped or forgot the coaching. */
val GUIDE = listOf(
    "拉弓发射" to "捏住发射架上的探测器，往身前拉，松手发射。拉得越远飞得越快，蓝色瞄准线是预测的航线。",
    "摆放引力球" to "紫色引力球和行星一样有引力，能把航线拉弯；留在原位时不起作用。捏住它拖到空中松手，蓝色瞄准线会显示新航线。发射前可以反复调整。",
    "飞行中点火" to "第二章起：飞行时捏一下手指（在哪儿捏都行），探测器沿当前方向加速一次，每次飞行限一次。橙色虚线是「现在点火」会走的航线。",
    "星图选关" to "送达后，场景上方会浮现星图：每颗星是一关，捏住它就去那一关。也可以用本窗口的关卡按钮。",
    "看不清航线" to "「瞄准线：完整」显示整条预测航线；「看标准航线」显示参考解法。",
    "场景太高或太低" to "用本窗口下方的「升高」「降低」调整。",
)

@Composable
private fun CoachCard(title: String, text: String, color: Color) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(color.copy(alpha = 0.22f)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, fontSize = 21.sp, color = color)
        Text(text, fontSize = 21.sp)
    }
}

@Composable
private fun GuideCard() {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0x44223355)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("玩法说明", fontSize = 25.sp, color = Accent)
        GUIDE.forEach { (name, how) ->
            Text(name, fontSize = 20.sp, color = Gold)
            Text(how, fontSize = 19.sp)
        }
        Button(onClick = { Game.replayCoaching() }) { Text("重看新手引导") }
    }
}

const val BURN_TIP = "点火：飞行中捏一下手指，探测器沿当前方向加速一次（每次飞行限一次）。橙色虚线是「现在点火」会走的航线。"

/** After a delivery that unlocked encyclopedia cards: name them, one fact, and a way to read on. */
@Composable
private fun NewCardNote(ids: List<String>) {
    val navigator = LocalSpatialNavigator.current
    val cards = ids.mapNotNull { Codex.byId(it) }
    if (cards.isEmpty()) return
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0x4431E8A0)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("新图鉴：" + cards.joinToString("、") { it.title }, fontSize = 23.sp, color = Accent)
        cards.forEach { Text("· ${it.fact}", fontSize = 19.sp) }
        Button(onClick = {
            Game.selectedCard = cards.first().id
            navigator.openWindowContainer(CODEX_ID)
        }) { Text("打开图鉴看看") }
    }
}

@Composable
private fun TutorialCard(step: Tutorial.Step) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0x5531C4E8)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val n = step.ordinal + 1
        Text(if (step == Tutorial.Step.DONE) "新手引导 · 完成" else "新手引导 $n / 3", fontSize = 19.sp, color = Accent)
        Text(step.text, fontSize = 23.sp)
    }
}

@Composable
private fun CelebrationCard(c: Game.Celebration) {
    val navigator = LocalSpatialNavigator.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0x55E8B431)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (c.allDone) {
            Text("全部航线完成！", fontSize = 30.sp, color = Gold)
            Text("总星数 ★ ${Game.progress().totalStars()} / ${Levels.all.size * 3}。你已经是星驿快递的金牌调度员了。" +
                "每章的挑战关和「盲射挑战」都已开放，还没拿满的星星也随时可以回来补。", fontSize = 20.sp)
        } else {
            Text("${Levels.chapterTitles[c.chapter]} 完成！", fontSize = 27.sp, color = Gold)
            Text("本章 ★ ${c.stars} / ${c.maxStars}。下一章和本章的挑战关都已解锁。", fontSize = 20.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val next = Levels.all.firstOrNull { it.chapter == c.chapter + 1 }
            if (!c.allDone && next != null) Button(onClick = { Game.selectLevel(next.id) }) { Text("前往下一章 →") }
            Button(onClick = { navigator.openWindowContainer(CODEX_ID) }) { Text("看看图鉴") }
            Button(onClick = { Game.dismissCelebration() }) { Text("关闭") }
        }
    }
}
