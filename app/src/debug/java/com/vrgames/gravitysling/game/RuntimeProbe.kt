package com.vrgames.gravitysling.game

import com.vrgames.gravitysling.render.Playfield
import java.lang.ref.WeakReference

/** In-process instrumentation only: no exported component, network or intent command surface. */
object RuntimeProbe {
    private var reference = WeakReference<Playfield>(null)
    var readyLevel: String? = null; private set
    var readyVersion: Int = -1; private set
    fun current(): Playfield? = reference.get()
    fun attach(playfield: Playfield) { reference = WeakReference(playfield); readyLevel = Game.levelId; readyVersion = Game.levelVersion }
    fun detach() { reference.clear(); readyLevel = null; readyVersion = -1 }
}
