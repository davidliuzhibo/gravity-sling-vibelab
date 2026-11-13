package com.vrgames.gravitysling.content

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.platform.LengthUnit
import com.pico.spatial.ui.platform.PhysicalLengthConverter
import com.vrgames.gravitysling.core.Codex
import com.vrgames.gravitysling.core.Shelf
import com.vrgames.gravitysling.game.Game

const val LABEL_CODEX = "codex"

/** The codex panel's size, metres. */
const val CODEX_W = 0.72f
const val CODEX_H = 0.6f

private val PCyan = Color(0xFF6FE3FF)
private val PDim = Color(0xFFB8C2D6)
private val PGold = Color(0xFFFFC94D)
private val PInk = Color(0xFFEAF6FF)

/**
 * 航天图鉴 in the scene, at arm's length in front of the player (device test 2026-11-12: as a system window it opened too
 * far away and its text was small, and closing it was hard to find). Big text, the card's text scrolls when it is long,
 * and a close button at the bottom right. The model turns beside the panel.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CodexPanel(converter: PhysicalLengthConverter) {
    fun m(meters: Float): Dp = converter.lengthToDp(meters, LengthUnit.Meters)
    fun size(meters: Float) = m(meters).value.sp
    @Suppress("UNUSED_VARIABLE") val pv = Game.progressVersion
    val unlocked = Game.progress().unlockedCards().toSet()
    val selected = Game.selectedCard?.takeIf { it in unlocked } ?: unlocked.firstOrNull()
    val card = selected?.let { Codex.byId(it) }
    var shelf by remember { mutableStateOf(card?.shelf ?: Shelf.BODY) }
    val shape = RoundedCornerShape(m(0.02f))

    @Composable
    fun Chip(text: String, on: Boolean, enabled: Boolean = true, big: Boolean = false, onClick: () -> Unit) {
        val s = RoundedCornerShape(m(0.01f))
        Text(
            text,
            Modifier.clip(s).background(if (on) Color(0x556FE3FF) else Color(0x22FFFFFF)).border(m(0.0012f), if (on) PCyan else Color(0x33FFFFFF), s)
                .clickable(enabled = enabled) { onClick() }.padding(horizontal = m(if (big) 0.016f else 0.011f), vertical = m(if (big) 0.009f else 0.006f)),
            fontSize = size(if (big) 0.021f else 0.017f), color = if (enabled) Color.White else Color(0xFF8C96AA),
        )
    }

    Column(
        Modifier.size(m(CODEX_W), m(CODEX_H)).clip(shape).background(Color(0xEE0B1426)).border(m(0.002f), Color(0x886FE3FF), shape)
            .padding(m(0.018f)),
        verticalArrangement = Arrangement.spacedBy(m(0.008f)),
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(m(0.012f))) {
            Text("航天图鉴", fontSize = size(0.032f), color = PInk)
            val all = Codex.cards.size
            Text(if (unlocked.size >= all) "全部 $all 张收齐！小驿号已镀金" else "已收集 ${unlocked.size} / $all", fontSize = size(0.018f), color = if (unlocked.size >= all) PGold else PDim)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(m(0.008f))) {
            for (sh in Shelf.entries) {
                val have = Codex.onShelf(sh).count { it.id in unlocked }
                Chip("${sh.title} $have/${Codex.onShelf(sh).size}", sh == shelf, big = true) { shelf = sh }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(m(0.006f)), verticalArrangement = Arrangement.spacedBy(m(0.006f))) {
            for (c in Codex.onShelf(shelf)) {
                val open = c.id in unlocked
                Chip(if (open) c.title else "？？？", c.id == selected, enabled = open) {
                    Game.selectedCard = c.id
                    Game.showcase = c.id
                    Game.narrate(c.id)
                }
            }
        }
        // The card itself scrolls: big text never runs past the panel's edge.
        val scroll = rememberScrollState()
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(m(0.008f))) {
            if (card == null) Text("完成第一单，解锁第一张图鉴卡。", fontSize = size(0.022f), color = PInk)
            else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(m(0.01f))) {
                    Column(Modifier.weight(1f)) {
                        Text(card.title, fontSize = size(0.028f), color = PCyan)
                        Text(card.subtitle, fontSize = size(0.017f), color = PDim)
                    }
                    Chip(if (Game.narrating == card.id) "■ 停止讲解" else "▶ 听讲解", Game.narrating == card.id) {
                        Game.narrate(if (Game.narrating == card.id) null else card.id)
                    }
                }
                if (card.stats.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(m(0.008f))) {
                    for ((k, v) in card.stats) Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(m(0.008f))).background(Color(0x1A6FE3FF)).padding(m(0.006f)),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(k, fontSize = size(0.015f), color = PDim, textAlign = TextAlign.Center)
                        Text(v, fontSize = size(0.019f), color = PGold, textAlign = TextAlign.Center)
                    }
                }
                if (card.route.isNotEmpty()) RouteDrawing(card)
                Text(card.body, fontSize = size(0.021f), color = PInk)
            }
            Text(DISCLAIMER, fontSize = size(0.013f), color = Color(0xFF8C96AA))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (scroll.maxValue > 0) "内容较长：捏住文字上下拖动可以滚动" else "", fontSize = size(0.014f), color = PDim, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(m(0.01f)))
            // Closing in plain sight, bottom right (device test 2026-11-12: the old way was too hidden).
            Text(
                "关闭图鉴",
                Modifier.clip(RoundedCornerShape(m(0.012f))).background(PGold).clickable { Game.closeCodex() }
                    .padding(horizontal = m(0.022f), vertical = m(0.011f)),
                fontSize = size(0.022f), color = Color(0xFF1A1200),
            )
        }
    }
}
