package com.vrgames.gravitysling.core

import org.junit.Assert.*
import org.junit.Test

/** The feedback round: a miss says how far and which way, and level 1-2 gets a real assist. */
class GuidanceTest {
    private fun session(id: String) = GameSession().apply { load(id) }

    private fun flyOut(s: GameSession, pull: Vec3) {
        s.setPull(pull)
        assertTrue(s.release())
        val out = ArrayList<FlightEvent>()
        var guard = 0
        while (s.phase == Phase.FLYING && guard++ < 20000) s.tick(0.05, out)
        assertEquals(Phase.RESULT, s.phase)
    }

    @Test fun missSaysHowFarAndWhichWay() {
        val r = MissReport(FlightStatus.LOST, closest = .30, offset = Vec3(.2, -.1, 0.0))
        val text = r.describe(goalRadius = .10)
        assertTrue(text, text.contains("20 厘米"))
        assertTrue(text, text.contains("右边"))
        assertTrue(text, text.contains("手再往右拉"))
        assertTrue(text, text.contains("手再往下压"))
    }

    @Test fun failedFlightRecordsAMissAndDeliveryClearsIt() {
        val s = session("1-2")
        flyOut(s, Vec3(.2, 0.0, 0.0))
        assertNotEquals(FlightStatus.SUCCESS, s.lastStatus)
        assertNotNull(s.lastMiss)
        assertEquals(1, s.failsOnLevel)
        s.reset()
        flyOut(s, s.level.solution!!.pull)
        assertEquals(FlightStatus.SUCCESS, s.lastStatus)
        assertNull(s.lastMiss)
    }

    @Test fun assistIsOfferedAfterTwoMissesAndItsPullDelivers() {
        val s = session("1-2")
        repeat(GameSession.ASSIST_AFTER_FAILS) {
            assertFalse(s.assistOffered)
            flyOut(s, Vec3(.2, 0.0, 0.0))
            s.reset()
        }
        assertTrue(s.assistOffered)
        s.enableAssist()
        val mark = s.assistPull!!
        s.setPull(mark + Vec3(.02, -.01, 0.0))
        assertEquals("a pull near the mark locks on", mark, s.pull)
        assertTrue("the assisted line must reach the station", s.aimRequest()!!.run().delivers)
    }

    @Test fun everyLevelWithASolutionHasAnAssistThatReallyDelivers() {
        for (level in Levels.all) {
            val solution = level.solution ?: continue
            if (solution.burnAt != null) continue
            val s = session(level.id)
            s.enableAssist()
            s.setPull(solution.pull)
            assertTrue(level.id, s.aimRequest()!!.run().delivers)
        }
    }

    @Test fun aimLineRequestChangesOnlyWhenTheAimChanges() {
        val s = session("1-1")
        val before = s.aimVersion
        assertEquals(before, s.aimVersion)
        s.setPull(Vec3(0.0, -.04, .17))
        assertTrue(s.aimVersion > before)
        val sameRun = s.aimRequest()!!.run().path
        assertEquals("worker result matches the live preview", s.preview(), sameRun)
    }

    @Test fun burnPreviewOnACopyLeavesTheRealFlightAlone() {
        val burnLevel = Levels.all.first { it.burn && it.solution != null }
        val s = session(burnLevel.id)
        s.setPull(burnLevel.solution!!.pull)
        burnLevel.solution!!.placements.forEach { (id, p) -> s.place(id, p) }
        assertTrue(s.release())
        val out = ArrayList<FlightEvent>()
        s.tick(.1, out)
        val before = s.flight!!.position()
        val fork = s.burnPreviewFork()!!
        assertTrue(s.runBurnPreview(fork).isNotEmpty())
        assertEquals(before, s.flight!!.position())
        assertFalse(s.flight!!.burnUsed)
    }
}
