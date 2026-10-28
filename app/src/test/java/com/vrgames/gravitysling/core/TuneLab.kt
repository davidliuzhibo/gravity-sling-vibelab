package com.vrgames.gravitysling.core

import org.junit.Test

/** Scratch diagnostics for level tuning; run with -Dlab=true. */
class TuneLab {
    @Test
    fun tune() {
        if (System.getProperty("lab") != "true") return
        val l24 = Levels.byId("2-4")
        val p = l24.solution!!.placements.getValue("ball1")
        val me = l24.bodies.first { it.id == "ball1" }
        println("2-4 bounds ok=${l24.bounds.contains(p.x, p.y, p.z)}")
        l24.bodies.filter { it.id != "ball1" }.forEach { b -> val d = p.distanceTo(b.pos); if (d < me.radius + b.radius + Placement.CLEARANCE) println("  near body ${b.id} d=$d") }
        println("  launch d=${p.distanceTo(l24.launch)} goal d=${p.distanceTo(l24.goal.pos)}")
        l24.stars.forEachIndexed { i, s -> println("  star$i d=${s.distanceTo(p)}") }
        for (id in listOf("2-5", "3-4", "3-5", "2-2")) {
            val l = Levels.byId(id)
            for (gr in listOf(0.09, 0.11, 0.13)) {
                val v = l.copy(goal = l.goal.copy(radius = gr), checkpoints = l.checkpoints.map { it.copy(radius = gr + 0.02) })
                println("$id goal r=$gr deliver=${Solver.robustness(v, v.solution!!, requireAllStars = false)}")
            }
        }
    }
}
