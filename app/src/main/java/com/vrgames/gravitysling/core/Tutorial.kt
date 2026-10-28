package com.vrgames.gravitysling.core

/**
 * First-run coaching for the slingshot gesture: pinch the probe, pull it toward you, let go.
 * Advances only forward; a cancelled pull drops back to "pull" so the player can try again.
 */
class Tutorial(done: Boolean) {
    enum class Step(val text: String) {
        GRAB("看向发射架上的小驿号，用拇指和食指捏住它。"),
        PULL("保持捏合，把它往身前拉。拉得越远飞得越快，蓝色的瞄准线会告诉你它会飞向哪里。"),
        RELEASE("瞄准线对准星星了吗？松开手指，发射！"),
        DONE("很好！收集星星，把包裹送进空间站。打不中也没关系，探测器会自动补上。"),
    }

    var step: Step? = if (done) null else Step.GRAB; private set
    val active get() = step != null && step != Step.DONE

    /** Returns true if the step changed. */
    fun onGrab(): Boolean = advance(Step.GRAB, Step.PULL)

    fun onPull(length: Double): Boolean =
        if (length >= Physics.MIN_PULL * 1.5) advance(Step.PULL, Step.RELEASE) else false

    fun onLaunch(): Boolean = advance(Step.RELEASE, Step.DONE) || advance(Step.PULL, Step.DONE)

    /** A release without firing: ask for a longer pull. */
    fun onCancel(): Boolean = advance(Step.RELEASE, Step.PULL)

    fun dismiss() { step = null }

    private fun advance(from: Step, to: Step): Boolean {
        if (step != from) return false
        step = to
        return true
    }
}
