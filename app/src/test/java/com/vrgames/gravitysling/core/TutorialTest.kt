package com.vrgames.gravitysling.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorialTest {
    @Test
    fun walksThroughGrabPullRelease() {
        val t = Tutorial(done = false)
        assertEquals(Tutorial.Step.GRAB, t.step)
        assertFalse(t.onPull(0.2)) // must grab first
        assertTrue(t.onGrab())
        assertFalse(t.onPull(0.01)) // too short
        assertTrue(t.onPull(0.1))
        assertTrue(t.onCancel()) // let go without firing
        assertEquals(Tutorial.Step.PULL, t.step)
        assertTrue(t.onPull(0.1))
        assertTrue(t.onLaunch())
        assertEquals(Tutorial.Step.DONE, t.step)
        assertFalse(t.active)
    }

    @Test
    fun finishedTutorialStaysHidden() {
        val t = Tutorial(done = true)
        assertNull(t.step)
        assertFalse(t.onGrab())
    }

    @Test
    fun chapterSummaries() {
        val p = Progress.parse("1-1:3,1-2:2,1-3:3,1-4:1,1-5:3,2-1:2")
        assertTrue(p.chapterCleared(1))
        assertFalse(p.chapterCleared(2))
        assertEquals(12, p.chapterStars(1))
        assertFalse(p.allCleared())
    }
}
