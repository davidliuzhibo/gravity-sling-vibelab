package com.vrgames.gravitysling

import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultStage
import com.pico.spatial.ui.foundation.dsl.Form
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import com.pico.spatial.ui.foundation.dsl.WindowContainer
import com.pico.spatial.ui.foundation.dsl.WindowContainerSize
import com.pico.spatial.ui.platform.LengthUnit
import com.vrgames.gravitysling.content.CodexWindow
import com.vrgames.gravitysling.content.ConsoleWindow
import com.vrgames.gravitysling.content.GameStage

const val CONSOLE_ID = "Console"
const val CODEX_ID = "Codex"

/**
 * Three linked spaces: the full-space solar system (Stage), the mission console (planar window)
 * and the encyclopedia (volumetric window). They share state through [com.vrgames.gravitysling.game.Game].
 */
fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        DefaultStage {
            PicoTheme { GameStage() }
        }
        WindowContainer(CONSOLE_ID, Form.Planar, { defaultSize = WindowContainerSize(680.dp, 1000.dp) }) {
            PicoTheme { ConsoleWindow() }
        }
        WindowContainer(CODEX_ID, Form.Volumetric, { defaultSize = WindowContainerSize(0.9f, 0.55f, 0.4f, LengthUnit.Meters) }) {
            PicoTheme { CodexWindow() }
        }
    }
